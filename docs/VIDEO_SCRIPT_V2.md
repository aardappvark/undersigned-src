# Video script v2 — PARKED until the device UI is built out and approved (owner, 10 Oct 2026)

**Status:** not yet approved; no Gracie credits spent. The owner will review the video once the app's build-out is done,
because the video is to be built around many device screenshots. What the final video must include (owner's notes):
1. **Lots of real device screenshots** of the finished UI (not the motion graphics alone).
2. **Side-by-side comparisons with the current dApp Store review flow**, showing the difficulties it leaves for both
   sides. For the developer: no quick way to see what is wrong (no tags, no per-version view), no way to answer in a
   place that lasts, no way to flag a spam review that is dragging the score, no proof of who the publisher is, no way to
   say "fixed" and have the reviewer told. For the reviewer: a star with no weight, no way to follow up after a fix, no
   way to show how long they actually used the app, no record of their own, and bursts and campaigns invisible. The full
   list is the comparison matrix in the session of 10 Oct (also docs/PITCH.md objections and MOTION_BRIEF "Content
   review"); each row becomes a before/after pair of screens.
3. **The analytics images** (leaderboard, payments by dApp, categories, rising) only once the build-out is seen and the
   UI approved.
4. **The deck (assets/submission/pitch.html) and the rest of the presentation are to be reviewed and edited to match**
   at the same time, by me, not left as they are.

The scene plan below stands as the narrative spine; the screenshots and comparisons slot into scenes 3, 4, 6, 7 and 8.


Eleven scenes, 1:50. Built from the independent review of v1 (docs/MOTION_BRIEF.md "Content review" still applies).
Nothing is generated with Higgsfield until this script is approved; then every line is voiced by **Gracie** (about 10
credits for the lot), laid in by `tools/build_voice.sh`, and the motion is re-rendered.

## What changed from v1, and why

| Change | Why |
|---|---|
| "Undersigned" on screen from 0:00 (a small wordmark in the corner of every scene) and said in the first line | v1 named the product at 0:36 |
| Scene 2 (the field of identical stars) cut; its one line folded into scene 1 | 5 seconds of silence over a still |
| The campaign scene gets its payoff: "Undersigned shows this burst, as a fact" | those reviewers held Genesis Tokens; without the payoff the scene argued against us |
| Fix scene: the version label ticks 184 → 185 while the two stars dim | nothing moved, so "the stars stay" was not visible |
| Core loop: the phone (Seed Vault) is the centre of the shot; the four cards sit beside it | the phone was 300 px and dim |
| Fake-review cost reworded: "every extra voice **on an app** costs another Seeker"; the "210–290×" ratio dropped | one Seeker can sign one counted review on each app, so "per review" overstated it |
| Second round: a 2.5× crop of the real reply and "Mark fixed" | the full screen was unreadable and showed an operator wallet's stake |
| Side by side cut to four rows | seven rows and 5 s of silence; "shown beside each other ✓/✓" said nothing |
| **New scene: what dApps are paid** (the live analytics chart) | a differentiator nobody else has, built today, and it reads well on screen |
| Numbers: the card says "**Target**: ~$194k a year"; "all in" → "infrastructure" | false precision on a target; "all in" was wrong (no labour) |
| **New beat: why now, and who** (121,161 Genesis Tokens; ~206 new store ratings a day; 28 apps on the store) | the two things judges ask that v1 never answered |
| Close: the icon on a violet tile, the URL at 36 px white | the white Ø on black read as a "prohibited" sign |
| Amber for the problem numbers, green only for Undersigned | green meant nothing when it was used for everything |

## Scenes and voice-over

Pace: about 2.4 words a second. Every line ends at least 1 s before its scene does.

| # | Time | On screen | Voice-over |
|---|---|---|---|
| 1 | 0:00–0:09 | Wordmark. 1,543 dApps and 478,289 star ratings count up (white). "How many came from a real Seeker owner?" | "Undersigned. The dapp Store has fifteen hundred apps and almost half a million star ratings. How many came from a real Seeker owner? On the listing, every star looks the same." |
| 2 | 0:09–0:19 | Amber **69** ★★★★★; "five-star reviews in eight days, during an in-game-gold-for-5★ offer"; then the payoff card: **"Undersigned shows this burst, as a fact."** | "In March, one app offered in-game gold for five-star reviews. Sixty-nine arrived in eight days. The listing says nothing. Undersigned shows the burst on the app's page, as a fact." |
| 3 | 0:19–0:28 | ★★ "Crashes when I open my wallet" · v184. Reply: "Fixed in version 185." The version label ticks to 185; the two stars dim and stay. | "When a developer fixes the problem, the two stars stay. Nothing ties the fix to the review." |
| 4 | 0:28–0:42 | The Seeker, centre, Seed Vault double-tap; four cards beside it: Write · Sign · Counted · Read anywhere. Then two quick device beats: tap a star row (the list filters), tap a reviewer’s record (their history) | "Undersigned makes a review a transaction. Write it, sign it with the Seed Vault, and it counts: one Seeker, one counted voice per app. Tap a star to read why. Tap a reviewer to see everything they have signed. Anyone can read it: the app, the web, the API, Solscan." |
| 5 | 0:42–0:52 | Two bars to scale: "$2 — a fake review bought for an Android app (34 providers, 2019)" and "$450–500 — another Seeker, for every extra voice on an app" | "In a twenty nineteen study, a fake review for an Android app cost about two dollars. Here, every extra voice on an app needs another Seeker: four hundred and fifty dollars or more." |
| 6 | 0:52–1:05 | The real round, signed on mainnet 11 Oct (`assets/submission/shots/tk_thread.png`): 4★ "crashed on 166" → "Fixed in version 168" by the publisher → 5★ follow-up, +1 | "When the developer fixes it, they sign that it's fixed. The reviewer is told, and can follow up. Both sets of stars stay public, and so does which developers listen." |
| 7 | 1:05–1:25 | The eight side-by-side frames (`assets/video/frames/compare-1..8.png`, 2.5 s each): who is speaking · saying what was wrong · reading why · copies and bursts · who the publisher is · answering · fixing (the real round of 11 Oct) · seeing what is wrong fast | "Beside the store's own reviews: one phone, one voice. Tags and your own words, device facts you choose. Tap a star to read why. Copies named, bursts shown as facts. A publisher proven, an answer that lasts, a fix the reviewer confirms, and problems found in a minute." |
| 8 | 1:15–1:26 | **Live chart**: "What dApps were paid · last 30 days" (the top-dApps bars from undersigned.provenby.dev/payments), confirmed in green, found-in-code in amber | "And because it reads the ledger, it shows what the store's apps are actually paid: leaderboards, categories, what's rising. From treasuries publishers proved, and wallets found in app code, labelled apart." |
| 9 | 1:26–1:36 | Three cards: $843 a year, infrastructure · 127 Pro licences to break even · **Target** ~$194k a year | "It runs on about eight hundred dollars a year; a hundred and twenty-seven licences cover it. Our target, one Seeker in ten on Pro plus publisher and data plans, is about two hundred thousand a year." |
| 10 | 1:36–1:43 | 121,161 Genesis Tokens · ~206 new store ratings a day · 28 apps we publish on this store | "A hundred and twenty-one thousand Genesis Tokens are out there, and two hundred new store ratings land every day, none tied to a device. We publish twenty-eight apps on this store. We built the review layer we wanted for them." |
| 11 | 1:43–1:50 | Icon on a violet tile · Undersigned · "One Seeker, one voice. Written to Solana. Readable by anyone." · undersigned.provenby.dev (36 px) · Not affiliated with Solana Mobile | "Undersigned. One Seeker, one voice. Written to Solana. Readable by anyone." |

## Features the film must show (audit, 11 Oct)

Every feature below has a beat in the scenes or the shot list in `docs/VIDEO_AND_DECK_PLAN_2026_10_11.md` §3: tour and A–Z (shot 0:23) · app page with the store’s rating credited (0:33) · composer with tags, own tags (Pro), note, device facts, Sign in with wallet (0:43) · Seed Vault double-tap (0:58) · the labelled record and the reviewer’s record (1:10) · website and Solscan (1:20) · publisher claim, reply, Mark fixed → follow-up (1:30) · the eight side-by-sides including tap-a-star, copies named, bursts (1:45) · Pro: own tags, Translate button, offers that hide, notification switches, quick review without a wallet (2:05) · payments board and charts (2:15) · `api verify` on /status (2:45). Batch signing and store-review import are stills in the 2:05 beat.

## Figures on screen (all measured; sources in docs/MOTION_BRIEF.md and docs/FINANCIAL_CASE.md)

1,543 dApps · 478,289 ratings · 69 five-star reviews, 17–24 Mar 2026 · $1.73–2.14 per fake Android review (Martens &
Maalej 2019) · Seeker $450 early pre-order, $500 retail · $843 a year · 127 licences · $194,031 target (labelled target) ·
121,161 Genesis Tokens (on-chain, 10 Oct) · ~206 new store ratings a day (28 Sep → 7 Oct snapshots) · 28 apps published
(docs/SUBMISSION.md). The chart in scene 8 is the live `/v1/payments/chart/top.svg?window=30d` on the day of rendering.

## Still to decide (owner)

1. **Decided (owner, 11 Oct): 28 apps** everywhere. `/v1/stats` counts 29 proven claims because Undersigned itself is one; say "28 apps we publish" and never 29.
2. The close has no "find it on the dApp Store" line because Undersigned is not listed yet. Add it only once it is.
3. Music: a 90–100 BPM bed at about −20 dB under the voice, ticks on count-ups, a soft thunk on the double-tap. Yes or no?
4. Exports after approval: this 1:50 master at 10 Mbps; a 45 s 4:5 cut with burned-in captions for X (scenes 1, 2, 4, 5, 11); a 30 s 16:9 cut for Discord.

Reply **approve** (with any edits) and I generate the Gracie lines, rebuild the motion to this script, and send the result.

## Cut v3 — rebuilt 12 Oct 2026, 2:59, `assets/video/undersigned-demo-v3.mp4` (Higgsfield Gracie voice + cold open)

Built by `assets/video/build3.py` (`FORCE=1 VOICE=file`) from the 1.1.26 takes and stills, subtitles burned in, a three-second
Higgsfield cold open (`assets/video/broll/cold_open.mp4`: a fingertip on a Seeker's side sensor, generated with minimax_h3_max
from the deck's hero plate, 12.5 credits), the logo card at both ends, the motion opener and close, two terminal frames with the real API and MCP answers captured 12 Oct, and the
feature-audit beats (batch signing, the mainnet fix thread, the feature-request thread, the publisher desk, Pro paid in
SKR, translation, notifications, abuse refused and the store reviews it would hide, the payments ledger, /status).
Voice: Higgsfield Gracie (seed_audio preset), approved by the owner 12 Oct; 35 generations, 20.8 credits. Gracie reads about
a third slower than the Gordon draft, so to stay under three minutes five lines were shortened and six beats were cut
rather than any line sped up: the eight comparison frames (the deck carries them), the store-review import still, the
quick-review still, the Changes still, the cost scene, and the spoken line over the hidden store review (the still stays).
Each line ends before the next cue; "Solana" fed as "Solarna", "dApp" as "dapp".

| At | On screen | Line |
|---|---|---|
| 0:05 |  | Undersigned. Fifteen hundred dApps, half a million star ratings, all from Seeker owners. How many say something a developer can act on? |
| 0:14 | jup_lens.png | Copies and bursts are shown as facts, so care stands out. |
| 0:19 | compare-7.png | Meaningful reviews create outcomes: here the fix is signed, and the reviewer follows up. |
| 0:26 | tour2.mp4 | Open it fresh: a tour, every dApp, the signed score beside the store's. |
| 0:33 | browse.mp4 | Flick the rail, search, sort, filter by tag. |
| 0:39 | apppage.mp4 | An app's page: scores per version, reviews over time, and what this phone can check about the build. |
| 0:46 | compose.mp4 | Write a review: stars, fixed tags, your own words, and the device facts you choose. |
| 0:54 | w_sheet.png | The Seeker Wallet shows the fee. A double tap signs it in the Seed Vault. The app never sees a key. |
| 1:02 | batch_list.png | Or tick the apps this phone has used: eight reviews, one double tap. |
| 1:09 | record.mp4 | The review carries its marks. Tap a star row to read only those; tap a reviewer for all they have signed. |
| 1:17 | web_app.png | The same record on the web, the API, MCP and Solana: nobody can edit it. |
| 1:26 | term_api.png | The API answers every review, every signal; MCP gives Claude or Cursor twenty-five read-only tools. |
| 1:36 | thread.mp4 | On mainnet: a crash report, the publisher's fix, a five-star follow-up; ours, never counted. |
| 1:45 | request_category.png | Tag a feature request and its kind; the publisher signs will build or won't. |
| 1:51 | request_thread.png | Signed today: the request, will build forty-five minutes later, delivered within the hour. |
| 1:59 | desk.png | A publisher desk: unanswered, reply speed, a kit. |
| 2:04 | pro_pay.png | Pro is paid in SOL or SKR, three hundred thirty-two with a Genesis Token. |
| 2:11 | translate.mp4 | Then any review or reply reads in your language with one tap, labelled as a suggestion by AI. |
| 2:19 | settings.mp4 | In-app notifications for every review, reply and fix, each its own switch. |
| 2:25 | abuse_block.png | Abusive language is refused as you type and hidden wherever it is read. |
| 2:32 | caught.png | Of fifty-five thousand store reviews read, fifteen would be hidden. |
| 2:39 | payments.mp4 | It reads the ledger: what dApps are paid, leaderboards, categories, what is rising. |
| 2:46 | web_status.png | Every score is recomputed from the chain; anyone can check it. |
| 2:51 |  | Undersigned. One Seeker, one voice. Written to Solana, readable by anyone. |

Known limits: no music; the double-tap is the 8 Oct wallet stills, not a camera take; the abuse figure is the 55,824-review scan
of 12 Oct (the full crawl is still running on the server). Nothing in it is 1.0.0.
