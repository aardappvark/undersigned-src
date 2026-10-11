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

## Cut v3 — rebuilt 12 Oct 2026, 2:58, `assets/video/undersigned-demo-v3.mp4` (TEMPORARY Gordon voice)

Built by `assets/video/build3.py` from the 1.1.19 takes and stills, the logo card at both ends, the motion opener and close
(the cost scene is cut: redundant, owner), the eight comparison frames (store cards in English; the Polish review appears
only translated), two terminal frames with the real API and MCP answers captured 12 Oct, and the feature-audit beats
(batch signing, Pro paid in SKR, store-review import, quick reviews, the Changes tab, the publisher desk, the store review
hidden for abuse). Every line below is spoken as written ("dApp" fed as "dapp"); each ends before the next cue. **Owner to
approve these lines before any Gracie credit**; then `VOICE` swaps and the cut is rebuilt.

| At | On screen | Line |
|---|---|---|
| 0:02 | motion 0–9s | Undersigned. Fifteen hundred dapps, half a million star ratings. How many came from a real Seeker owner? The listing cannot say. |
| 0:11 | jup_lens.png | One app took eight five-star store reviews in two days. Undersigned shows the burst as a fact. |
| 0:15 | compare-7.png | A developer fixes the bug. In the store the stars stay. Here the fix is signed, and the reviewer follows up. |
| 0:22 | tour2.mp4 | Open it fresh: a tour, every dapp in the store, updated every Monday, the signed score beside the store's own rating. |
| 0:27 | browse.mp4 | Flick the rail, search by name, sort by signed score, filter by tag. |
| 0:31 | apppage.mp4 | An app's page: the store rating credited, the signed score, scores per version, reviews over time, and what this phone can check about the build. |
| 0:37 | compose.mp4 | Write a review: stars, tags from a fixed vocabulary, your own words, and device facts you choose to attach. No wallet yet? The form offers Sign in with wallet. |
| 0:45 | w_sheet.png | The Seeker Wallet shows the fee. A double tap on the sensor signs it in the Seed Vault. The app never sees a key. |
| 0:53 | batch_list.png | Or tick the apps this phone has used and sign them together: eight reviews, one double tap. |
| 0:58 | record.mp4 | Back on the page the review carries its marks; staked SKR is shown, never weighed. Tap a star row to read only those reviews. Tap a reviewer for everything they have signed. |
| 1:05 | web_app.png | The same record on the web, through the API and MCP, and on Solscan: one transaction nobody can edit. |
| 1:12 | term_api.png | And it answers: the API, every review with every signal, no key to start; and the MCP server, one URL in Claude or Cursor, twenty-five read-only tools. |
| 1:21 | thread.mp4 | Signed on mainnet: a four-star crash report, the publisher's Fixed in version one sixty-eight, and a five-star follow-up. Our own thread, labelled, never counted. |
| 1:29 | desk.png | A publisher desk on the phone: what is unanswered, how fast you reply, a developer kit. |
| 1:34 | compare-1.png | Beside the store: one phone, one voice. Tags and your words. Tap a star to read why. Copies named, bursts shown. A publisher proven, an answer that lasts, a fix the reviewer confirms, problems found in a minute. |
| 1:44 | pro_pay.png | Pro is paid in SOL or SKR, three hundred thirty-two with a Genesis Token, the licence written to the ledger. |
| 1:49 | translate.mp4 | Then any review or reply reads in your language with one tap, labelled as a suggestion by AI, plus two tags of your own. |
| 1:58 | myreviews.png | Your store reviews come across in one approval, signed beside the originals. |
| 2:02 | review_gate_emulator.png | No Seeker? A quick review, shown apart, never counted. |
| 2:05 | changes.png | When an installed app's certificate or permissions change, Changes says so. |
| 2:09 | settings.mp4 | In-app notifications for every review, reply and fix, each its own switch, so a publisher acts the same day; twenty-eight languages. |
| 2:16 | abuse_block.png | Abusive language is refused as you type and hidden wherever reviews are read; a moderator can exclude a wallet, on-chain. |
| 2:22 | caught.png | Read from the store today: of twenty-seven thousand reviews, four would be hidden here, words masked. |
| 2:27 | store_hidden.png | On the app's page the store review stays, its text replaced by the reason. |
| 2:31 | payments.mp4 | Because it reads the ledger, it shows what the store's dapps are paid: leaderboards, categories, what is rising; proven treasuries kept apart from wallets found in code. |
| 2:39 | motion 90–97s | It runs on about eight hundred dollars a year; a hundred and twenty-seven Pro licences cover it. |
| 2:46 | web_status.png | Every score is recomputed from the chain alone, six-hourly; a browser viewer reads the chain with no server at all. |
| 2:50 | motion 104–112s | Undersigned. One Seeker, one voice. Written to Solana. Readable by anyone. |

Known limits of this draft: motion scene 9 still carries a v1 figure ("$194,002 at 10%" where the deck says "target"); no captions; no music; the double-tap is the 8 Oct wallet stills, not a camera take. The abuse line will carry the
full store-crawl figure once the server-side crawl of every dApp's reviews finishes. Nothing in it is 1.0.0.
