# Payments, licences and their edge cases

Every paid thing in Undersigned is a transfer on Solana mainnet to the treasury
`DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q`, in SOL or SKR (`SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3`), carrying the
registry as a reference account. Nothing is granted by a server flag: the app, the website and the API each read the
chain and decide for themselves. Prices: `docs/FINANCIAL_CASE.md`; wire details: `docs/SPEC.md §2`; code:
`protocol/.../Payments.kt`, `Weighting.proTerms`, `app/.../solana/ProLicence.kt`, `api/.../Billing.kt`.

## Pro (the reviewer's licence)

| Case | What happens | Where |
|---|---|---|
| Normal purchase | a `pro` record (`SRL1\|pro\|<months>`) signed by the buyer in the same transaction as the transfer; the licence is active from the block time for `months × 365 / 12` days | `ProLicence.status`, `Weighting.proTerms` |
| Underpayment | a transfer below the floor price is not a licence: the record is ignored by every reader; the SOL or SKR stays with the treasury and is refunded on request (see refunds) | `ProLicence.kt:45-46` (`floorLamports`, `floorSkrRaw`) |
| Overpayment | counts as one licence of the stated term; nothing extra is granted; refunded on request | same |
| Renewal inside a term | the new term starts when the current one ends, never overlapping, so paying early loses nothing | `Weighting.proTerms` (`start = max(expiry, blockTime)`) |
| Expiry | Pro tools, the mark and the Pro members' score line stop at expiry; signed reviews stay counted exactly as before, because Pro never changed a weight | `Weighting.Parts.pro` is read at the review's own time |
| Genesis price (332 SKR) | accepted only if the paying wallet held a Genesis Token when the transaction landed; otherwise the standard floor applies and an underpayment follows the rule above | `Billing.kt` SGT check on-chain |
| Shared seats (two wallets) | the buyer signs a `shr` record naming the seat holder; readers grant Pro only while the granter's own paid term is live; a seat from an app's publisher to a reviewer of that app makes the review *gifted*: shown, counted nowhere | `ProSharing.kt`, `Weighting.Parts.gifted` |
| Promotion (`bst`) | a promoted review counts once at the same weight as an unpaid one, after the organic ones, only if the author paid for it, holds Pro, is not a publisher and the app is not ours | `Weighting.promoted` |
| Price drift | prices are fixed in the app's config at release; the chain is read at the transaction's own time, so an old purchase is not re-judged by a new price | `AppConfig.Payment`, `Weighting.proTerms` |
| A wrong rail or token | only SOL to the treasury or SKR to its token account count; any other token is ignored and refundable on request | `Rpc.kt` balance reads |

## Publisher plans, treasury listings and API passes

- A publisher plan (per app, 30 days a payment, up to 12 months) is read like Pro, from the chain, for the app's proven publisher only (`Billing.kt`).
- A treasury listing is a one-time payment plus a signed `trs`/`trd` record; the listing shows on the payments board only while the publisher's claim stays proven.
- An API pass (30 days, 100,000 requests) is bought at `/plans`: the server watches the treasury for the transfer, then issues a key whose hash it stores; the key is shown once. Replay of the same transaction is refused (`redeemed` primary key). Per-call payment answers a `402` with the amount and a Solana Pay link.

## Refunds

Pro, promotions, plans, listings and passes are digital services supplied at once; the payment screen asks for
immediate supply before the wallet signs (terms §"Refunds and your statutory rights"). Where a statutory right to
withdraw applies, or a payment did not result in what it was for (underpayment, wrong rail, a transfer to the
treasury without a record), we refund the amount to the paying wallet on request to the support address in the
listing; the refund is itself a visible transfer from the treasury. No reader ever revokes a licence for a refunded
term retroactively: readers only ever read the chain, so a refunded term simply expires.

## Abuse resistance, in one place

- **Buying weight is impossible.** No payment changes the signed score. Pro and promoted reviews have their own labelled
  lines; a review counts once, with the same device weight as everyone's (`Weighting.Parts.weight`).
- **Gifted seats recuse.** A reviewer holding Pro given by the reviewed app's publisher is counted nowhere on that app.
- **Publishers recuse.** A proven publisher's reviews of their own apps are shown and never counted; their flags on their
  own apps are ignored.
- **The operator recuses.** Reviews from Undersigned's own wallets are labelled and never counted anywhere; the
  operator's moderation never reaches apps the operator publishes (`Moderation`), and that includes bans.
- **Relayed quick reviews** are anonymous, counted in no score, limited per key, per network and per day, and refused for
  listed abusive text; the relayer pays about 0.003 SOL a day at most.
- **Every control is on the chain.** A reader who disagrees with any of this can recompute from the same records; `api
  verify` does so every six hours and publishes the result on `/status`.
