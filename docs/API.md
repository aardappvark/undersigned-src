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
| Every decoded record, newest first, paginated by cursor; filter by type (`rev rvk rep clm flg unf up unv clr dsp pro shr shx use tim snt trs trx`), package, signer, time range | `GET /v1/records` | P |
| One record by id (`sig` or `sig:n`): decoded memo, signer, block time, network fee, Seeker-wallet transaction shape, revoked or not | `GET /v1/records/{id}` | F |
| Ledger totals: records, live reviews, counted reviews, signers, attested signers, apps, replies, publisher replies, claims (verified), flags, clears, disputes, hidden, withdrawn, licences, first and latest record | `GET /v1/stats` | F |
| Tag vocabulary (24 wire values) with counts across the ledger | `GET /v1/tags` | F |

### B. A dApp, from signed reviews
| Data | Endpoint | Tier |
|---|---|---|
| Signed score: rank score (shrunk toward 3.0, for ordering), weighted mean (for display), counted reviews, star distribution, tag counts, latest version reviewed; beside it `pro_members` and `promoted` as their own scores with counts (never part of the signed score) and `operator_publishes`; each review carries `weight`, `weight_parts` (device, quality, pro_member, promoted), `counted`, and `related` (`operator`, `pro_from_publisher` or null; related reviews count in no score) | `GET /v1/apps/{pkg}` | F |
| Signed reviews, each with: rating, tags, text, version, signer, attested device, weight, staked SKR and SOL now and as read when the review appeared (`stake_at_review`, shown, never weighed), `.skr` name, install age on their phone (`use`), usage time (`tim`), reward season, "paid for this app", similar-text finding, flags, helpful weight, dispute, hidden-by-consensus, withdrawn, replies (publisher badge), self-published exclusion | `GET /v1/apps/{pkg}/reviews` | F (first 20) / P (all) |
| Scores per version (mean and count per versionCode) | `GET /v1/apps/{pkg}/versions` | P |
| Review bursts: 5★/1★ clusters vs the app's median pace, window, count vs expected, multiple (×), attested share, same-text count, season overlap, 30-day daily sparkline | `GET /v1/apps/{pkg}/bursts` | F |
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
| Store-rating bursts: sudden jumps in 5★ or 1★ counts between snapshots | `GET /v1/apps/{pkg}/bursts` | F |
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
| **Found in app code, not confirmed**: wallets written into an app's public store build that are ordinary wallets, in one publisher's apps only, not a known shared address, and paid by 5+ wallets in the newest 40 transactions of the wallet and its SKR/USDC accounts. By app name, never ranked with the board; confirmed treasuries and disputed rows drop out | `GET /v1/payments/found` | F (20) / P |

### F. Wallets (reviewers, publishers, payers)
| Data | Endpoint | Tier |
|---|---|---|
| Attested device (Seeker Genesis Token), staked SKR, review weight, standing (flags against, helpful received), reviews given, flags given, publisher of, treasury listings, Pro licence | `GET /v1/wallets/{address}` | F (summary) / P (full lists) |

### G. Rankings
| Data | Endpoint | Tier |
|---|---|---|
| Leaderboard by metric: signed score, signed reviews, store rating, store reviews, revenue, paying users, growth this month; overall or per category | `GET /v1/leaderboard?metric=&category=` | F (top 10) / P |

