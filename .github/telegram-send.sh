#!/usr/bin/env bash
# Mengirim APK ke channel Telegram lewat bot (dipakai workflow build). Pemakaian: telegram-send.sh FILE...
# Bentuk pesan (seperti channel FlClash): semua file dalam satu kelompok, lalu keterangan di bawahnya:
#   judul + tagar / link (opsional) / "Perubahan" berisi daftar poin.
# Env: TG_TOKEN, TG_CHAT : secret TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID (kosong → langkah dilewati)
#      TITLE  : judul HTML, mis. "<b>Amier SSH 1.0.0</b> (build 38)"
#      TAGS   : tagar aplikasi, mis. "#AmierSSH"
#      LINK   : link yang ditampilkan (opsional; mis. halaman rilis repo publik)
#      BODY   : daftar perubahan, teks biasa satu poin per baris; dipotong agar muat di keterangan Telegram
#      RELEASE_URL : link rilis, disertakan bila ada file di atas batas 50 MB bot Telegram
set -euo pipefail
if [ -z "${TG_TOKEN:-}" ] || [ -z "${TG_CHAT:-}" ]; then
  echo "Secret Telegram belum diisi: langkah dilewati."
  exit 0
fi
LIMIT=$((49 * 1024 * 1024))
esc() { sed -e 's/&/\&amp;/g' -e 's/</\&lt;/g' -e 's/>/\&gt;/g'; }
# Memotong per karakter (bukan per byte) supaya huruf/emoji tidak terbelah.
cut_chars() { python3 -c 'import sys; t=sys.stdin.read(); n=int(sys.argv[1]); print(t if len(t)<=n else t[:n-1]+"…", end="")' "$1"; }
FAIL=0
send() { # METHOD ARGS... — token tidak pernah dicetak, hanya jawaban Telegram
  local r
  r=$(curl -sS -X POST "https://api.telegram.org/bot${TG_TOKEN}/$1" "${@:2}") || { echo "Gagal menghubungi Telegram."; FAIL=1; return; }
  grep -q '"ok":true' <<<"$r" || { echo "Telegram menolak: $r"; FAIL=1; }
}

small=(); big=()
for f in "$@"; do
  if [ "$(stat -c%s "$f")" -gt "$LIMIT" ]; then big+=("$(basename "$f")"); else small+=("$f"); fi
done

caption="${TITLE:-} ${TAGS:-}"
[ -n "${LINK:-}" ] && caption+=$'\n\n'"$(printf '%s' "$LINK" | esc)"
body=$(printf '%s' "${BODY:-}" | cut_chars 650 | esc)
[ -n "$body" ] && caption+=$'\n\n'"<b>Perubahan</b>"$'\n'"$body"
if [ ${#big[@]} -gt 0 ]; then
  caption+=$'\n\n'"Di atas 50 MB, unduh dari rilis: $(printf '%s, ' "${big[@]}" | sed 's/, $//' | esc)"
  [ -n "${RELEASE_URL:-}" ] && caption+=$'\n'"<a href=\"${RELEASE_URL}\">Buka rilis</a>"
fi

# Field teks memakai --form-string: dengan -F, nilai berawalan "@" (mis. ID channel @nama) dianggap nama file.
if [ ${#small[@]} -eq 0 ]; then
  send sendMessage --form-string "chat_id=${TG_CHAT}" --form-string "parse_mode=HTML" --form-string "text=${caption}"
elif [ ${#small[@]} -eq 1 ]; then
  send sendDocument --form-string "chat_id=${TG_CHAT}" -F "document=@${small[0]}" --form-string "parse_mode=HTML" --form-string "caption=${caption}"
else
  # Beberapa file → satu kelompok (album dokumen, maks. 10); keterangan pada file terakhir tampil di bawah kelompok.
  media="["; files=()
  last=$((${#small[@]} - 1))
  for i in "${!small[@]}"; do
    item=$(python3 -c 'import json,sys; d={"type":"document","media":"attach://f"+sys.argv[1]}
if sys.argv[2]=="1": d.update(caption=sys.argv[3], parse_mode="HTML")
print(json.dumps(d, ensure_ascii=False))' "$i" "$([ "$i" = "$last" ] && echo 1 || echo 0)" "$caption")
    media+="${item},"; files+=(-F "f${i}=@${small[$i]}")
  done
  media="${media%,}]"
  send sendMediaGroup --form-string "chat_id=${TG_CHAT}" --form-string "media=${media}" "${files[@]}"
fi
[ "$FAIL" = 0 ] && echo "Terkirim ke Telegram." || exit 1
