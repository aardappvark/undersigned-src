# Security audit, 12 October 2026: findings and what was done

Three reviews ran against the code as submitted (commit `e434fb1` on the public mirror): the hackathon's scanner, and
two independent read-throughs of the server and of the app/protocol against the project's 18-rule baseline
(secrets, server-side trust, transport, operations). Everything below was fixed in **1.1.28 and 1.1.29 / API of 12 Oct** unless
marked accepted, and the public mirror carries the fixed code. Model and threat notes: `docs/PRIVACY_MODEL.md`,
`docs/ONCHAIN.md`, `docs/PAYMENTS.md`.

## Findings and fixes

| # | Severity | Finding | Fix |
|---|---|---|---|
| 1 | Critical (scanner) | `bcprov-jdk18on 1.78.1` carried four advisories | upgraded to 1.86 (`app/build.gradle.kts`) |
| 2 | High | SVG charts escaped `"` but not `'` while attributes were single-quoted; a crafted `category=` broke out of an attribute on the live host | `esc()` escapes `'` in both chart builders; every `image/svg+xml` response now carries `Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; sandbox` |
| 3 | High | Paid RPC keys compiled into the APK as string resources | removed; the app reads through the API's read-only JSON-RPC proxy (`POST /v1/rpc`): exactly the fifteen methods the app calls, params ≤ 4 KB, upstream answers ≤ 8 MB, its own budget of 120/min and 20,000/day per network outside the free tier; the key stays on the server with a daily call budget (`RPC_KEYED_DAILY_BUDGET`) past which public pools serve |
| 4 | High | The quick-review Ed25519 seed sat in plain SharedPreferences | sealed with AES-256-GCM under an Android Keystore key (`device/QuickKey.kt`, `device/Seal.kt`); a clear seed from an earlier build is re-sealed on first read; `dataExtractionRules` exclude it from device transfer |
| 5 | High | The fleet signing keystore had been committed to the private repository since the first commit | untracked and ignored; the public mirror was built with it removed from every commit; the private history is unchanged and private (its password was rotated before the commit) |
| 6 | Medium | Client IPs written to disk (relay log, AI allowance) against the privacy policy | the relayer and the AI ledger store a keyed, daily-rotating hash of the network, never the address, and delete rows older than two days; the policy now states exactly what the app sends |
| 7 | Medium | The abuse list missed look-alike scripts, zero-width and combining marks, bidi overrides and joiners (`fu‑ck`, Cyrillic `с`) | the normaliser applies NFKC, drops format and combining marks, folds a confusables map and removes joiners inside a word; tests cover each disguise |
| 8 | Medium | A moderator's ban dropped a wallet's records on apps the moderator publishes, with no recusal and no confirmation | recusal in `Moderation.excludeBanned` (a ban never touches records about an app its signer publishes), applied by the server and the app; the app confirms before signing a ban |
| 9 | Medium | The in-memory five-minute cache never evicted and was keyed on raw query strings | expired entries evicted once the cache passes 500 entries; `category` and `package` are validated against the catalogue before they can become keys (unknown values answer 404) |
| 10 | Medium | No HSTS; plain HTTP answered through the proxy | `Strict-Transport-Security` on the public host; HTTP behind the proxy is redirected to HTTPS |
| 11 | Medium | The publisher-proof fetch followed redirects with no private-network guard | https only, each redirect hop re-resolved and refused if private, 3 hops at most |
| 12 | Medium | No budget on the paid RPC key | daily budget on keyed endpoints, then public pools |
| 13 | Medium | The analytics key ships in the APK (public by design) with no server-side limits; the same key read `/stats` | the worker rate-limits per IPv6 /64 or IPv4 address, caps any row's count a day (500 for payment events, 50,000 otherwise), and reads `/stats` with a separate `STATS_KEY` once set. **Written, not yet deployed**: the Cloudflare CLI on the build machine is logged out |
| 14 | Low | Relayer: a replay of the same bytes took a second slot; a parallel duplicate could share a reservation | the relayer's own signature (the transaction id) is computed first and is the row key; a replay returns the same id; a duplicate insert is refused |
| 15 | Low | IPv6 buckets per /128 on the free tier | per /64, as the relayer already did |
| 16 | Low | MCP `get_wallet` only length-checked addresses | base58-decoded to 32 bytes |
| 17 | Low | Bidi and zero-width characters could reach a record's text and the web | stripped in `LongText.clean`, at review encoding and in the web escaper (script joiners stay) |
| 18 | Low | A third-party app could crash navigation with a malformed `pkg` intent extra | validated as a package name |
| 19 | Low | The registry address was a constant, never re-derived on the phone | re-derived at start from the seeds and the Memo program, fail-closed: posting (wallet reviews, quick reviews and payments) is enabled only after the derivation matched |
| 20 | Low | The wallet's MWA auth token in plain SharedPreferences | sealed under the Keystore; a plain token is sealed on first read |
| 21 | Low | systemd unit without sandboxing; Dockerfile without a digest or a user | `NoNewPrivileges`, `ProtectSystem=strict`, `PrivateTmp`, `ProtectHome`, `ReadWritePaths` only for the data; image pinned by digest and run as a non-root user |
| 22 | Low | `.env.local` on the developer machine world-readable | mode 600 |
| 23 | Low | A category key interpolated unescaped on one web line; `VACUUM INTO` built by concatenation (server-controlled path) | escaped; bound parameter |
| 24 | Low (scanner) | `handoff/*/support.js` assigned `innerHTML` | design mockups, not shipped code; absent from the public mirror's current tree (they remain in its earlier commits, where they are harmless) |
| 25 | Medium (scanner) | `stream-json`, `uuid`, `proxy-addr`, `fast-uri`, `ip-address`, MCP SDK advisories in the local MCP package | `npm audit fix` plus overrides for the two that come through `@solana/web3.js`; zero advisories remain |

