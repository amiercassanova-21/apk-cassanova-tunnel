package main

import (
	"encoding/binary"
	"errors"
	"io"
	"net"
	"strconv"
	"sync"
	"time"
)

const (
	sessionWait  = 20 * time.Second
	channelWait  = 15 * time.Second
	atypIPv4     = 1
	atypDomain   = 3
	atypIPv6     = 4
	cmdConnect   = 1
	cmdUDP       = 3
	repOK        = 0
	repHostUnrch = 4
	repCmdUnsupp = 7
)

// socksServer is a local SOCKS5 server (no auth) that forwards through the SSH tunnel.
type socksServer struct {
	ln        net.Listener
	udpgwPort int
	tun       *tunnel
}

func newSocksServer(addr string, udpgwPort int, tun *tunnel) (*socksServer, error) {
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		return nil, err
	}
	return &socksServer{ln: ln, udpgwPort: udpgwPort, tun: tun}, nil
}

func (s *socksServer) close() { _ = s.ln.Close() }

func (s *socksServer) serve() {
	for {
		c, err := s.ln.Accept()
		if err != nil {
			return
		}
		go s.handle(c)
	}
}

func (s *socksServer) handle(c net.Conn) {
	defer c.Close()
	if tc, ok := c.(*net.TCPConn); ok {
		_ = tc.SetNoDelay(true)
	}
	hdr := make([]byte, 2)
	if _, err := io.ReadFull(c, hdr); err != nil || hdr[0] != 5 {
		return
	}
	if _, err := io.ReadFull(c, make([]byte, hdr[1])); err != nil {
		return
	}
	if _, err := c.Write([]byte{5, 0}); err != nil {
		return
	}
	req := make([]byte, 3)
	if _, err := io.ReadFull(c, req); err != nil || req[0] != 5 {
		return
	}
	host, err := readAddr(c)
	if err != nil {
		return
	}
	pb := make([]byte, 2)
	if _, err := io.ReadFull(c, pb); err != nil {
		return
	}
	port := int(binary.BigEndian.Uint16(pb))

	switch req[1] {
	case cmdConnect:
		s.connect(c, net.JoinHostPort(host, strconv.Itoa(port)))
	case cmdUDP:
		s.udpAssociate(c)
	default:
		reply(c, repCmdUnsupp, 0)
	}
}

func readAddr(r io.Reader) (string, error) {
	t := make([]byte, 1)
	if _, err := io.ReadFull(r, t); err != nil {
		return "", err
	}
	switch t[0] {
	case atypIPv4:
		b := make([]byte, 4)
		_, err := io.ReadFull(r, b)
		return net.IP(b).String(), err
	case atypIPv6:
		b := make([]byte, 16)
		_, err := io.ReadFull(r, b)
		return net.IP(b).String(), err
	case atypDomain:
		n := make([]byte, 1)
		if _, err := io.ReadFull(r, n); err != nil {
			return "", err
		}
		b := make([]byte, n[0])
		_, err := io.ReadFull(r, b)
		return string(b), err
	}
	return "", errors.New("bad address type")
}

func reply(w io.Writer, rep byte, bindPort int) {
	_, _ = w.Write([]byte{5, rep, 0, atypIPv4, 127, 0, 0, 1, byte(bindPort >> 8), byte(bindPort)})
}

// dial opens a direct-tcpip channel, bounded by channelWait.
func (s *socksServer) dial(addr string) (net.Conn, error) {
	client := s.tun.current(sessionWait)
	if client == nil {
		return nil, errors.New("SSH not connected")
	}
	type result struct {
		c   net.Conn
		err error
	}
	ch := make(chan result, 1)
	go func() {
		c, err := client.Dial("tcp", addr)
		ch <- result{c, err}
	}()
	select {
	case r := <-ch:
		return r.c, r.err
	case <-time.After(channelWait):
		go func() {
			if r := <-ch; r.c != nil {
				_ = r.c.Close()
			}
		}()
		return nil, errors.New("channel open timeout")
	}
}

