# Undersigned

A Seeker dApp that puts a **sybil-resistant, on-chain review and trust layer** on top of the Solana
dApp Store. One Genesis Token = one voice. Reviews are memo transactions the reviewer signs in their
own wallet; ranking is deterministic from the chain; the phone adds the facts it can prove about
installed apps (certificate changes, permission drift). No token and no prizes; the app itself reads the chain
directly and uses no server of ours. An optional API and website (`api/`, `docs/API.md`) index the same public
ledger for other programs and for readers without a Seeker.

CLOCK IN hackathon entry (8 Sep – 8 Oct 2026). See `docs/SPEC.md` (protocol), `docs/LEGAL_GATES.md`
(compliance), and this file's "How it exists" section for the relationship to the store.

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
layer that reads the store's public catalogue and lets verified Seeker owners publish signed
opinions about it to Solana, where anyone can read them.* If Solana Mobile's Guardian curation
programme wants the data, it is already theirs to read; nothing about the design depends on them
adopting it, and nothing about it competes with their install flow.

Three lines of copy that must appear in the listing, onboarding and terms, for trademark and
"not misleading" reasons: **not affiliated with Solana Mobile**; **reviews are the signed opinions of
individual Seeker owners, not the store's ratings**; **the facts panel reports what your device can
verify, it is not a safety verdict.**

## Repo layout

```
protocol/   pure-Kotlin JVM module: memo codec, ranking, safety-fact model, unit tests (runs today)
docs/       SPEC.md (wire format, registry, reader), LEGAL_GATES.md (compliance, feature gates)
app/        Android shell (Compose, MWA 2.0.3, seeker-verify 1.1.0), bootstrapped 8 Sep from the RentBack Sol
            scaffold: terms gate (SIWS), onboarding, Browse (Installed / Signed / All), App detail (signed score,
            tags, device facts, reviews, hide/report), Review composer (rating + tags + double-consented note,
            Genesis-Token gate, simulate → sign → confirm), What-changed feed, Settings. Bundled catalogue
            snapshot (1,333 apps) + background GraphQL refresh; ledger reader with on-device cache.
            Debug build verified on a throwaway AVD 8 Sep (all screens, live catalogue refresh, ledger sync).
```