**Accepted as designed** (documented, not changed): any key may have quick reviews relayed (bounded by per-key, per-network and global limits and shown apart, counted nowhere); store APK downloads for the found-treasury scan are bounded by free disk rather than a size cap (owner's choice, 9 Oct); CORS `*` on a cookie-less, bearer-token API; the exported launcher activity with deep links that only open screens and dialogs, never sign.

**Not fixable in code**: the keystore blob remains in the private repository's history. Anyone who held a clone before 12 Oct holds it (password-protected). Rotating the certificate would change the app's identity on the store, so the mitigation is the private repository plus the history-free public mirror.

## Second pass (same day)

An independent re-check of the 25 items confirmed 20 outright and found five only partly what this page first said; those rows are corrected above. It also raised eleven further points, fixed the same day: the proxy now sits outside the generic free budget with an explicit method set and body caps; webhook destinations are re-checked at every delivery; the registry check fails closed and covers quick reviews and payments; the ban recusal resolves the app of replies, withdrawals, fixes, decisions, follow-ups, disputes and flags through the record they name; the quick seed rotates only on a genuine decryption failure; look-alike folding applies only inside words that already carry a Latin letter (Belarusian and Serbian words are no longer flagged); analytics parameters are validated before caching; relayer fees come from an aggregate that is never pruned; the worker keys its limiter per /64 and evicts the oldest entry. Still open and documented: DNS rebinding between resolve and connect on the proof fetch (bounded by https and the per-hop check), and the deployment of the worker change.

## Baseline after the fixes

| Rule | State |
|---|---|
| 1–2 Secrets, nothing privileged in clients | pass: no secret in source or history blobs (6,050 scanned); the APK carries only the analytics key, which is public by design and server-limited |
| 3 Rotation | the keystore password was rotated; no API key ever reached git |
| 4 Spending caps | Anthropic per day and per caller; relayer 0.003 SOL a day; keyed RPC daily budget |
| 5 Server-side trust | all entitlements read from the chain by every reader |
| 6 Auth and limits | tiers, per-IP and per-key limits, admin on loopback only, the RPC proxy limited per network |
| 8 SQL | bound parameters only |
| 9 XSS | HTML escaped with a per-response CSP nonce; SVG escaped and sandboxed |
| 12 Inputs | body 16 KB; every record parsed by the shared codec; APK parsing capped |
| 14 Transport | HTTPS, HSTS, redirect; the app refuses cleartext |
| 15 Errors | generic 500s; no stack traces |
| 16 Logging | hashed networks, two-day retention; the policy states it |
| 17 Dependencies | BouncyCastle 1.86; `org.json 20240303`; `sqlite-jdbc 3.46.1.3`; MCP package with zero advisories |
| 18 Backups | daily, owner-only files, surfaced on `/v1/health`; off-host copies remain on the list |
