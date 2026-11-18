package main

// Cassanova: a periodic diagnostic line for the connection log, to find out why SlowDNS
// uploads stall on some networks (resolver errors, dropped queries, KCP retransmits or a
// full send queue).

import (
	"fmt"
	"log"
	"net"
	"time"

	"github.com/xtaci/kcp-go/v5"
	"www.bamsoftware.com/git/dnstt.git/turbotunnel"
)

const statsInterval = 15 * time.Second

// statsSnapshot holds cumulative counters at one moment.
type statsSnapshot struct {
	queriesData, queriesPoll, sendErrors        uint64
	replies, repliesData, repliesErr, repliesTC uint64
	packetsIn                                   uint64
	bytesUp, bytesDown                          uint64
	kcpOut, kcpRetrans, kcpLost                 uint64
	dropOut, dropIn                             uint64
}

func takeSnapshot(c *DNSPacketConn) statsSnapshot {
	k := kcp.DefaultSnmp.Copy()
	return statsSnapshot{
		queriesData: c.stats.queriesData.Load(),
		queriesPoll: c.stats.queriesPoll.Load(),
		sendErrors:  c.stats.sendErrors.Load(),
		replies:     c.stats.replies.Load(),
		repliesData: c.stats.repliesData.Load(),
		repliesErr:  c.stats.repliesErr.Load(),
		repliesTC:   c.stats.repliesTC.Load(),
		packetsIn:   c.stats.packetsIn.Load(),
		bytesUp:     k.BytesSent,
		bytesDown:   k.BytesReceived,
		kcpOut:      k.OutSegs,
		kcpRetrans:  k.RetransSegs,
		kcpLost:     k.LostSegs,
		dropOut:     turbotunnel.DroppedOutgoing.Load(),
		dropIn:      turbotunnel.DroppedIncoming.Load(),
	}
}

// sub returns cur - prev field by field.
func (cur statsSnapshot) sub(prev statsSnapshot) statsSnapshot {
	return statsSnapshot{
		queriesData: cur.queriesData - prev.queriesData,
		queriesPoll: cur.queriesPoll - prev.queriesPoll,
		sendErrors:  cur.sendErrors - prev.sendErrors,
		replies:     cur.replies - prev.replies,
		repliesData: cur.repliesData - prev.repliesData,
		repliesErr:  cur.repliesErr - prev.repliesErr,
		repliesTC:   cur.repliesTC - prev.repliesTC,
		packetsIn:   cur.packetsIn - prev.packetsIn,
		bytesUp:     cur.bytesUp - prev.bytesUp,
		bytesDown:   cur.bytesDown - prev.bytesDown,
		kcpOut:      cur.kcpOut - prev.kcpOut,
		kcpRetrans:  cur.kcpRetrans - prev.kcpRetrans,
		kcpLost:     cur.kcpLost - prev.kcpLost,
		dropOut:     cur.dropOut - prev.dropOut,
		dropIn:      cur.dropIn - prev.dropIn,
	}
}

// busy reports whether anything besides idle polling happened in delta d.
func (d statsSnapshot) busy() bool {
	return d.queriesData > 0 || d.repliesData > 0 || d.kcpRetrans > 0 || d.sendErrors > 0 ||
		d.repliesErr > 0 || d.dropOut > 0 || d.dropIn > 0
}

// formatStats renders delta d over secs seconds.
func formatStats(d statsSnapshot, secs float64, queueLen, queueCap int, srttMs, rtoMs int64) string {
	kb := func(b uint64) float64 { return float64(b) / 1024 / secs }
	return fmt.Sprintf("stats %.0fs: up %.1f KB/s down %.1f KB/s | queries data %d poll %d senderr %d"+
		" | replies %d data %d err %d tc %d | kcp out %d retrans %d lost %d"+
		" | queue %d/%d drop out %d in %d | srtt %dms rto %dms",
		secs, kb(d.bytesUp), kb(d.bytesDown), d.queriesData, d.queriesPoll, d.sendErrors,
		d.replies, d.repliesData, d.repliesErr, d.repliesTC, d.kcpOut, d.kcpRetrans, d.kcpLost,
		queueLen, queueCap, d.dropOut, d.dropIn, srttMs, rtoMs)
}

// statsLoop logs one line every statsInterval while the session moves data, until done closes.
func statsLoop(c *DNSPacketConn, conn *kcp.UDPSession, addr net.Addr, done <-chan struct{}) {
	ticker := time.NewTicker(statsInterval)
	defer ticker.Stop()
	prev := takeSnapshot(c)
	last := time.Now()
	for {
		select {
		case <-done:
			return
		case now := <-ticker.C:
			cur := takeSnapshot(c)
			d := cur.sub(prev)
			if d.busy() {
				q := c.QueuePacketConn.OutgoingQueue(addr)
				log.Print(formatStats(d, now.Sub(last).Seconds(), len(q), cap(q),
					int64(conn.GetSRTT()), int64(conn.GetRTO())))
			}
			prev, last = cur, now
		}
	}
}
