# Undersigned — Claude Design brief (v2, Higgsfield imagery)

Paste this whole document into a new Claude Design canvas.

**Direction first.** Produce a one-frame moodboard for the chosen direction (section 2) and stop for approval before rendering any screen or store asset. No final assets until the direction is agreed.

> **All imagery is generated with Higgsfield, photorealistic, full-page.** Every picture in this app and in every store asset. No stock, no clip-art, no illustration, no 3D-render look, and **no emoji or emoticons anywhere** — not in the UI, not in slides, not in store art, not in mockup copy. No text baked into images; the app draws its own type over them.

---

## 1. What the app is, in one breath

**Undersigned** — a signed-review layer beside the Solana dApp Store, for the Solana Seeker. "The undersigned" are the people who signed. A review is a small transaction the owner approves on the phone with the double-tap; only wallets holding an attested-device credential (today the Seeker Genesis Token) are counted; staked SKR weights an opinion, capped at three times; every record lives at one address on Solana that nobody controls. The app shows the store's own rating beside the signed one, credited; shows what the phone can prove about an installed app; lets publishers verify themselves against the website the store lists; lets the community flag, clear, dispute and vote helpful; and lets owners import their own store reviews with one signature. No server, no token, no fees, no rewards. Not affiliated with Solana Mobile.

Package `com.undersigned.app`. Live on mainnet since 8 Sep 2026. Open viewer: https://aardappvark.github.io/undersigned-viewer/

**Audience.** Seeker owners: crypto-literate, privacy-minded, allergic to unnatural reviews and to anything that looks like a points scheme. Publishers of Seeker dApps. Solana Mobile and Guardian curators reading the ledger as open data.

**Words that must never appear:** earn, reward, points, yield, APY, invest, stake more, safe, unsafe, scam (as a verdict), fraud, verified by us. Say "suspected spam", "unnatural", "integrity". Tags are a fixed vocabulary shown capitalised: Works, Crashes, Wallet ok, Wallet fails, Fast, Slow, Battery, Ads, Paywall, Fair price, Overpriced, Perms ok, Perms excess, Unnatural reviews, Misleading, Suspected scam, Abandoned, Active dev, Onboarding ok, Confusing, Offline ok, Needs account, Seeker native, Webview.

---

## 2. Visual direction — pick one, render a moodboard, stop

The subject is **signing**: ink meeting paper, a seal pressed into wax, a fingerprint on black glass, a ledger that cannot be edited. Photograph that world. Solana's purple and green are **light**, never paint: coloured practicals and rim light in a dark room.

**Direction A — Ink and Light (recommended).** Macro and near-macro photography in a dark study: a fountain-pen nib mid-stroke on heavy cream paper, wet ink catching a violet rim light; a wax seal under a mint-green practical; an open ledger, its pages edge-lit; a Seeker face-down on black leather with a single green reflection. Chiaroscuro, matte blacks, one light source per frame. It says "permanent, personal, deliberate" without a single word.

**Direction B — Attested.** Cool, precise, architectural: brushed steel vault doors, glass, engraved plates, a single hand pressing a fingerprint sensor, condensation on cold metal. Says "verified device" more than "your voice". Use only if A feels too warm.

**Direction C — Many hands, one ledger.** Documentary daylight: hands holding phones, a long shared table, a signature book at a doorway. Warmer and more social, but the most likely to drift toward stock-photo. Not recommended.

Whichever is chosen, **one visual world for every image**: same lens family (85–100 mm macro, f/2–f/2.8, shallow depth of field), same light logic (one practical, violet or mint, on matte black), same materials (ink, cream paper, wax, black glass, dark leather, brushed metal). Every frame must hold a quiet zone for type: for portrait slides the lower 45 % fades to near-black; for the banner the lower-left is empty.

### Higgsfield prompt scaffold (fill the subject, keep the rest)

