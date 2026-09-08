# Incentives, the marriage with the native store, and what Solana Mobile gets

Written 8 Sep 2026 against Solana Mobile's own framing: the Solana Mobile Stack opened to every Android OEM at MWC 2026; 200K+ devices, $5B+ on-chain volume, 500+ dApps; Season 2 runs on partner-app perks and SKR incentives. Nothing in their announcements mentions review quality, curation mechanics or device attestation beyond the Genesis Token. That gap is the opening.

## 1. Incentives that stay legal

The rule that keeps every incentive on the right side of securities, gambling and consumer law: **nobody is ever paid for a rating, and nothing Undersigned issues can be sold.** Recognition, access and usefulness are the currencies.

### Seeker owners
| Incentive | Why it works | Why it is safe |
|---|---|---|
| **Portable standing** — credibility, helpful votes, agreement, all bound to the wallet and readable by any app | Reputation that outlives one store or one phone is rare in crypto; people invest in it | Counts and a published formula; nothing transferable, nothing priced |
| **Being answered** — verified publishers reply on-chain; the inbox tells you | The main reason people stop reviewing is silence | UGC, no money |
| **Being useful to yourself** — cert-match, permission drift, version scores, update prompts | The app earns its place on the phone on days you write nothing | Device facts, never uploaded |
| **Being seen** — helpful votes pin the best reviews; "signers who caught it first" on version pages; a weekly viewer page of the most helpful signed reviews | Reddit-style social proof, no karma shop | Recognition, not reward |
| **Early access at the publisher's discretion** — publishers can open test builds or feature flags to *followers* of their app | Devs want testers; owners want first looks | Offered to everyone who follows, never conditioned on a rating; that keeps it outside endorsement-disclosure rules |
| **Season hooks that Solana Mobile controls** — if they choose to count signed reviews or upheld flags as Season activity, the signal is already on-chain | Their programme, their tokens, their legal wrapper | Undersigned pays nothing and counts nothing toward rewards; it only publishes facts. If they reward, they must reward participation, never the rating |

### Publishers
| Incentive | Why it works |
|---|---|
| **Verified badge + right of reply** with zero paperwork (claim + one file on the site the store already lists) | Ownership proof today takes an email thread |
| **Actionable signal** — tags, per-version scores, "same text across apps" and no-Genesis-wave alerts on their store reviews | A 3.8 tells you nothing; "Wallet fails · v176 · 4 signers" does |
| **A public responsiveness badge** — hours / days / weeks, answered N of M | Good devs get discovered for being good; it costs nothing to earn |
| **The desk** — MCP server: inbox, alerts, one-line replies handed to the phone to sign | Support becomes a Claude Desktop conversation, not a portal login |
| **Publisher Pro later** — exports, cross-app analytics, alerts SLA, as a plain software licence | The only money in the system, and it is a licence fee from publishers to the developer of the tool |

## 2. What Solana Mobile actually needs, and what they would want from this

Their stated year: take SMS to other OEMs, keep dApp count and quality climbing, give SKR reasons to exist, keep Season programmes honest. Their unstated problems, visible from the outside: store reviews are gameable, curation is manual, and any move toward collecting ecosystem data collides with a user base that chose a phone with a hardware wallet in it.

What the protocol gives them, in the order they would care:

