# Motion segments — brief, data sheet and review (10 Oct 2026)

Animated parts of the CLOCK IN video: the store today, the core loop, why it is better, the numbers. They frame the
**real device footage** (`docs/DEMO_SCRIPT.md`); they do not replace it, because judges score completion from the demo.

- Source: `assets/video/motion/motion.html` (open it in a browser to preview; `?scene=7` jumps to a scene).
- Render: `node tools/render_motion.mjs` → `assets/video/motion/undersigned-motion.mp4` (1920×1080, 30 fps, 1:52);
  `--scenes` writes one MP4 per scene for the edit.
- Voice-over: `assets/video/motion/undersigned-motion-vo.mp4` (the full video with the voice track, AAC) and
  `assets/video/motion/undersigned-motion-60s.mp4` (the short cut, with voice). `undersigned-motion.mp4` stays silent.
- To make a variant in Claude Motion instead: paste the data sheet and one scene's prompt below.

## Scenes, timings and voice-over

| # | Time | On screen | Voice-over (about 2.5 words a second) |
|---|---|---|---|
| 1 | 0:00–0:09 | 1,543 dApps · 478,289 star ratings count up; "Which of them came from a real Seeker owner?" | "The dApp Store has fifteen hundred apps and almost half a million star ratings. Which of them came from a real Seeker owner?" |
| 2 | 0:09–0:18 | A field of identical stars | "On a listing, every star looks the same, whoever left it." |
| 3 | 0:18–0:27 | 69 ★★★★★ in eight days, during a gold-for-5★ campaign | "In March, one app offered in-game gold for five-star reviews. Sixty-nine arrived in eight days. Nothing on the listing says so." |
| 4 | 0:27–0:36 | Example: ★★ "crashes" → developer "fixed in 185" → the stars stay | "And when a developer fixes the problem, the two stars stay. Nothing ties the fix to the review." |
| 5 | 0:36–0:50 | Write → Sign → Counted → Read anywhere (line icons); a Seeker phone with the real Seed Vault double-tap screen (`w_doubletap2.png`) slides in at Sign | "Undersigned makes a review a transaction. Write it, sign it with the Seed Vault, and it counts once for each Seeker. Anyone can read it, anywhere." |
| 6 | 0:50–1:01 | 100 phones → $40,000 | "A fake voice now costs a phone. A hundred fake reviews cost forty thousand dollars in Seekers, in public." |
| 7 | 1:01–1:15 | Example: ★★ → publisher signs "fixed" → follow-up ★★★★★, +3; on the right, "In the app": the real reviews screen (`tk_reviews.png`) with its "Mark fixed" action ringed | "When the developer fixes it, they sign that it's fixed. The reviewer is told, and can follow up. Everyone sees both sets of stars, and which developers listen." |
| 8 | 1:15–1:30 | Side by side, row by row | "Side by side with the store: one phone, one voice; fixes followed up; real usage; bursts shown as facts; and an open record anyone can recompute." |
| 9 | 1:30–1:44 | $843 a year · 127 licences · $194,031 at 10% | "It costs about eight hundred dollars a year to run and breaks even at a hundred and twenty-seven Pro licences. With one Seeker in ten on Pro, plus publisher and data plans, that's over a hundred and ninety thousand dollars a year: paid for by tools, never by reviews." |
| 10 | 1:44–1:52 | Logo, tagline, not affiliated | "Undersigned. One Seeker, one voice. Written to Solana. Readable by anyone." |

Suggested cut for a 3-minute video: scenes 1, 3 and 4 (0:27) before the device footage; scene 5 as the bridge into the
signing; scenes 6 and 7 after it; scene 9 or 8 before the close; scene 10 to end.

## Files, voice and the short cut (10 Oct 2026)