```
[SUBJECT], photorealistic macro photograph, 90mm lens, f/2.2, shallow depth of field, single practical light with a [violet #9945FF | mint #14F195] gel, chiaroscuro, matte black background, cream paper and dark leather textures, fine grain, 8K, portrait 9:20 [or 2:1 for the banner, 1:1 for the icon and Editor's Choice], lower half falls to black for text, no text, no logos, no watermark, no faces, no illustration, no cartoon, no emoji
```

**What we learned generating the moodboard (8 Sep 2026, Higgsfield Soul 2.0, 2K, ~0.12 credits a frame).** The photographic model is excellent at ink, wax, leather and light, and unreliable about three things — plan for them:

1. **It invents brands.** Unprompted it added a made-up wordmark, a circular logo and a human pictogram to the first banner attempt, and a small emblem to a notebook cover. Every prompt therefore has to say, in the positive text as well as the negative, that the scene is *unbranded and anonymous*, and every returned frame must be checked for invented marks before use.
2. **It writes.** Asked for an abstract ink stroke it produces letterforms every time. Photographs may contain *illegible* handwriting where a signature genuinely belongs (slides 2 and 5, the banner); nothing else may contain characters.
3. **It paints with the accent instead of lighting with it.** Say "mint-green light / reflection", never "mint-green object", or you get a mint notebook.

**The icon is not photographic.** Three attempts produced violet lettering rather than a mark. The icon is authored as vector geometry instead (see `assets/design/icon/`): one tapered signature stroke resting on a mint rule — the line you sign *above*, which is what "the undersigned" means. It holds at 48 dp and 24 dp and gives a clean white monochrome layer. Refine that geometry; do not photograph it. Photography stays where it belongs: slides, gates, empty states, banner, Editor's Choice.

Negative prompt for every generation: `text, letters, words, brand name, wordmark, logo, emblem, badge, icon, pictogram, human figure, watermark, emoji, emoticon, sticker, cartoon, illustration, 3d render, CGI look, plastic, neon signage, face, hands with jewellery, stock photo, oversaturated`.

Approved reference frames from that session are in `assets/design/moodboard/` (five slide frames, banner study) and `assets/design/icon/` (icon, adaptive layers, monochrome, legibility proofs). Match their light, materials and depth of field.

---

## 3. Where photographs go, and where they do not

Full-page photographs belong where the user reads one thing: the terms gate, the five intro slides, the empty states, the profile header, and every store asset. **Lists and forms stay photo-free**: Browse, the App page cards, the composer, Inbox, Settings. Those get AMOLED black, the token palette, and at most a faint paper grain at 4 % opacity. A photo under a list of 1,300 apps is noise.

---

## 4. Screens — all of them, completely finished

