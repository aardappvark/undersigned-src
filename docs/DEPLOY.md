# Putting the Undersigned API on a public host

Today it runs on the owner's Mac only (`api/install-local.sh`, http://127.0.0.1:8797). A public https host is what makes
badges, pages, web reviewing and the QR path usable by anyone, and keeps the daily store snapshots from missing days when
the Mac sleeps (5 of 7 days were read between 28 Sep and 4 Oct 2026).

## What a host needs
- A JVM 17 container or process, always on, with **one persistent volume** for the SQLite file and its `backups/` folder
  (14 daily copies are kept beside the database). Passes and redeemed payments are the only data the chain cannot rebuild.
- **https** with a stable name. Wallets only fetch Solana Pay transaction requests (the QR path) over https.
- Outbound access to: a Solana RPC (a keyed one first), dappstore.solanamobile.com, solanaappstore.com (store builds of
  claimed apps), api.trmlabs.com, api.coingecko.com, and publishers' websites for proof files.

## Environment
| Variable | Value |
|---|---|
| `UNDERSIGNED_PUBLIC=1` | Required. Turns off test prices and localhost webhooks; the server refuses to start with test prices set. |
| `TRUST_PROXY=1` | When a proxy or the platform's router sits in front: the client address is the LAST hop of X-Forwarded-For. |
| `HOST=0.0.0.0`, `PORT` | Listen address and port. |
| `PUBLIC_URL` | `https://your.host`: used in badge snippets, sitemap, feeds and the QR. |
| `UNDERSIGNED_RPC` | Comma-separated RPC URLs, keyed provider first. Public pools refuse `getProgramAccounts` (staked SKR, `.skr` names). |
| `UNDERSIGNED_DB` | Path on the persistent volume. |
| `UNDERSIGNED_CONTACT` | An address shown on `/report` for content complaints. |

## Options (owner's choice)
1. **A small always-on VM or container host** with a volume: build with `api/Dockerfile`, set the variables above.
2. **A tunnel from this Mac** (no new machine): keeps everything local but still depends on the Mac staying awake, so
   the missed-snapshot problem remains. `TRUST_PROXY=1` and `UNDERSIGNED_PUBLIC=1` are still required.

## Before the first public request
- [ ] Publish the updated Terms and Privacy pages (sections 2B, 8A and 5A describe the website, the listing fee, the API
      pass and what the server holds).
- [ ] Set `UNDERSIGNED_CONTACT` and read `/report` once.
- [ ] Decide the store-data question in `docs/API.md` §6 (selling passes that include store-derived series).
- [ ] Copy the local database across if the passes on it should survive (`backups/undersigned-YYYY-MM-DD.db`).
- [ ] Point the app's badge and page links at `PUBLIC_URL`.

## Operating it
- `GET /v1/health`: last syncs, last error, skipped transactions, snapshot days, last backup, webhook failures.
- Hide a record's text from the site: `bin/api hide <record id> <reason>`; undo with `bin/api unhide <record id>`;
  list with `bin/api hidden`. The running server applies it at its next rebuild (within five minutes).
