# Undersigned

**Signed reviews from Seeker owners. One Seeker, one voice, written to Solana, readable by anyone.**

A Seeker dApp that puts a sybil-resistant review layer beside the Solana dApp Store. A review is a memo transaction the
owner signs in their own wallet with the Seed Vault double-tap. It counts only when the signing wallet holds a Seeker
Genesis Token, so a review farm needs a farm of phones, at US$450–500 each (early pre-order and retail price). Ranking is deterministic from the chain;
anyone can recompute it. No token, no prizes, nothing staked or moved by the app.

CLOCK IN hackathon entry (Solana Mobile). First commit 7 Sep 2026 (UTC); submissions close Mon 12 Oct 2026, 11:59 UTC.
Submission pack: [docs/SUBMISSION.md](docs/SUBMISSION.md). Protocol: [docs/SPEC.md](docs/SPEC.md).
Developers (API, MCP): [docs/DEVELOPERS.md](docs/DEVELOPERS.md). What was built when: [docs/HISTORY.md](docs/HISTORY.md).
The numbers: [docs/FINANCIAL_CASE.md](docs/FINANCIAL_CASE.md).

## Try it in 60 seconds

| | |
|---|---|
| **APK** (signed with the fleet key) | [`assets/submission/Undersigned-1.1.16-vc27.apk`](assets/submission/) with its SHA-256 beside it; works on any Seeker |
| **The site and API** | https://undersigned.provenby.dev · [`/payments`](https://undersigned.provenby.dev/payments) · [`/v1/stats`](https://undersigned.provenby.dev/v1/stats) · MCP at `/mcp` |
| **One review, on-chain** | Jupiter Mobile, signed on a Seeker with the Seed Vault: [4Eqgb1ex…odmNXXL5](https://solscan.io/tx/4Eqgb1exXvtVofUzh6SuGLEatr876qwLgjm5cr37bdXCRzEemMGbA2v65GbzQKaLs93QDSMPir7ys1XwodmNXXL5) (an operator wallet: shown, never counted) |
| **Recompute it yourself** | `api/build/install/api/bin/api verify` reads the registry from the chain into a throwaway database, scores it with the protocol module, and diffs the live dataset (exit 0 on a match) |
| **Where it stands** | 0 independent counted reviews yet; every record so far is from Undersigned's own wallets, labelled and never counted. The rules hold even for us. |

<p><img src="assets/submission/shots/browse_az.png" width="200"> <img src="assets/submission/shots/tk_reviews.png" width="200"> <img src="assets/submission/shots/chart_top.png" width="420"></p>

**Disclosure:** Undersigned is run by AardAppvark, publisher of 28 dApps on this store. Reviews from its own wallets are shown, labelled, and never counted; the confirmed treasuries on the payments board today are those apps'.

## The core loop

1. **Write.** Stars, one to three tags from a fixed set, an optional note (up to 2,000 characters, on-chain) and,
   opt-in, what the phone can report: install age, time actually used, reinstalls.
2. **Sign.** Mobile Wallet Adapter hands the transaction to the Seeker Wallet; the owner double-taps; the key never
   leaves the Seed Vault. The wallet sets the network fee (0.000065 SOL on the first mainnet review); Undersigned takes nothing.
3. **Counted.** One Genesis Token, one voice. Each review's weight is between 0.25 and 1: more when it carries what the
   phone saw or a written note, halved for a wallet with a review hidden by consensus. Staked SKR and SOL are shown
   beside each review, never weighed. No payment to Undersigned adds weight to anything.
4. **Read anywhere.** Every record carries one reference address, so the app, the website, the API, the MCP server, a
   browser viewer that reads the chain directly, Solscan, or Solana Mobile's own tools all read the same bytes.

Everything else hangs off that loop:

| For | What |
|---|---|
| Seeker owners | Browse all 1,547 dApps with the store's rating beside the signed score; follow apps; per-action notifications; review nudges; import existing store reviews in one approval; a second round when a publisher says a problem is fixed; facts the phone can prove (certificate matches the store's, permission drift), never verdicts; 28 languages; a guided tour. |
| Publishers | Prove an app is yours (a signed claim checked against the website the store lists or the manifest of the store's own build); reply on-chain under a Publisher mark; sign "fixed in version N" and see who followed up; responsiveness shown publicly; per-version scores; desk, webhooks, badge, MCP. |
| Integrity | Genesis-weighted flags and "looks genuine" clears; author disputes; near-duplicate labels; store review bursts and no-Genesis waves shown as facts; moderation hides text for four narrow reasons, never a voice; abusive language (one shared list: slurs, strong profanity, threats) is refused by the composer and the relayer and hidden automatically by every reader, and a moderator's signed `ban` record excludes a wallet that keeps at it ([docs/MODERATION.md](docs/MODERATION.md)). |
| Payments board | Treasuries proven by the publisher, payments counted per app and over time, a leaderboard; wallets found in app code listed apart as unconfirmed. |
| Developers and agents | Public website and JSON API, OpenAPI, dataset download, feeds, signed webhooks, a hosted MCP server (25 tools) and a local MCP desk (53 tools). |

### Shown apart, labelled, and kept out of the signed score's ordering

- **Quick reviews**: stars, tags and text signed by a key the app holds on the phone, no wallet; Undersigned's relayer
  pays the fee. Anonymous, so **never counted**: their own section, average and sort.
- **Reviews from wallets without a Genesis Token**, and from Undersigned's own wallets: shown, **not counted**.
- **Promoted reviews**: their author paid to promote them. Counted **once, at the same weight** as if unpaid; the
  payment buys only a labelled place **after** organic reviews, never first, never weight.
- **Translate, one tap**: a Translate button on every review, store review and reply (Pro); auto-translate as they are shown, from Settings. Suggestions, labelled as AI; the author's own words stay above.
- **Tap a star to read why**: the histogram rows, signed and store, filter the reviews to that rating; on the web they link to `?stars=N`.
- **A reviewer's history, one tap from any review**: every review they signed with date and time, dApp, stars, tags, note, follow-ups and fixes, and a twelve-month strip.
- **The reviewer's record beside every review**: signed, apps reviewed, follow-ups (and how many came after a publisher's fix and confirmed it), flags upheld, since when. Counts a reader can weigh; never a weight we apply, never points, nothing to earn.
- **Similar text, named**: a review whose note matches another's (6+ words at 60 % overlap, or an identical note of 20+ characters by another wallet) carries "Similar text to a review of X signed D", and "within 3 days" when so. A label that names its match; it never hides a review and never says why.
- **Pro members' reviews**: counted like everyone's, and also shown as their own labelled score. Pro reviewers may add up to two **tags of their own** beside the fixed vocabulary (`SRL1|ctg`), shown on the review and counted on the app's page; no score reads them.
- **Reviews by holders of a Pro seat gifted by the app's publisher**: labelled, counted in no score.

