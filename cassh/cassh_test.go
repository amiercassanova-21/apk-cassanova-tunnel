package main

import (
	"bufio"
	"bytes"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/binary"
	"io"
	"math/big"
	"net"
	"strconv"
	"strings"
	"testing"
	"time"

	"golang.org/x/crypto/ssh"
)

// ---------- fixtures ----------

// echoServer echoes TCP data; udpgwServer echoes udpgw frames (same conid, address, data).
func listen(t *testing.T) net.Listener {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	return ln
}

func port(ln net.Listener) int { return ln.Addr().(*net.TCPAddr).Port }

func echoServer(t *testing.T) net.Listener {
	ln := listen(t)
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func() { defer c.Close(); _, _ = io.Copy(c, c) }()
		}
	}()
	return ln
}

func udpgwEcho(t *testing.T) net.Listener {
	ln := listen(t)
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func() {
				defer c.Close()
				hdr := make([]byte, 2)
				for {
					if _, err := io.ReadFull(c, hdr); err != nil {
						return
					}
					body := make([]byte, binary.LittleEndian.Uint16(hdr))
					if _, err := io.ReadFull(c, body); err != nil {
						return
					}
					_, _ = c.Write(append(hdr, body...))
				}
			}()
		}
	}()
	return ln
}

// sshServer accepts user/pass and serves direct-tcpip to any address.
func sshServer(t *testing.T) net.Listener {
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	signer, _ := ssh.NewSignerFromKey(key)
	cfg := &ssh.ServerConfig{
		PasswordCallback: func(m ssh.ConnMetadata, p []byte) (*ssh.Permissions, error) {
			if m.User() == "user" && string(p) == "pass" {
				return nil, nil
			}
			return nil, io.EOF
		},
	}
	cfg.AddHostKey(signer)
	ln := listen(t)
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func() {
				_, chans, reqs, err := ssh.NewServerConn(c, cfg)
				if err != nil {
					return
				}
				go ssh.DiscardRequests(reqs)
				for nc := range chans {
					if nc.ChannelType() != "direct-tcpip" {
						_ = nc.Reject(ssh.UnknownChannelType, "no")
						continue
					}
					var p struct {
						Host     string
						Port     uint32
						OrigHost string
						OrigPort uint32
					}
					_ = ssh.Unmarshal(nc.ExtraData(), &p)
					dst, err := net.Dial("tcp", net.JoinHostPort(p.Host, strconv.Itoa(int(p.Port))))
					if err != nil {
						_ = nc.Reject(ssh.ConnectionFailed, err.Error())
						continue
					}
					ch, rq, _ := nc.Accept()
					go ssh.DiscardRequests(rq)
					go func() { _, _ = io.Copy(dst, ch); dst.Close() }()
					go func() { _, _ = io.Copy(ch, dst); ch.CloseWrite(); ch.Close() }()
				}
			}()
		}
	}()
	return ln
}

// payloadProxy reads the payload header, answers 200 then 101, and pipes to the SSH server.
func payloadProxy(t *testing.T, sshAddr string, useTLS bool) net.Listener {
	ln := listen(t)
	var tcfg *tls.Config
	if useTLS {
		tcfg = &tls.Config{Certificates: []tls.Certificate{selfSigned(t)}}
	}
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func() {
				conn := c
				if tcfg != nil {
					conn = tls.Server(c, tcfg)
				}
				defer conn.Close()
				br := bufio.NewReader(conn)
				var buf bytes.Buffer
				for !bytes.HasSuffix(buf.Bytes(), []byte("\r\n\r\n")) {
					b, err := br.ReadByte()
					if err != nil {
						return
					}
					buf.WriteByte(b)
				}
				_, _ = conn.Write([]byte("HTTP/1.1 200 OK\r\n\r\nHTTP/1.1 101 Switching Protocols\r\n\r\n"))
				up, err := net.Dial("tcp", sshAddr)
				if err != nil {
					return
				}
				defer up.Close()
				go func() { _, _ = io.Copy(up, br) }()
				_, _ = io.Copy(conn, up)
			}()
		}
	}()
	return ln
}

