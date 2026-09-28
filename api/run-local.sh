#!/bin/sh
# Local run: builds the API, loads api/.env.local (git-ignored: RPC keys live there), serves on $PORT (8787).
cd "$(dirname "$0")" || exit 1
set -a; [ -f .env.local ] && . ./.env.local; set +a
export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
../gradlew -q -p .. :api:installDist || exit 1
exec ./build/install/api/bin/api
