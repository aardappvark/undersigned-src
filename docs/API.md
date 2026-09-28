# Undersigned API and MCP — design (28 Sep 2026)

Everything the app computes on a phone, served to programs: other dApps, wallets, agents, researchers, the store's own
tooling. Same `:protocol` module as the app, so a score from the API is the score the app shows. Read-only toward the chain
and the dApp Store: the API never signs, never moves funds, never writes to the store.

Code: `api/` (Kotlin, JDK HTTP server, SQLite), `mcp/` (Node, stdio MCP server that calls the API).

## 1. What the data can provide

Tier: **F** = free (no key), **P** = paid pass. Everything is public data or derived from public data; nothing about the
caller's own device is available (install age and usage time exist only as what reviewers chose to sign).

### A. The ledger (on-chain, SRL1 records on registry `BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs`)
| Data | Endpoint | Tier |
|---|---|---|
| Every decoded record, newest first, paginated by cursor; filter by type (`rev rvk rep clm flg clr hlp dsp use tim snt trs trx` …), package, signer, time range | `GET /v1/records` | P |
| One record by id (`sig` or `sig:n`): decoded memo, signer, block time, network fee, Seeker-wallet transaction shape, revoked or not | `GET /v1/records/{id}` | F |
| Ledger totals: records, live reviews, counted reviews, signers, attested signers, apps, replies, publisher replies, claims (verified), flags, clears, disputes, hidden, withdrawn, licences, first and latest record | `GET /v1/stats` | F |
| Tag vocabulary (24 wire values) with counts across the ledger | `GET /v1/tags` | F |

### B. A dApp, from signed reviews
| Data | Endpoint | Tier |
|---|---|---|
| Signed score: rank score (shrunk toward 3.0, for ordering), weighted mean (for display), counted reviews, star distribution, tag counts, latest version reviewed | `GET /v1/apps/{pkg}` | F |
| Signed reviews, each with: rating, tags, text, version, signer, attested device, weight, staked-SKR weight, `.skr` name, install age on their phone (`use`), usage time (`tim`), reward season, "paid for this app", similar-text finding, flags, helpful weight, dispute, hidden-by-consensus, withdrawn, replies (publisher badge), self-published exclusion | `GET /v1/apps/{pkg}/reviews` | F (first 20) / P (all) |
| Scores per version (mean and count per versionCode) | `GET /v1/apps/{pkg}/versions` | P |
| Review bursts: 5★/1★ clusters vs the app's median pace, window, count vs expected, multiple (×), attested share, same-text count, season overlap, 30-day daily sparkline | `GET /v1/apps/{pkg}/bursts` | P |
| Install-age and usage-time distributions of reviewers (buckets d0…m3, t0…t4) | inside `/v1/apps/{pkg}` | F |
| Publisher responsiveness: answered/total, reply-time bucket | inside `/v1/apps/{pkg}` | F |
| Copy-paste findings: reviews whose text matches others (same app or across apps) | `GET /v1/apps/{pkg}/similar` | P |

### C. The dApp Store, mirrored read-only
| Data | Endpoint | Tier |
|---|---|---|
| Catalogue: every listed app (package, name, category, last updated) | `GET /v1/apps?category=&q=&sort=` | F |
| Store details: latest versionCode, signing-certificate fingerprint, publisher name, website, support email, "What's new" | inside `/v1/apps/{pkg}` | F |
| Store rating and star counts, as of the last read | inside `/v1/apps/{pkg}` | F |
| Store rating history: daily star-count snapshots the server has taken | `GET /v1/apps/{pkg}/store-ratings` | P |
| Store-rating bursts: sudden jumps in 5★ or 1★ counts between snapshots | `GET /v1/apps/{pkg}/bursts` | P |
| Store watch: every app with a ratings burst in the last N days, strongest first | `GET /v1/watch?days=3` | P |
| Changes feed: versionCode and certificate changes the server has seen (a changed certificate is the loudest signal a store can give) | `GET /v1/changes?since=` | P |
| Store reviews are NOT resold: the store's own reviews stay the store's (see §5) | — | — |

### D. Publishers
| Data | Endpoint | Tier |
|---|---|---|
| Claims ("I publish this app") and their proof: website `/.well-known/undersigned.txt` (refused on shared hosts), when first seen; the APK-declared proof needs the installed app, so the API reports it only as "declared in the store build" when checked | inside `/v1/apps/{pkg}` | F |
| Publisher's signed notes under store reviews | inside `/v1/apps/{pkg}` | F |

### E. Payments received on-chain (publisher-confirmed treasuries only)
| Data | Endpoint | Tier |
|---|---|---|
| Board: listed dApps ordered by USD received (at the price on the day of each payment), payers, payers with an attested device, SOL/SKR/USDC raw totals, first/last payment, own-wallet payments counted apart | `GET /v1/payments` | F (top 10) / P |
| One dApp's tally with a monthly series | `GET /v1/apps/{pkg}/payments` | P |
| Preview any treasury + memo code or reference account, unlisted | `GET /v1/payments/preview?treasury=&code=` | P |

