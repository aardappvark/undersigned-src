# Motion segments — brief, data sheet and review (10 Oct 2026)

Animated parts of the CLOCK IN video: the store today, the core loop, why it is better, the numbers. They frame the
**real device footage** (`docs/DEMO_SCRIPT.md`); they do not replace it, because judges score completion from the demo.

- Source: `assets/video/motion/motion.html` (open it in a browser to preview; `?scene=7` jumps to a scene).
- Render: `node tools/render_motion.mjs` → `assets/video/motion/undersigned-motion.mp4` (1920×1080, 30 fps, 1:52);
  `--scenes` writes one MP4 per scene for the edit.
- To make a variant in Claude Motion instead: paste the data sheet and one scene's prompt below.

## Scenes, timings and voice-over

| # | Time | On screen | Voice-over (about 2.5 words a second) |
|---|---|---|---|
| 1 | 0:00–0:09 | 1,543 dApps · 478,289 star ratings count up; "Which of them came from a real Seeker owner?" | "The dApp Store has fifteen hundred apps and almost half a million star ratings. Which of them came from a real Seeker owner?" |
| 2 | 0:09–0:18 | A field of identical stars | "On a listing, every star looks the same, whoever left it." |
| 3 | 0:18–0:27 | 69 ★★★★★ in eight days, during a gold-for-5★ campaign | "In March, one app offered in-game gold for five-star reviews. Sixty-nine arrived in eight days. Nothing on the listing says so." |
| 4 | 0:27–0:36 | Example: ★★ "crashes" → developer "fixed in 185" → the stars stay | "And when a developer fixes the problem, the two stars stay. Nothing ties the fix to the review." |
| 5 | 0:36–0:50 | Write → Sign → Counted → Read anywhere | "Undersigned makes a review a transaction. Write it, sign it with the Seed Vault, and it counts once for each Seeker. Anyone can read it, anywhere." |
| 6 | 0:50–1:01 | 100 phones → $40,000 | "A fake voice now costs a phone. A hundred fake reviews cost forty thousand dollars in Seekers, in public." |
| 7 | 1:01–1:15 | Example: ★★ → publisher signs "fixed" → follow-up ★★★★★, +3 | "When the developer fixes it, they sign that it's fixed. The reviewer is told, and can follow up. Everyone sees both sets of stars, and which developers listen." |
| 8 | 1:15–1:30 | Side by side, row by row | "Side by side with the store: one phone, one voice; fixes followed up; real usage; bursts shown as facts; and an open record anyone can recompute." |
| 9 | 1:30–1:44 | $843 a year · 127 licences · $53,110 base case | "It costs about eight hundred dollars a year to run, and breaks even at a hundred and twenty-seven Pro licences. It's paid for by tools, never by reviews." |
| 10 | 1:44–1:52 | Logo, tagline, not affiliated | "Undersigned. One Seeker, one voice. Written to Solana. Readable by anyone." |

Suggested cut for a 3-minute video: scenes 1, 3 and 4 (0:27) before the device footage; scene 5 as the bridge into the
signing; scenes 6 and 7 after it; scene 9 or 8 before the close; scene 10 to end.

## Data sheet (every figure on screen)

| Figure | Value | Source |
|---|---|---|
| dApps in the store | 1,543 | store public catalogue, 9 Oct 2026 |
| Star ratings | 478,289 (1,475 rated dApps) | Undersigned's snapshot of the store feed, 9 Oct 2026 |
| Campaign burst | 69 five-star reviews, 17–24 Mar 2026, during an in-game-gold-for-5★ offer | store reviews of one dApp (not named), read from the store feed; Undersigned burst detector |
| Sybil cost | US$400+ per Seeker; 100 × $400 = $40,000 | owner's figure for the device price |
| Running cost | US$843 a year | `docs/FINANCIAL_CASE.md` §4 |
| Break-even | 127 Pro licences a year (0.06% of 200,000) | same |
| Base case | US$53,110 a year (3% of 200,000 take Pro, plus publishers, API) | same, §5 (assumption, labelled) |
| Target | about US$172,000 a year at 10% | owner's target, labelled as such |
| Pro price | 0.0607 SOL or 369 SKR a year | `AppConfig.kt` |
| Devices shipped | 200,000 | Solana Mobile, March 2026 (company figure) |

## Content review — what we claim, what we cut, and why

- **Fair to the store.** The store already shows publisher replies, so the comparison gives it the tick. We claim only
  what it lacks: a voice bound to a device, a follow-up tied to a fix, usage on the review, bursts shown, an open record.
- **The campaign is anonymised.** Naming an app in a video invites a dispute; the pattern makes the point. Its
  reviewers held Genesis Tokens, so the scene says "nothing on the listing says so", not "fake accounts": Undersigned
  shows such bursts as facts, it cannot stop a bribe.
- **Examples are labelled "Example".** The fix → follow-up records are illustrative; the flow itself is built.
- **No "rewards".** Paying anyone around reviews breaks "no payment buys weight" and consumer law on incentivised
  reviews. What users and developers get is a voice that counts and gets answered, proof, and a second round; the
  money comes from tools around reviews.
- **Numbers that are measured, and targets that are labelled.** "Reduces spam" is said as "a fake voice costs a phone";
  "revenue centre" as "breaks even at 127 licences", with the base case and target named as assumptions.
- **Not affiliated with Solana Mobile** stays on screen in scenes 8 and 10.

## Claude Motion prompts (one per scene, with the data sheet pasted first)

1. "Animate a 9-second opener on a black background, purple and green accents, font Manrope: count up 1,543 dApps and
   478,289 star ratings, then type the question 'Which of them came from a real Seeker owner?'"
2. "9 seconds: a calm grid of 96 identical gold stars appearing in sequence, title 'Every star looks the same.'"
3. "9 seconds: the number 69 counts up beside five gold stars; caption 'five-star reviews in eight days, while one app
   offered in-game gold for a 5★ review'; then 'Nothing on the listing marks the burst or the campaign behind it.'"
4. "9 seconds: a review card with two stars, 'Crashes when I open my wallet', a developer reply 'Fixed in version 185'
   slides in; the two stars stay; headline 'Nothing ties the fix to the review.' Label 'Example'."
5. "14 seconds: four cards in a row connected by lines, lit in turn: Write, Sign (Seed Vault double-tap), Counted (one
   Seeker Genesis Token, one voice), Read anywhere (app, web, API, Solscan)."
6. "11 seconds: 100 phone outlines appear in a grid while a counter runs from $0 to $40,000; caption 'A fake voice
   costs a phone.'"
7. "14 seconds: three cards appear left to right: a 2-star review (v184), a green publisher card 'Fixed in version 185',
   a 5-star follow-up (v185); then pills 'Change +3★', 'The reviewer is notified'. Label 'Example'."
8. "15 seconds: a comparison table, rows revealed one at a time, columns 'dApp Store reviews' and 'Undersigned', using
   the rows in the data sheet; publisher replies ticked on both sides."
9. "14 seconds: three cards count up: $843 a year to run; 127 Pro licences to break even; $53,110 base case; footnote
   with sources."
10. "8 seconds: the app icon, 'Undersigned', the line 'One Seeker, one voice. Written to Solana. Readable by anyone.'"
