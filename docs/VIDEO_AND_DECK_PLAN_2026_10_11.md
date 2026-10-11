# Video and deck: critical review and plan (11 Oct 2026)

Deadline: Mon 12 Oct 2026, 11:59 UTC (13:59 Budapest). Judge re-score today: 75/100. Completion 14/20 is scored from the
video, and the only cut that exists (`assets/video/undersigned-demo.mp4`, 2:29, 8 Oct) shows 1.0.0 and narrates a rule
that no longer exists ("weighted by staked SKR, capped at three times") and says "reward". It must not be submitted, not
even as a fallback. The motion piece (1:52) has no device footage. The v2 script (`docs/VIDEO_SCRIPT_V2.md`) is parked.

**Verdict in one line.** The deck was good and is now fixed (see §2); the video is the blocker and the single
highest-value task left. One independent counted review, signed before filming, is the only thing that would lift three
criteria at once, and it is the one thing only the owner can arrange.

## 1. What the judges score, and where each piece falls short

| Criterion | Score | Shortfall the video or deck can fix |
|---|---|---|
| Completion | 14/20 | The video shows 1.0.0. Nothing on film shows the sign-in button, the follow-up round, the Pro tags, the Payments tab or the status page. |
| Technical depth | 17/20 | `api verify` ("scores recomputed from the chain alone · OK" on /status) is the strongest proof we have and appears in neither. Batch signing (8 reviews, one approval) is in no shot. |
| Mobile UX | 15/20 | The judge's note was "Seed Vault only through the Seeker Wallet". Slide 7 now says so plainly and lists what is Seeker-only; the video must show the tour, the A–Z rail, offline icons, notification switches. |
| Use of Solana | 13/20 | All 77 records are ours; 0 counted. Only an independent signer changes this. The video can at least show the record read back on Solscan and the website. |
| Clarity and vision | 16/20 | Stale figures (fixed today), one text-wall slide (cut today), two unsourced lines (cut today). |
| SKR prize | 5/10 | One SKR payment, ours. Slide 8 now shows the second rail (28 treasury listings priced in SKR) and the Genesis price (332 vs 369 SKR). A second wallet paying in SKR would do more than any slide. |

## 2. Deck: what was wrong and what is now done (pitch.html, PDF re-rendered 19:13)

Done today, in the file:
- Slide 2: "trustworthy" (banned) → "What a review needs to count"; the overstated "a thousand accounts" bullet now says
  what the listing cannot tell you (Seeker, use, burst).
- Integrity moved to slide 4 with the hook as its headline: "A fake review costs $2 on Android. Here it costs a Seeker."
  Seeker price sourced on the slide (US$450 pre-order, US$500 retail). The burst caption now reconciles 8 reviews with 6
  wallets read. "verify" → "check".
- Slide 7 (mobile stack): says plainly that the Seed Vault is reached through the wallet's MWA sheet like every Seeker
  dApp, and that everything else on the slide is Seeker-only.
- Slide 8 (SKR): the unsourced "Solana Mobile names curation as a use of SKR" line is cut; two duplicate bullets merged;
  added SKR-as-identity (332 vs 369) and the second live SKR rail (28 treasury listings).
- Slide 10 (developers): three 230-word text walls cut to short cards; `api verify` and the 144/144 live checks added.
- Slide 12 (analytics): the caption claimed "confirmed treasuries in green"; the chart has none. It now says all ten are
  found-in-code (amber) and the confirmed treasuries, our own apps, sit far below.
- Slide 13 (numbers): "<$1k a year" labelled "at list prices"; the "Not yet priced in" card contradicted the $194k figure
  and quoted an unsourced Samsung line; replaced with "In the model, and beyond it".
- Slide 14 (proof): 196 commits, 288 tests, 21,543 + 4,047 lines, all measured; release pill 1.1.8.
- Figures aligned everywhere: 1,547 dApps (11 Oct), first commit 7 Sep 2026 (UTC), 19 active days.

Still open on the deck (needs fresh shots, 1–1.5 h):
- Slide 5 shot `tk_top.png` is from 8 Oct; replace with a 1.1.8 app page.
- Slide 5/6 `tk_reviews.png` shows "Staked when read: 152,262 SKR" on an operator review, which invites "so stake matters?".
  Crop above the stake line or shoot a review without it.
- Slide 11 reuses `jup_store.png`; shoot "Copy this review to the dApp Store" instead.
- Slide 10 would carry one screenshot of Claude or Cursor calling an MCP tool.
- Decide 28 vs 29 apps (28 published; 29 claims because Undersigned itself is one). The deck says 28 throughout.

## 3. Video: what is wrong and the shot list

The v2 script is motion only. The owner's rule stands: the final video is built around real device screenshots and
footage, side-by-side comparisons with the store's own review flow for both the developer and the reviewer, and the
analytics images, with the deck edited to match. Treat the motion scenes as 50 seconds of bookends around 2:10 of screen
recording. Status bar never in shot (`tools/shot.sh` rule); `dApp` written "dapp" in every TTS line.

