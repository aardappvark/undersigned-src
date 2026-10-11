# Undersigned — the financial case

Prepared 10 Oct 2026. Undersigned is independent and ours: a listed dApp beside the Solana dApp Store, paid for by
optional software licences. We are glad to give back to the Seeker community as it grows, never by paying anyone for
what they sign. Every figure below is either **measured** (source and date given) or an **assumption** (labelled).
Reproduce: `python3 tools/revenue_model.py case` and `python3 tools/payments_record.py`.

## 1. The case in one table

| | Figure | Basis |
|---|---|---|
| Cost to run | about **US$843 a year** | measured and list prices (§4) |
| Break-even | **127 Pro licences a year** (0.06% of devices shipped), or **11 apps** on the publisher plan | §4 |
| Base case, year 1 | about **US$64,000** | 3% of devices on Pro, 10% of dApps on the publisher plan (assumptions) |
| Owner target, year 1 | **US$194,000** at 10% Pro, **US$327,000** at 20% | owner's target, not a forecast |
| What the store's apps have actually been paid | about **US$2.64M** in 12 months into 48 wallets found in app code; 97% to five wallets | on-chain record, §3 |
| Margin after running costs | 89% in the conservative case; 98% or more in the others | the reviewer pays their own network fee |

The margin is structural: a signed review costs Undersigned nothing (the reviewer's wallet pays its own network fee, under 0.0001 SOL),
the ledger is Solana, and the server is a US$7-a-month machine. What is uncertain is uptake, not cost.

## 2. The market (measured)

| | Figure | Source |
|---|---|---|
| Seekers shipped | 200,000+ | Solana Mobile, March 2026 (company figure, not independently confirmed) |
| Seeker Genesis Tokens claimed | 121,161 (10 Oct 2026); +246 in 22 days | on-chain group `GT22s89n…`, `tokenGroup.size` |
| dApps in the store | 1,543 | the store's public catalogue, 9 Oct 2026 |
| Star ratings in the store | 478,289 across 1,475 rated dApps | Undersigned's snapshot of the store feed, 9 Oct 2026 |
| New store ratings | about 206 a day (75,000 a year) | same ~1,450 apps, 28 Sep → 7 Oct snapshots |
| SOL | US$109.57; trailing-year range US$62.18–234.78 | server price history, 376 days |
| SKR | US$0.0164; range US$0.0065–0.0425 since 20 Jan 2026 | same |

Two bases matter: **devices** for Pro (anyone with a Seeker can buy it) and **Genesis Tokens** for counted reviews.

## 3. Historical record: what the store's apps have been paid

Measured 2026-10-10 from the public API. The last 12 months are fully read; older history read about 60% (it continues in the background; the 12-month figures are complete). 517 store builds read, 980 skipped; 48 wallets.

| Month | Payments | Payers | Wallets paid | USD when paid |
|---|---:|---:|---:|---:|
| 2025-10 | 3,302 | 1,059 | 13 | $336,382 |
| 2025-11 | 3,267 | 1,176 | 13 | $211,222 |
| 2025-12 | 3,862 | 1,416 | 16 | $376,206 |
| 2026-01 | 5,185 | 1,942 | 18 | $181,500 |
| 2026-02 | 5,376 | 2,251 | 26 | $174,887 |
| 2026-03 | 8,184 | 2,838 | 30 | $204,210 |
| 2026-04 | 3,607 | 1,279 | 28 | $334,571 |
| 2026-05 | 2,756 | 924 | 33 | $210,320 |
| 2026-06 | 30,377 | 2,922 | 32 | $278,335 |
| 2026-07 | 15,608 | 1,797 | 33 | $93,497 |
| 2026-08 | 7,949 | 1,997 | 30 | $135,293 |
| 2026-09 | 5,044 | 1,000 | 34 | $77,840 |
| 2026-10 | 1,188 | 362 | 22 | $26,977 |
| **12 months + this month** | **95,705** | | | **$2,641,241** |

Gross receipts, not revenue: a game's or a market's wallet can take deposits it later pays back out; outflows are not netted here.

Concentration: the top 5 wallets took 97% of it. Wallets paid at all in the period: 47. Median single payment across wallets: $0.13. Wallets whose full history is read: 33 of 48.

| Rank | Found in the code of | USD, period | Payments | Payers (all time) | Repeat payers |
|---|---|---:|---:|---:|---:|
| #1 | Photo Finish Live | $2,236,079 | 21,711 | 3,279 | 1,606 |
| #2 | GOLD RUSH | $141,420 | 49,904 | 2,582 | 2,067 |
| #3 | SALAMANDER | $104,481 | 1,230 | 351 | 148 |
| #4 | Jimbo’s Adventure | $47,448 | 639 | 176 | 92 |
| #5 | TokenNoFlash | $23,889 | 141 | 52 | 15 |
| #6 | HOOP COLOR SORT | $14,354 | 6,660 | 4,547 | 1,152 |
| #7 | Klout | $12,949 | 810 | 222 | 131 |
| #8 | Jimbo’s Adventure | $8,619 | 493 | 146 | 69 |
| #9 | Degen Dash | $7,016 | 1,736 | 391 | 187 |
| #10 | Arka Seeker | $6,816 | 837 | 322 | 103 |

**What it says.** Over the thirteen calendar months Oct 2025 to Oct 2026 (the twelve full months: about US$2.61M),
wallets written into dApp Store apps took about **US$2.64 million** in 95,700 payments: real money moves through the store's apps. It is extremely concentrated (five wallets took 97%; one
racing game took US$2.24 million), it is mostly games, and most single payments are cents (the median wallet's median payment is US$0.13; the median payment
across all of them US$0.53: in-game actions, not licences). So the ecosystem can pay; the question for Undersigned is uptake of a US$6.65 tool, not whether
Seeker owners spend on-chain.