### F. Wallets (reviewers, publishers, payers)
| Data | Endpoint | Tier |
|---|---|---|
| Attested device (Seeker Genesis Token), staked SKR, review weight, standing (flags against, helpful received), reviews given, flags given, publisher of, treasury listings, Pro licence | `GET /v1/wallets/{address}` | F (summary) / P (full lists) |

### G. Rankings
| Data | Endpoint | Tier |
|---|---|---|
| Leaderboard by metric: signed score, signed reviews, store rating, store reviews, revenue, paying users, growth this month; overall or per category | `GET /v1/leaderboard?metric=&category=` | F (top 10) / P |

### H. Later (designed, not built in v1)
- **Transaction builders**: unsigned review / reply / flag / claim transactions (base64) for wallets and agents to sign
  themselves — the carrier shape in `docs/SPEC.md` §1. Needs a careful abuse review first.
- **Webhooks**: new review on a followed app, a burst, a certificate change, a publisher reply.
- **`.skr` names** for wallets (the app's AllDomains lookup needs `getProgramAccounts`; port it with a keyed RPC).
- **x402** pay-per-call for agents (HTTP 402 with a Solana payment requirement per request), beside the prepaid pass.

## 2. Gating with SOL/SKR

- **Free**: no key. 30 requests per minute and 1,000 per day per IP. Free-tier endpoints only; paid endpoints answer
  `402 Payment Required` with the price and how to pay.
- **Pass**: the fleet's paid-tier price — **0.0607 SOL or 651 SKR (586 SKR with a Seeker Genesis Token)** — buys
  **100,000 requests over 30 days**, 600 per minute, every endpoint. Paying again adds 100,000 requests and extends the
  term by 30 days from the later of now and the current end. Numbers live in `api/.../Config.kt` (`PASS_*`).

### Paying and redeeming
1. Transfer the price to the Undersigned treasury `DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q` (SKR to its token
   account), with the API reference account **`8j4SnnJaR5DUb7cqvqRf7Bq7PTwKQg1m223VsZf1avtf`** (`Config.API_REFERENCE`) on the transfer and the memo `UNS`.
   `GET /v1/pricing` returns ready-made Solana Pay URLs for both rails.
2. Sign the message `Undersigned API key for <transaction signature>` with the paying wallet (any wallet's "sign
   message"), and `POST /v1/keys` `{signature, message_signature}`. Only the payer can redeem: a copied transaction
   signature is worthless without the payer's signature over it. Each transaction redeems once.
3. Use the returned key as `Authorization: Bearer <key>`. `GET /v1/keys/me` shows requests left and the end date.

The server verifies from balances, never from anything the memo claims: the transaction succeeded, it credited the
treasury at least the floor on one rail, it carries `API_REFERENCE`, and the wallet signature matches its fee payer.
The SGT price is honoured when the payer holds a Seeker Genesis Token at redemption.

API payments carry `API_REFERENCE`, never the registry, so the app never reads one as a Pro licence or a listing fee;
fleet ledger tools see `UNS` and attribute them to Undersigned.

## 3. MCP

The existing `mcp/` server (the publisher desk: inbox, replies handed to the Seeker, local ledger sync) gains the API's
tools when `UNDERSIGNED_API_URL` is set (`mcp/api.js`): `api_search_apps`, `api_app`, `api_reviews`, `api_bursts`,
`api_versions`, `api_store_ratings`, `api_app_payments`, `api_wallet`, `api_leaderboard`, `api_store_watch`,
`api_changes`, `api_payments_board`, `api_records`, `api_record`, `api_stats`, `api_pricing`, `api_redeem_pass`,
`api_pass`. `UNDERSIGNED_API_KEY` holds a pass key; a 402 comes back as plain instructions to buy one. The MCP never
signs or pays: paying stays the wallet owner's own act.

## 4. Running

```
api/run-local.sh                # builds, reads api/.env.local (git-ignored: UNDERSIGNED_RPC, UNDERSIGNED_DB), serves on $PORT (8787 by default)
UNDERSIGNED_API_URL=http://localhost:8787 node mcp/server.js
```

Nothing is deployed. Hosting, a domain and the store-data question (§5) are owner decisions.

## 5. Open questions for the owner
- **Store data**: the catalogue, details and star counts are the dApp Store's public data. v1 serves them free and
  never resells store review text. Selling passes that include store-derived series (rating history, watch, changes)
  should be checked against Solana Mobile's terms before launch.
- **Prices**: pass size and term are proposals.
- **Hosting**: any JVM host; state is one SQLite file.