| t | Shot | Capture |
|---|---|---|
| 0:00–0:08 | Motion scene 1 (1,547 dApps, 478,289 ratings, the question) | existing render, re-number to 1,547 |
| 0:08–0:15 | Motion burst (69 in 8 days) → cut to the real burst card | screen: Jupiter page, scroll to Review bursts, 4 s |
| 0:15–0:23 | Side-by-side 1, developer: the store's reply box with no "fixed" vs Mark fixed | screen: a store listing with a reply, 4 s; Toolkit review card, 4 s |
| 0:23–0:33 | Open 1.1.8 fresh: tour bubble, Browse, A–Z rail flick, "New this week" | one take, 10 s |
| 0:33–0:43 | App page: store rating beside the signed score, Seeker-owner lens on store reviews | 10 s |
| 0:43–0:58 | Write a review: stars, two tags, note, usage toggle on, the Sign in with wallet button | 15 s |
| 0:58–1:10 | Seeker Wallet sheet (fee visible) → double-tap, filmed with a camera over the screen → "Published" | 12 s, owner's hands |
| 1:10–1:20 | Back on the page: the review with Genesis and Seeker-wallet marks and its label | 10 s; if an independent owner signed, this is their review |
| 1:20–1:30 | Browser: the app's page on the website, then Solscan with the memo and reference account | Mac screen, 10 s |
| 1:30–1:45 | Publisher: Publisher mark, Mark fixed → notification on a second Seeker → follow-up with new stars, change shown | two screens, 15 s |
| 1:45–2:05 | Side-by-sides 2 and 3: developer matrix and reviewer matrix, four rows each, a real shot in every Undersigned cell | motion + stills, 20 s |
| 2:05–2:15 | Pro: offers that hide once Pro is active, up to two tags of your own, notification switches | 10 s |
| 2:15–2:25 | Payments tab on the phone, then the three web charts (top dApps 30 d, weekly 12 months, categories/rising) | 4 s + 6 s |
| 2:25–2:33 | Motion scene 5 ($2 vs $450–500) | existing |
| 2:33–2:45 | Motion scenes 9 and 10 (numbers, labelled target; why now) trimmed to 35 words | existing, re-timed |
| 2:45–2:52 | Read → adopt → sign card; "Scores recomputed from the chain alone · OK" from /status | one still |
| 2:52–3:00 | Close: icon on violet, "One Seeker, one voice", URL, not affiliated | existing scene 11 |

The two matrices (the owner's required comparison):
- Developer: no tags or per-version view → tags, versions, bursts; a reply that vanishes → a signed reply that lasts;
  spam dragging the score with no recourse → flags, consensus, labels; no "fixed" loop → Mark fixed, reviewer told,
  follow-up stars shown.
- Reviewer: a star with no weight → one Seeker, one counted voice; no follow-up after a fix → the second round;
  no proof of use → install age, usage time, activity, opt-in; no record of your own → your reviews on-chain, your
  standing; bursts invisible → bursts shown as facts.

Nothing before 1:30 is optional. If the cut runs long, quick reviews and device facts go to the deck.

## 4. Gap list (features neither deck nor video shows, by judge value)

1. Any review by an independent Seeker owner, counted. Not a feature gap; the gap. One signer moves three criteria.
2. The 1.1.7–1.1.8 features: sign-in on the form, own tags for Pro, offers that hide, the named similar-text label.
3. Batch signing (8 in one approval) and the store-review import in one approval: pure Seeker/MWA depth, in no shot.
4. The follow-up thread end to end (notification → follow-up): scripted, never shot.
5. Tour, A–Z rail, New this week, offline icons, 28 locales: the Mobile UX evidence; one shot exists.
6. Notification switches per action; quick reviews shown apart; the browser viewer with no server; `/status` with the
   chain recompute; MCP in a real client; the publisher desk, webhooks and badge; 2,000-character notes; `.skr` names.

## 5. Plan, in order (one person; about 13 h plus approvals)

| # | Task | Est. | Who |
|---|---|---|---|
| 1 | Ask one or more real Seeker owners (users of our 28 apps, the community) to install 1.1.8 and sign one review each. Unpaid; it costs them about 0.0001 SOL. Do this first; it runs in the background. | 1 h ask | owner |
| 2 | Capture the 1.1.8 footage from the shot list: about 12 takes over adb screenrecord on a Seeker whose launcher is in front, plus the camera take of the double-tap and the two-Seeker fix → follow-up. | 3 h | me (adb), owner (sensor taps) |
| 3 | Capture the stills: store listing with a reply, Pro tags with Pro active, Settings switches, batch sign, viewer, /status, three payment charts; replace the four deck shots in §2. | 1.5 h | me |
| 4 | Edit the 3:00 cut: ffmpeg concat of takes with the existing motion scenes re-timed as in §3. | 3 h | me |
| 5 | Final script to the owner → approve → Gracie lines ("dapp") → `tools/build_voice.sh` → loudness check → unlisted YouTube. Gordon (Apple) is the fallback if Gracie slips. | 2 h + approval | owner approves, me |
| 6 | Deck: the four shot swaps, re-render, open the PDF and look at every page. | 1 h | me |
| 7 | SUBMISSION.md: video link, final APK row (done: 1.1.8 vc19, 10.7 MB, SHA 99085b11…cb57), the 28/29 decision. | 0.5 h | me |
| 8 | Drop until after submission: the 45 s X cut, the 30 s Discord cut, a music bed, new analytics views. | — | — |

If only one thing gets done, it is tasks 2 and 4: a 1.1.8 video with the double-tap and the record read back on Solscan
is worth more than every deck edit combined.

Decisions needed from the owner: (a) approve the shot list and the two matrices above so filming can start; (b) 28 or 29
apps; (c) whether to ask independent owners before filming; (d) approve the final script before any Gracie credit.
