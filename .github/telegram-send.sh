#!/usr/bin/env bash
# Mengirim APK ke channel Telegram lewat bot (dipakai workflow build). Pemakaian: telegram-send.sh FILE...
# Env: TG_TOKEN, TG_CHAT  : secret TELEGRAM_BOT_TOKEN / TELEGRAM_CHAT_ID (kosong → langkah dilewati)
#      TITLE             : judul HTML, mis. "<b>Amier SSH 1.0.0</b> (build 38)"
#      TAGS              : tagar aplikasi, mis. "#AmierSSH"
#      BODY              : teks biasa (perubahan); dipotong agar muat di keterangan Telegram
#      RELEASE_URL       : link rilis, disertakan bila ada file di atas batas 50 MB bot Telegram
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
body=$(printf '%s' "${BODY:-}" | cut_chars 700 | esc)
[ -n "$body" ] && caption+=$'\n'"$body"
if [ ${#big[@]} -gt 0 ]; then
  caption+=$'\n'"Di atas 50 MB, unduh dari rilis: $(printf '%s, ' "${big[@]}" | sed 's/, $//' | esc)"
  [ -n "${RELEASE_URL:-}" ] && caption+=$'\n'"<a href=\"${RELEASE_URL}\">Buka rilis</a>"
fi

if [ ${#small[@]} -eq 0 ]; then
  send sendMessage -F "chat_id=${TG_CHAT}" -F "parse_mode=HTML" --form-string "text=${caption}"
else
  first=1
  for f in "${small[@]}"; do
    if [ "$first" = 1 ]; then
      send sendDocument -F "chat_id=${TG_CHAT}" -F "document=@${f}" -F "parse_mode=HTML" --form-string "caption=${caption}"
      first=0
    else
      send sendDocument -F "chat_id=${TG_CHAT}" -F "document=@${f}" --form-string "caption=${TAGS:-}"
    fi
  done
fi
[ "$FAIL" = 0 ] && echo "Terkirim ke Telegram." || exit 1
