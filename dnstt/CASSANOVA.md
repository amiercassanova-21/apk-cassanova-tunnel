# dnstt client for Cassanova Tunnel

Source: https://github.com/Mygod/dnstt (fork of https://www.bamsoftware.com/git/dnstt.git),
commit 17aa1fe, public domain (CC0, see COPYING).

Local change: `dnstt-client -type txt|null` selects the DNS record type used for the
tunnel. With `null`, queries use QTYPE NULL (10) and the payload is read raw from the NULL
RDATA. Tested against the Cassanova SlowDNS server (dnstt-server with NULL support).

Local change: every 15 s while data moves, the client logs one `stats` line (upload and
download rate, data/poll queries, replies with data or an error RCODE or TC, KCP
retransmits and losses, send-queue fill, packets dropped by full queues, SRTT/RTO). The
app shows it in the connection log to diagnose SlowDNS uploads (`dnstt-client/stats.go`).

Built for Android arm64 by `.github/workflows/cassanova-build.yml` into
`V2rayNG/app/libs/arm64-v8a/libdnstt.so`.
