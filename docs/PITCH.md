# Undersigned — how to sell it, to whom

One line: **the review layer the dApp Store cannot build for itself: one Seeker, one voice, written to Solana, readable by anyone.**

## To Solana Mobile (SKR track, and the partnership ask)

- **It is the user-facing half of what SKR was designed for.** Solana Mobile's own SKR messaging lists dApp Store curation as a core utility. Undersigned is the first live implementation: Genesis Tokens make each device one voice, staked SKR and SOL are shown beside each review for transparency (never weighed), and nothing is staked or moved by the app.
- **It reduces unnatural reviews on the store without touching the store.** Every counted review comes from a soulbound Genesis Token wallet, so a farm of accounts is a farm of Seekers. On top, each ledger entry shows the Seeker wallet's own transaction fingerprint (the Lighthouse guard it adds to everything it signs), so "signed on the device with the double-tap" is visible per review. The same check is applied to the store's public reviewers: readers can see which store reviews come from wallets with no Genesis Token at all. That signal is open data; Solana Mobile can consume it for free.
- **Open, permanent, and theirs to read.** The ledger is public chain state at one fixed address nobody controls. The reader logic is deterministic and published; any Guardian tool, dashboard, or the store itself can rank from the same bytes. No API, no partnership needed to start; a partnership makes it better.
- **It sends traffic to the store, not away from it.** Every app opens in the store; the store's own rating and reviews are shown beside the signed ones, credited; historical store reviews are imported by re-signing, which rewards people who already review in the store.
- **Publisher accountability the store can verify.** A publisher claims an app by signing, and the claim only verifies if the website the *store* lists for that app publishes the wallet. Verified publishers reply on-chain under their wallet.
- The ask: read the ledger in Guardian curation, and list the app with the SKR-track story. Nothing in the design depends on a yes.

## The handover path (we offer it; we do not compete)

1. **Read** — today. The store, Guardians or any tool reads the ledger at one reference address and re-runs the published ranking.
2. **Adopt** — the publisher proof file (`/.well-known/undersigned.txt`), the Genesis lens on store reviewers, the memo
   format and the ranking rules, in the store's own review flow.
3. **Take it** — the store's review box signs the same records through MWA, the two ledgers become one, and Solana
   Mobile takes the code or rebuilds it. Undersigned becomes one reader among many.

Until then it is an ordinary listed dApp beside the store. We see it as rightfully under Seeker's stewardship:
optional Pro and API passes pay for hosting, every payment is public on-chain under the `UNS` memo (the economics
ledger separates it from the rest of the fleet), and what is left after running costs is intended to go back to the
Seeker community. Never to reviewers or moderators for what they sign: paying for reviews would undo the point.

What that is worth to a steward ([docs/REVENUE_MODEL.md](REVENUE_MODEL.md), `tools/revenue_model.py`): at today's scale
and these prices, about US$37,000 over the last 12 months and US$83,000 over the next (mid cases; US$12k–124k across
cases), against under US$1,000 a year to run. Device and store numbers are measured; adoption rates are stated assumptions.

## To publishers

- Verified, unfakeable reviews of your app from real devices, with structured tags that tell you *what* is wrong (crashes, permissions, paywall) rather than a star.
- A verified badge that proves you are the publisher, and replies that live next to the review forever.
- Per-version scores: a fixed app recovers instead of dragging a bad month around.
- Sign "fixed in version N" under a review; the reviewer is told and can follow up. Your responsiveness (fixes, follow-ups, the change in stars, hours to fix) is public, which rewards publishers who listen.
- Follow your own app and get told the moment a signed review lands, a reply arrives, or a review wave in the store comes from wallets with no Genesis Token.
- Import your users' store reviews? No; that is theirs. But when they import their own, you keep the history.

## To Seeker owners

- Your voice counts exactly once, and no one can delete it. What makes it weigh more is what you add (the phone's facts, a written note), never money or stake.
- When a publisher fixes what you reported, you are told, and your follow-up stars sit beside the first ones.
- See what your phone can prove about an app: whether the installed build matches the certificate the store lists, whether permissions grew between versions, whether the signing key changed.
- Follow apps, get told when they update and whether they still work, reply to reviews, and see who is who through `.skr` names.
- Bring your existing store reviews across in one wallet approval. Free to read and sign; no payment to Undersigned changes a score.

## Objections and answers

- *"It competes with the store."* It reads the store's public feed, credits it on every card, deep-links every install, and never writes to it. It is a trust layer beside the store, not a store.
- *"Reviews on-chain are permanent; what about bad actors?"* Tags first, prose second and double-consented; same-wallet withdrawal; per-device hide and report; a publisher reply is one signature away. And a bad actor needs a Seeker per fake voice.
- *"Why would Solana Mobile trust it?"* They do not have to. They can re-run the ranking from the chain.
- *"What stops one person buying many voices?"* Each voice needs its own Seeker Genesis Token, and a Seeker costs
  US$400 or more. A farm of 100 favourable reviews costs over US$40,000 in phones, and every one of them is public.
- *"Why give it away?"* Reviews are only worth something if everyone reads the same ones. The most useful outcome is
  the store adopting the format; a rival review silo would split the signal.
- *"Who moderates, and why would they?"* Spam and off-topic records are handled by any Seeker owner's flag, weighted
  by Genesis Token like a review, so a flag farm costs the same phones as a review farm. Named moderators can only hide
  text for four reasons (illegal, personal data, abuse, impersonation), never a voice, and every action is public. No one
  is paid or staked into the role: paying moderators would buy outcomes, and stake would let money decide.
- *"Is any of this a security, a wallet, a money transmitter?"* Nothing is issued, nothing is rewarded, keys never leave the Seed Vault; the only payments are software licences to the treasury. See `docs/LEGAL_GATES.md`.
