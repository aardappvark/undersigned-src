#!/bin/sh
# Hosts the API on this Mac while it is tested and built: installs the build under
# ~/Library/Application Support/UndersignedAPI (outside ~/Downloads, which launchd jobs may not read) and runs it as a
# LaunchAgent that starts at login and restarts if it stops. Loopback only: http://127.0.0.1:8797.
# Re-run after changes to redeploy. Remove: launchctl bootout gui/$(id -u)/com.undersigned.api; rm ~/Library/LaunchAgents/com.undersigned.api.plist
set -e
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
DEST="$HOME/Library/Application Support/UndersignedAPI"
PLIST="$HOME/Library/LaunchAgents/com.undersigned.api.plist"
../gradlew -q -p .. :api:installDist
mkdir -p "$DEST"
launchctl bootout "gui/$(id -u)/com.undersigned.api" 2>/dev/null || true
rm -rf "$DEST/bin" "$DEST/lib"; cp -R build/install/api/bin build/install/api/lib "$DEST/"
# Settings: the RPC pool from .env.local (git-ignored); the database lives beside the install.
RPC=$(sed -n 's/^UNDERSIGNED_RPC=//p' .env.local 2>/dev/null | tail -1)
{ [ -n "$RPC" ] && printf 'UNDERSIGNED_RPC="%s"\n' "$RPC"; printf 'UNDERSIGNED_DB="%s"\nPORT=8797\nHOST=127.0.0.1\nJAVA_HOME="%s"\n' "$DEST/undersigned-api.db" "$JAVA_HOME"; } > "$DEST/env"
chmod 600 "$DEST/env"
# First install: carry over the database the local test run already built (snapshots, passes).
[ -f "$DEST/undersigned-api.db" ] || { [ -f data/undersigned-api.db ] && cp data/undersigned-api.db "$DEST/"; } || true
cat > "$PLIST" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>com.undersigned.api</string>
  <key>ProgramArguments</key><array><string>/bin/sh</string><string>-c</string><string>set -a; . ./env; set +a; exec ./bin/api</string></array>
  <key>WorkingDirectory</key><string>$DEST</string>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>$DEST/api.log</string>
  <key>StandardErrorPath</key><string>$DEST/api.log</string>
</dict></plist>
PL
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo "Undersigned API: http://127.0.0.1:8797/v1/health  (log: $DEST/api.log)"