How to read it: payments of SOL, SKR or USDC **into** wallets found in apps' store builds, valued on the day paid. A floor
and a proxy: wallets are found in code, not confirmed; receipts are gross (a game takes deposits it pays back out); and
it does not see how the biggest Solana apps make money.

**Trading apps are missing by construction.** Wallets and aggregators earn swap fees that never reach an identifiable app
treasury. For scale only (global figures, **not** attributable to Seeker or the dApp Store), DefiLlama's daily revenue
summed over the last 365 days to 9 Oct 2026: Phantom about US$92 million; Jupiter about US$83 million, of which its
aggregator about US$34 million. The money in this ecosystem flows through fees; the record in this section measures
the smaller, visible part: direct payments to apps.

## 4. Unit economics

| Product | Price | Who pays |
|---|---|---|
| Pro (12 months; shareable with two wallets) | 0.0607 SOL, 369 SKR, 332 SKR with a Genesis Token | owners and publishers |
| **Publisher plan** (per app, 30 days; up to 12 months a payment): the app's report, webhooks, the API | 0.0607 SOL / 369 SKR / 332 SKR with a Genesis Token | the app's proven publisher |
| **Data plan** (30 days): 2 million requests, CSV exports of the store's history | 0.607 SOL / 3,690 SKR / 3,320 SKR | analysts, wallets, funds, Guardians |
| Publisher treasury listing (payments board) | 0.0607 SOL / 369 SKR, once | publishers |
| Promote one's own review (Pro; labelled, listed after organic, never weight) | 0.00607 SOL / 37 SKR | reviewers |
| API pass (30 days, 100,000 requests) | 0.0607 SOL / 369 SKR | developers |
| API or MCP call on credits | 0.00001 SOL / 0.06 SKR | agents |

| Cost | A year | Basis |
|---|---:|---|
| Server (Hetzner CX23) | US$85 | the order, 9 Oct 2026 |
| Keyed RPC at scale | US$588 | Helius Developer list price; free tier today |
| Quick-review fees at the daily cap | US$120 | 300 a day × 10,000 lamports, measured per transaction |
| AI answers | about US$50 | US$0.00003 an answer measured; daily allowances cap it |

| Case | Pro buyers | Pro | Publisher plans | Data plans | API passes | Promotion | Agent calls | **Year 1** | at SOL $62 | at SOL $235 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Conservative | 500 | $3,325 | $2,463 | $798 | $239 | $250 | $548 | **$7,624** | $4,326 | $16,335 |
| Base | 6,000 | $39,905 | $12,315 | $3,991 | $1,596 | $999 | $5,478 | **$64,285** | $36,481 | $137,745 |
| Owner target | 20,000 | $133,018 | $24,630 | $7,981 | $3,991 | $2,498 | $21,914 | **$194,031** | $110,111 | $415,758 |
| Owner stretch | 40,000 | $266,036 | $24,630 | $7,981 | $3,991 | $2,498 | $21,914 | **$327,049** | $185,597 | $700,781 |

