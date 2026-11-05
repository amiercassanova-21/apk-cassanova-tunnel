package main

import (
	"bufio"
	"bytes"
	"crypto/tls"
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"

	"golang.org/x/crypto/ssh"
)

const (
	connectTimeout   = 30 * time.Second
	dialTimeout      = 20 * time.Second
	keepaliveEvery   = 15 * time.Second
	keepaliveTimeout = 15 * time.Second
	keepaliveMisses  = 3
	replyWait        = 10 * time.Second
	secondReplyWait  = 1500 * time.Millisecond
	maxHeader        = 32 * 1024
)

// tunnel keeps one SSH client connected, reconnecting with backoff and rotating variants.
type tunnel struct {
	cfg *Config

	mu      sync.Mutex
	cond    *sync.Cond
	client  *ssh.Client
	stopped bool
	variant int
}

func newTunnel(cfg *Config) *tunnel {
	if len(cfg.Variants) == 0 {
		cfg.Variants = []Variant{{}}
	}
	t := &tunnel{cfg: cfg}
	t.cond = sync.NewCond(&t.mu)
	return t
}

// current waits up to timeout for a connected client.
func (t *tunnel) current(timeout time.Duration) *ssh.Client {
	deadline := time.Now().Add(timeout)
	t.mu.Lock()
	defer t.mu.Unlock()
	for t.client == nil && !t.stopped {
		left := time.Until(deadline)
		if left <= 0 {
			return nil
		}
		timer := time.AfterFunc(left, func() {
			t.mu.Lock()
			t.cond.Broadcast()
			t.mu.Unlock()
		})
		t.cond.Wait()
		timer.Stop()
	}
	return t.client
}

func (t *tunnel) stop() {
	t.mu.Lock()
	t.stopped = true
	c := t.client
	t.client = nil
	t.cond.Broadcast()
	t.mu.Unlock()
	if c != nil {
		_ = c.Close()
	}
}

func (t *tunnel) isStopped() bool {
	t.mu.Lock()
	defer t.mu.Unlock()
	return t.stopped
}

func backoff(attempt int) time.Duration {
	d := time.Duration(attempt) * 2 * time.Second
	if d > 30*time.Second {
		d = 30 * time.Second
	}
	return d
}

func (t *tunnel) run() {
	everConnected := false
	attempt := 0
	for !t.isStopped() {
		if everConnected {
			status("RECONNECTING")
		} else {
			status("CONNECTING")
		}
		attempt++
		n := len(t.cfg.Variants)
		v := t.cfg.Variants[t.variant%n]
		if n > 1 {
			logf("SSH trying variant %d/%d", t.variant%n+1, n)
		}
		client, err := t.connect(v)
		if err != nil {
			if t.isStopped() {
				return
			}
			logf("SSH connect failed (attempt %d): %v", attempt, err)
			if n > 1 {
				t.variant++
			}
			time.Sleep(backoff(attempt))
			continue
		}
		t.mu.Lock()
		if t.stopped {
			t.mu.Unlock()
			_ = client.Close()
			return
		}
		t.client = client
		t.cond.Broadcast()
		t.mu.Unlock()

		logf("SSH connected (%s)", string(client.ServerVersion()))
		everConnected = true
		attempt = 0
		status("CONNECTED")

		done := make(chan struct{})
		go t.keepalive(client, done)
		_ = client.Wait()
		close(done)

		t.mu.Lock()
		if t.client == client {
			t.client = nil
		}
		t.mu.Unlock()
		if !t.isStopped() {
			logf("SSH disconnected, reconnecting")
		}
	}
}

// keepalive closes the client after keepaliveMisses unanswered requests, so a dead link is
// noticed even when no traffic flows.
func (t *tunnel) keepalive(c *ssh.Client, done <-chan struct{}) {
	ticker := time.NewTicker(keepaliveEvery)
	defer ticker.Stop()
	misses := 0
	for {
		select {
		case <-done:
			return
		case <-ticker.C:
		}
		res := make(chan error, 1)
		go func() {
			_, _, err := c.SendRequest("keepalive@openssh.com", true, nil)
			res <- err
		}()
		var err error
		select {
		case err = <-res:
		case <-time.After(keepaliveTimeout):
			err = errors.New("timeout")
		case <-done:
			return
		}
		// Servers answer the unknown request with a failure reply: that still proves the link.
		if err != nil {
			misses++
			logf("SSH keepalive missed (%d/%d): %v", misses, keepaliveMisses, err)
			if misses >= keepaliveMisses {
				_ = c.Close()
				return
			}
		} else {
			misses = 0
		}
	}
}

func (t *tunnel) target(v Variant) string {
	c := t.cfg
	switch {
	case c.Mode == "slowdns":
		return net.JoinHostPort("127.0.0.1", strconv.Itoa(c.DnsttPort))
	case c.usesPayload() && v.ProxyHost != "" && c.ProxyPort > 0:
		return net.JoinHostPort(v.ProxyHost, strconv.Itoa(c.ProxyPort))
	default:
		return net.JoinHostPort(c.Host, strconv.Itoa(c.Port))
	}
}