func selfSigned(t *testing.T) tls.Certificate {
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	tpl := &x509.Certificate{SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "x"},
		NotBefore: time.Now().Add(-time.Hour), NotAfter: time.Now().Add(time.Hour)}
	der, err := x509.CreateCertificate(rand.Reader, tpl, tpl, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	return tls.Certificate{Certificate: [][]byte{der}, PrivateKey: key}
}

func startEngine(t *testing.T, cfg *Config) string {
	t.Helper()
	cfg.Listen = "127.0.0.1:0"
	tun := newTunnel(cfg)
	srv, err := newSocksServer(cfg.Listen, cfg.UdpgwPort, tun)
	if err != nil {
		t.Fatal(err)
	}
	go srv.serve()
	go tun.run()
	t.Cleanup(func() { tun.stop(); srv.close() })
	if tun.current(10*time.Second) == nil {
		t.Fatal("tunnel did not connect")
	}
	return srv.ln.Addr().String()
}

// socksConnect does a SOCKS5 CONNECT to 127.0.0.1:dstPort and returns the stream.
func socksConnect(t *testing.T, socks string, dstPort int) net.Conn {
	c, err := net.Dial("tcp", socks)
	if err != nil {
		t.Fatal(err)
	}
	_, _ = c.Write([]byte{5, 1, 0})
	r := make([]byte, 2)
	_, _ = io.ReadFull(c, r)
	_, _ = c.Write([]byte{5, 1, 0, 1, 127, 0, 0, 1, byte(dstPort >> 8), byte(dstPort)})
	rep := make([]byte, 10)
	if _, err := io.ReadFull(c, rep); err != nil || rep[1] != 0 {
		t.Fatalf("CONNECT failed: %v %v", rep, err)
	}
	return c
}

func assertEcho(t *testing.T, c net.Conn) {
	t.Helper()
	msg := []byte("hello cassanova")
	_ = c.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err := c.Write(msg); err != nil {
		t.Fatal(err)
	}
	got := make([]byte, len(msg))
	if _, err := io.ReadFull(c, got); err != nil || !bytes.Equal(got, msg) {
		t.Fatalf("echo mismatch %q %v", got, err)
	}
}

func payloadChunks() []Chunk {
	return []Chunk{{Text: "GET / HTTP/1.1\r\nHost: bug.example\r\n", DelayMs: 0},
		{Text: "Upgrade: websocket\r\n\r\n", DelayMs: 50}}
}

// ---------- tests ----------

func TestDirectConnect(t *testing.T) {
	sshLn, echo := sshServer(t), echoServer(t)
	socks := startEngine(t, &Config{Host: "127.0.0.1", Port: port(sshLn), User: "user", Pass: "pass", Mode: "direct"})
	c := socksConnect(t, socks, port(echo))
	defer c.Close()
	assertEcho(t, c)
}

func TestPayloadThroughProxyWithTwoReplies(t *testing.T) {
	sshLn, echo := sshServer(t), echoServer(t)
	proxy := payloadProxy(t, sshLn.Addr().String(), false)
	socks := startEngine(t, &Config{Host: "127.0.0.1", Port: port(sshLn), User: "user", Pass: "pass",
		Mode: "payload", ProxyPort: port(proxy),
		Variants: []Variant{{Chunks: payloadChunks(), ProxyHost: "127.0.0.1"}}})
	c := socksConnect(t, socks, port(echo))
	defer c.Close()
	assertEcho(t, c)
}

func TestSslPayloadWithSelfSignedCert(t *testing.T) {
	sshLn, echo := sshServer(t), echoServer(t)
	proxy := payloadProxy(t, sshLn.Addr().String(), true)
	socks := startEngine(t, &Config{Host: "127.0.0.1", Port: port(proxy), User: "user", Pass: "pass",
		Mode: "ssl_payload", Variants: []Variant{{Chunks: payloadChunks(), Sni: "bug.example"}}})
	c := socksConnect(t, socks, port(echo))
	defer c.Close()
	assertEcho(t, c)
}

