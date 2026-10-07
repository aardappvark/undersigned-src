# CLOCK IN submission — Undersigned (draft, 8 Oct 2026)

Everything the Align form is likely to ask for, in final wording. Nothing here is submitted until the owner does it.

## Deadline and where

| | |
|---|---|
| Submissions close | **Monday 12 October 2026, 22:59 Melbourne (GMT+11)** = 13:59 Budapest = 11:59 UTC. No late entries. [verified: solanamobile.radiant.nexus desk calendar + terminal, 8 Oct] |
| Judging | 13 Oct – 9 Nov; results 11 Nov (the brief says "November 10th"; the calendar says 11 Nov 14:00 GMT+11) |
| Where | https://solanamobile.radiant.nexus → the terminal (RADOS) → sign in / sign up (email or Google) → complete the builder profile → register for Clock In → submit. One submission per contestant; the team lead submits. A draft can be edited until the "final submission agreement" is completed. |
| GitHub access | The repo can stay PRIVATE: install the **Radiants Align GitHub app** on `aardappvark/Undersigned` and share that repo. No judge invites needed. [verified: FAQ "Does my GitHub repo need to be public?"] |
| AI Coach | The form runs an "AI Coach" first review against the rubric before human judges. Write to the four criteria by name. |

## Required deliverables (all four, before the deadline)

| Deliverable | Status | Where |
|---|---|---|
| Functional Android APK | **READY** — 1.0.0 (versionCode 10), 10.1 MB, signed v2+v3 with the fleet key, lint 0 errors, 174 tests | `app/build/outputs/apk/release/app-release.apk` (copy in `assets/submission/`). Attach it as a GitHub Release asset on the repo (`v1.0.0`) or upload in the form. |
| GitHub repository | **READY after push** — 115 commits, 8 Sep → 8 Oct, all inside the window. 5 commits are local only (icons, snapshot). | https://github.com/aardappvark/Undersigned (private) |
| Demo video (≤ 3 min) | **OWNER** — needs the Seed Vault double-tap on camera. Script: `docs/DEMO_SCRIPT.md` | Upload to YouTube (unlisted) or Drive; paste the link |
| Pitch deck | **READY** — 11 slides | `assets/submission/Undersigned-pitch.pdf` (source `pitch.html`) |

## Eligibility check (against the Rules page and the T&Cs, 8 Oct)

- Started within 3 months of launch: first commit 8 Sep 2026, the launch day. Nothing published before it.
- No VC or angel funding: true. Solo builder, AU resident; Australia is on the eligible list (terms memo, 8 Sep).
- "Significant new mobile development during the hackathon": the whole app.
- Integrates the Solana Mobile Stack + Mobile Wallet Adapter: every signature goes through MWA to the Seeker Wallet / Seed Vault.
- Interacts meaningfully with Solana: every review, reply, flag, claim, listing and licence is a mainnet transaction.
- Runs on a device, not only an emulator: three Seekers run it; the demo is recorded on one.
- Winners must publish to the dApp Store within 30 days: `docs/STORE_LISTING.md` is ready; cost about 0.021 SOL.
- KYC via Sumsub for finalists (owner, after the announcement).

## Form fields — final wording

