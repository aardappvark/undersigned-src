#!/bin/sh
# Moves the public API from this Mac to a Hetzner Cloud server (Ubuntu 24.04), keeping the same URL:
#   sh api/deploy-hetzner.sh <server-ip> [--fresh-db]  first time, and to redeploy a new build (the server's live
#                                                       database is kept; --fresh-db replaces it with this Mac's)
# What it does, in order:
#   1. builds the API here; snapshots the live database with sqlite3 .backup (consistent while the Mac API runs);
#   2. on the server (as root, over this Mac's SSH key): Java 21, sqlite3, cloudflared, a system user `undersigned`;
#   3. copies the build, the env, the database, the relayer key and the Claude key (0600, owned by `undersigned`);
#   4. runs the API as systemd `undersigned-api` on 127.0.0.1:8817, and the SAME Cloudflare tunnel as
#      `undersigned-tunnel`, so undersigned.provenby.dev now reaches the server;
#   5. only once the server answers through the tunnel, stops the Mac's tunnel (two connectors would split traffic
#      between two databases). The Mac's API keeps running locally as a warm backup: `launchctl kickstart -k
#      gui/$(id -u)/com.undersigned.tunnel` points the URL back at it.
# Secrets never pass through the command line or the logs.
set -e
IP="$1"; [ -n "$IP" ] || { echo "usage: sh api/deploy-hetzner.sh <server-ip>"; exit 1; }
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
LOCAL="$HOME/Library/Application Support/UndersignedAPI"
SSH="ssh -o StrictHostKeyChecking=accept-new root@$IP"
STAGE=$(mktemp -d); trap 'rm -rf "$STAGE"' EXIT
../gradlew -q -p .. :api:installDist
cp -R build/install/api/bin build/install/api/lib "$STAGE/"
sqlite3 "$LOCAL/undersigned-api.db" ".backup '$STAGE/undersigned-api.db'"
cp "$LOCAL/relayer.key" "$LOCAL/relayer.pub" "$STAGE/" 2>/dev/null || true
[ -f "$HOME/.config/undersigned/anthropic.env" ] && cp "$HOME/.config/undersigned/anthropic.env" "$STAGE/anthropic.env"
# The env, with the server's paths (RPC pool and public settings carried over from the Mac's env).
grep -E '^(UNDERSIGNED_RPC|UNDERSIGNED_PUBLIC|TRUST_PROXY|PUBLIC_URL|UNDERSIGNED_CONTACT)=' "$LOCAL/env" > "$STAGE/env" || true
printf 'UNDERSIGNED_DB=/opt/undersigned/undersigned-api.db\nPORT=8817\nHOST=127.0.0.1\n' >> "$STAGE/env"
# The tunnel: same id and credentials, pointed at the server's loopback.
TUNNEL=$(sed -n 's/^tunnel: *//p' "$HOME/.cloudflared/undersigned.yml")
CREDS=$(sed -n 's/^credentials-file: *//p' "$HOME/.cloudflared/undersigned.yml")
cp "$CREDS" "$STAGE/tunnel.json"
printf 'tunnel: %s\ncredentials-file: /etc/cloudflared/undersigned.json\ningress:\n  - hostname: undersigned.provenby.dev\n    service: http://127.0.0.1:8817\n  - service: http_status:404\n' "$TUNNEL" > "$STAGE/tunnel.yml"
chmod 600 "$STAGE"/*.key "$STAGE/env" "$STAGE/tunnel.json" "$STAGE/anthropic.env" 2>/dev/null || true

$SSH 'set -e
  export DEBIAN_FRONTEND=noninteractive
  command -v java >/dev/null || { apt-get update -q; apt-get install -yq openjdk-21-jre-headless sqlite3 curl || apt-get install -yq default-jre-headless sqlite3 curl; }
  command -v cloudflared >/dev/null || { curl -fsSL -o /tmp/cf.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.deb && dpkg -i /tmp/cf.deb; }
  id undersigned >/dev/null 2>&1 || useradd --system --home /opt/undersigned --shell /usr/sbin/nologin undersigned
  mkdir -p /opt/undersigned /etc/cloudflared /opt/undersigned/.config/undersigned
  systemctl stop undersigned-api 2>/dev/null || true'
[ "$2" = "--fresh-db" ] && touch "$STAGE/FRESH"
COPYFILE_DISABLE=1 tar -C "$STAGE" -cf - bin lib env relayer.key relayer.pub anthropic.env tunnel.json tunnel.yml undersigned-api.db $( [ -f "$STAGE/FRESH" ] && echo FRESH ) 2>/dev/null | $SSH 'set -e
  cd /tmp && rm -rf us-stage && mkdir us-stage && tar -C us-stage -xf -
  rm -rf /opt/undersigned/bin /opt/undersigned/lib && cp -R us-stage/bin us-stage/lib /opt/undersigned/
  cp us-stage/env /opt/undersigned/env
  # Once the server has a database it is the live one: never overwritten unless --fresh-db.
  [ -f us-stage/undersigned-api.db ] && { [ ! -f /opt/undersigned/undersigned-api.db ] || [ -f us-stage/FRESH ]; } && cp us-stage/undersigned-api.db /opt/undersigned/
  [ -f us-stage/relayer.key ] && cp us-stage/relayer.key us-stage/relayer.pub /opt/undersigned/
  [ -f us-stage/anthropic.env ] && cp us-stage/anthropic.env /opt/undersigned/.config/undersigned/anthropic.env
  cp us-stage/tunnel.json /etc/cloudflared/undersigned.json && cp us-stage/tunnel.yml /etc/cloudflared/undersigned.yml
  chown -R undersigned:undersigned /opt/undersigned && chmod 600 /opt/undersigned/env /opt/undersigned/relayer.key /opt/undersigned/.config/undersigned/anthropic.env 2>/dev/null || true
  chmod 600 /etc/cloudflared/undersigned.json; rm -rf us-stage
  cat > /etc/systemd/system/undersigned-api.service <<UNIT
[Unit]
Description=Undersigned API
After=network-online.target
[Service]
User=undersigned
WorkingDirectory=/opt/undersigned
Environment=HOME=/opt/undersigned
EnvironmentFile=/opt/undersigned/env
ExecStart=/opt/undersigned/bin/api
Restart=always
RestartSec=5
[Install]
WantedBy=multi-user.target
UNIT
  # Every six hours: recompute every score from the chain alone (a throwaway database) and diff the live dataset; /status shows the result.
  cat > /etc/systemd/system/undersigned-verify.service <<UNIT
[Unit]
Description=Undersigned: recompute scores from the chain and compare with the live dataset
After=undersigned-api.service
[Service]
Type=oneshot
User=undersigned
WorkingDirectory=/opt/undersigned
Environment=HOME=/opt/undersigned
EnvironmentFile=/opt/undersigned/env
ExecStart=/opt/undersigned/bin/api verify
StandardOutput=truncate:/opt/undersigned/verify.txt
StandardError=inherit
UNIT
  cat > /etc/systemd/system/undersigned-verify.timer <<UNIT
[Unit]
Description=Undersigned verify, every six hours
[Timer]
OnBootSec=10min
OnUnitActiveSec=6h
[Install]
WantedBy=timers.target
UNIT
  cat > /etc/systemd/system/undersigned-tunnel.service <<UNIT
[Unit]
Description=Cloudflare tunnel for undersigned.provenby.dev
After=network-online.target undersigned-api.service
[Service]
ExecStart=/usr/bin/cloudflared --no-autoupdate tunnel --config /etc/cloudflared/undersigned.yml run
Restart=always
RestartSec=5
[Install]
WantedBy=multi-user.target
UNIT
  systemctl daemon-reload
  systemctl enable --now undersigned-api
  for i in $(seq 1 60); do curl -fs http://127.0.0.1:8817/v1/health >/dev/null && break; sleep 2; done
  curl -fs http://127.0.0.1:8817/v1/health >/dev/null || { journalctl -u undersigned-api -n 40 --no-pager; exit 1; }
  systemctl enable --now undersigned-tunnel
  systemctl enable --now undersigned-verify.timer
  echo "server: API and tunnel up"'
# Hand the URL over: stop the Mac connector, then check the public URL answers from the server.
launchctl bootout "gui/$(id -u)/com.undersigned.tunnel" 2>/dev/null || true
printf '%s\n' "$IP" > "$LOCAL/server"   # the watchdog (watch-local.sh) now watches the public URL and this server
for i in $(seq 1 30); do curl -fs -A deploy https://undersigned.provenby.dev/v1/health >/dev/null && break; sleep 3; done
curl -fs -A deploy https://undersigned.provenby.dev/v1/health | head -c 200; echo
echo "Public URL now served by $IP. Backup: launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.undersigned.tunnel.plist"
