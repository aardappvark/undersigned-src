# Undersigned — dApp Store listing (1.1.1, updated 10 Oct 2026)

The source of truth for the Publisher Portal fields. Nothing here is submitted until the owner says so.

## Build
- APK: `app/build/outputs/apk/release/app-release.apk`, `com.undersigned.app`, versionCode **12**, versionName **1.1.1**
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

### What's new (1.1.1)
First release on the store: signed reviews from Seeker owners, long reviews, quick reviews (shown apart, never counted), time actually used (opt-in), a second round when a publisher fixes a problem, publisher proof and replies, every dApp in the store with a weekly update, rating bursts and device facts, the payments board with what the store's apps are paid (leaderboards, categories, what is rising), a guided tour, notifications you choose one by one, and 28 languages.

### Testing instructions (for the reviewer)
1. Open the app and accept the terms. "Browse without a wallet" shows everything that needs no signature.
2. Browse → Signed: apps with signed reviews (AardAppvark Toolkit, Solflare). Open one: the signed score, the reviews, the publisher's reply and proof ("How is this proven?").
3. All apps: every dApp in the store (about 1,543) with "Update dApps".
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
2. `dapp-store --apk-file app/build/outputs/apk/release/app-release.apk --whats-new "<above>" --keypair ~/.config/dappstore/publisher.json --idempotency-key com.undersigned.app-vc12`
   (API key from `~/.config/dappstore/env`). Cost about 0.021 SOL from DD4a (release NFT rent + fee), measured on the fleet 19 Aug.
3. A retry after a rollback needs a NEW idempotency key (`…-vc12-r2`).