Rules: [integrity policy](https://aardappvark.github.io/undersigned-legal/integrity.html), `protocol/.../Weighting.kt`.

## How it exists relative to the Solana dApp Store

**It is not part of the store and does not modify it.** It is an ordinary third-party dApp, submitted
and published through the same portal as the rest of the fleet, sitting *beside* the store:

| Layer | Who owns it | What Undersigned does with it |
|---|---|---|
| Store catalogue (apps, versions, categories) | Solana Mobile | **Reads** it through the same public GraphQL feed the AarDappvark Toolkit has used in production since its 2026-05-06 approval. Never writes. |
| Store's own star ratings and reviews | Solana Mobile | **Never written to.** Read and shown beside the signed ledger, labelled as the store's own (read-only mirror); never replaced. The app never claims to be, or to speak for, the store. |
| The review ledger | Nobody — it is public chain data | **Created by users**, one signed transaction per review, discoverable by anyone through a fixed reference address. Solana Mobile, Guardians, or any other app can read the same ledger; it is open data by construction. |
| Installed-app facts (cert, permissions) | The user's device | Computed on-device with PackageManager, compared with the device's own earlier snapshot, never uploaded. |
| Launching / installing an app | Solana Mobile | Deep-links out: `solanadappstore://details?id=<package>` (the Toolkit's existing path). Undersigned never installs, updates or removes anything. |

So the honest one-line answer to "is this the dApp Store?" is: *no, it is an independent trust
layer that reads the store's public catalogue and lets Seeker owners holding a Genesis Token publish signed
opinions about it to Solana, where anyone can read them.* If Solana Mobile's Guardian curation
programme wants the data, it is already theirs to read; nothing about the design depends on them
adopting it, and nothing about it competes with their install flow.

Three lines of copy that must appear in the listing, onboarding and terms, for trademark and
"not misleading" reasons: **not affiliated with Solana Mobile**; **reviews are the signed opinions of
individual Seeker owners, not the store's ratings**; **the facts panel reports what your device can
verify, it is not a safety verdict.**


## Where it runs

- **The app** reads the chain directly (an RPC pool with fallbacks): reading, signing and scoring need no server of ours.
  The server adds extras: quick reviews (its relayer pays the fee), AI help and the digest, wallets found in app code.
- **The website, API and MCP** (`https://undersigned.provenby.dev`) run on a Hetzner Cloud server (CX23, Nuremberg)
  as two systemd services: the API (JVM, SQLite, daily backups kept 14 days) and the Cloudflare tunnel. Deploy and
  redeploy: `sh api/deploy-hetzner.sh <ip>` (keeps the live database). A watchdog on the owner's Mac checks the public
  URL every 10 minutes and the server's cost/revenue alerts, and posts a notification when either needs attention.
  See [docs/DEPLOY.md](docs/DEPLOY.md).
- **If the server is down**, nothing is lost: every record is on Solana, the app keeps working, and the
  [browser viewer](https://aardappvark.github.io/undersigned-viewer/) reads the ledger with no server at all.
- **Quick-review relayer**: a fee-payer wallet the server holds, which can only co-sign quick reviews (memo + one
  0-lamport self-transfer); 10,000 lamports per review, limits per phone, per network and per day.

## State today (measured 11 Oct 2026)

- Release **1.1.16** (versionCode 27). 212 commits on 19 days (UTC) since 7 Sep 2026; 21,780 lines of Kotlin plus 4,165 lines of tests;
  295 Kotlin tests (119 protocol, 102 API, 74 app) and 8 MCP tests; 144 of 144 live checks against the public API and MCP.
- Ledger: 97 records, 29 proven publisher claims, 28 treasury listings, one Pro licence paid in SKR (a self-paid test). All from Undersigned's own wallets. **0 counted reviews
  so far**: the reviews on the ledger come from Undersigned's own wallets (shown, labelled, never counted). The signed
  scores fill as Seeker owners sign.
- Legal pack live: https://aardappvark.github.io/undersigned-legal/ (terms, privacy, EULA, integrity policy).

## Repo layout

```
protocol/   pure Kotlin/JVM: memo codec, ranking and weighting, threads, long text, quick reviews, payments, tests
app/        Android (Compose, MWA 2.0, Seeker Genesis Token check, WorkManager, 28 locales)
api/        JVM server over :protocol: indexer, website, JSON API, MCP, relayer, economics; deploy scripts
mcp/        local MCP server for publishers (Node): desk, inbox, handoffs to the phone, every API route as a tool
docs/       SPEC (wire format), DEVELOPERS, API, DEPLOY, MODERATION, LEGAL_GATES, SUBMISSION, PITCH, HISTORY
assets/     submission APK, deck (pitch.html, PDF), video
```

Build and test everything:
`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleRelease test :app:lintRelease :api:installDist`

## Owner decisions still open

1. **Name** — DONE 8 Sep: Undersigned (web-screened clean; store/Play/IP Australia screen still owed).
2. **Registry PDA** — DONE 8 Sep: `BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs` (seeds `review-ledger`,`v1`, bump 255); app re-derives at startup.
3. **Free or Pro** — DONE 15 Sep: **reading, reviewing, replying, voting and flagging are free**, and so is the
   ranking every reader sees. Pro is an optional 12-month licence (0.0607 SOL or 369 SKR — 276 SKR until 28 Sep 2026, still honoured — fleet payment cluster,
   memo `UNS`, treasury-credit ≥ floor = the licence) for the work *around* signing: import all store reviews in one approval, the publisher desk, CSV/JSON export, and
   hourly instead of six-hourly alerts for followed apps (copying a review to the store is free). It is shareable with two other wallets. **Pro never weights an opinion, lifts a listing or marks
   anyone trusted** — weight comes only from the attested device and what the review carries; staked SKR is shown, never weighed. Pro members' reviews also get their
   own labelled score; reviews are listed under Organic / Pro members / Promoted (paid, after organic, never first) / Related to Undersigned / Not counted, and the
   operator's own wallets and publisher-gifted Pro seats count in no score ([integrity policy](https://aardappvark.github.io/undersigned-legal/integrity.html)). It changes none of the legal
   analysis (a software licence fee, as in every other fleet app). First licence paid on mainnet 15 Sep (276 SKR, from the operator's own wallet: an end-to-end test).
4. **Package visibility** — `QUERY_ALL_PACKAGES` with a disclosure (the Toolkit precedent) or a generated
   `<queries>` list from the catalogue. The former is simpler and already store-approved in the fleet.