Run the protocol tests: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :protocol:test`

## Live on mainnet (8 Sep 2026, owner's Seeker `airdropper.skr`)

Review `2zuU…` → withdrawal `3aTw…` → re-review `3mMg…`, all from the owner's wallet, wallet-set fee
47,000 lamports each. Genesis Token + staked-SKR reads worked against the real wallet (weight capped at 3×).

## Built on top since (same day)

- Real icons (installed launcher icon → store icon URI → letter badge), Toolkit pattern.
- Follow apps; local 6-hourly ledger alerts (new signed reviews on followed apps, replies to yours); signed replies (`SRL1|rep`).
- Score display = true weighted mean; the Bayesian value is shown only as a "rank score" and used only to order lists.
- **Store mirror**: the native store's public rating histogram + reviews (`.skr` names, publisher-replied badge) read through the
  store's own `DAppByPackageSummary` / `DAppReviews` operations, which answer without any token (probed 8 Sep). Read-only.
- **Import my store reviews**: scans installed + followed apps' public store reviews for the user's own wallet address and
  re-signs the chosen ones as ledger records in ONE wallet approval (multi-memo transactions, record ids `sig`, `sig:1`, …).
- `.skr` main-domain names for ledger signers (AllDomains MainDomain PDA), cached on-device.

## Built since (26–28 Sep 2026)

- **My reviews**: every review the wallet wrote, one card per dApp, signed beside store, searchable; the store scan runs
  in the background and notifies when done.
- **Review bursts**: each app page flags clusters of 5★ (or 1★) reviews against the app's median day, with how many
  reviewers hold an attested device and whether the burst fell in a reward period. Checked on real data: CrushCoin's
  gold-for-5★ campaign (17 Mar 2026) reads as 69 five-star reviews in 17–24 Mar, 67 of 67 from attested wallets.
- **Star-rating bursts and Store watch**: daily snapshots of the store's star counts (the batch query takes 20 apps; the
  whole store is 72 requests), per followed app and, opt-in, store-wide; alerts on new bursts for followed apps.
- **Install age and usage time on reviews** (`SRL1|use`, `SRL1|tim` in the review's own transaction): total time on the
  phone across reinstalls, and opt-in foreground time, both rounded and shown as the reviewer's device report.
- **Payments board**: a treasury confirmed by both its own signature (`SRL1|trs`) and the publisher's website, counted
  by memo code or reference account, publisher's own wallets left out, USD at each payment's day. Matches the fleet
  ledger exactly (SeekerVerify: 63 payments, 0.4335 SOL + 1,690 SKR). "Paid for this app" badge on reviews by payers.
- **Publisher notes on store reviews** (`SRL1|snt`): a verified publisher's signed, permanent answer under a store review.
- **Reward-period tags**: reviews and bursts inside Seeker Season 1 (approximate) or Seeker Summer are tagged.

## Built since (28 Sep – 5 Oct 2026)

- **Listing fee and Pro at the fleet price** (0.0607 SOL / 651 SKR, 586 SKR with a Seeker Genesis Token); the 276 SKR
  Pro licence of 15 Sep still reads (legacy floor).
- **Publisher proof hardened**: the app's own signed build names the wallet (`undersigned.publisher` manifest line), read
  from the installed app, or on request from the build the store serves (shared reader in `:protocol`); website proof
  refused on shared hosts. All 29 fleet apps carry the line from their next release.
- **API, website and MCP** (`api/`, `mcp/`, `docs/API.md`, `docs/DEPLOY.md`): a JVM server over `:protocol` with
  public pages per app and wallet, badges, a Store watch report, web reviewing (server builds, wallet signs), helpful
  votes/replies/flags from the web, a QR path for desktops, webhooks, and passes bought in SOL or SKR. Runs on the
  owner's Mac only; a public host is the owner's decision. Buying a pass was run end to end on mainnet (29 Sep).
- **Two reviews** of the server code and the feature suite; their findings are fixed (see the commit log).
- 190+ tests across `:protocol`, `:app` and `:api`, including a golden transaction both builders must produce.

## Status — what is NOT done yet

- **Legal site** is live at `https://aardappvark.github.io/undersigned-legal/` (repo `aardappvark/undersigned-legal`,
  Pages from `main`/`docs`, published 28 Sep 2026; privacy policy dated 28 Sep). Its favicon (the icon in the wallet approval
  sheet), the launcher and the store icon are all the Undersigned mark from the design handoff (`handoff/store/icon-512.png`).
- **Payments board listing fee**: not built; it needs a price decision, and a fee paid under the `UNS` memo must be
  told apart from a Pro licence by amount.
- Store assets, demo video, pitch deck.
- `FLEET_PACKAGES` lock-step on publish; Pro payment memo `UNS` reserved in the fleet registry (one 3-letter memo, nothing else).

## Owner decisions still open

1. **Name** — DONE 8 Sep: Undersigned (web-screened clean; store/Play/IP Australia screen still owed).
2. **Registry PDA** — DONE 8 Sep: `BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs` (seeds `review-ledger`,`v1`, bump 255); app re-derives at startup.
3. **Free or Pro** — DONE 15 Sep: **reading, reviewing, replying, voting and flagging are free**, and so is the
   ranking every reader sees. Pro is an optional 12-month licence (0.0607 SOL or 651 SKR — 276 SKR until 28 Sep 2026, still honoured — fleet payment cluster,
   memo `UNS`, treasury-credit ≥ floor = the licence) for the work *around* signing: copy reviews to the store,
   import all store reviews in one approval, the publisher desk, CSV/JSON export, and an hourly instead of six-hourly
   certificate watch. It is shareable with two other wallets. **Pro never weights an opinion, lifts a listing or marks
   anyone trusted** — weight comes only from the attested device and staked SKR. It changes none of the legal
   analysis (a software licence fee, as in every other fleet app). First licence paid on mainnet 15 Sep (276 SKR).
4. **Package visibility** — `QUERY_ALL_PACKAGES` with a disclosure (the Toolkit precedent) or a generated
   `<queries>` list from the catalogue. The former is simpler and already store-approved in the fleet.