| File | Length | What |
|---|---|---|
| `assets/video/motion/undersigned-motion.mp4` | 1:52 | All 10 scenes, silent, 1920×1080, 30 fps |
| `assets/video/motion/undersigned-motion-vo.mp4` | 1:52 | The same with the voice-over (AAC, 48 kHz, about −16 LUFS) |
| `assets/video/motion/undersigned-motion-60s.mp4` | 1:05 | Short cut with voice: scenes 1, 3, 5, 6, 9, 10 in that order |

- **Voice:** macOS `say`, voice **Samantha** (en_US). No Premium or Enhanced English voice is installed on this Mac;
  installing one (System Settings → Accessibility → Spoken Content → System voice → Manage Voices, e.g. Ava or Zoe
  Premium) and re-running the steps below would sound more natural.
- **Script:** `bash tools/build_voice.sh` rebuilds both voiced files from `undersigned-motion.mp4`, reading the lines
  from the table above and the scene times from `motion.html`; re-run it after any edit (render first).
- **Timing:** each line starts at its scene's start + 0.6 s and must end 0.15 s before the scene does. The script reads
  a long line faster (`say -r` 180 → at most 200), then starts it earlier (down to +0.2 s), and prints each adjustment.
- **Scene 9 does not fit at a natural rate.** Its 49 words need about 14.7 s at 200 words a minute; the scene has 14 s.
  It is read at `say -r 200` (about 216 words a minute measured) and starts at +0.2 s, ending 0.19 s before 1:44.
  To relax it: shorten the line by about 6 words, or lengthen scene 9 in `motion.html` by 1–2 s.
- **Spoken spelling:** "dApp" is fed to `say` as "dee-app"; the words are the table's, unchanged.
- **Build:** one AIFF per line → ffmpeg `adelay` per line → `amix` (normalize=0) → two-pass `loudnorm` (I=−16,
  TP=−1.5, linear) → muxed with the video, `-c:v copy -c:a aac`.
- **Short cut (65 s, not 60):** the six scenes add up to 9 + 9 + 14 + 11 + 14 + 8 = 65 s; each is cut on its whole-second
  boundary from the full render and concatenated (each already fades in and out), with the same six voice lines placed
  at each scene's new start + 0.6 s. To reach 60 s, drop scene 6 (54 s) or shorten scenes 5 and 9 in `motion.html`.

## Data sheet (every figure on screen)

| Figure | Value | Source |
|---|---|---|
| dApps in the store | 1,543 | store public catalogue, 9 Oct 2026 |
| Star ratings | 478,289 (1,475 rated dApps) | Undersigned's snapshot of the store feed, 9 Oct 2026 |
| Campaign burst | 69 five-star reviews, 17–24 Mar 2026, during an in-game-gold-for-5★ offer | store reviews of one dApp (not named), read from the store feed; Undersigned burst detector |
| Sybil cost | US$400+ per Seeker; 100 × $400 = $40,000 | owner's figure for the device price |
| Running cost | US$843 a year | `docs/FINANCIAL_CASE.md` §4 |
| Break-even | 127 Pro licences a year (0.06% of 200,000) | same |
| On screen: 10% case | US$194,031 a year (10% of 200,000 on Pro; 20% of 1,543 dApps on the monthly publisher plan; 10 data plans a month; API passes, promotion, agent calls) | `docs/FINANCIAL_CASE.md` §5, "Owner target" (assumption, labelled "a target, not yet a record") |
| Base case (not on screen) | US$64,285 a year at 3% | `docs/FINANCIAL_CASE.md` §4–5, "Base" |
| Pro price | 0.0607 SOL or 369 SKR a year | `AppConfig.kt` |
| On screen: plan prices | publisher-plan month 0.0607 SOL; data-plan month 0.607 SOL | `docs/FINANCIAL_CASE.md` §4 |
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
9. "14 seconds: three cards count up: $843 a year to run; 127 Pro licences to break even; $194,031 a year at 10% (one
   Seeker in ten on Pro, plus publisher and data plans); footnote with sources and 'a target, not yet a record'."
10. "8 seconds: the app icon, 'Undersigned', the line 'One Seeker, one voice. Written to Solana. Readable by anyone.'"
