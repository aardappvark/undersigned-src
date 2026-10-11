# Verify this submission: build it, run the tests, read the on-chain modules

**Public source:** https://github.com/aardappvark/undersigned-src (the same history with the signing keystore removed from
every commit; anyone can clone it). The private original is https://github.com/aardappvark/Undersigned. **Audit commit:** the
tip of `main` on 12 October 2026. Everything below was measured on that commit on 12 October 2026.

## Build and test (10 minutes on a Mac or Linux box)

```bash
git clone git@github.com:aardappvark/Undersigned.git && cd Undersigned
export JAVA_HOME=/path/to/jdk-21            # Android Studio's bundled JBR works: .../Android Studio.app/Contents/jbr/Contents/Home
./gradlew :protocol:test :api:test :app:testDebugUnitTest   # 305 tests
./gradlew :app:assembleRelease                              # needs the signing key; use :app:assembleDebug for an installable debug build
./gradlew :api:installDist && api/build/install/api/bin/api verify   # recompute every score from mainnet and diff the live dataset
```

The release APK we submitted is `assets/submission/Undersigned-1.1.28-vc39.apk` (also at
https://undersigned.provenby.dev/download/Undersigned-1.1.28-vc39.apk and on the GitHub release `v1.1.27`); its
SHA-256 is beside it. It is signed with the fleet key (certificate SHA-256 `ac6932…100b`, the same key as our 28 live
store apps) and installs with Play Protect `result=ALLOW`.

## Measured size (non-test Kotlin, `find src/main -name '*.kt' | xargs wc -l`)

| Module | Lines | What it is |
|---|---|---|
| `protocol/` | 2,189 | pure Kotlin/JVM, no Android: the memo codec, ranking and weighting, threads, reputation, moderation, payments, publisher proof, abuse list. Shared byte-for-byte by the app, the server and `api verify`. |
| `api/` | 6,094 | the JVM server: chain indexer, views, REST, MCP, web pages, analytics, payments board, relayer, hooks, `verify` |
| `app/` | 14,648 | the Android app (Jetpack Compose): MWA signing, Seed Vault flow, device facts, screens, notifications, 28 locales |
| tests | 4,276 | 305 tests: protocol 125, api 106, app 74 |

## Test results (12 Oct 2026, fresh run)

`./gradlew :protocol:test :api:test :app:testDebugUnitTest` → **305 tests, 0 failures** (reports under
`*/build/test-results/`). Among them: `protocol/.../ProtocolTest.kt` (codec round trips, every limit),
`api/.../TxGoldenTest.kt` (the app and the server build identical transaction bytes for the same record),
`api/.../VerifyTest.kt` (the verifier fails on a tampered score, count or record), `protocol/.../WeightingTest.kt`,
`ThreadsTest.kt`, `AbuseTest.kt`, `api/.../McpTest.kt`. The live suite (144 checks against the public API and MCP)
is `api/src/test/.../ApiTest.kt` with `UNDERSIGNED_LIVE=1`.

## Where the Solana work is

| Concern | Files |
|---|---|
| Record format and codec (every record type, limits, tags) | `protocol/.../ReviewMemo.kt`, `LongText.kt`, `QuickTx.kt` |
| Registry address derivation (off-curve PDA on the Memo program) | `protocol/.../Registry.kt`, spec §6 |
| Building and signing transactions with Mobile Wallet Adapter / Seed Vault, batch signing | `app/.../wallet/`, `app/.../service/ReviewTxService.kt`, `app/.../solana/` |
| Reading the chain on the phone (getSignaturesForAddress, parsing, caching) | `app/.../ledger/` |
| Genesis Token (soulbound Token-2022) and stake reads | `app/.../ledger/` with `seeker-verify`; `protocol/.../StakeView.kt`; `app/src/main/assets/attestations.json` |
| Ranking, weighting, threads, standing | `protocol/.../Ranking.kt`, `Weighting.kt`, `Threads.kt`, `Reputation.kt` |
| Publisher proof against the store build or `.well-known` | `protocol/.../PublisherProof.kt`, `Apk.kt`; `api/.../Indexer.kt` |
| SKR and SOL payments: Pro, plans, listings, passes, treasuries | `protocol/.../Payments.kt`, `app/.../solana/ProLicence.kt`, `api/.../Boards.kt`, `Analytics.kt` |
| Server indexer and the independent verifier | `api/.../Indexer.kt`, `api/.../Verify.kt` (`api verify`) |
| Quick reviews relayed without a wallet | `protocol/.../QuickReviews.kt`, `api/.../Relayer.kt` |

The data model and the verification rules are in `docs/ONCHAIN.md`; the privacy model in `docs/PRIVACY_MODEL.md`;
the protocol specification in `docs/SPEC.md`; the change log in `docs/HISTORY.md`.
