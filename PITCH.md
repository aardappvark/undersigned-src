# Undersigned — how to sell it, to whom

One line: **the review layer the dApp Store cannot build for itself: one Seeker, one voice, written to Solana, readable by anyone.**

## To Solana Mobile (SKR track, and the partnership ask)

- **It is the user-facing half of what SKR was designed for.** Solana Mobile's own SKR messaging lists dApp Store curation as a core utility. Undersigned is the first live implementation: Genesis Tokens make each device one voice, staked SKR and SOL are shown beside each review for transparency (never weighed), and nothing is staked or moved by the app.
- **It reduces unnatural reviews on the store without touching the store.** Every counted review comes from a soulbound Genesis Token wallet, so a farm of accounts is a farm of Seekers. On top, each ledger entry shows the Seeker wallet's own transaction fingerprint (the Lighthouse guard it adds to everything it signs), so "signed on the device with the double-tap" is visible per review. The same check is applied to the store's public reviewers: readers can see which store reviews come from wallets with no Genesis Token at all. That signal is open data; Solana Mobile can consume it for free.
- **Open, permanent, and theirs to read.** The ledger is public chain state at one fixed address nobody controls. The reader logic is deterministic and published; any Guardian tool, dashboard, or the store itself can rank from the same bytes. No API, no partnership needed to start; a partnership makes it better.
- **It sends traffic to the store, not away from it.** Every app opens in the store; the store's own rating and reviews are shown beside the signed ones, credited; historical store reviews are imported by re-signing, which rewards people who already review in the store.
- **Publisher accountability the store can verify.** A publisher claims an app by signing, and the claim only verifies if the website the *store* lists for that app publishes the wallet. Verified publishers reply on-chain under their wallet.
- The ask: read the ledger in Guardian curation, and list the app with the SKR-track story. Nothing in the design depends on a yes.

## How the store can adopt it (we offer it; we do not compete)

1. **Read** — today. The store, Guardians or any tool reads the ledger at one reference address and re-runs the published ranking.
2. **Adopt** — the publisher proof file (`/.well-known/undersigned.txt`), the Genesis lens on store reviewers, the memo
   format and the ranking rules, in the store's own review flow.
3. **Sign it** — the store's review box signs the same records through MWA and the two ledgers become one.
   Undersigned stays ours, as one reader and writer among many.

Undersigned itself stays ours: an independent, ordinary listed dApp beside the store, paid for by optional Pro and API
passes (every payment public on-chain under the `UNS` memo). We are happy to contribute to the Seeker community as it
grows, never by paying reviewers or moderators for what they sign.

The numbers ([docs/FINANCIAL_CASE.md](FINANCIAL_CASE.md)): about US$843 a year to run, break-even at 127 Pro licences
a year, or 11 apps on the publisher plan. Base case (3% of the 200,000 Seekers on Pro, 10% of dApps on the publisher plan)
about US$64,000 in year one; the owner's target (10–20% on Pro) US$194,000–327,000. What the store's apps have actually been paid is read from the chain in §3 of the case.

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
- *"What stops one person buying many voices?"* Price. A fake review for an Android app sells for US$1.73–2.14 on
  average across 34 paid review providers (Martens & Maalej, 2019). One more counted review of an app here needs one
  more Seeker Genesis Token, so one more Seeker: US$450 at early pre-order, US$500 retail, about 210–290 times as much.
  That prices out made-up reviewers; it does not stop a real owner being paid to review, which is why bursts and
  campaigns are shown as facts.
- *"Why give it away?"* Reviews are only worth something if everyone reads the same ones. The most useful outcome is
  the store adopting the format; a rival review silo would split the signal.
- *"Who moderates, and why would they?"* Spam and off-topic records are handled by any Seeker owner's flag, weighted
  by Genesis Token like a review, so a flag farm costs the same phones as a review farm. Named moderators can only hide
  text for four reasons (illegal, personal data, abuse, impersonation), never a voice, and every action is public. No one
  is paid or staked into the role: paying moderators would buy outcomes, and stake would let money decide.
- *"Is any of this a security, a wallet, a money transmitter?"* Nothing is issued, nothing is rewarded, keys never leave the Seed Vault; the only payments are software licences to the treasury. See `docs/LEGAL_GATES.md`.
