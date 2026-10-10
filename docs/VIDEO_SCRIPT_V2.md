# Video script v2 — for approval before the Gracie voice-over (10 Oct 2026)

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
| 4 | 0:28–0:42 | The Seeker, centre, Seed Vault double-tap; four cards beside it: Write · Sign · Counted · Read anywhere | "Undersigned makes a review a transaction. Write it, sign it with the Seed Vault, and it counts: one Seeker, one counted voice per app. Anyone can read it: the app, the web, the API, Solscan." |
| 5 | 0:42–0:52 | Two bars to scale: "$2 — a fake review bought for an Android app (34 providers, 2019)" and "$450–500 — another Seeker, for every extra voice on an app" | "In a twenty nineteen study, a fake review for an Android app cost about two dollars. Here, every extra voice on an app needs another Seeker: four hundred and fifty dollars or more." |
| 6 | 0:52–1:05 | Example cards: ★★ → "Fixed in 185" (publisher, signed) → ★★★★★ follow-up, "+3★". Right: a 2.5× crop of the real app showing the reply and **Mark fixed** | "When the developer fixes it, they sign that it's fixed. The reviewer is told, and can follow up. Both sets of stars stay public, and so does which developers listen." |
| 7 | 1:05–1:15 | Four rows, dApp Store vs Undersigned: one phone one voice · fix → follow-up · bursts shown as facts · an open record anyone can recompute | "Beside the store's own reviews: one phone, one voice. Fixes followed up. Bursts shown as facts. An open record anyone can recompute." |
| 8 | 1:15–1:26 | **Live chart**: "What dApps were paid · last 30 days" (the top-dApps bars from undersigned.provenby.dev/payments), confirmed in green, found-in-code in amber | "And because it reads the ledger, it shows what the store's apps are actually paid: leaderboards, categories, what's rising. From treasuries publishers proved, and wallets found in app code, labelled apart." |
| 9 | 1:26–1:36 | Three cards: $843 a year, infrastructure · 127 Pro licences to break even · **Target** ~$194k a year | "It runs on about eight hundred dollars a year; a hundred and twenty-seven licences cover it. Our target, one Seeker in ten on Pro plus publisher and data plans, is about two hundred thousand a year." |
| 10 | 1:36–1:43 | 121,161 Genesis Tokens · ~206 new store ratings a day · 28 apps we publish on this store | "A hundred and twenty-one thousand Genesis Tokens are out there, and two hundred new store ratings land every day, none tied to a device. We publish twenty-eight apps on this store. We built the review layer we wanted for them." |
| 11 | 1:43–1:50 | Icon on a violet tile · Undersigned · "One Seeker, one voice. Written to Solana. Readable by anyone." · undersigned.provenby.dev (36 px) · Not affiliated with Solana Mobile | "Undersigned. One Seeker, one voice. Written to Solana. Readable by anyone." |

## Figures on screen (all measured; sources in docs/MOTION_BRIEF.md and docs/FINANCIAL_CASE.md)

1,543 dApps · 478,289 ratings · 69 five-star reviews, 17–24 Mar 2026 · $1.73–2.14 per fake Android review (Martens &
Maalej 2019) · Seeker $450 early pre-order, $500 retail · $843 a year · 127 licences · $194,031 target (labelled target) ·
121,161 Genesis Tokens (on-chain, 10 Oct) · ~206 new store ratings a day (28 Sep → 7 Oct snapshots) · 28 apps published
(docs/SUBMISSION.md). The chart in scene 8 is the live `/v1/payments/chart/top.svg?window=30d` on the day of rendering.

## Still to decide (owner)

1. Scene 10 says "28 apps". The deck says 28; `/v1/stats` counts 29 proven claims because Undersigned itself is one. Keep 28?
2. The close has no "find it on the dApp Store" line because Undersigned is not listed yet. Add it only once it is.
3. Music: a 90–100 BPM bed at about −20 dB under the voice, ticks on count-ups, a soft thunk on the double-tap. Yes or no?
4. Exports after approval: this 1:50 master at 10 Mbps; a 45 s 4:5 cut with burned-in captions for X (scenes 1, 2, 4, 5, 11); a 30 s 16:9 cut for Discord.

Reply **approve** (with any edits) and I generate the Gracie lines, rebuild the motion to this script, and send the result.
