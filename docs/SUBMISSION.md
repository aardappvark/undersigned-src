# CLOCK IN submission — Undersigned (final draft, 10 Oct 2026)

Everything the Align form is likely to ask for, in final wording. Nothing here is submitted until the owner does it.

## Deadline and where

| | |
|---|---|
| Submissions close | **Monday 12 October 2026, 22:59 Melbourne (GMT+11)** = 13:59 Budapest = 11:59 UTC. No late entries. [checked: solanamobile.radiant.nexus desk calendar + terminal, 8 Oct] |
| Judging | 13 Oct – 9 Nov; results 11 Nov (the brief says "November 10th"; the calendar says 11 Nov 14:00 GMT+11) |
| Where | https://solanamobile.radiant.nexus → the terminal (RADOS) → sign in / sign up (email or Google) → complete the builder profile → register for Clock In → submit. One submission per contestant; the team lead submits. A draft can be edited until the "final submission agreement" is completed. |
| GitHub access | The repo can stay PRIVATE: install the **Radiants Align GitHub app** on `aardappvark/Undersigned` and share that repo. No judge invites needed. [checked: FAQ "Does my GitHub repo need to be public?"] |
| AI Coach | The form runs an "AI Coach" first review against the rubric before human judges. Write to the four criteria by name. |

## Required deliverables (all four, before the deadline)

| Deliverable | Status | Where |
|---|---|---|
| Functional Android APK | **READY** — 1.1.13 (versionCode 24), 10.8 MB, signed v2+v3 with the fleet key (cert SHA-256 ac6932…100b), lint 0 errors, all tests pass; SHA-256 9ff9b21c…1bbf | `assets/submission/Undersigned-1.1.6-vc17.apk` (SHA-256 beside it). Upload in the form, or attach to a GitHub Release `v1.1.6` (owner). |
| GitHub repository | **READY** — 212 commits on 19 days (UTC) from 7 Sep 2026, all before the deadline | https://github.com/aardappvark/Undersigned (private; Radiants Align GitHub app) |
| Demo video (≤ 3 min) | **OWNER re-records** for 1.1.13 from `docs/DEMO_SCRIPT.md` (the 8 Oct cut shows 1.0.0) | Upload to YouTube (unlisted) or Drive; paste the link |
| Pitch deck | **READY** — 17 slides, core loop first, then how the store can adopt it, and the numbers; a framed screenshot on every product slide | `assets/submission/Undersigned-pitch.pdf` (source `pitch.html`) |

## Eligibility check (against the Rules page and the T&Cs, 8 Oct)

- Started within 3 months of launch: first commit 7 Sep 2026 19:30 UTC (8 Sep in Melbourne), the launch day. Nothing published before it.
- No VC or angel funding: true. Solo builder, AU resident; Australia is on the eligible list (terms memo, 8 Sep).
- "Significant new mobile development during the hackathon": the whole app.
- Integrates the Solana Mobile Stack + Mobile Wallet Adapter: every signature goes through MWA to the Seeker Wallet / Seed Vault.
- Interacts meaningfully with Solana: every review, reply, flag, claim, listing and licence is a mainnet transaction.
- Runs on a device, not only an emulator: three Seekers run it; the demo is recorded on one.
- Winners must publish to the dApp Store within 30 days: `docs/STORE_LISTING.md` is ready; cost about 0.021 SOL.
- Hosting for the judging window: the API runs on a Hetzner server prepaid for more than two months (`docs/DEPLOY.md`); if it ever stops, the app and the browser viewer still read every record from the chain.
- KYC via Sumsub for finalists (owner, after the announcement).

## Form fields — final wording

