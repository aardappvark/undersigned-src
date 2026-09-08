# Undersigned — Claude Design brief

Paste into a new Claude Design canvas. **Direction first: no final assets until the direction is agreed.**

> **All imagery is generated with Higgsfield.** Every picture in this app and in every store asset. No stock,
> no clip-art, and **no emoji or emoticons anywhere** — not in the UI, not in slides, not in store art.
> Photoreal or richly illustrated, never cartoon stickers.

---

## What the app is

**Undersigned** — a signed-review layer beside the Solana dApp Store, for the **Solana Seeker**. Package
`com.undersigned.app`. "The undersigned" are the people who signed. Every review is a memo transaction the owner
approves on the phone with the Seed Vault double-tap; only wallets holding a Seeker Genesis Token are counted;
staked SKR weights an opinion, capped at 3×; records live at one registry address on Solana that nobody controls.
It reads the store's public catalogue, rating and reviews (credited, read-only, never writes), shows what the
phone can prove about an installed app (certificate matches the store's listing, permission drift), lets
publishers claim their app by signing (verified against the website the store lists), and lets owners import
their own store reviews with one signature. No server, no token, no fees, no rewards. Not affiliated with
Solana Mobile — that line must be visible on onboarding, in Settings and on every store asset.

Live on mainnet since 8 Sep 2026. Open viewer: https://aardappvark.github.io/undersigned-viewer/

## Who uses it

Seeker owners: crypto-literate, privacy-minded, allergic to unnatural reviews and to anything that smells like a
points scheme. Publishers of Seeker dApps who want reviews they can act on and a way to answer with proof of who
they are. Solana Mobile and Guardian curators reading the ledger as open data.

## Words that must never appear

"earn", "reward", "points", "yield", "APY", "invest", "stake more", "safe", "unsafe", "scam" (as a verdict),
"verified by us". Tags are a fixed vocabulary shown Capitalised: Works, Crashes, Wallet ok, Wallet fails, Fast,
Slow, Battery, Ads, Paywall, Fair price, Overpriced, Perms ok, Perms excess, Unnatural reviews, Misleading,
Suspected scam, Abandoned, Active dev, Onboarding ok, Confusing, Offline ok, Needs account, Seeker native, Webview.

---

## 1. Screens — all of them, completely finished

| Screen | Notes |
|---|---|
| **Terms gate** | First launch. Five confirmations (18+, not sanctioned, accept docs, permanence, no funds), Sign in with wallet (Seed Vault), "Browse without a wallet" fallback with checkbox. Links: Terms · Privacy · Disclaimer |
| **Intro slides** | 5 slides — see section 2 |
| **Browse** | The hero. Search; tabs Installed · Signed · All apps; rows = real app icon, name, package, Installed pill, category pill, stars + mean + "N signed" + top two tags, or "No signed reviews yet". Header shows the ledger line ("1 review from 1 Seeker"), a What-changed icon with a badge, and Settings |
| **App page** | Icon + name + package + date; Follow heart; Open in dApp Store; Write / Update / Withdraw my review. Cards in order: **Signed score** (big mean, stars, "N signed", small "Rank score x.xx" line), **Publisher** (store-listed name + website, Verified publisher line in green, "I publish this app"), **By version** (stars · vNN · mean · N signed, "installed" pill), **What owners tagged** (chips with counts), **dApp Store rating** (store average, 5-bar histogram with counts, up to three store reviews with `.skr` names, "Publisher replied" pill, "Genesis ✓" / "no Genesis found" pill, "also signed here" pill, and "You rated this N★ in the store on date → Import as a signed review"), **What your device can verify** (version, certificate hash, store version, green "matches the certificate the store lists" or red "does NOT match", dangerous permissions, permission deltas), **Signed reviews** (stars, `.skr` name or You, "Genesis ✓ 3.0×" or "not counted" pill, "Seeker wallet" pill, "Publisher ✓" pill, "N signed" standing link, tags, note, date · version, replies indented with Publisher ✓, Reply / Hide / Report) |
| **Review composer** | Rating (five big stars), tag chips (pick 1–3), optional note with "chars, bytes left" counter, the **permanence consent card** (amber) that appears only when text is typed, gate messages (sign in / checking Genesis / no Genesis), fee line, Sign and publish → stages Checking → Waiting for wallet → Confirming → "Published. Signature …" |
| **Reply dialog** | Text, counter, the same permanence consent, Sign and publish reply |
| **Withdraw dialog** | Explains that withdrawal stops counting but nothing deletes the text |
| **Claim dialog** | Shows the store-listed website, the exact `/.well-known/undersigned.txt` URL and the wallet to paste, Sign the claim |
| **Signer standing dialog** | Counts only: counted · withdrawn · replies received · given · tag agreement · signing since · "Counts only. No score, no reward, nothing to buy." |
| **What changed** | Cards per installed app: certificate CHANGED (red), updated to vNN, permissions added/removed, "Rate this version" |
| **Settings** | Wallet (sign in / disconnect); Your standing (Genesis found, staked SKR read on-chain, weight); Import my dApp Store reviews (scan → list with checkboxes → Sign and import N); Alerts (toggle, "Following N apps"); Ledger (registry address, Re-read); Hidden reviews (Unhide all); Legal (Terms, Privacy, Disclaimer, Restricted jurisdictions, Licence, Font licence) with the not-affiliated line; Your data (Export consent record, Delete local data); Replay the intro; version + contact |
| **BlockedRegion** | Sanctions gate. Dead end, no navigation |

Do we need Settings? Yes: wallet, standing, import, alerts, ledger, hidden list, legal links (mandatory), GDPR export/delete, replay. It may become a "You" surface with the legal/data block at the bottom, but every row above must survive.

## 2. Intro slides — match AardAppvark Toolkit

Full-bleed `HorizontalPager`, one generated image per slide, dot indicators, Skip top-right, Next / Start browsing.
Five slides, copy already in the app:

1. **One Seeker, one voice** — a review layer beside the dApp Store; only Genesis-Token wallets count; nothing to buy, stake or earn
2. **Signed, not typed** — a small transaction approved with the double-tap; wallet pays only its own network fee; never holds keys or funds
3. **Beside the store, not instead of it** — store rating shown and credited; installs open in the store; bring your store reviews across with one signature
4. **What your phone can prove** — certificate matches the store's listing, permission changes; facts, never verdicts
5. **Follow, get told, reply** — follows, local alerts, replies, verified publishers; everything published is permanent and public

Each slide needs a **Higgsfield-generated image**, photoreal, sharing one visual world. No icons standing in for pictures.

## 3. dApp Store assets — exact specs

| Asset | Size | Rules |
|---|---|---|
| **dApp icon** | **512 × 512** | One source drives the store icon, the adaptive launcher layers, the legal-site favicon (which is also the wallet's identity icon) and the banner. Must read at 48dp |
| **Banner** | **1200 × 600** | Sits behind the icon on the detail page; keep the lower-left quiet; carries the name and a tagline |
| **Editor's Choice graphic** | **1200 × 1200** | Standalone square with name and key features |
| **Screenshots** | **1200 × 2670** | Five from the finished UI (working captures in `assets/store/screenshots/`) |

- **The dApp icon must NOT be repeated on the banner.**
- Tagline direction (pick or better it): *"One Seeker, one voice."* Key features worth naming: signed on the device · Genesis-gated, SKR-weighted · store rating beside it · what your phone can prove · verified publishers.
- Icon direction candidates: a signature stroke on black; a seal with a single tick; a ledger line-mark. The name is about signing, so the mark should read as a signature or seal, never as a star or a speech bubble.

## 4. Launcher and launch experience

- Adaptive launcher icon from the same 512 mark: foreground + background + monochrome layers, safe zone radius 36.
- Splash via the Android 12+ splash API — the mark on AMOLED black, no spinner, no wordmark crawl.

---

## Palette (dark, AMOLED-first) — the code's current tokens

```
background #000000  surface #0E0B18  surfaceVariant #17122A  card #120E22  glassBorder #339945FF
purple #9945FF  purpleDim #6B2FC7  purpleLight #B98CFF  green #14F195
textPrimary #F2EEFF  textSecondary #B7AED1  textTertiary #7C7396  textDim #4E4766
negative #FF6B6B  warning #FFC46B  info #6BB9FF
```

Semantic colour is separate from the accent: green = verified / matches / Genesis, amber = permanence consent and
"no Genesis found", red = certificate mismatch and withdraw, blue = "Seeker wallet" and "Publisher replied".
Font: Manrope (OFL, shipped). Numbers tabular. Keep the token names; the code already uses them.

## Hard constraints — not preferences

1. **Seeker screen is 1200 × 2670.** Design to that.
2. **No orientation or rotation changes.** Standing rule on this fleet.
3. **28 locales.** German, Greek, Vietnamese and Turkish run 30–60% longer than English.
4. The "not affiliated with Solana Mobile" line and "opinions of individual owners, not the store's ratings" must be visible, not buried.
5. No emoji anywhere. Tags are text chips.