func TestRotationMovesToWorkingVariant(t *testing.T) {
	sshLn, echo := sshServer(t), echoServer(t)
	proxy := payloadProxy(t, sshLn.Addr().String(), false)
	// Variant 1 points at a closed port, variant 2 at the working proxy.
	dead := listen(t)
	deadPort := port(dead)
	dead.Close()
	cfg := &Config{Host: "127.0.0.1", Port: port(sshLn), User: "user", Pass: "pass", Mode: "payload",
		ProxyPort: port(proxy),
		Variants: []Variant{
			{Chunks: payloadChunks(), ProxyHost: "127.0.0.2"},
			{Chunks: payloadChunks(), ProxyHost: "127.0.0.1"},
		}}
	_ = deadPort
	socks := startEngine(t, cfg)
	c := socksConnect(t, socks, port(echo))
	defer c.Close()
	assertEcho(t, c)
}

func TestUdpAssociateThroughUdpgw(t *testing.T) {
	sshLn, gw := sshServer(t), udpgwEcho(t)
	socks := startEngine(t, &Config{Host: "127.0.0.1", Port: port(sshLn), User: "user", Pass: "pass",
		Mode: "direct", UdpgwPort: port(gw)})
	ctrl, err := net.Dial("tcp", socks)
	if err != nil {
		t.Fatal(err)
	}
	defer ctrl.Close()
	_, _ = ctrl.Write([]byte{5, 1, 0})
	_, _ = io.ReadFull(ctrl, make([]byte, 2))
	_, _ = ctrl.Write([]byte{5, 3, 0, 1, 0, 0, 0, 0, 0, 0})
	rep := make([]byte, 10)
	if _, err := io.ReadFull(ctrl, rep); err != nil || rep[1] != 0 {
		t.Fatalf("UDP ASSOCIATE failed: %v %v", rep, err)
	}
	relay := &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1), Port: int(binary.BigEndian.Uint16(rep[8:10]))}
	u, err := net.DialUDP("udp", nil, relay)
	if err != nil {
		t.Fatal(err)
	}
	defer u.Close()
	pkt := append([]byte{0, 0, 0, 1, 8, 8, 8, 8, 0, 53}, []byte("dns-query")...)
	_, _ = u.Write(pkt)
	_ = u.SetReadDeadline(time.Now().Add(5 * time.Second))
	buf := make([]byte, 1500)
	n, err := u.Read(buf)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.Equal(buf[:n], pkt) {
		t.Fatalf("udp reply %v, want %v", buf[:n], pkt)
	}
}

func TestUdpgwFraming(t *testing.T) {
	frame := encodeUdpgw(7, net.IPv4(1, 2, 3, 4), 53, []byte("q"))
	if binary.LittleEndian.Uint16(frame) != uint16(len(frame)-2) {
		t.Fatal("length prefix")
	}
	p, ok := decodeUdpgw(frame[2:])
	if !ok || p.conID != 7 || p.port != 53 || !p.ip.Equal(net.IPv4(1, 2, 3, 4)) || string(p.data) != "q" {
		t.Fatalf("decode %+v", p)
	}
	v6 := encodeUdpgw(1, net.ParseIP("2001:db8::1"), 443, nil)
	if p, ok := decodeUdpgw(v6[2:]); !ok || p.ip.String() != "2001:db8::1" || p.port != 443 {
		t.Fatalf("v6 %+v", p)
	}
}

func TestWrongPasswordKeepsRetrying(t *testing.T) {
	sshLn := sshServer(t)
	cfg := &Config{Host: "127.0.0.1", Port: port(sshLn), User: "user", Pass: "bad", Mode: "direct", Listen: "127.0.0.1:0"}
	tun := newTunnel(cfg)
	go tun.run()
	defer tun.stop()
	if tun.current(1500*time.Millisecond) != nil {
		t.Fatal("connected with a wrong password")
	}
}

func TestChunkLatin1Bytes(t *testing.T) {
	c := Chunk{Text: "A\u00ff\u0080\r\n"}
	if got := c.Bytes(); !bytes.Equal(got, []byte{'A', 0xff, 0x80, '\r', '\n'}) {
		t.Fatalf("bytes %v", got)
	}
}

func TestEscapeLine(t *testing.T) {
	if got := escapeLine("a\\b\nc\r\td"); got != `a\\b\nc\r\td` {
		t.Fatalf("escape %q", got)
	}
}