Field names are my best guess at Align's form `[unverified: the form is behind the owner's login]`; the texts are final.

**Project name:** Undersigned

**Tagline (one line):** Signed reviews from Seeker owners. One Seeker, one voice, written to Solana, readable by anyone.

**Category:** Mobile (single category). Also entering the **SKR Integration prize** (see below).

**Short description (about 300 characters):**
Undersigned is a review layer beside the Solana dApp Store. Every review is a transaction the owner signs on their Seeker; only wallets holding a Genesis Token count, so a review farm needs a farm of phones at US$450–500 each. Publishers prove their apps, reply on-chain and say when a problem is fixed. Anyone can read and recompute it.

**Full description:**

The dApp Store shows a star rating, but nothing tells you which of those stars came from a real Seeker. Undersigned does. It is an Android app for Seeker that lists every dApp in the store (1,543 today, updated every Monday), shows the store's own rating beside a **signed score**, and runs one loop: **write → sign → counted → read anywhere.**

**Write.** Stars, tags from a fixed set, an optional note of up to 2,000 characters, and, if the owner opts in, what the phone can report: how long the app has been installed, how many of the last six months it was actually used, and how often it was reinstalled.

**Sign.** Mobile Wallet Adapter hands the transaction to the Seeker Wallet and the owner double-taps; the key never leaves the Seed Vault. The record is a memo transaction carrying one fixed reference address.

**Counted.** A review counts only when the signing wallet holds a Seeker Genesis Token, which is soulbound to the device. One Seeker is one voice, and a Seeker sold for US$450 at early pre-order and US$500 retail, so a farm of fake voices is a farm of phones. A review's weight grows with what it carries (the phone's own facts, a written note), never with money: staked SKR and SOL are shown beside each review for readers to judge and never weighed, and no payment to Undersigned adds weight to anything.

**Read anywhere.** The app, a public website and JSON API (https://undersigned.provenby.dev), an MCP server for AI agents, a browser viewer that reads the chain with no server, and Solscan all read the same records. The ranking rules are a pure Kotlin module with unit tests; anyone can recompute every score.

Around the loop: **publishers** prove an app is theirs with a signed claim checked against the website the store lists or the manifest of the store's own build, reply on-chain under a Publisher mark, and can sign "fixed in version N" under a review; the reviewer is notified and can sign a follow-up with new stars, and the publisher's responsiveness (fixes, follow-ups, the change in stars, hours to fix) is shown publicly. **Owners** follow apps, choose which actions notify them, import their existing store reviews in one approval, and see facts their phone can prove (the installed build matches the certificate the store lists; permissions grew) as facts, never verdicts. **Integrity** is in the open: Genesis-weighted flags and clears, author disputes, near-duplicate labels, store review bursts, and moderation that can hide text for four narrow reasons but never a voice. A **payments board** shows what each dApp has been paid, from treasuries its publisher proved.

**Shown apart, never mixed in.** Quick reviews (signed by a key the app holds, no wallet; Undersigned pays the fee) are anonymous, so they have their own section and average and are never counted. Reviews from wallets without a Genesis Token, and from Undersigned's own wallets, are shown and not counted. A promoted review counts once at the same weight as an unpaid one; the payment buys only a labelled place after the organic reviews.

**A companion to the dApp Store, open for it to adopt.** Every app opens in the store; the store's own rating and reviews are shown and credited on every page and never written to; a signed review can be copied into the store's review box in one tap. The memo format, the Genesis lens, the publisher proof file and the ranking rules are public, documented and tested, and offered to Solana Mobile in three steps: read the ledger (Guardian curation, today), adopt the proof file and the ranking rules, then sign the same records from the store's own review box. Undersigned is independent and stays ours: an ordinary listed dApp beside the store, paid for by optional Pro and API passes in SOL or SKR, every payment public on-chain under its own memo. We are happy to contribute to the Seeker community as it grows, and never by paying reviewers for their reviews. It costs under US$1,000 a year to run; at 10% of the 200,000 Seekers Solana Mobile reports shipped, Pro alone would bring about US$133,000 a year; it breaks even at 127 licences a year (`docs/FINANCIAL_CASE.md`).

**Where it stands (10 Oct).** 77 records on mainnet: 29 proven publisher claims, 28 treasury listings, a Pro licence paid in SKR, and the first reviews, all signed by Undersigned's own wallets: they prove the plumbing end to end, and they are shown, labelled and never counted. There is no independent usage yet. The counted score starts at zero and fills only as Seeker owners sign; the website's home page says so. Twenty-eight languages. No token; nothing is ever paid for a review.

**Beside the store:** `docs/COMPARISON.md` sets the dApp Store's review flow (measured on v1.59.0) against Undersigned row by row, for reviewers and for developers, with the outcome of each; deck slides 5–6 show it with paired screens.

**What was built during the hackathon:** everything, from the first commit on 7 Sep 2026 (UTC): 212 commits on 19 days; 21,780 lines of Kotlin (plus 4,165 of tests) across the Android app, a pure-JVM protocol module (memo codec, ranking and weighting, follow-up threads, long text, quick reviews, payments) and the API server, plus 3,400 lines of tests (273 Kotlin tests, including golden transaction bytes shared by the app and the server, and 8 for the MCP package); the website, the hosted MCP server (25 tools), a local MCP desk (53 tools), 28 locales and the legal pack. 144 of 144 live checks pass against the public API and MCP. Milestones on mainnet: the first review 8 Sep; the first Pro licence paid in SKR 15 Sep; 29 publisher claims and 28 treasury listings 7 Oct; the first quick review relayed 9 Oct.

**Solana Mobile Stack / MWA usage:** every write is an MWA `transact` to the Seeker Wallet (authorize once, then sign-and-send); the Genesis Token check reads the soulbound Token-2022 mint on the signer; the Seeker wallet fingerprint (the Lighthouse guard it adds to every transaction) is read back from the chain and shown on the record; `.skr` names resolve from the ANS registry; batch signing puts up to 8 reviews in one approval; a 2,000-character review goes on-chain in pieces under one approval; quick reviews are co-signed by a relayer that can pay fees and nothing else. The app is Compose, portrait, mobile-first. The website also lets a desktop wallet sign a short review, but the proofs that make a review count (the Genesis Token, the Seed Vault double-tap, the phone's own facts) come only from the phone.

**SKR integration (for the SKR prize):** SKR is a payment rail and an identity layer, not a staking integration. Pro, publisher treasury listings and API passes are priced in SKR (369 SKR, 10% below the SOL price at the 9 Oct 2026 rate) and paid straight to the treasury token account with a memo and a reference address, so the licence is read back from the chain by the app, the website and the API without any server of ours. It was tested end to end on mainnet with a 276 SKR Pro payment on 15 Sep 2026 (signature 2L6fwENd…, from the operator's own wallet), read back from the chain. `.skr` names are shown everywhere a wallet appears. Separately, staked SKR and SOL are *read* and shown beside each review for transparency (never weighed), which is not the claim.

**Links**
- Website + API: https://undersigned.provenby.dev · API docs: https://undersigned.provenby.dev/api
- Browser viewer (reads the chain in-browser): https://aardappvark.github.io/undersigned-viewer/
- Terms, privacy, EULA: https://aardappvark.github.io/undersigned-legal/
- Registry (reference address every record carries): `BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs`
- Store listing draft, ready to publish on a win: `docs/STORE_LISTING.md`

**Team:** AardAppvark — solo. Publisher of 28 live apps on the Solana dApp Store since May 2026 (the AardAppvark Toolkit among them), all of which now list Undersigned as their review layer. Wallet for the hackathon: the publisher wallet named in each app's manifest.

**Funding:** none (no VC, no angel).

**Demo video:** `[link, owner]` — follows `docs/DEMO_SCRIPT.md`, recorded on a Seeker with the double-tap on camera.

**Pitch deck:** `assets/submission/Undersigned-pitch.pdf`

## Five minutes with it (the judge's path)

1. **The app** (`assets/submission/Undersigned-1.1.13-vc24.apk`): open it, take the tour, open any app's page, tap Write a signed review; with no wallet yet, the form offers Sign in with wallet, then the Seed Vault sheet. Payments tab: leaderboards, categories, rising dApps; tap one for its history.
2. **The site**: https://undersigned.provenby.dev · [/payments](https://undersigned.provenby.dev/payments) · [/status](https://undersigned.provenby.dev/status), where "Scores recomputed from the chain alone · OK" is a six-hourly run of `api verify` that rebuilds every score from the registry in a throwaway database and diffs the live dataset.
3. **The chain**: a review signed on a Seeker, [4Eqgb1ex…odmNXXL5](https://solscan.io/tx/4Eqgb1exXvtVofUzh6SuGLEatr876qwLgjm5cr37bdXCRzEemMGbA2v65GbzQKaLs93QDSMPir7ys1XwodmNXXL5); the registry `BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs`; the [browser viewer](https://aardappvark.github.io/undersigned-viewer/) that reads it with no server.
4. **An agent**: add `https://undersigned.provenby.dev/mcp` to Claude or Cursor; 25 read-only tools.
5. **Honesty check**: `/v1/stats` says `reviews_counted: 0`. Every record so far is ours, labelled and never counted; the rules hold even for us.

## What the judges will check, and where it is

| Evaluation process item | Evidence |
|---|---|
| Completion based on the demo video | the video shows a review signed, landed and read back from the chain in a browser |
| Technical depth based on GitHub commits | 212 commits on 19 days; 295 Kotlin tests; golden tx bytes; `docs/SPEC.md`; 144/144 live checks |
| Mobile-optimised UX, mobile features | Seed Vault signing, Genesis Token, PackageManager facts, usage stats, notifications, weekly WorkManager, A–Z rail, offline icons, 28 locales |
| Usage and interaction with the Solana network | every record is a mainnet tx; 77 records live today (`/v1/stats`) |
| Clarity and vision from the presentation | the deck: the core loop first, then the handover path to the store |

## Owner's steps (in order)

1. Re-record the demo video on 1.1.13 (script in `docs/DEMO_SCRIPT.md`), upload, copy the link.
2. Sign in to RADOS / Align at solanamobile.radiant.nexus, complete the builder profile, register for Clock In.
3. Install the Radiants Align GitHub app on `aardappvark/Undersigned`.
4. Fill the form from this file; attach the APK and the deck; paste the video link.
5. Complete the final submission agreement before Monday 13:59 Budapest.