Field names are my best guess at Align's form `[unverified: the form is behind the owner's login]`; the texts are final.

**Project name:** Undersigned

**Tagline (one line):** Signed reviews from Seeker owners. One Seeker, one voice, written to Solana, readable by anyone.

**Category:** Mobile (single category). Also entering the **SKR Integration prize** (see below).

**Short description (about 300 characters):**
Undersigned is a review layer beside the Solana dApp Store. Every review is a transaction the owner signs on their Seeker; only wallets holding a Genesis Token count, so a review farm needs a farm of phones. Publishers prove their apps and reply on-chain. No server is needed to read it.

**Full description:**

The dApp Store shows a star rating, but nothing tells you which of those stars came from a real Seeker. Undersigned does. It is an Android app for Seeker that lists every dApp in the store (1,539 today, updated every Monday), shows the store's own rating beside a **signed score**, and lets the owner write a review that their wallet signs with the Seed Vault double-tap. The review is a memo transaction carrying a fixed reference address, so anyone can find every review by reading the chain.

What makes a review count is the device, not the account. A review is counted only when the signing wallet holds a Seeker Genesis Token, which is soulbound to a Seed Vault key; the wallet's own transaction fingerprint shows the review was signed on the phone. One Seeker is one voice. Staked SKR raises a voice's weight, capped at three times, and the app never asks anyone to stake, buy or move anything: it reads the chain, it does not touch it.

Publishers prove an app is theirs by signing a claim that verifies against the website the store lists (`/.well-known/undersigned.txt`) or the manifest of the store's own build, then reply on-chain under their name. Publishers who do not want to sign with a treasury wallet can list a treasury from their proven publisher wallet instead. Twenty-nine apps carry proven claims today.

What the phone can prove is shown as facts, never verdicts: whether the installed build matches the certificate the store lists, whether permissions grew between versions, how long the owner has actually used the app (opt-in). Integrity is handled in the open: flags, "looks genuine" clears, consensus hiding at a Genesis-weighted threshold, author disputes, and moderation that can hide text but never a voice.

Everything is readable without the app: a public website and JSON API (https://undersigned.provenby.dev), a browser viewer that reads the chain directly, a dataset download, and an MCP server so AI agents can read reviews and hand the owner a transaction to sign. Twenty-eight languages. No token, no rewards, no server of ours between the owner and the chain.

**What was built during the hackathon (8 Sep – 8 Oct 2026):** everything. 115 commits, about 17,000 lines of Kotlin across the Android app, a pure-JVM protocol module (memo codec, ranking, reputation, payments; 174 unit tests, including golden-transaction byte tests shared with the server), the API server, the website, the MCP server, 28 locales and the legal pack. The first mainnet review was signed on 8 Sep; the first Pro licence was paid in SKR on 15 Sep; 29 publisher claims and 28 treasury listings were signed on 7 Oct.

**Built after the deck was cut (8 Oct, in the repo before the deadline):** review nudges (opt-in; one store app a day that has been on the phone a week or used an hour and has no review from this wallet, each asked once, opening its composer), "Share to Undersigned" (share a store listing or an app name from any app; it opens that app's page or a search), and a one-tap badge embed for proven publishers on the app page. The public API now seeds its dApp list from the app's shipped list so both say 1,539.

**Solana Mobile Stack / MWA usage:** every write is an MWA `transact` to the Seeker Wallet (authorize once, then sign-and-send); the Genesis Token check reads the soulbound Token-2022 mint on the signer; the Seeker wallet fingerprint (the Lighthouse guard it adds to every transaction) is read back from the chain and shown on the record; `.skr` names resolve from the ANS registry; batch signing puts up to 8 reviews in one approval. The app is Compose, portrait, mobile-first; nothing of it exists as a web app.

**SKR integration (for the SKR prize):** SKR is a payment rail and an identity layer, not a staking integration. Pro, publisher treasury listings and API passes are priced in SKR (651 SKR, the fleet price) and paid straight to the treasury token account with a memo and a reference address, so the licence is read back from the chain by the app, the website and the API without any server of ours. The first real Pro payment was 276 SKR on 15 Sep 2026 (signature 2L6fwENd…). `.skr` names are shown everywhere a wallet appears. Separately, staked SKR is *read* to weight a counted voice (cap 3×), which the FAQ excludes from the prize, so it is not the claim.

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

## What the judges will check, and where it is

| Evaluation process item | Evidence |
|---|---|
| Completion based on the demo video | the video shows a review signed, landed and read back from the chain in a browser |
| Technical depth based on GitHub commits | 115 commits over 31 days; protocol tests; golden tx bytes; `docs/SPEC.md` |
| Mobile-optimised UX, mobile features | Seed Vault signing, Genesis Token, PackageManager facts, usage stats, notifications, weekly WorkManager, A–Z rail, offline icons, 28 locales |
| Usage and interaction with the Solana network | every record is a mainnet tx; 73 records live today (`/v1/stats`) |
| Clarity and vision from the presentation | the deck; the ask is one line: read the ledger in Guardian curation |

## Owner's steps (in order)

1. Record the demo video (script in `docs/DEMO_SCRIPT.md`), upload, copy the link.
2. Sign in to RADOS / Align at solanamobile.radiant.nexus, complete the builder profile, register for Clock In.
3. Install the Radiants Align GitHub app on `aardappvark/Undersigned`.
4. Fill the form from this file; attach the APK and the deck; paste the video link.
5. Complete the final submission agreement before Monday 13:59 Budapest.
