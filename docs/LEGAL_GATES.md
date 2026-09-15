# Undersigned — legal gates (same four tests as RentBack Sol `docs/HACKATHON_SCAFFOLD.md`, plus UGC)

Posture: Claude legal review, as for every fleet app. Home jurisdiction Victoria, Australia; global
distribution through the Solana dApp Store; fleet geo-block and sanctions screen inherited.

## Gate results

| Gate | Test | Result |
|---|---|---|
| **Custody / wallet** | Does the app hold keys or funds, or move value without a per-transaction Seed Vault signature? | **Pass.** It builds one unsigned memo transaction per review; the only value that moves is the reviewer's own network fee. No send, no receive, no balance UI beyond the reviewer's own staked-SKR figure. |
| **Money transmission** (FinCEN 2019 CVC guidance, AUSTRAC 50A/50B, MiCA Title V, UK arranging) | Does it accept value for another person, or arrange an exchange? | **Pass.** No value is accepted from anyone. No exchange, no swap, no payment rail exists in the app. (If a Pro licence is added later it is a fee to the developer for software, as in every fleet app: consideration, not transmission.) |
| **Securities / Howey**, Corporations Act s 763A, MiCA issuance, UK financial promotion | Investment of money, common enterprise, expectation of profit from others' efforts? Any promotion of a cryptoasset? | **Pass.** No token is issued, sold or distributed. No reward, points, airdrop or prize for reviewing. Staked SKR is *read* to weight opinions; the app never invites anyone to buy or stake it. Copy rule: "reviews are weighted by staked SKR as read on-chain" — never "stake more to count more". |
| **Consumer / misleading conduct** (ACL s 18, FTC Act s 5) | Could a user be misled about what the app is or what a score means? | **Pass with copy rules.** Three mandatory lines (README). Scores are labelled "signed opinions of Seeker owners"; the facts panel is "what your device can verify"; the app never says an app is safe or a scam. |
| **Trademarks** | "Seeker", "Solana", "dApp Store", "Genesis Token" | Descriptive, attributed use only; none in the app name; no store iconography imitated. |

## The one new area: user-generated content on a permanent public ledger

This is the only exposure RentBack Sol did not have. Handled by design rather than by policy:

1. **Structured over prose.** Rating + fixed tags are the primary signal. Tags are neutral product
   descriptors ("crashes", "paywall", "suspected-scam" is the only accusatory one and it is phrased as
   suspicion, aggregated, never attributed to the publisher as fact by the app).
2. **Free text is optional, 140 chars, and double-consented.** Second consent screen states, verbatim:
   "This text will be written to the Solana blockchain. It is public and permanent. It cannot be
   deleted by you, by us, or by anyone. You can withdraw it from this app's ranking, but the text
   itself stays on-chain forever." Defamation and IP warnings sit on the same screen.
3. **Right to erasure (GDPR Art 17) vs immutability.** Standard chain treatment: the user publishes to
   a public ledger by their own act; the app is a reader. Erasure is honoured *within the app* by a
   revocation record (the app stops displaying and counting the review) and by a local hide list.
   The privacy policy states this plainly and names the wallet address as pseudonymous personal data
   the user chooses to publish. The developer stores nothing server-side, so there is no controller
   database to erase.
4. **Notice-and-action.** A report button on every review (writes a local hide + emails the fleet
   abuse address). The app maintains a signed, on-chain **moderation list** (a revocation-like record
   from the developer's own moderation wallet, type `mod`, reserved for v2) so hidden reviews are
   hidden for every reader without a server. EU DSA hosting-provider duties do not attach: the app
   hosts nothing; the design still meets the notice-and-action spirit.
5. **Publisher right of reply** (v2): publishers sign a reply memo from the wallet that receives the
   app's payments (the fleet payment-reference pattern proves ownership). Shown alongside, not counted.
6. **Minors:** 18+ representation at sign-in, as the fleet standard.

## Feature kill list (do not build, do not pitch)

| Feature | Why |
|---|---|
| Paying reviewers in SKR/SOL, points, badges convertible to anything | Expectation of profit; also turns reviews into paid endorsements (FTC / ACL) |
| "Stake SKR to boost your voice" copy or CTA | Inducement to acquire a cryptoasset — UK financial promotion, ASIC advice line |
| Any install / update / uninstall action | Competes with the store's install flow and adds platform-terms risk for no judging gain |
| Replacing or scraping the store's own reviews | Not needed, and would blur "whose opinion is this" |
| Verdict words: safe, unsafe, scam, malware, verified | Facts and deltas only; a verdict is an opinion the developer would own |
| Server-side storage of reviews or addresses | Creates a data controller and a moderation liability the chain design avoids |
| Free text longer than 140 chars, or text without the second consent | Permanence + defamation exposure scale with prose |

## Documents to produce at submission time (legal last, per fleet rule)

Fleet 8-page pack cloned from the current best source, with: description of service (reads store
catalogue; publishes user-signed memos; on-device facts), permissions (QUERY_ALL_PACKAGES,
POST_NOTIFICATIONS opt-in, INTERNET), privacy table (wallet address pseudonymous, chain permanence,
no server), UGC terms (own your words, defamation, IP, immutability, revocation ≠ deletion),
not-affiliated notice, sanctions page, 18+.

## Pro (added 15 Sep 2026)

Pro is a 12-month software licence sold the fleet way: a transfer to the shared treasury with the registry PDA as the payment reference and the memo `UNS` appended last, carrying the `SRL1|pro|12` licence record in the same transaction. It is consideration for software, not transmission (no value is accepted for anyone else), not a security (no expectation of profit, no token, no yield), and it changes nothing about counting, weighting or hiding — the gates above still pass. Consumer law: price in SOL and SKR shown before signing, no auto-renewal, term chains from the previous expiry, the withdrawal right is waived by express consent on the payment screen (digital content supplied immediately), conformity remedies and a proportionate refund of the current term are stated in terms.html §8A. Region and sanctions gates run before the wallet opens; the cluster simulation runs before the signature sheet; nothing is reported as paid until confirmed.