func TestBannerIsEmitted(t *testing.T) {
	key, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	signer, _ := ssh.NewSignerFromKey(key)
	cfg := &ssh.ServerConfig{
		PasswordCallback: func(m ssh.ConnMetadata, p []byte) (*ssh.Permissions, error) { return nil, nil },
		BannerCallback:   func(m ssh.ConnMetadata) string { return "<b>Halo</b>\nBaris 2" },
	}
	cfg.AddHostKey(signer)
	ln := listen(t)
	go func() {
		c, err := ln.Accept()
		if err != nil {
			return
		}
		_, chans, reqs, err := ssh.NewServerConn(c, cfg)
		if err != nil {
			return
		}
		go ssh.DiscardRequests(reqs)
		for nc := range chans {
			_ = nc.Reject(ssh.Prohibited, "no")
		}
	}()
	var out bytes.Buffer
	outMu.Lock()
	prev := output
	output = &out
	outMu.Unlock()
	defer func() { outMu.Lock(); output = prev; outMu.Unlock() }()
	tun := newTunnel(&Config{Host: "127.0.0.1", Port: port(ln), User: "u", Pass: "p", Mode: "direct"})
	go tun.run()
	defer tun.stop()
	if tun.current(10*time.Second) == nil {
		t.Fatal("no connection")
	}
	outMu.Lock()
	got := out.String()
	outMu.Unlock()
	if !strings.Contains(got, "BANNER <b>Halo</b>\\nBaris 2\n") {
		t.Fatalf("banner line missing in %q", got)
	}
}

func TestUdpgwFailureWindowIsSharedAndLogsOnce(t *testing.T) {
	s := &socksServer{}
	now := time.Unix(1000, 0)
	if !s.udpgwUsable(now) {
		t.Fatal("udpgw should be tried at first")
	}
	if !s.markUdpgwDown(now) {
		t.Fatal("first failure should open a new window (log once)")
	}
	if s.markUdpgwDown(now.Add(time.Second)) {
		t.Fatal("second failure inside the window must not log again")
	}
	if s.udpgwUsable(now.Add(udpgwRetryAfter / 2)) {
		t.Fatal("udpgw must be skipped inside the window")
	}
	if !s.udpgwUsable(now.Add(udpgwRetryAfter + 2*time.Second)) {
		t.Fatal("udpgw should be retried after the window")
	}
}

func TestDNSCacheKeyIDAndExpiry(t *testing.T) {
	server := net.IPv4(8, 8, 8, 8)
	q1 := []byte{0x12, 0x34, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 3, 'c', 'o', 'm', 0, 0, 1, 0, 1}
	q2 := append([]byte(nil), q1...)
	q2[0], q2[1] = 0xAB, 0xCD
	k1, ok1 := dnsQuestionKey(server, q1)
	k2, ok2 := dnsQuestionKey(server, q2)
	if !ok1 || !ok2 || k1 != k2 {
		t.Fatal("same question with different IDs must share a key")
	}
	if _, ok := dnsQuestionKey(server, []byte{1, 2, 3}); ok {
		t.Fatal("short query must not be cached")
	}
	resp := append([]byte(nil), q1...)
	resp[2] = 0x81
	resp[3] = 0x80
	resp[6], resp[7] = 0, 1
	if !dnsAnswerCacheable(resp) {
		t.Fatal("NOERROR with an answer should be cacheable")
	}
	nx := append([]byte(nil), resp...)
	nx[3] = 0x83
	if dnsAnswerCacheable(nx) {
		t.Fatal("NXDOMAIN must not be cached")
	}
	c := newDNSCache(2)
	now := time.Unix(2000, 0)
	c.put(k1, resp, now)
	got := c.get(k2, now.Add(time.Second))
	if got == nil {
		t.Fatal("cache miss")
	}
	if out := withDNSID(got, q2); out[0] != 0xAB || out[1] != 0xCD || got[0] != 0x12 {
		t.Fatal("ID must be rewritten on a copy")
	}
	if c.get(k1, now.Add(dnsCacheTTL)) != nil {
		t.Fatal("expired entry must be dropped")
	}
}

func TestDNSCacheBounded(t *testing.T) {
	c := newDNSCache(2)
	now := time.Unix(3000, 0)
	for i := 0; i < 5; i++ {
		c.put(string(rune('a'+i)), []byte{1}, now)
	}
	if len(c.entries) > 2 {
		t.Fatalf("cache grew to %d", len(c.entries))
	}
}
