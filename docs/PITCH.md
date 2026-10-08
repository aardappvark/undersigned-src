# Undersigned — how to sell it, to whom

One line: **the review layer the dApp Store cannot build for itself: one Seeker, one voice, written to Solana, readable by anyone.**

## To Solana Mobile (SKR track, and the partnership ask)

- **It is the user-facing half of what SKR was designed for.** Solana Mobile's own SKR messaging lists dApp Store curation as a core utility. Undersigned is the first live implementation: staked SKR weights verified opinions, Genesis Tokens make each device one voice, and nothing is staked, moved or promoted by the app.
- **It reduces unnatural reviews on the store without touching the store.** Every counted review comes from a soulbound Genesis Token wallet, so a farm of accounts is a farm of Seekers. On top, each ledger entry shows the Seeker wallet's own transaction fingerprint (the Lighthouse guard it adds to everything it signs), so "signed on the device with the double-tap" is visible per review. The same check is applied to the store's public reviewers: readers can see which store reviews come from wallets with no Genesis Token at all. That signal is open data; Solana Mobile can consume it for free.
- **Open, permanent, and theirs to read.** The ledger is public chain state at one fixed address nobody controls. The reader logic is deterministic and published; any Guardian tool, dashboard, or the store itself can rank from the same bytes. No API, no partnership needed to start; a partnership makes it better.
- **It sends traffic to the store, not away from it.** Every app opens in the store; the store's own rating and reviews are shown beside the signed ones, credited; historical store reviews are imported by re-signing, which rewards people who already review in the store.
- **Publisher accountability the store can verify.** A publisher claims an app by signing, and the claim only verifies if the website the *store* lists for that app publishes the wallet. Verified publishers reply on-chain under their wallet.
- The ask: read the ledger in Guardian curation, and list the app with the SKR-track story. Nothing in the design depends on a yes.

## To publishers

- Verified, unfakeable reviews of your app from real devices, with structured tags that tell you *what* is wrong (crashes, permissions, paywall) rather than a star.
- A verified badge that proves you are the publisher, and replies that live next to the review forever.
- Per-version scores: a fixed app recovers instead of dragging a bad month around.
- Follow your own app and get told the moment a signed review lands, a reply arrives, or a review wave in the store comes from wallets with no Genesis Token.
- Import your users' store reviews? No; that is theirs. But when they import their own, you keep the history.

## To Seeker owners

- Your voice counts exactly once, weighted by what you have staked, and no one can delete it.
- See what your phone can prove about an app: whether the installed build matches the certificate the store lists, whether permissions grew between versions, whether the signing key changed.
- Follow apps, get told when they update and whether they still work, reply to reviews, and see who is who through `.skr` names.
- Bring your existing store reviews across in one wallet approval. Nothing to buy, nothing to stake, nothing to earn.

## Objections and answers

- *"It competes with the store."* It reads the store's public feed, credits it on every card, deep-links every install, and never writes to it. It is a trust layer beside the store, not a store.
- *"Reviews on-chain are permanent; what about bad actors?"* Tags first, prose second and double-consented; same-wallet withdrawal; per-device hide and report; a publisher reply is one signature away. And a bad actor needs a Seeker per fake voice.
- *"Why would Solana Mobile trust it?"* They do not have to. They can re-run the ranking from the chain.
- *"Is any of this a security, a wallet, a money transmitter?"* No funds move, nothing is issued, nothing is rewarded, keys never leave the Seed Vault. See `docs/LEGAL_GATES.md`.
