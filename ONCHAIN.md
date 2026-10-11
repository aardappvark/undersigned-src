# The on-chain data model, and how to verify it from chain state

Every Undersigned record is a Solana mainnet transaction. There is no program of our own: a record is one or more
[SPL Memo](https://spl.solana.com/memo) instructions whose text starts with `SRL1|`, in a transaction that also
references one fixed address, the **registry**. The signer of the transaction is the identity; nothing in the memo
text is trusted for identity. Anyone can rebuild every score from the chain alone, and the server does so every six
hours (`api verify`, result on [/status](https://undersigned.provenby.dev/status)).

## Addresses

| What | Address | Role |
|---|---|---|
| Registry (reference account) | `BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs` | `findProgramAddress(["review-ledger","v1"], MEMO_PROGRAM)`, bump 255: an **off-curve** address, so no key exists and nobody can capture the ledger. Every record transaction includes it as a read-only account, which is what lets a reader enumerate the whole ledger with `getSignaturesForAddress`. `protocol/.../Registry.kt`; the app re-derives it at start and refuses to post on mismatch. |
| Memo program | `MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr` | carries the record text |
| Lighthouse | `L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95` | the Seeker Wallet adds an assertion to every transaction it signs; readers show it as the "Seeker wallet" mark (a fingerprint, not a proof) |
| Genesis Token | read as a soulbound Token-2022 mint on the signer, through the `seeker-verify` library in the app's ledger module (the governed credential list is `app/src/main/assets/attestations.json`, with a remote slot) | the one-device-one-voice gate: a review counts only when the signing wallet holds one |
| Treasury | `DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q` | receives Pro, publisher-plan, listing and API-pass payments in SOL or SKR; the payment itself is the licence |
| Relayer | `5fnAzQBqUK3cF82G8tbmhRMPSECQwgKk2XjYRNseYyN` | pays the fee of quick reviews (no wallet); a transaction it pays is authored by its second signer and counts nowhere |
| SKR mint | `SKRbvo6Gf7GondiT3BbTfuRDPqLWei4j2Qy2NPGZhW3` | the SKR rail for every price |

## Record types, with one real transaction each

The wire formats, limits and the 24-tag vocabulary are specified in [SPEC.md §2](SPEC.md) and implemented once, in
`protocol/.../ReviewMemo.kt` (parsed by the app, the server, the viewer and `api verify`; anything that does not parse
exactly is ignored). One record per memo instruction; the n-th memo of a transaction is addressed `<signature>:<n>`.

| Record | Wire | Who may sign for it to count | Mainnet example (12 Oct 2026) |
|---|---|---|---|
| Review | `SRL1\|rev\|<pkg>\|<1-5>\|<tags>\|<versionCode>\|<text b64>` | anyone; **counted** only when the signer holds a Genesis Token, is not the operator, not the app's proven publisher, and the review is live (latest per wallet per app, not withdrawn) | [`crbe2vGz…`](https://solscan.io/tx/crbe2vGzQfNPoSdUteC7) (the record id is the full signature; see `/v1/records?type=rev`) |
| Install age · usage time · activity | `SRL1\|use\|…`, `SRL1\|tim\|…`, `SRL1\|act\|…` | only beside a review, same transaction, same signer, same app | `63brHXS5…`, `Pzgu7ECV…`, `3nRym2bX…` |
| Own tags (Pro) | `SRL1\|ctg\|<pkg>\|<tag,tag>` | same transaction as the review; shown only where the signer held Pro when signing | `B3bYeSMY…` |
| Reply | `SRL1\|rep\|<review>\|<text b64>` | anyone; the Publisher mark only when the signer holds a proven claim on the app | `gf5yf4Er…` |
| Fixed | `SRL1\|fix\|<review>\|<versionCode>\|<note b64>` | a proven publisher of the reviewed app, signed after the review; the latest counts | `5yEMEw1z…` |
| Follow-up | `SRL1\|fup\|<original review>` | beside a new review by the same signer of the same app, after the original | `63brHXS5…` (4★ → 5★ after the fix) |
| Feature-request decision | `SRL1\|ack\|<review>\|<y\|n>\|<note b64>` | a proven publisher, after a review tagged `feature`; the latest counts | `RwvPFz1u…` (will build, 45 min after the request) |
| Request category | `SRL1\|req\|<pkg>\|<category>` | beside a `feature` review, same transaction | (first one lands with the next request) |
| Publisher claim | `SRL1\|clm\|<pkg>` | anyone can sign; **proven** only when the app's own signed store build names the wallet in its manifest, or the website the store lists serves `/.well-known/undersigned.txt` naming it (`protocol/.../PublisherProof.kt`) | `4J6hFBZ3…` (29 claim records over our 28 store apps) |
| Treasury listing | `SRL1\|trs\|<pkg>\|<code>` | signed by the treasury wallet itself (or `trd` by the proven publisher) | 28 listings, `/v1/payments/treasuries` |
| Pro licence | no record: the **payment** is the licence | a transfer to the treasury of at least the floor price, carrying the registry as reference and the memo `UNS`; the term comes from the amount | `2L6fwENd…` (276 SKR, 15 Sep 2026) |
| Quick review | `SRL1\|qck` beside a review | fee paid by the relayer; anonymous; counted in no score | `5K2oY8Xq…` |
| Flag · clear · dispute · vote | `flg`, `clr`, `dsp`, `vot`/`unv` | weighted by the signer's Genesis Token; hiding needs net Genesis-weighted agreement | `4axpJLz8…` |
| Withdrawal | `SRL1\|rvk\|<record id>` | only the wallet that signed the named record | (none yet) |
| Moderation | `mod`/`umd` on a record's text; `ban`/`unb` on a wallet | only wallets on `Moderation.MODERATORS`; never on the moderator's own apps | (none yet) |
| Long text | `lng` head + `txt` pieces | same signer, same approval; readers join only when every piece is there and the hash matches | in the 2,000-character reviews |

Full ids for every example: `GET https://undersigned.provenby.dev/v1/records?type=<wire>&limit=5`, each with `transaction`,
`signer`, `time`, `fee_lamports` and `seeker_wallet_shape`.

## How a reader verifies, from chain state alone

1. **Enumerate**: `getSignaturesForAddress(registry)`, then `getTransaction` for each (the app does this on the phone;
   the server's `Indexer.kt` does it with a cursor; `api verify` does it into a throwaway database).
2. **Parse**: every memo instruction in the transaction through `ReviewMemo.parse`; the record's signer is the
   transaction's fee payer (or the second signer when the relayer paid). Non-parsing memos are dropped.
3. **Facts**: for each signer read the Genesis Token (soulbound Token-2022 mint), staked SKR and SOL (shown, never
   weighed), and for each claimed app the publisher proof (store build manifest or `.well-known`).
4. **Score**: `Ranking.score` and `Weighting` in the protocol module: one live review per wallet per app, device
   weight 1 with a Genesis Token and 0 without, quality from text length and phone facts, Bayesian shrink toward 3.0;
   promoted reviews once at the same weight after the organic ones; the operator's, quick and publisher-own reviews
   counted nowhere. Threads (`Threads.kt`) attach replies, fixes, decisions and follow-ups to their reviews; only a
   proven publisher's fix or decision, signed after the review, counts.
5. **Compare**: `api verify` prints every app's counted reviews, mean and record ids against `/v1/dataset.json`;
   any difference fails the run. The browser viewer (`https://aardappvark.github.io/undersigned-viewer/`) does
   steps 1–4 in the page with no server at all.

Golden tests pin the bytes: `protocol/src/test/.../ProtocolTest.kt` (codec round trips and limits) and `api/src/test/.../TxGoldenTest.kt`
assert that the app and the server build identical transactions for the same record.
