# dnstt client for Cassanova Tunnel

Source: https://github.com/Mygod/dnstt (fork of https://www.bamsoftware.com/git/dnstt.git),
commit 17aa1fe, public domain (CC0, see COPYING).

Local change: `dnstt-client -type txt|null` selects the DNS record type used for the
tunnel. With `null`, queries use QTYPE NULL (10) and the payload is read raw from the NULL
RDATA. Tested against the Cassanova SlowDNS server (dnstt-server with NULL support).

Built for Android arm64 by `.github/workflows/cassanova-build.yml` into
`V2rayNG/app/libs/arm64-v8a/libdnstt.so`.
