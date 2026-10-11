# Undersigned beside the dApp Store (11 Oct 2026)

What the store's own review flow offers today, measured on dApp Store v1.59.0 on a Seeker (screens in
`assets/submission/shots/cmp/store_*.png`), against Undersigned 1.1.10 (`us_*.png` and the other shots in that folder).
Every Undersigned row names the screen that shows it. Nothing here is a verdict on the store; it is a companion, and the
store's rating is credited on every Undersigned page.

## For the reviewer (a Seeker owner)

| What you want | dApp Store today | Undersigned | Outcome | Shots |
|---|---|---|---|---|
| A voice that counts because you hold the phone | Stars and text from any wallet; nothing on the listing says whether a Seeker owner wrote it | One counted voice per Genesis Token; reviews from other wallets, our own wallets and quick reviews are shown, labelled, never counted | A score a reader can trust to be one phone, one voice | store_tk_ratings · us_tk_top |
| Say what was wrong, not only how many stars | 15-character minimum, free text, no structure | 1–3 tags from a fixed vocabulary (Crashes, Wallet fails, Perms excess…), a 2,000-character note, up to two tags of your own (Pro) | Problems are findable and comparable across reviews | store_tk_compose · us_compose_pro |
| Prove you actually used it | Nothing | Opt-in device facts in the review's own transaction: install age, usage time, months active, reinstalls; the signing wallet's Seeker Genesis check | A review carries evidence, never a claim | us_tk_reviews |
| Know who else is speaking | A name and a date | The reviewer's record beside every review: signed, apps, follow-ups, fixes confirmed, since when; tap it for their whole history with date and time, dApp, stars, tags, note | Weigh a voice yourself; counts only, never a score on a person | us_tk_reviews · reviewer_history |
| Read why an app got 3 stars | A histogram you cannot tap | Tap a star row: only those reviews, signed or from the store | Straight to the reason | us_tk_stars · star_filter |
| Be told when your problem is fixed, and say so | Nothing ties a fix to a review | The publisher signs "fixed in version N"; you get an alert and can follow up with new stars; both ratings and the change stay public | The second round exists | us_tk_publisher (Mark fixed, the reply) |
| See a burst for what it is | Invisible | Bursts shown as facts: 8 five-star store reviews in two days, 16× the usual pace, with how many wallets held a Genesis Token | Campaigns are visible, never hidden | jup_lens (deck slide 4) |
| Spot copied text | Nothing | "Similar text to a review of X signed D, within 3 days": a label that names its match, never an accusation | Copy-paste is visible, nobody is silenced | us_sf_similar |
| Keep your own record | The review lives in the store's database | Your review is a Solana transaction; readable in the app, on the web, through the API and MCP, on Solscan; recomputable by anyone (`api verify`) | Permanent, portable, checkable | web_solscan · web_app |
| Review without a wallet | Only with a store account | A quick review signed by a key on the phone, fee paid by the relayer; shown apart, never counted | Everyone can speak; only Seekers count | review_gate_emulator |

## For the developer (a publisher)

| What you want | dApp Store today | Undersigned | Outcome | Shots |
|---|---|---|---|---|
| Prove the app is yours | The store knows; readers do not | A signed claim proven against the website the store lists or the manifest of the store's own build; a Publisher mark on every reply | Readers see who is speaking for the app | us_tk_publisher |
| See what is wrong, fast | Read every review | Tags with counts, scores per version, bursts, copy-paste findings, a changes feed, a weekly digest | Triage in a minute | us_tk_stars · tk_publisher (deck) |
| Answer where it lasts | A reply in the store, deletable, tied to nothing | A signed reply on-chain beside the review, forever | Your answer survives | store_tk_reviews · us_tk_publisher |
| Close the loop on a fix | No way to say "fixed", no way to tell the reviewer | Mark fixed (version N) → reviewer alerted → follow-up stars → responsiveness shown as fixes, follow-ups, mean change, hours to fix | Fixing is rewarded by the record, not by a score | us_tk_publisher |
| Deal with spam without a verdict | Report and wait | Flags with Genesis-weighted consensus, disputes that double the bar, moderation that hides text and never a voice, labels for bursts and similar text | Fair, open, no censorship | deck slide 4 |
| Know what the store's apps are paid | Nothing public | Payments board: leaderboards, categories, rising and falling, confirmed treasuries kept apart from wallets found in app code | A market view nobody else publishes | chart_top · chart_store |
| Build on it | No API for reviews | REST API, OpenAPI, dataset download, webhooks (review, reply, fixed, follow-up), badge, hosted MCP with 25 read-only tools; no tool ever writes a review | Agents and sites read the same record | web_app · slide 10 |
| Post to the store from here | Not possible: no deep link to the store's review box, no write API (measured) | Copy to clipboard, open the listing, paste under Ratings & Reviews | Honest about the one thing the store does not allow | us_tk_top |

## What the store does better, said plainly

Reach: every Seeker has it. Reviews there feed the store's own ranking. Publisher replies render with the app icon. The
store's ratings are credited on every Undersigned page and in every chart; Undersigned adds a layer beside them and asks the
store to read the ledger, nothing more.

## Where these appear

- Deck: slides "Reviewer: store vs Undersigned" and "Developer: store vs Undersigned" (`assets/submission/pitch.html`),
  built from the rows above with the paired shots.
- Video: `assets/video/compare.html` renders the same rows as full-frame 1920×1080 pairs for the 1:45–2:05 segment in
  `docs/VIDEO_AND_DECK_PLAN_2026_10_11.md`.
