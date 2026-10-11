# Seeding the ledger with the fleet's own apps (prepared 5 Oct 2026, nothing signed)

Why: the payments board has 0 listings and 1 proven publisher claim. Our own apps can fill both with real data. The
Undersigned treasury lists **free** (a self-transfer credits nothing; see `AppConfig.Payment`), so this costs network fees only.

Everything below needs the owner: signatures from the wallets named, and publishing proof files. Nothing here has been sent.

## 1. Publisher claims (`SRL1|clm|<package>`)
Signed by the publisher wallet. Either wallet named in the fleet manifests works: the treasury `DD4a…Wq2Q` or the
airdropper `HVXV…yDaD`. A claim is proven once the app's release carrying the `undersigned.publisher` manifest line is in
the store (the server reads the store's build), or by the website file in step 3.

## 2. Treasury listings (`SRL1|trs|<package>|<reference>`)
Signed by the treasury wallet `DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q` itself, one record per app. The code is each app's payment **reference account**,
not its 3-letter memo: 97 of the fleet's first 100 payments carried the reference and only 33 a memo (reconciled 28 Sep).
The app's importer signs several records in one approval (`TxBuilder.chunkForBatches`).

| App | Package | Memo | Listing code (reference account) |
|---|---|---|---|
| AarDappvark Toolkit | `com.adappvark.toolkit` | ART | `AZFbwj8hARyRpAVU2umxAQ2fqscS3CfwPYpYzYigTgQr` |
| BountySeeker | `com.bountyseeker.app` | BSR | `E4eFq1z8VzjM9t8kFEGzszVhoTvjy1pynU6d4WKCwMF5` |
| CallSealSkr | `com.callsealskr.app` | CER | `DWbAREJXaN2harfRCmzSNpeHweJytc7pxNoWTxqBKk93` |
| ChotkiSol | `com.chotkisol.app` | CKL | `3KsrAEVcK1tJXbhc7CoEZaKyMv79ET5mTSDf2dQ1m2yB` |
| CitizenSol | `com.citizensol.app` | CEL | `FfV1osZHopXJ1HR1xLCENDuk4g1AD4P8wgHJNSwpECNC` |
| Divine Quotes Sol | `com.divinequotes.sol` | DUL | `GpQVKQaqFFyyykfKyubp6ArCFRU3XMCQaEWpxpJvpqmE` |
| FarmSolDirect | `com.farmsoldirect.app` | FLT | `5cRDXBwg2UcQFZ5bBU3M6Gmy5cpRmoJgxTSWyhyGQ3Xz` |
| HealthSeeker | `com.healthseeker.app` | HSR | `5EgiKm5rBPi8q28yG5ahW1NGEMJVsJamwwb4ZrcVB6th` |
| JapaMalaSol | `com.japamalasol.app` | JAL | `65rm7GU3UXyiTMQR3NcwaNNHvg31KCR3jCKaZwr6hxfo` |
| RoaringTrades | `com.roaringtrades.game` | RGS | `2mktAxwjhLPoLUmo9SWi2fxKUnThhLFAXeyLgR9WRK1M` |
| ScreenRecSkr | `com.screenrecskr.app` | SRR | `H6f5UwRwSfXpAVFzoBPg7ZnQcsNqEozsSjT9RMiqGLhE` |
| Scrolls for Sol Enoch | `com.scrollsforsol.enoch` | SRH | `8GaCdUYyiHGDHz1Ubb5sgw2zak19nVcG4N2odX8jrAhQ` |
| SeekerVerify | `com.seekerverify.app` | SVY | `GpUhbtQPEEeH1QN2Z528KtZW9pZuiB3J6FGY2XPvGn3o` |
| SignalFortSkr | `com.signalfortskr.app` | SFR | `GPZA9JQFcXyMGrTQJ9hvgrVKCXkBNScG4fpyFHf7oTSA` |
| Sol Card Games | `com.solcards.app` | SDS | `2MusFeWjEWQKf2kDHCeVoBFzsC9qes9LiSB1Wz2QDYBC` |
| Sol Clock | `com.solclock.app` | SLK | `DZX8186EbVRWT7mPvhscwAsz2hdFUQkRrCW34smhMF8S` |
| Sol Mala | `com.solmala.app` | SMA | `Ajrv25po33oy33BWfbEjRqxs415anRxACY87NLwV6hTT` |
| Sol Quotes | `com.solquotes.app` | SUS | `4yEq4R6kuvvwzBYmsTF2GKmoJWWuWXB3ZA8sCfntnvUq` |
| Sol Rosary | `com.solrosary.app` | SOY | `Gx11kWR6Jyp4JYEdsKdBJkyUKLiVbqhdkvYnxYXJNkWe` |
| Sol Weather | `com.solweather.app` | SAR | `G3mJZfPUVSXSALHgMFbEr7mWPMbGfLavKj8yCC6gCeLy` |
| SolHerd | `com.solherd.app` | SHD | `E4unHnxs6Y22RM87MvmqEX8qC2wdYZfZwZLGFUCQw7sD` |
| SolScripture | `com.solscripture.app` | SIE | `D4M9qHsiprx1RNtXWKTo4TNgVscGrwxE4sanpEnRxJ71` |
| SolSow | `com.solsow.app` | SSW | `5FXZErVLUcuAhPTBsUHRoVwCibbXemx3iRiPKWABVvoS` |
| TasbihSol | `com.tasbihsol.app` | TIL | `CDfQkYG3xn3EMw7aMWgHirU8kDHWhLhLSVhqZTTPfpMm` |
| Time and Weather Sol | `com.timeandweather.sol` | TEL | `HLgTWioFsFjRRu5DtgPB5Kc2de9km1MUBzCMQCfZ1Uwh` |
| VeilScreenSkr | `com.veilscreenskr.app` | VRR | `D5cHesqz9vtTzExWzEGz7GD98Q4Gh44fahL4ZP2XNaDP` |
| VeriCapture | `com.vericapture.app` | VAE | `ERi9q1pLrorQLXcBmk3fZTEqByTZhsR5ftVDYq7kygg4` |
| WalkDivine | `com.walkdivine.app` | WIE | `CzicoVxYqHJRZrzQ7xw5XSg4KgRPyiKGdDuCHwKqthcy` |

28 published apps with a memo and a reference. Not included: apps without a payment rail, unpublished apps, and
Undersigned itself (its reference is the registry; its Pro payments already read as licences).

## 3. The website line
A listing shows on the board only when the website the STORE lists for that app serves `/.well-known/undersigned.txt`
containing the line `treasury: DD4aPDhf396NhNDxa4PBVf1u3uzCUP2QYm2dFMmJWq2Q` (and, for the website proof of a claim, the claiming wallet on a line by itself).
For a GitHub website the file is read from the repository's default branch. Check what the server reads for any app at
`/publishers?package=<package>`. Adding these files is a publish step in each app's site repository.

## 4. Open point
Whether the treasury wallet can sign from a Seeker (Mobile Wallet Adapter) is not verified here. If it cannot, the
listings can be signed with any tool that can send a memo transaction from that wallet; the record format is in `docs/SPEC.md`.