### H. Pages, badges, alerts and web reviewing (built 28–29 Sep 2026)
| What | Where | Tier |
|---|---|---|
| **Badge** for publishers' sites and READMEs (the app page offers the proven publisher a one-tap "Copy the badge for your website"; the web app page shows the same snippet under *Badge*): `Undersigned · 4.6 · 12 signed`, cached an hour, links to the dApp's page | `GET /v1/badge/{pkg}.svg` | F |
| **Public pages** rendered by the server: home and search, one per dApp (both scores, stars, tags, bursts with sparklines, signed reviews with every signal, publisher proof, payments, store facts, badge snippet), one per wallet | `/`, `/app/{pkg}`, `/wallet/{address}` | F (own per-IP budget: 120/min) |
| **Store watch report**: every store app with a star-rating burst in the last 7 days, strongest first; shows "day N of 8" while snapshots collect | `/watch` (JSON: `/v1/watch`, pass) | F |
| **Review from a browser**: the server builds the unsigned carrier transaction (same bytes as the app; store's current version recorded), the reader's own wallet signs through the Wallet Standard (extensions, in-wallet browsers, the Seeker's wallet via Mobile Wallet Adapter in Chrome). Terms and a second consent before text; sanctions screen as in the app | `/review`, `POST /v1/tx/review` | F |
| **`.skr` names** for reviewers and wallets (the app's lookup and its three gates; cached a week, refusals never cached) | inside reviews and `/v1/wallets/{address}` | F |
| **Publisher proof from the store's own build**: for each claimed app the server downloads the build the store serves, reads `undersigned.publisher` from its manifest and the signing certificate from its v2/v3 signature block; a claim is proven when the build names the wallet and its certificate is the one the store lists | `proof: "store_build"` in `/v1/apps/{pkg}` | F |
| **Webhooks** for pass holders: review, reply, store_note, flag, claim, burst, release (any app's listing date moved), version, certificate, digest on up to 50 watched apps; each POST signed `X-Undersigned-Signature: sha256=<HMAC>`; https only and never into private networks (http://localhost is allowed only on a private loopback server); delivered by a worker from an outbox, three tries; stop when the pass ends | `POST/GET /v1/hooks`, `DELETE /v1/hooks/{id}` | P |
| **Checkout without message signing**: a secret whose commitment rides in the payment memo (`UNS:<hash>`); whoever holds the secret redeems | `POST /v1/checkout`, then `POST /v1/keys {signature, secret}` | F |

Added 5 Oct 2026:
| What | Where | Tier |
|---|---|---|
| **Helpful votes, replies and flags from the web** (server builds, wallet signs) | `/act?kind=up|reply|flag&target=<record id>`, `POST /v1/tx/record` | F |
| **QR for a phone wallet** (Solana Pay transaction request; shown on https hosts only) | `GET/POST /v1/pay/review` | F |
| **For publishers**: the two proofs explained, and a checker of what the server reads for a package | `/publishers?package=` | F |
| **Atom feed** of an app's counted reviews; sitemap; robots; link previews; the Undersigned icon | `/app/{pkg}/feed.xml`, `/sitemap.xml`, `/robots.txt`, `/icon.svg` | F |
| **Labels in the reader's language** (tags, install age, usage time, flag reasons) from the app's 27 translations; `?lang=` or Accept-Language | all pages | F |
| **The operator's hide list**: this site stops serving a record's text and says so; the record stays | `api hide|unhide|hidden`, `/report` | — |
| **Uncounted reviews folded** on pages; counted ones lead | `/app/{pkg}` | F |
| **Webhook events** `flag`, `claim`; **key recovery** (the same proof returns the same key) | `/v1/hooks`, `/v1/keys` | P |

| **Open dataset**: every decoded record, every app's signed score, every signer's public facts; built once per rebuild | `/v1/dataset.json` | F |
| **Tag groups and opposite pairs** on the web composer, as in the app (protocol `TagGroups`) | `/review` | F |

Still later: x402 pay-per-call, fully translated pages (only the shared vocabulary is localised today).

## 2. Gating with SOL/SKR

- **Free**: no key. 30 requests per minute and 1,000 per day per IP. Free-tier endpoints only; paid endpoints answer
  `402 Payment Required` with the price and how to pay.
- **Pass**: the fleet's paid-tier price — **0.0607 SOL or 369 SKR (332 SKR with a Seeker Genesis Token)** — buys
  **100,000 requests over 30 days**, 600 per minute, every endpoint. Paying again adds 100,000 requests and extends the
  term by 30 days from the later of now and the current end. Numbers live in `api/.../Config.kt` (`PASS_*`).

### Paying and redeeming
1. Transfer the price to the Undersigned treasury `DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q` (SKR to its token
   account), with the API reference account **`8j4SnnJaR5DUb7cqvqRf7Bq7PTwKQg1m223VsZf1avtf`** (`Config.API_REFERENCE`) on the transfer and the memo `UNS`.
   `GET /v1/pricing` returns ready-made Solana Pay URLs for both rails.
2. Sign the message `Undersigned API key for <transaction signature>` with the paying wallet (any wallet's "sign
   message"), and `POST /v1/keys` `{signature, message_signature}`. Only the payer can redeem: a copied transaction
   signature is worthless without the payer's signature over it. Each transaction redeems once.
   Or, without message signing: `POST /v1/checkout` returns a secret and Solana Pay URLs whose memo is `UNS:<commitment>`;
   pay one, then `POST /v1/keys {signature, secret}`.
3. Use the returned key as `Authorization: Bearer <key>`. `GET /v1/keys/me` shows requests left and the end date.

The server verifies from balances, never from anything the memo claims: the transaction succeeded, it credited the
treasury at least the floor on one rail, it carries `API_REFERENCE`, and the wallet signature matches its fee payer.
The SGT price is honoured when the payer holds a Seeker Genesis Token at redemption.

API payments carry `API_REFERENCE`, never the registry, so the app never reads one as a Pro licence or a listing fee;
fleet ledger tools see `UNS` and attribute them to Undersigned.

## 3. MCP

Two MCP servers, for two audiences (full setup in [DEVELOPERS.md](DEVELOPERS.md)):

**Hosted, for anyone: `POST https://undersigned.provenby.dev/mcp`** (`api/.../Mcp.kt`, 9 Oct 2026). Stateless Streamable
HTTP, one JSON-RPC message per POST (batches are refused, so every call is metered), protocol 2025-06-18 / 2025-03-26 /
2024-11-05. It is read-only: no tool drafts, signs or submits a review, flag or vote.
- **Tools, free:** `search_apps`, `get_app`, `get_reviews` (sort and, with a pass, offset), `get_bursts`, `get_wallet`, `get_record`,
`list_records`, `leaderboard`, `payments_board`, `ledger_stats`, `review_tags` and `get_pricing`.
- **AI tools, labelled suggestions:** `app_summary` (free), `translate_review` and `weekly_digest` (Pro wallet or a key).
- **Tools, with a pass:** `store_watch`, `app_changes`, `version_scores`, `similar_reviews` and `store_rating_history`.
- **Resources:** `undersigned://policy`, `undersigned://pricing`, `undersigned://tags`, and the template `undersigned://app/{package}`.
- **Prompts:** `audit_app_reputation` and `compare_apps`.
- **Discovery:** `/llms.txt` and `/.well-known/mcp/server-card.json`.

**AI help and the weekly digest (HTTP).** `POST /v1/help {question, lang}` answers how scoring works from the integrity policy
only (free, labelled). `GET /v1/digest` is this ISO week's store-wide bursts and app updates in plain words with the counts
behind them (Pro wallet or key); it is also sent once a week as the `digest` webhook event, and the app posts it as a Pro
notification. `GET /v1/apps?packages=a,b,c` reads many apps in one call (20 free, 100 with a key). `GET /v1/changelog` lists API/MCP changes.

**Local, for publishers: `mcp/` (Node, stdio).** Run it on your own machine with `node mcp/server.js`.
- **Publisher desk:** `desk`, `inbox`, `profile`, `star`, `store_reviews`, `app_reviews`, `ledger_sync` and `watch_status` (live watch with macOS notifications).
- **Hand-offs:** `reply_handoff` and `action_handoff`. Each returns an `undersigned://` link for the person to sign on their Seeker.
- **`sign_reply`:** signs a publisher's own reply locally, only when the operator sets `UNDERSIGNED_KEYPAIR`. It never signs a review, flag or vote.
- **API tools:** with `UNDERSIGNED_API_URL` set, it adds the API as tools (`mcp/api.js`): `api_search_apps`, `api_app`, `api_reviews`,
`api_bursts`, `api_versions`, `api_store_ratings`, `api_app_payments`, `api_wallet`, `api_leaderboard`, `api_store_watch`,
`api_changes`, `api_payments_board`, `api_records`, `api_record`, `api_stats`, `api_pricing`, `api_checkout`,
`api_redeem_pass`, `api_pass`, `api_hook_create`, `api_hooks`, `api_hook_delete` and `api_badge`.
- **Pass:** `UNDERSIGNED_API_KEY` holds a pass key. A 402 comes back as plain instructions to buy one. Paying stays the wallet owner's own act.

## 4. Running

```
api/run-local.sh                # builds, reads api/.env.local (git-ignored: UNDERSIGNED_RPC, UNDERSIGNED_DB), serves on 127.0.0.1:$PORT (8797 by default; HOST=0.0.0.0 to serve a network)
UNDERSIGNED_API_URL=http://localhost:8797 node mcp/server.js
```

Hosted on this Mac only (owner, 28 Sep 2026): `api/install-local.sh` runs it as a LaunchAgent at http://127.0.0.1:8797, loopback
only. Test prices for a local test run: `UNDERSIGNED_TEST_PASS_SOL` / `UNDERSIGNED_TEST_PASS_SKR` in `api/.env.local`,
honoured only while the server listens on loopback. A public host, a domain and the store-data question (§5) are owner decisions.

## 5. Rules the numbers follow
- **A payer, and "paid for this app", need a real amount**: at least 0.001 SOL, 1 SKR or 0.10 USDC in one payment
  (`Config.MIN_PAYER_*`). Dust cannot buy a pill. Totals still count every unit, as the app's tally does.
- **Revenue in USD** sums the payments whose day has a stored price and reports `unpriced` for the rest; daily closes are
  kept in the database as they are fetched, so a payment older than the feed's 365 days keeps its price.
- **Pages show up to 50 reviews; the free API shows 20** (a pass lifts it).
- **A wallet the ledger has not met** is answered from cache only: `attested_device: null` means "not read yet" and the
  indexer reads it within minutes. No chain call ever runs inside a request.
- **Going public** is `UNDERSIGNED_PUBLIC=1` (or `TRUST_PROXY=1`, which reads the client address from the LAST hop of
  X-Forwarded-For). It disables test prices (the server refuses to start with them set) and localhost webhooks.

## 6. Open questions for the owner
- **Store data**: the catalogue, details and star counts are the dApp Store's public data. v1 serves them free and
  never resells store review text. Selling passes that include store-derived series (rating history, watch, changes)
  should be checked against Solana Mobile's terms before launch.
- **Prices**: pass size and term are proposals.
- **Hosting**: any JVM host; state is one SQLite file.
