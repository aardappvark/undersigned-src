# What the review layer is worth to whoever runs it

Owner's question (10 Oct 2026): if Solana Mobile, or whoever stewards the dApp Store, ran this review layer at
Undersigned's prices, what could it have brought in over the last year, and what would next year look like?

**Short answer:** about US$37,000 over the last 12 months (mid case) had it run at today's scale, and about
US$83,000 over the next 12 months, against running costs under US$1,000 a year. The range runs from US$12,000 to
US$124,000. The money is modest beside the store; the point is that **the layer pays for itself many times over**,
so stewardship costs Solana Mobile nothing, and what is left can go back to the Seeker community.

**What is measured and what is assumed.** Device count, dApp count, store ratings, the rate of new ratings, prices and
costs are measured (sources below). Every adoption rate is an assumption: Undersigned's own conversion so far is one
Pro licence, so there is no observed rate yet. Freemium software commonly converts low single-digit percentages of
users [unverified: general industry figure]; the low case is 1%. Reproduce or change any number with
`python3 tools/revenue_model.py`.

**Last 12 months, had it run at today's scale**

| Stream | Low | Mid | High |
|---|---:|---:|---:|
| Owner Pro | 74 SOL ($8,058) | 221 SOL ($24,175) | 368 SOL ($40,291) |
| Publisher listing + Pro | 19 SOL ($2,052) | 47 SOL ($5,131) | 94 SOL ($10,262) |
| Promoted reviews | 5 SOL ($500) | 9 SOL ($999) | 23 SOL ($2,498) |
| API passes | 4 SOL ($399) | 15 SOL ($1,596) | 36 SOL ($3,991) |
| Agent calls (API/MCP) | 10 SOL ($1,096) | 50 SOL ($5,478) | 200 SOL ($21,914) |
| **Total** | **110 SOL ($12,105)** | **341 SOL ($37,380)** | **721 SOL ($78,956)** |

**Next 12 months: measured device growth, 20% more dApps and reviews, adoption one step on**

| Stream | Low | Mid | High |
|---|---:|---:|---:|
| Owner Pro | 228 SOL ($24,989) | 380 SOL ($41,649) | 570 SOL ($62,473) |
| Publisher listing + Pro | 56 SOL ($6,157) | 112 SOL ($12,315) | 169 SOL ($18,472) |
| Promoted reviews | 11 SOL ($1,199) | 27 SOL ($2,997) | 41 SOL ($4,496) |
| API passes | 15 SOL ($1,596) | 36 SOL ($3,991) | 55 SOL ($5,986) |
| Agent calls (API/MCP) | 50 SOL ($5,478) | 200 SOL ($21,914) | 300 SOL ($32,871) |
| **Total** | **360 SOL ($39,420)** | **756 SOL ($82,865)** | **1,134 SOL ($124,298)** |

Running costs a year: about $843 (hosting $85, keyed RPC $588, quick-review fees at the cap $120, AI $50).
Inputs: 121,161 Genesis Tokens; 1,543 dApps; 478,289 store ratings, 206 new a day (75,109 a year); SOL $109.57; next-year devices 125,242.

## Assumptions per case

| | Low | Mid | High |
|---|---:|---:|---:|
| Genesis holders buying Pro for a year (0.0607 SOL) | 1% | 3% | 5% |
| dApps whose publisher lists a treasury and takes Pro (0.0607 SOL each) | 10% | 25% | 50% |
| New reviews promoted by their own author (0.00607 SOL) | 1% | 2% | 5% |
| 30-day API passes active each month (0.0607 SOL) | 5 | 20 | 50 |
| Paid API/MCP calls a year (0.00001 SOL each) | 1M | 5M | 20M |

Next year moves each case one step on (low takes the mid rates, mid the high, high 1.5× the high), with the measured
device growth (+11 Genesis Tokens a day, 18 Sep → 10 Oct), 20% more dApps and 20% more reviews.

## Sources (measured 9–10 Oct 2026)

- **121,161 Seeker Genesis Tokens**: the Genesis group's on-chain size (`GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te`,
  Token-2022 `tokenGroup.size`), 10 Oct; 120,915 on 18 Sep.
- **1,543 dApps; 478,289 star ratings across 1,475 rated dApps**: the store's public feed, Undersigned's snapshot of 9 Oct.
- **About 206 new ratings a day**: the same ~1,450 apps, snapshots of 28 Sep and 7 Oct (450,210 → 452,062).
- **SOL US$109.57**: 9 Oct, from the price history the server keeps.
- **Prices**: `api/.../Config.kt` and `Indexer.kt`. Costs: Hetzner CX23 US$7.09 a month (the order, 9 Oct); a keyed RPC plan at
  list price; quick-review fees at the relayer's daily cap (300 × 10,000 lamports, measured per transaction on mainnet);
  Claude answers at a measured US$0.00003 each, under the daily allowances.

## What it leaves out

- **The bigger value is not cash**: one-device-one-voice reviews, publisher proof, review-burst and no-Genesis-wave
  signals for Guardian curation and Season integrity. Those protect the store's revenue rather than add to it.
- **Upside not counted**: the Solana Mobile Stack on other Android phones would grow the device base far beyond +11 a day.
- **Rule that does not bend with money**: no payment buys weight. Fees pay for running it; the surplus is intended for
  the Seeker community, never for reviewers or moderators for what they sign.
