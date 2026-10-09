#!/bin/sh
# Looks at the local API's health every 10 minutes (LaunchAgent com.undersigned.api.watch, installed by
# install-local.sh) and posts a macOS notification when something needs the owner: the server is down, the indexer
# reports an error, a store snapshot day was missed, or webhook deliveries failed. One notification per distinct
# message per 6 hours. Also: the AI cost alerts from /v1/admin/economics (10 Oct 2026).
# Once the API runs on a server (deploy-hetzner.sh writes its address to "server" beside the database), this watches the
# PUBLIC URL instead, and reads the server's economics over SSH (that route answers on the server's loopback only).
DIR="$HOME/Library/Application Support/UndersignedAPI"
SERVER=$(cat "$DIR/server" 2>/dev/null)
if [ -n "$SERVER" ]; then
  H=$(curl -s -m 15 -A undersigned-watch https://undersigned.provenby.dev/v1/health) || H=""
  ECON="ssh -o BatchMode=yes -o ConnectTimeout=10 root@$SERVER curl -s -m 10 http://127.0.0.1:8817/v1/admin/economics?days=7"
  WHERE="at https://undersigned.provenby.dev (server $SERVER)"
else
  H=$(curl -s -m 10 http://127.0.0.1:8817/v1/health) || H=""
  ECON="curl -s -m 10 http://127.0.0.1:8817/v1/admin/economics?days=7"
  WHERE="on this Mac"
fi
if [ -z "$H" ]; then MSG="The Undersigned API is not answering $WHERE."
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
  # Cost against revenue (Economics.kt): a costly day, or a week of AI cost above revenue plus a floor.
  E=$($ECON) || E=""
  EMSG=$(printf '%s' "$E" | /usr/bin/python3 -c 'import json,sys; print(" ".join(json.load(sys.stdin).get("alerts", [])))' 2>/dev/null)
  [ -n "$EMSG" ] && MSG="$MSG $EMSG"
  MSG=$(printf '%s' "$MSG" | sed 's/^ *//')
fi
[ -z "$MSG" ] && { rm -f "$DIR/watch.state"; exit 0; }
NOW=$(date +%s); LAST=""; AT=0
[ -f "$DIR/watch.state" ] && { AT=$(head -1 "$DIR/watch.state"); LAST=$(tail -n +2 "$DIR/watch.state"); }
if [ "$MSG" != "$LAST" ] || [ $((NOW - AT)) -ge 21600 ]; then
  /usr/bin/osascript -e "display notification \"$(printf '%s' "$MSG" | sed 's/[\"\\]/ /g')\" with title \"Undersigned API\""
  printf '%s\n%s\n' "$NOW" "$MSG" > "$DIR/watch.state"
fi
