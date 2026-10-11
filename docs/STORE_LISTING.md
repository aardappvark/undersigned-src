# Undersigned — dApp Store listing (1.1.27, updated 12 Oct 2026)

The source of truth for the Publisher Portal fields. Nothing here is submitted until the owner says so.

## Build
- APK: `app/build/outputs/apk/release/app-release.apk`, `com.undersigned.app`, versionCode **38**, versionName **1.1.26**
- Signed v2 + v3 with the fleet key, cert SHA-256 `ac693213aec9befa200be59881b9decd37646b1761591edc9bc6c144192d100b`
  (CN=MidMightBit Games), the same key as 28 of the fleet's live apps.
- The manifest names its publishers: `undersigned.publisher` = `DD4a…Wq2Q,HVXV…yDaD` (publisher proof by the store's build).
- First listing: the app is not in the store catalogue (checked 7 Oct).

## Listing
| Field | Value |
|---|---|
| Name | Undersigned |
| Short description | Signed reviews from Seeker owners |
| Category | Productivity & Utility |
| App website | https://undersigned.provenby.dev (serves `/.well-known/undersigned.txt` for its own publisher proof) |
| Contact email | aardappvark@proton.me |
| Privacy policy | https://aardappvark.github.io/undersigned-legal/privacy.html |
| Licence / EULA | https://aardappvark.github.io/undersigned-legal/license.html |
| Copyright | https://aardappvark.github.io/undersigned-legal/copyright.html |
| Terms | https://aardappvark.github.io/undersigned-legal/terms.html |

Known risk: the PUBLISHER website (one locked record for all listings) is the Toolkit's GitHub repo; BountySeeker and
CitizenSol were rejected for it on 21 Aug 2026 (memory: publisher website rejection). The per-app website above is
Undersigned's own site; if the reviewer reads the publisher field, the answer is the same as for those two.

### Long description
Undersigned is a review layer beside the dApp Store where every review is signed on-chain by a Seeker owner's own wallet.

One Seeker, one voice: only wallets holding a Seeker Genesis Token are counted, so a review farm needs a real Seeker for every voice, and no payment to Undersigned adds weight to any review. A review weighs more for what it carries (your phone's facts, a written note), never for money or stake; the SKR and SOL they had staked are shown beside each review for transparency, never weighed. Pro members' reviews, and reviews their authors paid to promote, are also shown as their own labelled scores, never blended in; promoted reviews are listed after organic ones, never first, and Undersigned's own wallets are labelled and never counted.

- Every dApp in the store, with its signed score beside the store's own rating, and the store's reviews read alongside.
- Write a review in seconds: stars, one to three tags, an optional note of up to 2,000 characters. Your wallet signs it with the double-tap; you pay only the Solana network fee.
- No wallet to hand? A quick review is signed by the app and shown apart, never counted.
- When a publisher marks your problem fixed, you are told and can follow up with new stars; both are shown, and so is how quickly each publisher fixes things.
- Publishers prove an app is theirs (by the app's signing key or the website the store lists) and reply under their own name.
- Facts, not verdicts: rating bursts, version and certificate changes, install age and usage time you choose to add, and who paid for an app on-chain.
- Follow apps to hear about new signed reviews, replies, fixes and updates; every kind of alert has its own switch.
- Pro: the dApp list updates itself every Monday, copy reviews to the store, a publisher desk, exports.

Undersigned never holds your keys or funds and moves nothing from your wallet that you have not signed for. Not affiliated with Solana Mobile.

### What's new (1.1.27)

Review analytics, every source: the dApp Store's own reviews beside the signed ones, filtered by organic, Pro, promoted, quick, other wallets and ours, by category, by dApp and over time, with the same figures as JSON, CSV and charts.

### What's new (1.1.26)

The leaderboard gains Pro members' score, the plain mean of every signed star, and the store's categories ranked by store reviews and by signed reviews with a 30-day trend. A feature request names its category (design, speed, wallet, payments, notifications, offline, integration, other) so a publisher can report against it. A per-dApp switch tells you when that dApp updates in the store. Tag a review as a feature request; the publisher signs Will build or Won't build, and the hours to decide, deliver and fix crashes are shown. Abusive language posted in the dApp Store is hidden here with the reason. The publisher desk shows the treasury from the ledger at once and the last 12 weeks received. Anonymous usage counts, with a switch in Settings. The store's publisher replies translate too, and a review's menu is tidier: icons, the moderator's rows apart, Report last. Translating a note that is already in your language now says so instead of failing. Abusive language (slurs, strong profanity, threats) is refused as you type and hidden automatically wherever reviews are read; the rating still counts. A moderator can exclude a wallet with a signed, public record.

### What's new (1.1.12)

The publisher's Mark fixed dialog opens again.

### What's new (1.1.11)

A Translate button on every review, store review and reply (Pro), and an auto-translate switch in Settings. Translations are suggestions, labelled as AI; the author's own words stay above them.

### What's new (1.1.10)

Tap a star row to read only those reviews, signed or from the store. Tap a reviewer's record to see every review they signed, with date and time, dApp, stars, tags and note, and a twelve-month strip. The store hand-off button says what it does: copy to clipboard to post in the dApp Store.

### What's new (1.1.9)

Every review now carries its reviewer's record: how many reviews they have signed, on how many apps, their follow-ups and which confirmed a fix, and since when. Counts only; nothing changes a score.

### What's new (1.1.8)

Pro reviewers can add up to two tags of their own beside the fixed ones; they show on the review and on the app's page and change no score. Pro offers now disappear the moment your Pro is active. When a review's text matches another's, the card now names which review and when, as a fact.

### What's new (1.1.7)

Sign in with your wallet from the review form itself. Payments analytics try again as the network returns, and say when a load failed for another reason. The reviews timeline draws its days in your time zone, matching the dates on the cards.

### What's new (1.1.6)
First release on the store: signed reviews from Seeker owners, long reviews, quick reviews (shown apart, never counted), time actually used (opt-in), a second round when a publisher fixes a problem, publisher proof and replies, every dApp in the store with a weekly update, rating bursts and device facts, the payments board with what the store's apps are paid (leaderboards, categories, what is rising), a guided tour, notifications you choose one by one, and 28 languages.

### Testing instructions (for the reviewer)
1. Open the app and accept the terms. "Browse without a wallet" shows everything that needs no signature.
2. Browse → Signed: apps with signed reviews (AardAppvark Toolkit, Solflare). Open one: the signed score, the reviews, the publisher's reply and proof ("How is this proven?").
3. All apps: every dApp in the store (about 1,550) with "Update dApps".
4. To write a review: "Sign in with your wallet" (the Seeker Wallet), then Write a signed review. A review is counted only when the wallet holds a Seeker Genesis Token; the fee is the Solana network fee (under 0.0001 SOL).
5. Settings: a switch per kind of alert, the weekly reminder, the guided tour again, data export and deletion, legal links.

### Permissions, and why
| Permission | Why |
|---|---|
| INTERNET, ACCESS_NETWORK_STATE | Read the ledger (Solana RPC), the store's public catalogue and ratings. |
| QUERY_ALL_PACKAGES | Show which dApp Store apps are installed and their device facts (version, signing certificate, permissions); nothing leaves the phone. Disclosed in the terms and privacy policy. |
| PACKAGE_USAGE_STATS | Optional: lets a reviewer add how long they have used an app to their review. Granted by the user in system settings; off unless they turn it on. |
| POST_NOTIFICATIONS | Local alerts for followed apps, the weekly reminder and the Pro Monday update. |
| RECEIVE_BOOT_COMPLETED, WAKE_LOCK, FOREGROUND_SERVICE | Added by Android's WorkManager, which runs those background checks. |

(REORDER_TASKS was removed 7 Oct 2026: the wallet library had been shipping test code that declared it.)

## Screenshots (1080 × 2400, `assets/store/screenshots-1.0.0/`)
1. `1-one-seeker-one-voice.png` · 2. `2-signed-not-typed.png` · 3. `3-app-page.png` · 4. `4-reviews-and-publisher.png` · 5. `5-every-dapp.png`
Icon `assets/store/icon_512.png`, banner `assets/store/banner_1200x600.png`.

## Publishing (after the owner's OK)
1. Publisher Portal (logged in as DD4a): New app → `com.undersigned.app`, the fields above, screenshots, icon, banner → mint the App NFT.
2. `dapp-store --apk-file app/build/outputs/apk/release/app-release.apk --whats-new "<above>" --keypair ~/.config/dappstore/publisher.json --idempotency-key com.undersigned.app-vc37`
   (API key from `~/.config/dappstore/env`). Cost about 0.021 SOL from DD4a (release NFT rent + fee), measured on the fleet 19 Aug.
3. A retry after a rollback needs a NEW idempotency key (`…-vc37-r2`).
