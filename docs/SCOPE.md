# Scope, honestly: what is deep, what is thinner, and how to tell

The surface is wide. This page says which parts carry the product and are tested to the byte, and which are thinner
conveniences, so a reviewer can spend their time where the claims are.

## Deep (the product; tested, live on mainnet, recomputable)

| Part | Depth | Evidence |
|---|---|---|
| The record format and the readers | one codec shared by the app, the server, the viewer and `api verify`; every record type round-trips in tests; malformed input is rejected, never partially read | `protocol/ReviewMemo.kt`, `ProtocolTest`, `TxGoldenTest` |
| Signing on the Seeker | MWA → Seeker Wallet → Seed Vault for every write; batch signing of up to 8; the app only ever receives the signature | `app/.../solana/ReviewTxService.kt`, the demo video 0:46–1:02 |
| Counting | one Seeker, one voice: the Genesis Token read on-chain; weighting, shrinkage, promotion, recusals and threads all in the protocol module with tests | `Ranking.kt`, `Weighting.kt`, `Threads.kt`, `WeightingTest`, `ThreadsTest` |
| The loop | review → publisher fix → notified reviewer → follow-up, and request → decision → delivery; both run on mainnet this week | `docs/ONCHAIN.md`, deck slide 4 |
| Publisher proof | a claim is proven against the store's own signed build or the listed website; 28 of our apps proven this way | `PublisherProof.kt`, `Apk.kt`, `ApkTest` |
| The API and MCP | 40 routes and 25 tools over the same views, 144 live checks, OpenAPI, webhooks with HMAC and private-network guards | `Server.kt`, `Mcp.kt`, `McpTest`, `ApiTest` |
| Payments as licences | Pro, plans, listings and passes read from the chain by every reader; edge cases listed | `docs/PAYMENTS.md`, `Billing.kt`, `PlansTest` |
| Moderation | a shared abusive-language list with disguise handling, auto-hide with the reason, moderator records on-chain with recusal | `Abuse.kt`, `Moderation.kt`, their tests, `docs/MODERATION.md` |

## Thinner (useful, but not where the claims rest)

| Part | What it is today | What it is not |
|---|---|---|
| AI reading aids (translation, summaries, the docs helper) | labelled suggestions, allowance-capped, Ollama first then Claude | never a score, a verdict or a moderation input |
| Review analytics | aggregates of the store crawl (155k reviews so far; the crawl continues) and of the ledger | the store crawl is ongoing, so store totals move daily |
| Payments analytics | gross receipts from the ledger; confirmed treasuries kept apart from wallets found in code | not revenue, not profit; found wallets are unconfirmed |
| Leaderboards | eleven lenses over the same data | counted-review lenses are empty until Seeker owners outside the team sign |
| Localisation | 28 locales from the first release; the newest screen (review analytics) is English in the other 27 for now | — |
| The browser viewer | reads the registry with no server | a reader, not a writer |
| Quick reviews | relayed without a wallet; shown apart, counted nowhere | not a sybil-resistant signal, by design |

## How to check depth yourself in ten minutes

1. `./gradlew :protocol:test :api:test :app:testDebugUnitTest` (305 tests, `docs/VERIFY.md`).
2. `api verify` against mainnet, or read `/status`.
3. Open any record id from `docs/ONCHAIN.md` on Solscan and in `/v1/records/{id}`: the same bytes.
4. Sign a review on a Seeker and watch it land in the app, on the website, in the API and on Solscan (the uncut walkthrough video does this with no cuts).
