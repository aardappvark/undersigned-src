# Build history

What was built when, oldest first, moved out of the README on 10 Oct 2026. The README describes the product as it is now.

## Live on mainnet (8 Sep 2026, owner's Seeker `airdropper.skr`)

The repository starts at 7 Sep 2026 19:30 UTC (05:30 on 8 Sep in Melbourne, where it was written). Its first two commits,
20 minutes apart, carry 855 and 3,002 lines: the protocol module and spec, then the first app screens, committed when they
built rather than line by line; the icon loading and the glass UI follow patterns from AardAppvark's earlier dApps.

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
- **Release 1.1.0** (versionCode 11); **1.1.1** (versionCode 12, 10 Oct): payment analytics in the app and on the web, quick reviews withdrawable, API keys hashed; **1.1.2** (versionCode 13, 10 Oct): a bottom tab bar (Browse, Changes, Payments, Pro, Me) with Pro as its own tab; **1.1.3**: Pro prompts, per-dApp payment history; **1.1.4** (versionCode 15, 10 Oct): the judge's list: operator wallets count nowhere in the app's own stats, device facts opt-in by default, store ★ on Browse rows, plain empty states, wording, https share links, alerts offer, 1.1 MB lighter; **1.1.5** (versionCode 16, 11 Oct): offline is said so (banner, 'couldn't load · try again' instead of 'none'), a first-run 'does this Seeker count' card, the operator disclosure in Settings and onboarding; **1.1.6** (versionCode 17, 11 Oct): AA text contrast, a notification channel per kind, distinct payers per window, a desk payments sparkline, retries when the network returns; **1.1.7** (versionCode 18, 11 Oct): the second judge pass: a sign-in button on the review form (no wallet was a dead end), analytics retried three times as the network returns and an HTTP failure said apart from offline, timeline day bars at local midnight to match the cards' dates. Server, same day: `api verify` compares record ids and fails on a tampered score, count or record (six tests), the home page says "no counted reviews yet" beside the operator-reviewed count, the footer disclosure reads as one sentence, stake pills drop "SOL not read", the stats key `claims_verified` became `claims_proven`; **1.1.8** (versionCode 19, 11 Oct, owner): **own tags for Pro reviewers** (`SRL1|ctg`, up to two words beside the fixed vocabulary, shown on the review and counted on the app's page, only where the reviewer held Pro at the time; blocked-word list; no score reads them) and **Pro offers that hide themselves** the moment Pro turns active (ProSignal), with a one-line offer in the composer where the own-tags field would be. Also 1.1.8 (owner): **similar-text findings name their match** ("Similar text to a review of X signed D", "within 3 days" when so) instead of a bare count; identical notes of 20+ characters by another wallet count even under six words (protocol Similarity, 4 tests). **1.1.9** (versionCode 20, 11 Oct, owner: "credibility"): the **reviewer's record beside every review**, counts only and never a weight: signed, apps reviewed, follow-ups, follow-ups after a fix and fixes confirmed, flags upheld, since when; on the card, in the standing sheet, on the profile, in the API (`reviewer.standing`, wallet `standing`) and on the web. No points, no level, nothing to earn: the banned-word rule and "counts only" stand. **1.1.10** (versionCode 21, 11 Oct, owner): the store hand-off button says what it does, "Copy to clipboard to post in the dApp Store" (the store has no review deep link or write API; measured on v1.59.0). Also 1.1.10 (owner): **tap a star row to read why**: the signed reviews' own histogram and the store's histogram are filters, the list and the store rows show only that rating, and the web histogram rows link to `?stars=N`; and the **reviewer's history** behind every standing line: date and time, dApp, stars, tags, own tags, follow-ups and fixes, the note, and a twelve-month strip; the web wallet page gains time, tags and a by-month line. **Comparison (11 Oct, late):** `docs/COMPARISON.md`, the dApp Store's review flow measured on v1.59.0 beside Undersigned 1.1.10 (18 rows, reviewer and developer, with the outcome of each); deck slides 5–6 and eight 1920×1080 video frames (`assets/video/frames/`) built from paired screenshots in `assets/submission/shots/cmp/`. **1.1.11** (versionCode 22, 11 Oct, owner): a **Translate button** on every review, store review and reply (Pro translates with one tap; without Pro the button opens the Pro tab), an **auto-translate** switch in Settings for Pro (each note and reply as it is shown, each an AI call, labelled a suggestion), and the similar-text label names the app from the catalogue. **1.1.12** (versionCode 23, 11 Oct): **Mark fixed opened nothing** since the report dialog was added: its dialog had been nested inside the report dialog's block and only rendered while a report was open (found driving airdropper for the second round); moved out, the publisher's fix flow works again. **1.1.13** (versionCode 24, 11 Oct, owner: "moderation by swear words, racist, abusive or other harassment… auto hiding… banning addresses"): a shared abusive-language list (`protocol/.../Abuse.kt`, whole words after folding case, accents, leetspeak, masking and repeats) that the composer and the relayer refuse and every reader hides automatically with the reason "abuse" (rating still counts); a moderator's on-chain `SRL1|ban|<wallet>|<reason>` / `SRL1|unb` that every reader honours by dropping the wallet's records (moderators and operator wallets can never be banned); the app's moderator menu gains "Exclude this wallet"; wallet pages say when a wallet is excluded. Tests: AbuseTest ×4, ModerationApiTest ×2. **1.1.14** (versionCode 25, 11 Oct): translating a note into the language it is already in now says so ("Already in your language.", 28 locales) instead of failing; the server answers `same_language: true` for that case. **1.1.15** (versionCode 26, 12 Oct, owner: a Polish store reply "isn't helpful currently in a different language"): the Translate control sits under the store's own publisher replies as well, so a review and its reply read in the reader's language together. **1.1.16** (versionCode 27, 12 Oct, owner: "make this dropdown menu more beautiful"): the review's menu is a rounded card with an icon on every row, the reader's own actions first, the moderator's rows behind a divider in the accent colour, Report last in the warning colour. **1.1.17** (versionCode 28, 12 Oct, owner: "ensure our standard dApp tracking is wired in"): the fleet's anonymous usage counts (one event name per action, no identifier; a Cloudflare Worker with a public `/public-stats`; a switch in Settings; the privacy policy rewritten the same day, 5B, and corrected about the App's own API calls); the tour's first step reads "Find any dApp". **1.1.18** (versionCode 29, 12 Oct, owner): the publisher desk shows a dApp's treasury from the ledger at once ("No treasury listed" had been a false negative while 28 listings were still being verified one by one) and "Received in the last 12 weeks", the same window its sparkline draws. **1.1.19** (versionCode 30, 12 Oct, owner: "show where our moderator has picked up things that were passed into the dApp Store"): the store's own review text and publisher replies pass through the shared abusive-language list; a match is hidden here with the reason, the stars still count in the store's tally. **1.1.20** (versionCode 31, 12 Oct, owner): the refusal names the words masked ("sh*t", "f*cking"), never in full; `Abuse.mask`/`masked` in the protocol with a test. **1.1.21** (versionCode 32, 12 Oct, owner: the deck said ".skr names everywhere" beside a Settings shot showing a raw address): the Settings wallet card shows the `.skr` name first and the address beneath it. **1.1.22** (versionCode 33, 12 Oct, owner: "how quickly a dApp has been modified to take into account issues raised"): a **Feature request** tag in the fixed vocabulary; the proven publisher signs a decision, `SRL1|ack|<review>|y|n|<note>`, Will build or Won't build; the card shows the decision with the hours it took and, after the fix, "Delivered in version N · 2 d after the request"; the Publisher section and the API (`feature_requests`, `crashes`) show requests answered, promised and delivered with median hours, and crash reports with the median hours to a signed fix. Protocol `Reputation.requests`/`crashes`, `Threads.decisions`, RequestsTest ×2. **1.1.23** (versionCode 34, 12 Oct, owner): a feature request carries a **category** (`SRL1|req|<pkg>|<design|speed|wallet|payments|alerts|offline|integration|other>`, riding beside the review like own tags), picked in the composer the moment the tag is chosen, shown as a pill on the card, counted per category in the Publisher section and the API (`feature_requests.by_category`); and a per-dApp **"Tell me when this dApp updates"** switch on the app page: the hourly watch reads the store's release versionCode for those dApps and posts a notification when it moves (14 strings ×28). **1.1.24** (versionCode 35, 12 Oct): a proven publisher can sign Mark fixed again (a later version, or a mistyped one); readers take the latest fix record. First request thread on mainnet, 12 Oct: vitalik's feature request on Seeker Verify (crbe2vGz…) → airdropper's Will build (RwvPFz1u…, 45 min) → Fixed in version 100 (5yEMEw1z…, 1 h; an earlier record with a mistyped version is superseded). **1.1.25** (versionCode 36, 12 Oct, owner: "lots of metrics"): the leaderboard gains Pro members' own score, the plain mean of every live signed star (no weighting, two reviews minimum), and the store's categories ranked by store reviews with their weighted rating and by signed reviews with the last 30 days against the 30 before; tapping a category opens that category's apps. **1.1.26** (versionCode 37, 12 Oct): the abusive-language list no longer collapses a doubled letter in a word under five letters (a Dutch name had matched a slur in the store crawl); AbuseTest ×6.
- **1.1.27** (versionCode 38, 12 Oct, SHA-256 dfbec5dc…): **review analytics over every source** (owner: "filter between
  reviews on our dApp, organic, pro users etc and the actual dAppstore itself … beautiful graphs"). Server
  `ReviewAnalytics.kt`: the paced crawl of the store's public review lists (`STORE_REVIEWS_DIR/reviews*.jsonl`, 155,095
  reviews across 541 dApps on 12 Oct, deduplicated by id; counts, means and shares only, no text republished) and the
  ledger's live reviews tagged by class (organic, pro, promoted, quick, other, ours); `GET /v1/reviews/analytics` (+ `.csv`),
  raw `GET /v1/reviews/store.csv`, four SVG charts, the `/reviews` page, MCP `review_analytics`. App: Me → Review analytics
  (`ReviewAnalyticsScreen`): source and window chips, totals, stars, by category (tap to narrow), by dApp, over time
  (columns and mean line), links to the JSON, CSV and raw rows; signed sources computed on the phone, the store source
  from the API with an offline fallback to the tallies read on the phone. Docs: DEVELOPERS.md and API.md sections.