| Screen | Notes |
|---|---|
| **Terms gate** | Full-page photo (A: nib on paper, violet rim light), five confirmations, "Sign in with your wallet", "Browse without a wallet" with checkbox. Links: Terms · Privacy · Disclaimer. The not-affiliated line is visible |
| **Intro slides ×5** | Full-bleed `HorizontalPager`, one Higgsfield image per slide, dot indicators, Skip top-right, Next / Start browsing. See section 5 |
| **Browse** | Search; tabs Installed · Signed · All apps; rows = real app icon, name, package, Installed pill, category pill, stars + mean + "N signed" + two tags, or "No signed reviews yet". Header: ledger line, Inbox icon with count badge, What-changed icon with badge, Settings |
| **App page** | Icon + name + package + date; Follow heart; Open in dApp Store; Write / Update / Withdraw my review. Cards in order: **Signed score** (big mean, stars, "N signed", small "Rank score x.xx"), **Publisher** (store-listed name + website; Verified line in green; responsiveness line "Answers signed reviews in hours · 3 of 5 answered"; "I publish this app"), **By version** (stars · vNN · mean · N signed · "installed" pill), **What owners tagged** (chips with counts), **dApp Store rating** (average, five-bar histogram with counts, three store reviews with `.skr` names, "Publisher replied", "Attested device" / "no attested device found", "also signed here"; "You rated this in the store → Import as a signed review"), **What your device can verify** (version, certificate hash, store version, green "matches the certificate the store lists" / red "does NOT match", permissions, deltas), **Signed reviews** (see below) |
| **Signed review card** | Stars · `.skr` name or You (tap → Profile) · star icon · "Attested ✓ 3.0×" or "not counted" · "Seeker wallet" · "Publisher ✓" · "N signed · credibility" · "net 1.0" flag weight · "Helpful · 2.0" · "Similar text" / "Same text across apps" · tags · note · replies indented · date · version · actions: Reply, Helpful / Withdraw my helpful vote, Flag / Withdraw my flag, Looks genuine, Hide, Report. **Swipe right = reply.** A consensus-hidden card collapses to one line ("Hidden by consensus: flagged as unnatural by 2.0 weighted Seekers", the author's dispute text if any, Show anyway, Dispute, Looks genuine) |
| **Review composer** | Five big stars, tag chips (pick 1–3), optional note with "chars, bytes left", the amber **permanence consent** card that appears only when text is typed, the "near-identical to an existing note" warning, gate messages, fee line, Sign and publish → Checking → Waiting for wallet → Confirming → Published, then "Also post in the dApp Store" |
| **Dialogs** | Reply (with permanence consent), Withdraw review, Flag (reasons: Suspected spam, Abuse, Off topic, Misleading; publisher note), Dispute (text + consent), Helpful vote confirmation, Claim (shows the store-listed site, the exact `/.well-known/undersigned.txt` URL and the wallet to paste) |
| **Profile** | Photo header band (A: wax seal), `.skr` name, credibility as a big number with the published formula under it, standing counts, flags given / upheld / received / hidden, "answers replies N % of the time", **Verified publisher of** list with responsiveness, their signed reviews |
| **Inbox** | Tabs Unanswered · Replies to me · Starred, each a list of review cards; empty states carry a photo |
| **What changed** | Cards per installed app: certificate CHANGED (red), updated to vNN, permissions added / removed, "Rate this version" |
| **Settings** | Wallet; Your standing ("Attested device found (Seeker Genesis Token)", staked SKR read on-chain, weight); Import my dApp Store reviews (scan → checkboxes → Sign and import N); Alerts toggle + "Following N apps"; Ledger (registry address, Re-read); Hidden reviews; Legal links + the not-affiliated line; Your data (Export consent record, Delete local data); Replay the intro; version + contact |
| **Blocked region** | Sanctions gate, dead end, photo permitted |
| **Notifications** | Small icon + title/body samples: "New signed review: AardAppvark Toolkit · 5★ · Works, Fast", "Reply to your review of …", "… updated to v177 — still works?", "… : unusual review activity in the store" |

Components: `GlassCard` `PrimaryButton` `SecondaryButton` `StatRow` `Pill` `TagChip` `RatingStars` `DAppIcon` (installed icon → store icon → letter badge) `SectionTitle`.

---

## 5. Intro slides — five full-bleed photographs, copy already in the app

| # | Title | Body (verbatim in the app) | Higgsfield subject (Direction A) |
|---|---|---|---|
| 1 | One Seeker, one voice | Undersigned is a review layer beside the dApp Store. Only wallets holding a Seeker Genesis Token are counted, so one device is one voice. Nothing to buy, stake or earn. | A single fingertip pressed onto black glass, a mint-green bloom spreading from the contact point, everything else black |
| 2 | Signed, not typed | Every review is a small transaction you approve on the phone with the double-tap. Your wallet pays only the network fee it sets. Undersigned never holds your keys or funds. | Fountain-pen nib mid-stroke on heavy cream paper, wet ink with a violet rim light, the stroke leaving frame |
| 3 | Beside the store, not instead of it | The store's own rating and reviews are shown next to the signed ones, credited. Installs open in the store. You can bring your existing store reviews across with one signature. | Two ledgers side by side on dark leather, one printed and modern, one hand-ruled, a Seeker face-down between them catching one green reflection |
| 4 | What your phone can prove | For apps you have installed, Undersigned shows whether the build matches the certificate the store lists and whether permissions changed. Facts, never verdicts. | A wax seal under a jeweller's loupe, the impression crisp, mint light through the glass |
| 5 | Follow, get told, reply | Follow apps to hear about new signed reviews, updates and replies. Publishers claim their app by signing and answer with a verified badge. Everything you publish is permanent and public. | An open ledger with a reply written in the margin in a second ink, a desk lamp just switched on, violet spill on the page edge |

Each image: portrait 9:20 at 1200 × 2670, lower 45 % to near-black, subject in the upper 55 %.

---

## 6. dApp Store assets — exact specs

| Asset | Size | Rules |
|---|---|---|
| **dApp icon** | **512 × 512** | One source drives the store icon, the adaptive launcher layers (foreground, background, monochrome; safe zone radius 36), the legal-site favicon (which is also the wallet's identity icon) and the banner. Must read at 48 dp. **Direction A icon: a single confident ink stroke, photographed, on matte black, with one violet highlight in the wet ink — plus a flat monochrome vector of the same stroke for the themed-icon layer.** Never a star, never a speech bubble |
| **Banner** | **1200 × 600** | Sits behind the icon on the detail page; lower-left empty; carries the name and the tagline in Manrope over the photo's dark zone. **The icon must not be repeated on the banner** |
| **Editor's Choice graphic** | **1200 × 1200** | Standalone square: name, tagline, three key features, over a Direction-A photograph |
| **Screenshots** | **1200 × 2670** | Five from the finished UI (working captures at 1080 × 2400 are in `assets/store/screenshots/` for reference) |

Tagline: **"One Seeker, one voice."** Key features to name: signed on the device · attested and SKR-weighted · the store's rating beside it · what your phone can prove · verified publishers.

Splash: Android 12+ splash API, the stroke mark on AMOLED black, no spinner, no wordmark crawl.

---

## 7. Palette and type — the code's tokens, keep the names

```
background #000000  surface #0E0B18  surfaceVariant #17122A  card #120E22  glassBorder #339945FF
purple #9945FF  purpleDim #6B2FC7  purpleLight #B98CFF  green #14F195
textPrimary #F2EEFF  textSecondary #B7AED1  textTertiary #7C7396  textDim #4E4766
negative #FF6B6B  warning #FFC46B  info #6BB9FF
```

Semantic colour is separate from the accent: green = attested / matches / verified; amber = permanence consent, "no attested device found", flags and disputes; red = certificate mismatch and withdraw; blue = "Seeker wallet" and "Publisher replied". Font: Manrope (OFL, shipped). Numbers tabular. Over photographs, type sits on the faded zone only; never on a busy area; minimum contrast 4.5:1.

---

## 8. Hard constraints — not preferences

1. **Seeker screen is 1200 × 2670.** Design to that.
2. **No orientation or rotation changes.**
3. **28 locales.** German, Greek, Vietnamese and Turkish run 30–60 % longer than English; every label must survive that.
4. "Not affiliated with Solana Mobile" and "opinions of individual owners, not the store's ratings" must be visible, not buried.
5. **No emoji anywhere.** Tags are text chips. No pictograms standing in for photographs.
6. Nothing in an image may resemble the Solana Mobile, Seeker or dApp Store marks.

---

## 9. Deliverables, in order

1. Moodboard frame for the chosen direction: six Higgsfield frames, the palette, the type scale, the icon stroke. **Stop for approval.**
2. Five intro slides at 1200 × 2670 with the copy set over them.
3. Icon 512 + adaptive layers + monochrome; banner 1200 × 600; Editor's Choice 1200 × 1200.
4. Every screen in section 4 at 1200 × 2670, including the empty states, dialogs, a consensus-hidden card, and one screen in German to prove the labels hold.
5. Export: PNG for images, SVG for the monochrome mark, a one-page token sheet.