func (s *socksServer) connect(c net.Conn, addr string) {
	remote, err := s.dial(addr)
	if err != nil {
		reply(c, repHostUnrch, 0)
		return
	}
	defer remote.Close()
	reply(c, repOK, 0)
	done := make(chan struct{}, 2)
	go func() {
		_, _ = io.Copy(remote, c)
		closeWrite(remote)
		done <- struct{}{}
	}()
	go func() {
		_, _ = io.Copy(c, remote)
		closeWrite(c)
		done <- struct{}{}
	}()
	<-done
	<-done
}

func closeWrite(c net.Conn) {
	type cw interface{ CloseWrite() error }
	if x, ok := c.(cw); ok {
		_ = x.CloseWrite()
	} else {
		_ = c.Close()
	}
}

// ---------- UDP ASSOCIATE over badvpn-udpgw ----------

func (s *socksServer) udpAssociate(ctrl net.Conn) {
	pc, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.IPv4(127, 0, 0, 1)})
	if err != nil {
		reply(ctrl, repHostUnrch, 0)
		return
	}
	relay := &udpRelay{srv: s, pc: pc, conIDs: map[string]uint16{}, nextID: 1}
	defer relay.close()
	reply(ctrl, repOK, pc.LocalAddr().(*net.UDPAddr).Port)
	go relay.run()
	// The association lives as long as the TCP control connection.
	_, _ = io.Copy(io.Discard, ctrl)
}

type udpRelay struct {
	srv *socksServer
	pc  *net.UDPConn

	mu       sync.Mutex
	client   *net.UDPAddr
	gw       net.Conn
	gwFailed bool
	conIDs   map[string]uint16
	nextID   uint16
	closed   bool
}

func (r *udpRelay) close() {
	r.mu.Lock()
	r.closed = true
	gw := r.gw
	r.gw = nil
	r.mu.Unlock()
	_ = r.pc.Close()
	if gw != nil {
		_ = gw.Close()
	}
}

func (r *udpRelay) run() {
	buf := make([]byte, 65535)
	for {
		n, from, err := r.pc.ReadFromUDP(buf)
		if err != nil {
			return
		}
		r.mu.Lock()
		r.client = from
		r.mu.Unlock()
		pkt := append([]byte(nil), buf[:n]...)
		r.handle(pkt)
	}
}

// handle parses a SOCKS UDP request: RSV(2) FRAG(1) ATYP ADDR PORT DATA.
func (r *udpRelay) handle(p []byte) {
	if len(p) < 10 || p[2] != 0 {
		return
	}
	var ip net.IP
	off := 4
	switch p[3] {
	case atypIPv4:
		ip = net.IP(p[off : off+4])
		off += 4
	case atypIPv6:
		if len(p) < off+16+2 {
			return
		}
		ip = net.IP(p[off : off+16])
		off += 16
	case atypDomain:
		l := int(p[off])
		off++
		if len(p) < off+l+2 {
			return
		}
		addrs, err := net.LookupIP(string(p[off : off+l]))
		if err != nil || len(addrs) == 0 {
			return
		}
		ip = addrs[0]
		off += l
	default:
		return
	}
	if len(p) < off+2 {
		return
	}
	port := int(binary.BigEndian.Uint16(p[off : off+2]))
	data := p[off+2:]

	if r.ensureGateway() {
		r.sendGateway(ip, port, data)
	} else if port == 53 {
		go r.dnsOverTCP(ip, data)
	}
}

func (r *udpRelay) ensureGateway() bool {
	r.mu.Lock()
	if r.gw != nil || r.closed {
		ok := r.gw != nil
		r.mu.Unlock()
		return ok
	}
	if r.gwFailed {
		r.mu.Unlock()
		return false
	}
	r.mu.Unlock()

	gw, err := r.srv.dial(net.JoinHostPort("127.0.0.1", strconv.Itoa(r.srv.udpgwPort)))
	r.mu.Lock()
	defer r.mu.Unlock()
	if err != nil {
		r.gwFailed = true
		logf("udpgw 127.0.0.1:%d unavailable (%v); DNS falls back to TCP", r.srv.udpgwPort, err)
		return false
	}
	if r.closed {
		_ = gw.Close()
		return false
	}
	r.gw = gw
	go r.readGateway(gw)
	return true
}

const (
	udpgwFlagDNS  = 1 << 2
	udpgwFlagIPv6 = 1 << 3
)

