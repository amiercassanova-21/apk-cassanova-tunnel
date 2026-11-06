# Amier VPN

(Sebelumnya bernama *Cassanova Tunnel*, nama paket `com.cassanova.tunnel`. Sejak versi 1.1.0 nama paketnya `com.amier.vpn`, jadi terpasang sebagai aplikasi baru: backup config di aplikasi lama, pasang Amier VPN, lalu restore.)

Aplikasi tunnel Android: **Xray** (VMess, VLESS, Trojan, semua transport termasuk XHTTP/REALITY),
**Hysteria2**, dan **SSH** (Direct, HTTP Payload, SSL/SNI, SSL + Payload, SlowDNS TXT/NULL).

Berbasis [v2rayNG](https://github.com/2dust/v2rayNG) oleh 2dust (GPL-3.0). Kode sumber aplikasi ini
juga berlisensi GPL-3.0 (lihat `LICENSE`).

## Unduh

APK arm64-v8a terbaru ada di halaman **Releases** repo ini (dibangun otomatis oleh GitHub Actions
setiap ada perubahan di branch `main`).

## Tambahan dibanding v2rayNG

- Tipe profil **SSH / SlowDNS** (menu `+` → *Add [SSH / SlowDNS]*):
  - Mode: Direct, HTTP payload, SSL/TLS (SNI), SSL + payload, SlowDNS (DNSTT).
  - Payload mendukung `[host] [port] [host_port] [protocol] [ua] [raw] [crlf] [lf] [cr] [split] [delay_split] [instant_split]`.
  - SlowDNS: resolver `8.8.8.8`, `1.1.1.1:53`, `https://dns.google/dns-query`, `tls://1.1.1.1`; record type TXT atau NULL.
  - UDP (game, telepon WA) lewat BadVPN UDPGW (default port 7300); DNS tetap jalan lewat TCP bila UDPGW tidak ada.
  - Sambung ulang otomatis bila koneksi SSH putus.
- Nama & ikon Amier VPN.

## Komponen pihak ketiga

- [v2rayNG](https://github.com/2dust/v2rayNG) — GPL-3.0
- [Xray-core](https://github.com/XTLS/Xray-core) via AndroidLibXrayLite — MPL-2.0
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) — MIT
- [JSch (mwiede)](https://github.com/mwiede/jsch) — BSD-style
- [dnstt](https://www.bamsoftware.com/software/dnstt/) (fork [Mygod/dnstt](https://github.com/Mygod/dnstt)) — CC0, dengan tambahan record type NULL (lihat `dnstt/CASSANOVA.md`)
