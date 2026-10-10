# Build history

What was built when, oldest first, moved out of the README on 10 Oct 2026. The README describes the product as it is now.

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

- **Listing fee and Pro at the fleet price** (0.0607 SOL / 369 SKR, 332 SKR with a Seeker Genesis Token); the 276 SKR
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

## Built 6 Oct 2026

- **Not reviewed on this phone**: installed store apps the wallet has not reviewed and has had a week or used an hour;
  review one, or tick up to eight and sign them in **one approval** (stars and tags; each review keeps its install-age and
  usage records in its own transaction, `TxBuilder.chunkGroups`; a 7-review batch simulated on mainnet). An optional
  weekly reminder (off by default) names how many there are.
- **"Still true on this version?"**: one tap re-signs a review for the version the phone now runs.
- **Tags by theme with opposite pairs** (protocol `TagGroups`) in the composer, the batch, the importer and the web.
- **Imports ask for tags** instead of writing "Works"/"Confusing" into every imported review.
- **Links** `undersigned://app?pkg=` and `undersigned://review?pkg=`; **share** an app or a review; **filter by tag**.
- **Alerts**: a record of mine flagged, my claim proven, a publisher's signed note on my store review.
- **Open dataset** at `/v1/dataset.json`.

## Built 7–10 Oct 2026

- **Guided tour** on first open: bubbles that point at each part of each screen; again from Settings.
- **In-app help** that answers "how does scoring work?" from the integrity policy, and a **weekly plain-language digest**
  of the store (both AI reading aids, labelled as such, cached; `/v1/help`, `/v1/digest`).
- **Payments over time** per dApp and a **payments leaderboard**; **wallets found in app code** (every store APK streamed
  and scanned, no size cap): an *unconfirmed* list kept apart from proven treasuries, with history and trends.
- **Publisher desk** expanded: trend, treasury, found wallets, translation of replies, a developer kit.
- **Three ways to sign a review**, kept apart in every list, filter and sort:
  the Seeker Wallet with the Seed Vault double-tap (counted when the wallet holds a Genesis Token);
  the same through any MWA wallet (shown, not counted, without a Genesis Token);
  and a **quick review** signed by a key the app holds on the phone, with the network fee paid by Undersigned's relayer
  (`SRL1|qck`; anonymous, its own section, average and sort, **never counted**). Mainnet test 9 Oct: `2JvWvTKC…`.
- **Long reviews**: up to 2,000 characters, fully on-chain in hash-checked pieces (`SRL1|lng`, `SRL1|txt`), one approval.
- **Time actually used vs time installed** (opt-in, `SRL1|act`): months used of the last six, how recently, and
  reinstalls, from Android's usage statistics; shown as the reviewer's device report.
- **Second round**: a verified publisher signs "fixed in version N" under a review (`SRL1|fix`); the reviewer is notified
  and can sign a follow-up with new stars (`SRL1|fup`). The app shows both stars and the change, and the publisher's
  responsiveness (fixes, follow-ups after a fix, mean change, median hours to fix).
- **Notifications per action**, each one a switch in Settings, grouped for reviewers and for publishers.
- **AI allowance and economics**: per-caller daily allowances; a cost and revenue ledger per day
  (`/v1/admin/economics`, server-local) with alerts.
- **Hosting**: the API moved from the owner's Mac to a Hetzner Cloud server (9 Oct), same URL, same tunnel.
- **Release 1.1.0** (versionCode 11); **1.1.1** (versionCode 12, 10 Oct): payment analytics in the app and on the web, quick reviews withdrawable, API keys hashed; **1.1.2** (versionCode 13, 10 Oct): a bottom tab bar (Browse, Changes, Payments, Pro, Me) with Pro as its own tab; **1.1.3**: Pro prompts, per-dApp payment history; **1.1.4** (versionCode 15, 10 Oct): the judge's list: operator wallets count nowhere in the app's own stats, device facts opt-in by default, store ★ on Browse rows, plain empty states, wording, https share links, alerts offer, 1.1 MB lighter.

