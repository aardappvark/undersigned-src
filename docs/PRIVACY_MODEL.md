# Privacy model: every phone fact, where it stays, and how to inspect or revoke it

The App has no account, no identifier and no server-side profile. The only things that ever leave the phone are
(a) what the owner signs, which is public on Solana by design, and (b) anonymous event counts that can be switched
off. This table is the whole list of phone-derived facts the App touches.

| Fact | Source on the phone | Computed where | Leaves the phone? | Shown where | How to inspect | How to revoke |
|---|---|---|---|---|---|---|
| Installed version code of a dApp | `PackageManager.getPackageInfo` | on-device | **No** | the app's page ("What your device can check") | the panel itself | uninstall the dApp, or nothing to revoke: never stored off-device |
| Signing certificate SHA-256 of an installed dApp, and "matches the certificate the store lists" | `PackageManager` signing info vs the store's public release metadata | on-device | **No** | same panel; the Changes tab when it moves | the panel; Changes keeps a local history | clear the App's storage |
| Dangerous permissions of an installed dApp, and changes between versions | `PackageManager.getPackageInfo(GET_PERMISSIONS)` | on-device | **No** | same panel; Changes tab; an alert if enabled | the panel | switch the Changes alert off in Settings |
| Install age of the reviewed dApp (rounded: < 1 day, 1–6 days, 1–4 weeks, 1–3 months, 3+ months) | `firstInstallTime` | on-device | **Only if the owner ticks "Your phone adds" in the composer**; then it is a memo (`SRL1\|use`) in the owner's own signed review transaction, public on-chain | on the review, labelled | the composer shows the exact value before signing; the record on Solscan | untick before signing; withdraw the review (`rvk`), which removes it from every reader |
| Usage time of the reviewed dApp (rounded buckets) | `UsageStatsManager`, needs the system Usage Access switch | on-device | **Only if ticked**, as `SRL1\|tim` in the same transaction | on the review, labelled | same | same, plus turn off Usage Access in Android Settings |
| Activity: months used of the last six, recency, reinstalls | `UsageStatsManager` | on-device | **Only if ticked**, as `SRL1\|act` in the same transaction | on the review, labelled | same | same |
| Apps installed on the phone (for "Installed" filters, batch review, "to review" list, weekly reminder) | `PackageManager` | on-device | **No** | lists in the App | the lists | the weekly reminder is off by default; clear storage |
| Wallet public address | Mobile Wallet Adapter authorize | — | the address is public by nature; the App sends it to public RPC and the store's public feed when reading that wallet's records | Settings, Me | Settings | sign out in Settings |
| Region (sanctions screen) | SIM/locale on-device | on-device | **No** | a block screen only when it applies | — | — |
| Anonymous usage counts (one event name per action, no identifier) | the App | — | **Yes**, to `undersigned-analytics.aardappvark.workers.dev`, switchable | the public counts on the fleet dashboard | Settings → usage counts | the switch in Settings |
| Store reviews read on the phone | the dApp Store's public GraphQL | on-device cache | **No** (read-only) | app pages | — | clear storage |

Rules that hold everywhere:

- **Nothing is uploaded to Undersigned's servers from the phone.** The App's own API calls (`undersigned.provenby.dev`)
  are reads for a few features (translations, summaries, the review analytics' store source); they carry no phone
  fact and no identifier beyond what any HTTPS request carries. Privacy policy §5A.
- **Signed means public.** The composer shows every fact it will attach and the exact text, behind a second consent;
  the Seeker Wallet shows the transaction before the double-tap. The record is then on Solana, readable by anyone,
  permanent except for the `rvk` withdrawal, which every reader honours.
- **Keys never touch the App.** Every signature is made in the Seed Vault through Mobile Wallet Adapter; the App
  receives only the signature.
- **No fact is ever a verdict.** Certificate and permission facts are shown as facts with the line "not a safety verdict".

Code: `app/.../device/InstalledApps.kt` and `InstallHistory.kt` (PackageManager facts, install age), `app/.../device/UsageTime.kt` (usage stats, opt-in), `protocol/.../SafetyFacts.kt` (certificate and permission facts),
`protocol/.../ReviewMemo.kt` (`Use`, `Time`, `Activity` wire), `app/.../ui/screens/ReviewScreen.kt` (the consent
and the "Your phone adds" switches). Policy: https://aardappvark.github.io/undersigned-legal/privacy.html.

## Keys and tokens on the phone

| Secret | Where it lives | Protection | What it can do |
|---|---|---|---|
| The owner's wallet keys | the Seed Vault, through the Seeker Wallet | never in the App; every signature is a Mobile Wallet Adapter request the owner approves with a double-tap | everything that counts: reviews, replies, fixes, claims, payments |
| The Mobile Wallet Adapter auth token | `SharedPreferences`, sealed with AES-256-GCM under an Android Keystore key (`device/Seal.kt`) | hardware-backed where the device has it, never exportable; excluded from backup and device transfer; cleared on sign-out | lets the wallet skip its Connect sheet; can never approve a transaction |
| The quick-review key (one random Ed25519 seed per install) | `SharedPreferences`, sealed the same way (`device/QuickKey.kt`) | as above; a seed written in the clear by a build before 1.1.28 is re-sealed on first read | signs only quick-review records that the relayer pays for; counted in no score; holds nothing |
| Pro, plans, passes | not on the phone at all: the payment on the chain is the licence | read back from the chain by every reader | — |

How opt-in works for phone facts: the composer shows a "Your phone adds" switch per fact with the exact value it will attach; nothing is attached while the switch is off; usage facts additionally need Android's Usage Access permission, which the owner grants in system settings and can revoke there.