func encodeUdpgw(conID uint16, ip net.IP, port int, data []byte) []byte {
	raw := ip.To4()
	flags := byte(0)
	if raw == nil {
		raw = ip.To16()
		flags |= udpgwFlagIPv6
	}
	bodyLen := 3 + len(raw) + 2 + len(data)
	out := make([]byte, 2+bodyLen)
	binary.LittleEndian.PutUint16(out[0:], uint16(bodyLen))
	out[2] = flags
	binary.LittleEndian.PutUint16(out[3:], conID)
	copy(out[5:], raw)
	binary.BigEndian.PutUint16(out[5+len(raw):], uint16(port))
	copy(out[7+len(raw):], data)
	return out
}

type udpgwPacket struct {
	conID uint16
	ip    net.IP
	port  int
	data  []byte
}

func decodeUdpgw(body []byte) (udpgwPacket, bool) {
	if len(body) < 3 {
		return udpgwPacket{}, false
	}
	flags := body[0]
	conID := binary.LittleEndian.Uint16(body[1:3])
	ipLen := 4
	if flags&udpgwFlagIPv6 != 0 {
		ipLen = 16
	}
	if len(body) < 3+ipLen+2 {
		return udpgwPacket{}, false
	}
	ip := net.IP(append([]byte(nil), body[3:3+ipLen]...))
	port := int(binary.BigEndian.Uint16(body[3+ipLen : 5+ipLen]))
	return udpgwPacket{conID, ip, port, body[5+ipLen:]}, true
}

func (r *udpRelay) sendGateway(ip net.IP, port int, data []byte) {
	r.mu.Lock()
	key := net.JoinHostPort(ip.String(), strconv.Itoa(port))
	id, ok := r.conIDs[key]
	if !ok {
		id = r.nextID
		r.conIDs[key] = id
		if r.nextID == 0xffff {
			r.nextID = 1
		} else {
			r.nextID++
		}
	}
	gw := r.gw
	r.mu.Unlock()
	if gw == nil {
		return
	}
	if _, err := gw.Write(encodeUdpgw(id, ip, port, data)); err != nil {
		logf("udpgw write failed: %v", err)
		r.mu.Lock()
		if r.gw == gw {
			r.gw = nil
		}
		r.mu.Unlock()
		_ = gw.Close()
	}
}

func (r *udpRelay) readGateway(gw net.Conn) {
	hdr := make([]byte, 2)
	for {
		if _, err := io.ReadFull(gw, hdr); err != nil {
			break
		}
		body := make([]byte, binary.LittleEndian.Uint16(hdr))
		if _, err := io.ReadFull(gw, body); err != nil {
			break
		}
		if p, ok := decodeUdpgw(body); ok {
			r.sendClient(p.ip, p.port, p.data)
		}
	}
	r.mu.Lock()
	if r.gw == gw {
		r.gw = nil
	}
	r.mu.Unlock()
}

func (r *udpRelay) dnsOverTCP(server net.IP, query []byte) {
	c, err := r.srv.dial(net.JoinHostPort(server.String(), "53"))
	if err != nil {
		logf("DNS over TCP failed: %v", err)
		return
	}
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(10 * time.Second))
	msg := make([]byte, 2+len(query))
	binary.BigEndian.PutUint16(msg, uint16(len(query)))
	copy(msg[2:], query)
	if _, err := c.Write(msg); err != nil {
		return
	}
	lb := make([]byte, 2)
	if _, err := io.ReadFull(c, lb); err != nil {
		return
	}
	resp := make([]byte, binary.BigEndian.Uint16(lb))
	if _, err := io.ReadFull(c, resp); err != nil {
		return
	}
	r.sendClient(server, 53, resp)
}

func (r *udpRelay) sendClient(ip net.IP, port int, data []byte) {
	r.mu.Lock()
	to := r.client
	r.mu.Unlock()
	if to == nil {
		return
	}
	raw := ip.To4()
	atyp := byte(atypIPv4)
	if raw == nil {
		raw = ip.To16()
		atyp = atypIPv6
	}
	out := make([]byte, 0, 4+len(raw)+2+len(data))
	out = append(out, 0, 0, 0, atyp)
	out = append(out, raw...)
	out = append(out, byte(port>>8), byte(port))
	out = append(out, data...)
	_, _ = r.pc.WriteToUDP(out, to)
}
