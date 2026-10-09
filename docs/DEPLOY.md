# Hosting the Undersigned API

**Live since 9 Oct 2026 on a Hetzner Cloud server**: CX23 (2 vCPU, 4 GB, 40 GB disk), Nuremberg, Ubuntu 26.04,
US$7.09 a month on prepaid credit. The public URL `https://undersigned.provenby.dev` reaches it through the same
Cloudflare tunnel that used to point at the owner's Mac.

| Piece | Where |
|---|---|
| API (JVM, SQLite) | systemd `undersigned-api`, user `undersigned`, `127.0.0.1:8817`, `/opt/undersigned` |
| Tunnel | systemd `undersigned-tunnel`, `/etc/cloudflared/undersigned.yml` |
| Database | `/opt/undersigned/undersigned-api.db`; a daily copy in `backups/`, 14 kept |
| Secrets (0600, owner `undersigned`) | `env`, `relayer.key` (the quick-review fee payer), `.config/undersigned/anthropic.env` |
| Watchdog | the owner's Mac, every 10 minutes (`watch-local.sh`): public `/v1/health`, and `/v1/admin/economics` over SSH |

Deploy a new build: `sh api/deploy-hetzner.sh 188.245.27.29`. It builds here, copies the build, and restarts the API;
the server's live database is never overwritten unless `--fresh-db` is given. Logs: `ssh root@<ip> journalctl -u undersigned-api`.

**If the server is down**: nothing on the ledger is lost (it is Solana). The app reads the chain itself and keeps
working, except for quick reviews, AI help and found-in-code wallets. The [browser viewer](https://aardappvark.github.io/undersigned-viewer/)
reads the ledger with no server. To serve the URL from the Mac again: `sh api/install-local.sh`, then
`launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.undersigned.tunnel.plist` (stop the server's tunnel first:
two connectors split traffic between two databases). Passes and redeemed payments live only in the server's database;
copy it back first.

**Known limit**: the watchdog runs on the Mac, so it only notifies while the Mac is awake.

## Environment (any host)
| Variable | Value |
|---|---|
| `UNDERSIGNED_PUBLIC=1` | Required. Turns off test prices and localhost webhooks; the server refuses to start with test prices set. |
| `TRUST_PROXY=1` | When a proxy or the platform's router sits in front: the client address is the LAST hop of X-Forwarded-For. |
| `HOST=0.0.0.0`, `PORT` | Listen address and port. |
| `PUBLIC_URL` | `https://your.host`: used in badge snippets, sitemap, feeds and the QR. |
| `UNDERSIGNED_RPC` | Comma-separated RPC URLs, keyed provider first. Public pools refuse `getProgramAccounts` (staked SKR, `.skr` names). |
| `UNDERSIGNED_DB` | Path on the persistent volume. |
| `UNDERSIGNED_CONTACT` | An address shown on `/report` for content complaints. |


## Operating it
- `GET /v1/health`: last syncs, last error, skipped transactions, snapshot days, last backup, webhook failures.
- Hide a record's text from the site: `bin/api hide <record id> <reason>`; undo with `bin/api unhide <record id>`;
  list with `bin/api hidden`. The running server applies it at its next rebuild (within five minutes).