Unit: one Pro licence or one publisher-plan month 0.0607 SOL = $6.65 (or 369 SKR = $6.04; 332 SKR with a Genesis Token = $5.44); a data-plan month 0.607 SOL = $66.51.
Running cost $843 a year -> break-even at 127 Pro licences a year (0.06% of devices shipped, 0.10% of Genesis holders), or 11 apps on the publisher plan.
Marginal cost per signed review: $0 (the reviewer's wallet pays its own network fee); per quick review $0.0011 (relayer fee); per AI answer about $0.00003 (measured).

Nothing is sold that changes a score. The free tier covers reading, reviewing, replying, voting and flagging.

### What the two business plans contain (live 10 Oct 2026)

- **Publisher plan** (`/plans`, `GET /v1/publisher/{package}/report`): signed reviews waiting for the publisher's answer;
  the store rating's trend from daily snapshots; review and rating bursts; certificate, version and permission changes;
  scores by version; fixes and follow-ups; where the app sits in its category (median rating, percentile); payments
  received; webhooks for the app. Redeemed only by a wallet proven as the app's publisher.
- **Data plan** (`GET /v1/export/{ratings|changes|apps|found}`): the store's star counts per app per day, every
  version, certificate and permission change seen, every dApp with its scores, the wallets found in app code.

Why these: paid tiers sold to owners reach tens of payers per app in this ecosystem so far (§3). Publishers and data
buyers are the parties with budgets, and what they buy is work around the reviews, never the reviews.

## 5. Scenarios

Assumptions per case (shares of 200,000 devices; of 1,543 dApps; of a year's 75,000 new reviews):

| Case | Pro | dApps on the publisher plan | Data plans a month | Reviews promoted | API passes a month | Agent calls a year |
|---|---:|---:|---:|---:|---:|---:|
| Conservative | 0.25% | 2% | 1 | 0.5% | 3 | 0.5 million |
| Base | 3% | 10% | 5 | 2% | 20 | 5 million |
| Owner target | 10% | 20% | 10 | 5% | 50 | 20 million |
| Owner stretch | 20% | 20% | 10 | 5% | 50 | 20 million |

Results are in the table in §4. Sensitivity: revenue is priced in SOL, so the trailing-year SOL range alone moves any
case between 0.57× and 2.1× of the figure at today's price. Every case clears the running cost; the conservative case
by about nine times.

## 6. Track record so far

- **Undersigned:** no paid sales yet from anyone outside the team. Its one Pro licence (276 SKR, 15 Sep 2026) was paid by
  the operator to test the flow end to end.
- **The AardAppvark fleet (28 published apps plus Undersigned, 29 proven claims, same payment rails):** 94 payments into its proven treasuries, Aug–Oct 2026, about
  US$205 at the day of payment (server payment history). Small, and real: the rails work and are read back from chain.

## 7. Giving back (options, not commitments)

Kept as options until the owner decides; none pays anyone for a review, a vote or a flag.
- A share of surplus, once running costs are met, to Seeker builders or community projects, paid from a dedicated,
  public treasury so anyone can audit it.
- Free Pro for publishers of free, open or community apps.
- The open dataset, the ranking rules and the publisher proof format stay free to read and adopt.

## 8. Risks

- **Uptake:** paid tiers in this ecosystem have so far reached tens to hundreds of payers per app (§3). The owner's
  10–20% targets need far more than any app has shown.
- **Price:** revenue is in SOL and SKR; a year's SOL range is almost 4×.
- **Platform:** the store's own review flow could adopt the same ideas; Undersigned is built so that helps rather than
  hurts (it reads and writes the same records).
- **Counted reviews are 0 today:** the integrity rules count none of the operator's own reviews, so the signed score
  starts empty and fills only as Seeker owners sign.

## Sources and method

- On-chain: Genesis group size via `getAccountInfo` (jsonParsed), mainnet, 10 Oct 2026.
- Store: the dApp Store's public GraphQL feed, snapshots kept daily by the Undersigned server.
- Payments: `/v1/payments/found/trends`, `/v1/payments/found`, `/v1/payments/found/{package}` (public API).
- Prices: CoinGecko daily closes held by the server; Undersigned prices from `api/.../Config.kt` and `Indexer.kt`.
- Devices shipped: [Solana Mobile on X, March 2026](https://x.com/solanamobile/status/2034031909932855668).
- Trading-app revenue: DefiLlama `/summary/fees/{protocol}?dataType=dailyRevenue`, last 365 days, 9 Oct 2026
  ([Phantom](https://defillama.com/protocol/phantom-wallet), [Jupiter](https://defillama.com/protocol/jupiter)).
