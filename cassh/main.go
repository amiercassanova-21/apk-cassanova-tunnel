// Command cassh is the native SSH engine of Cassanova Tunnel.
//
// It reads one JSON config line on stdin, keeps an SSH session to the server (over plain TCP,
// TCP + payload, TLS with SNI, TLS + payload, or the local dnstt port for SlowDNS) and exposes
// it as a SOCKS5 proxy (CONNECT and UDP ASSOCIATE via badvpn-udpgw) on the configured local
// address. Progress goes to stdout as "STATUS <state>" and "LOG <text>" lines. The process
// exits when stdin is closed, so it never outlives the app process that started it.
package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"os"
	"sync"
)

// Chunk is one payload write; DelayMs is waited before writing it. Text carries the bytes as
// Latin-1 code points (U+0000..U+00FF), the way the app builds payloads.
type Chunk struct {
	Text    string `json:"text"`
	DelayMs int    `json:"delayMs"`
}

// Bytes returns the Latin-1 bytes of Text.
func (c Chunk) Bytes() []byte {
	out := make([]byte, 0, len(c.Text))
	for _, r := range c.Text {
		out = append(out, byte(r))
	}
	return out
}

// Variant is one rotation step: payload chunks, SNI and remote proxy host.
type Variant struct {
	Chunks    []Chunk `json:"chunks"`
	Sni       string  `json:"sni"`
	ProxyHost string  `json:"proxyHost"`
}

// Config is the engine input, written by the app as one JSON line.
type Config struct {
	Host      string    `json:"host"`
	Port      int       `json:"port"`
	User      string    `json:"user"`
	Pass      string    `json:"pass"`
	Mode      string    `json:"mode"`
	ProxyPort int       `json:"proxyPort"`
	DnsttPort int       `json:"dnsttPort"`
	UdpgwPort int       `json:"udpgwPort"`
	Listen    string    `json:"listen"`
	Variants  []Variant `json:"variants"`
}

func (c *Config) usesTLS() bool     { return c.Mode == "ssl" || c.Mode == "ssl_payload" }
func (c *Config) usesPayload() bool { return c.Mode == "payload" || c.Mode == "ssl_payload" }

var (
	outMu  sync.Mutex
	output io.Writer = os.Stdout
)

func emit(kind, text string) {
	outMu.Lock()
	defer outMu.Unlock()
	fmt.Fprintf(output, "%s %s\n", kind, text)
}

func logf(format string, args ...any) { emit("LOG", fmt.Sprintf(format, args...)) }

func status(s string) { emit("STATUS", s) }

func main() {
	in := bufio.NewReaderSize(os.Stdin, 1<<20)
	line, err := in.ReadBytes('\n')
	if err != nil && len(line) == 0 {
		fmt.Fprintln(os.Stderr, "no config on stdin")
		os.Exit(2)
	}
	var cfg Config
	if err := json.Unmarshal(line, &cfg); err != nil {
		fmt.Fprintln(os.Stderr, "bad config:", err)
		os.Exit(2)
	}
	if cfg.UdpgwPort == 0 {
		cfg.UdpgwPort = 7300
	}

	tunnel := newTunnel(&cfg)
	srv, err := newSocksServer(cfg.Listen, cfg.UdpgwPort, tunnel)
	if err != nil {
		logf("SOCKS listen failed: %v", err)
		os.Exit(3)
	}
	go srv.serve()
	go tunnel.run()

	// The app closes stdin (or dies) to stop the engine.
	_, _ = io.Copy(io.Discard, in)
	tunnel.stop()
	srv.close()
	os.Exit(0)
}