func (t *tunnel) connect(v Variant) (*ssh.Client, error) {
	c := t.cfg
	addr := t.target(v)
	logf("Connecting to %s (%s)", addr, c.Mode)
	raw, err := net.DialTimeout("tcp", addr, dialTimeout)
	if err != nil {
		return nil, err
	}
	if tc, ok := raw.(*net.TCPConn); ok {
		_ = tc.SetNoDelay(true)
		_ = tc.SetKeepAlive(true)
		_ = tc.SetKeepAlivePeriod(30 * time.Second)
	}
	conn := net.Conn(raw)
	_ = conn.SetDeadline(time.Now().Add(connectTimeout))

	if c.usesTLS() {
		sni := v.Sni
		if sni == "" {
			sni = c.Host
		}
		tcfg := &tls.Config{InsecureSkipVerify: true} // injector servers use self-signed certs
		if sni != "" && net.ParseIP(sni) == nil {
			tcfg.ServerName = sni
		}
		tconn := tls.Client(conn, tcfg)
		if err := tconn.Handshake(); err != nil {
			_ = raw.Close()
			return nil, fmt.Errorf("TLS: %w", err)
		}
		state := tconn.ConnectionState()
		logf("TLS ready (SNI %s, %s)", orDash(tcfg.ServerName), tls.VersionName(state.Version))
		conn = tconn
	}

	if c.usesPayload() && len(v.Chunks) > 0 {
		for _, ch := range v.Chunks {
			if ch.DelayMs > 0 {
				time.Sleep(time.Duration(ch.DelayMs) * time.Millisecond)
			}
			if _, err := conn.Write(ch.Bytes()); err != nil {
				_ = conn.Close()
				return nil, fmt.Errorf("payload: %w", err)
			}
		}
		logf("Payload sent (%d part(s))", len(v.Chunks))
		br := bufio.NewReaderSize(conn, 64*1024)
		readReplies(conn, br)
		conn = &bufferedConn{Conn: conn, r: br}
	}

	cfg := clientConfig(c)
	sc, chans, reqs, err := ssh.NewClientConn(conn, net.JoinHostPort(c.Host, strconv.Itoa(c.Port)), cfg)
	if err != nil {
		_ = conn.Close()
		return nil, err
	}
	_ = conn.SetDeadline(time.Time{})
	return ssh.NewClient(sc, chans, reqs), nil
}

// readReplies consumes the HTTP replies ("HTTP/1.1 200 …", "HTTP/1.1 101 …") that follow the
// payload, so the SSH banner is read from the raw stream. Anything else is left for SSH.
func readReplies(conn net.Conn, br *bufio.Reader) {
	wait := replyWait
	for i := 0; i < 3; i++ {
		_ = conn.SetReadDeadline(time.Now().Add(wait))
		head, err := br.Peek(5)
		if err != nil {
			if i == 0 {
				logf("No reply to payload, continuing")
			}
			break
		}
		if string(head) != "HTTP/" {
			break
		}
		header, err := readHeader(br)
		if err != nil {
			break
		}
		line := header
		if idx := bytes.Index(header, []byte("\r\n")); idx >= 0 {
			line = header[:idx]
		}
		logf("Server reply: %s", string(line))
		// A short wait for a second reply (e.g. 200 then 101); none is fine.
		wait = secondReplyWait
	}
	_ = conn.SetReadDeadline(time.Now().Add(connectTimeout))
}

func readHeader(br *bufio.Reader) ([]byte, error) {
	var buf bytes.Buffer
	for buf.Len() < maxHeader {
		b, err := br.ReadByte()
		if err != nil {
			return nil, err
		}
		buf.WriteByte(b)
		if bytes.HasSuffix(buf.Bytes(), []byte("\r\n\r\n")) {
			return buf.Bytes(), nil
		}
	}
	return buf.Bytes(), nil
}

type bufferedConn struct {
	net.Conn
	r *bufio.Reader
}

func (b *bufferedConn) Read(p []byte) (int, error) { return b.r.Read(p) }

func clientConfig(c *Config) *ssh.ClientConfig {
	pass := c.Pass
	supported := ssh.SupportedAlgorithms()
	insecure := ssh.InsecureAlgorithms()
	cfg := &ssh.ClientConfig{
		User: c.User,
		Auth: []ssh.AuthMethod{
			ssh.Password(pass),
			ssh.KeyboardInteractive(func(name, instruction string, questions []string, echos []bool) ([]string, error) {
				answers := make([]string, len(questions))
				for i := range answers {
					answers[i] = pass
				}
				return answers, nil
			}),
		},
		// Tunnel servers change keys freely; there is no known_hosts on the phone.
		HostKeyCallback:   ssh.InsecureIgnoreHostKey(),
		HostKeyAlgorithms: append(append([]string{}, supported.HostKeys...), insecure.HostKeys...),
		Timeout:           connectTimeout,
		// The server banner (often HTML from tunnel autoscripts) goes to the app's connection log.
		BannerCallback: func(message string) error {
			if strings.TrimSpace(message) != "" {
				emit("BANNER", escapeLine(message))
			}
			return nil
		},
	}
	// Older Dropbear/OpenSSH builds on tunnel VPS still offer only SHA-1 key exchanges and CBC.
	cfg.KeyExchanges = append(append([]string{}, supported.KeyExchanges...), insecure.KeyExchanges...)
	cfg.Ciphers = append(append([]string{}, supported.Ciphers...), insecure.Ciphers...)
	cfg.MACs = append(append([]string{}, supported.MACs...), insecure.MACs...)
	return cfg
}

func orDash(s string) string {
	if s == "" {
		return "-"
	}
	return s
}

// escapeLine keeps a multi-line text on one output line: \ \n \r \t are escaped.
func escapeLine(s string) string {
	r := strings.NewReplacer("\\", "\\\\", "\n", "\\n", "\r", "\\r", "\t", "\\t")
	return r.Replace(s)
}
