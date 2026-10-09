#!/bin/sh
# Looks at the local API's health every 10 minutes (LaunchAgent com.undersigned.api.watch, installed by
# install-local.sh) and posts a macOS notification when something needs the owner: the server is down, the indexer
# reports an error, a store snapshot day was missed, or webhook deliveries failed. One notification per distinct
# message per 6 hours.
DIR="$HOME/Library/Application Support/UndersignedAPI"
H=$(curl -s -m 10 http://127.0.0.1:8817/v1/health) || H=""
if [ -z "$H" ]; then MSG="The Undersigned API is not answering on this Mac."
else MSG=$(printf '%s' "$H" | /usr/bin/python3 -c '
import json, sys, datetime
j = json.load(sys.stdin); out = []
if j.get("last_error"): out.append("Indexer error: " + str(j["last_error"])[:90])
day = j.get("last_snapshot_day")
if day:
    age = (datetime.datetime.utcnow().date() - datetime.date.fromisoformat(day)).days
    if age >= 2: out.append("No store snapshot since %s (%d days). Store watch needs one every day." % (day, age))
if j.get("skipped_transactions", 0) > 0: out.append("%d ledger transaction(s) could not be read yet." % j["skipped_transactions"])
if j.get("hook_failures_24h", 0) > 0: out.append("%d webhook delivery failure(s) in 24 h." % j["hook_failures_24h"])
print(" ".join(out))
' 2>/dev/null)
fi
[ -z "$MSG" ] && { rm -f "$DIR/watch.state"; exit 0; }
NOW=$(date +%s); LAST=""; AT=0
[ -f "$DIR/watch.state" ] && { AT=$(head -1 "$DIR/watch.state"); LAST=$(tail -n +2 "$DIR/watch.state"); }
if [ "$MSG" != "$LAST" ] || [ $((NOW - AT)) -ge 21600 ]; then
  /usr/bin/osascript -e "display notification \"$(printf '%s' "$MSG" | sed 's/[\"\\]/ /g')\" with title \"Undersigned API\""
  printf '%s\n%s\n' "$NOW" "$MSG" > "$DIR/watch.state"
fi
