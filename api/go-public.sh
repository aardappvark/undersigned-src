#!/bin/sh
# Puts the local Undersigned API on https://$HOSTNAME through a Cloudflare Tunnel from this Mac (docs/DEPLOY.md option 2).
# One-time: `cloudflared tunnel login` opens the browser to choose the domain (the owner's Cloudflare login).
# Re-runnable. Undo: sh go-public.sh --off
set -e
cd "$(dirname "$0")"
HOSTNAME="${UNDERSIGNED_HOSTNAME:-undersigned.provenby.dev}"
CONTACT="${UNDERSIGNED_CONTACT:-aardappvark@proton.me}"
CF="$HOME/.cloudflared"; CONF="$CF/undersigned.yml"; PLIST="$HOME/Library/LaunchAgents/com.undersigned.tunnel.plist"
# Its OWN login file and settings: this Mac also runs other tunnels (tenderfeed), whose cert.pem and config.yml must never be
# used or replaced (7 Oct 2026: the shared cert routed this name into tenderfeed.dev; that record was deleted).
CERT="$CF/undersigned-cert.pem"
cf() { cloudflared --config "$CONF" --origincert "$CERT" "$@"; }
if [ "$1" = "--off" ]; then
  launchctl bootout "gui/$(id -u)/com.undersigned.tunnel" 2>/dev/null || true; rm -f "$PLIST"
  sed -i '' -E '/^(UNDERSIGNED_PUBLIC|TRUST_PROXY|PUBLIC_URL|UNDERSIGNED_CONTACT)=/d' .env.local
  sh install-local.sh; echo "Tunnel off; the API is local only again."; exit 0
fi
if [ ! -f "$CERT" ]; then
  # cloudflared login always writes cert.pem: park the other project's file, log in (choose the domain of $HOSTNAME), restore.
  [ -f "$CF/cert.pem" ] && mv "$CF/cert.pem" "$CF/cert.pem.parked"
  cloudflared tunnel login || { [ -f "$CF/cert.pem.parked" ] && mv "$CF/cert.pem.parked" "$CF/cert.pem"; exit 1; }
  mv "$CF/cert.pem" "$CERT"; [ -f "$CF/cert.pem.parked" ] && mv "$CF/cert.pem.parked" "$CF/cert.pem"
fi
[ -f "$CONF" ] || : > "$CONF"
cf tunnel list 2>/dev/null | grep -q ' undersigned ' || cf tunnel create undersigned
ID=$(cf tunnel list 2>/dev/null | awk '$2=="undersigned"{print $1}')
cat > "$CONF" <<C
tunnel: $ID
credentials-file: $CF/$ID.json
ingress:
  - hostname: $HOSTNAME
    service: http://127.0.0.1:8797
  - service: http_status:404
C
cf tunnel route dns --overwrite-dns "$ID" "$HOSTNAME"
# The API in public mode: real prices only, client address from the last X-Forwarded-For hop, links on the public name.
touch .env.local
sed -i '' -E '/^(UNDERSIGNED_PUBLIC|TRUST_PROXY|PUBLIC_URL|UNDERSIGNED_CONTACT)=/d' .env.local
sed -i '' -E '/^UNDERSIGNED_TEST_PASS_(SOL|SKR)=/d' .env.local      # the server refuses to start public with test prices
printf 'UNDERSIGNED_PUBLIC=1\nTRUST_PROXY=1\nPUBLIC_URL=https://%s\nUNDERSIGNED_CONTACT=%s\n' "$HOSTNAME" "$CONTACT" >> .env.local
sh install-local.sh
launchctl bootout "gui/$(id -u)/com.undersigned.tunnel" 2>/dev/null || true
cat > "$PLIST" <<P
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.undersigned.tunnel</string>
  <key>ProgramArguments</key><array><string>$(command -v cloudflared)</string><string>tunnel</string><string>--config</string><string>$CONF</string><string>run</string></array>
  <key>RunAtLoad</key><true/><key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>$HOME/Library/Application Support/UndersignedAPI/tunnel.log</string>
  <key>StandardErrorPath</key><string>$HOME/Library/Application Support/UndersignedAPI/tunnel.log</string>
</dict></plist>
P
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo "Public: https://$HOSTNAME/  (health: https://$HOSTNAME/v1/health)"