1. **One device, one voice, without owning the identity.** Today "counted" means "holds a Seeker Genesis Token". For OEM phones the protocol should accept **any soulbound device-attestation credential from a list Solana Mobile governs** (mint authorities or program ids). That single change makes Solana Mobile the root of trust for every SMS phone's reviews, without them running a review system. It is the first cross-OEM trust primitive in their stack, and it is the reason to adopt rather than copy.
2. **Store spam control they never have to explain.** Flags with consensus, disputes, near-duplicate labels, "no Genesis found" on their own reviewers, review-wave alerts. All public, all recomputable. Guardians can moderate from it; the store can rank from it; nobody has to trust Undersigned.
3. **Publisher verification they can reuse.** The claim-plus-well-known check binds a wallet to the website the store already lists. That is a verified-publisher registry for the store itself, for free.
4. **Ecosystem telemetry without an SDK.** Version adoption, certificate changes, permission drift, responsiveness, per-version sentiment, all from consented device snapshots and the chain. No analytics SDK in 500 apps, no data broker.
5. **Evidence that SKR staking correlates with good citizenship** (or does not). Weighting is read from the chain; the correlation between staked SKR and credibility, agreement and upheld flags is computable by anyone. That is a marketing fact for the token if it holds, and a design fact if it does not.
6. **Season fraud signals.** Review farms and airdrop farms overlap. Duplicate-text clusters, no-Genesis waves and flag consensus are inputs their reward programmes can use.
7. **A hackathon story that keeps giving:** "N signed reviews from real devices" is a number they can publish.

## 3. Marrying with the native store, in stages

They will not swap review systems. They do not have to.

| Stage | What changes | Needs from Solana Mobile |
|---|---|---|
| 0 (now) | Undersigned sits beside the store, reads its public feed, credits it, deep-links installs, imports a user's own store reviews | Nothing |
| 1 | The store shows a **"Signed" badge and signed score** on listings, read from the registry with the published reader | An afternoon of engineering; no auth, no API |
| 2 | **Co-signing**: after a native review is posted, the store offers "also sign this on-chain"; the store's review and the ledger record carry the same text and both systems agree | A button in the store app |
| 3 | **Guardians moderate from flags**; responsiveness and verified-publisher status become ranking inputs in the store | Policy, not code |
| 4 | **OEM devices** join through the attestation list; the store on any SMS phone shows the same badge | Solana Mobile publishes the list |

At every stage the native system stays the system of record for installs and the publisher relationship. Undersigned is the trust layer, not a store.

## 4. Open source, and data that respects the people who chose a hardware wallet

**Licence.** Protocol spec, reference reader, viewer and MCP server under Apache-2.0 so an OEM, Solana Mobile or a competitor can implement it without asking. The Android app can follow the fleet's proprietary licence or open too; the protocol is what has to be open.

**Data.** The line is simple: **the chain is public; the phone is private.** Everything on the ledger is already public by the user's own act. Everything from the device stays on the device unless the owner opts in, per class, to a published aggregate:

- **Consent per data class**, recorded with the same signed consent mechanism as the terms: version adoption, permission drift events, certificate-change events, crash-free sessions later. Never wallet-linked in the export; never free text.
- **k-anonymity of 20** per published cell; cells below it are suppressed. Published as CC BY 4.0 datasets on GitHub Pages, with the aggregation code beside them.
- **Revocable**: opt out and the device stops contributing; already-published aggregates contain nothing to delete.
- **Legal footing**: anonymous aggregates fall outside GDPR and the Australian Privacy Act; wallet addresses on-chain are pseudonymous personal data the user published; no sale of data, so no CCPA "sale" exposure; no incentives tied to ratings, so no endorsement-disclosure exposure; no token, so no securities exposure. Solana Mobile receives what everyone receives, at the same time, from a public URL. That is the only way to give a privacy-first audience "valuable data" without losing them.

## 5. Other things they could derive, if they wanted

- A **device-integrity feed**: certificate mismatches between installed builds and store listings, at scale, are a sideloading and tampering map they cannot see today.
- **Time-to-first-review after a release** and per-version sentiment, the closest thing to release health without crash reporting.
- **Which permissions the community punishes**, from tag co-occurrence with permission drift.
- A **publisher graph**: verified claims tie wallets to store publishers; over time that shows who ships what across OEMs.
- **Guardian training data**: flags, disputes and outcomes are a labelled moderation dataset, public and consented.

## 6. What not to do

- No SKR or SOL for reviews, ever, from anyone in this stack. If Solana Mobile wants to reward, they reward participation in their own programme with their own wrapper.
- No hidden server. The moment a server exists, "no data" stops being true.
- No verdict words. The store's users and the store's lawyers both need "facts and deltas".
- No Seeker-only identity in the protocol. Genesis is the first attestation, not the only one.
