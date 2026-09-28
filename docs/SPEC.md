# Undersigned protocol v1 (SRL1)

## 1. A review is a transaction

```
tx {
  ix[0]  SPL Memo  (MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr)   data = UTF-8 SRL1 record (≤ 566 B)
  ix[1]  System transfer  from = reviewer, to = reviewer, lamports = 0,
         extra account = REGISTRY_REFERENCE (read-only, non-signer)     ← the fleet reference-key pattern
}
fee payer = reviewer.  Nothing but the network fee leaves the wallet.
```

The reviewer signs in the Seeker wallet through Mobile Wallet Adapter. Because the reference key is in
the transaction, `getSignaturesForAddress(REGISTRY_REFERENCE)` lists every review in chain order.
The self-transfer of 0 lamports is a carrier only; the fleet's simulate-before-sign gate (0c) runs on it
like any other transaction. **Verified 8 Sep 2026 by mainnet `simulateTransaction` (sigVerify off,
funded fleet payer): memo + 0-lamport self-transfer carrying the registry key as a 3rd read-only
account → `err: null`, 19,488 CU, memo logged, 301 bytes. 1 lamport behaves identically.**

## 2. Records

| Record | Wire | Notes |
|---|---|---|
| Review | `SRL1\|rev\|<package>\|<1-5>\|<tag,tag,tag>\|<versionCode>\|<b64url text or empty>` | 1–3 tags from the fixed vocabulary; text optional, ≤ 140 chars AND ≤ 280 UTF-8 bytes (3-byte scripts: ~93 chars), package ≤ 100 chars, behind a second consent |
| Revocation | `SRL1\|rvk\|<record id>` | Only honoured if the same wallet signed the review it names |
| Reply | `SRL1\|rep\|<record id>\|<b64url text>` | Anyone may reply; never affects ranking; publisher badge if the signer holds a verified claim |
| Claim | `SRL1\|clm\|<package>` | "I publish this app". Verified, strongest first: (1) the app's OWN signed build declares the signer — `<meta-data android:name="undersigned.publisher" android:value="<wallet>"/>` in its manifest — and the installed certificate equals the one the store lists (only the signing-key holder can ship that; checked on phones with the app installed); or (2) the website the STORE lists serves `/.well-known/undersigned.txt` containing the signer wallet (GitHub sites read raw from the default branch), cached 24 h, with the first-seen date shown. Shared hosts (social profiles, link pages, app-store links, ecosystem sites such as solana.com) never verify anyone: their owner is not the app's publisher. The store's public API does not expose which on-chain App NFT belongs to which app (probed 28 Sep 2026), so an App NFT cannot serve as proof — anyone can mint one with the same package in its metadata. |
| Pro licence | *(no record — the payment IS the licence)* | A transfer to the developer's treasury of at least the floor price, carrying the registry as its reference account and the fleet memo `UNS` as the transaction's ONLY memo. The term comes from the amount, the way the fleet tells every tier apart. Licences bought before 20 Sep 2026 carry a legacy `SRL1\|pro\|<months>` record and are still honoured. Pro unlocks tools and touches no score, weight or rank. |

| Install age | `SRL1\|use\|<package>\|<d0\|d1\|w1\|m1\|m3>` | Rides as a second memo in the REVIEW's own transaction: how long the reviewed app had been installed on the reviewer's phone, rounded (under a day, 1–6 days, 1–4 weeks, 1–3 months, 3+ months). Attaches only to a review of the same package, same transaction, same signer. The device's report, never proof; read by no score or weight. Older readers ignore it and still read the review. Worst case (largest review + this) simulated on mainnet 27 Sep 2026: `err: null`, 234,619 CU. |
| Usage time | `SRL1\|tim\|<package>\|<t0\|t1\|t2\|t3\|t4>` | Rides in the review's own transaction like install age: the app's foreground time on the reviewer's phone from Android's usage-access count, rounded (under 10 min, 10–60 min, 1–5 h, 5–20 h, 20+ h). Opt-in (usage access is a system-settings switch). Same attachment rule; read by no score. First live record 28 Sep 2026 (`4KYcFQFc…`, review + `use` + `tim` in one transaction). The install age it rides beside is the TOTAL time on the phone across reinstalls, from the app's own install log. |
| Store note | `SRL1\|snt\|<package>\|<store review uuid>\|<b64url text>` | A publisher's signed note on a dApp Store review of their own app. Shown only when the signer is a verified publisher of `<package>`; newest per review wins; withdrawn with `rvk`. Text limits as a review. |
| Treasury | `SRL1\|trs\|<package>\|<code>` | Signed BY a treasury wallet: payments to it that carry `<code>` are for `<package>`. `<code>` is a memo code (2–31 of `A-Za-z0-9_-`; the transfer's memo starts with it) OR a reference account (a base58 address the payment transaction includes). Reference mode reads that account's own history, memo or not: the fleet ledger attributed 97 of 100 payments by reference and only 33 carried a memo (reconciled 28 Sep 2026; SeekerVerify's v1 reference lists exactly its 63 memo-less payments). Payments from wallets the publisher's proof file names are counted apart. Listed on the payments board only when the store-listed website's `/.well-known/undersigned.txt` also carries `treasury: <address>` (a bare address line does not count). Read: the wallet (SOL) and its SKR and USDC associated token accounts — token payments name the token account, not the wallet. |
| Treasury withdrawn | `SRL1\|trx\|<package>` | Latest of `trs`/`trx` per (treasury, package) wins; deleting the website line also withdraws it. |

**Reversibility.** `unf` withdraws my flag, `unv` my helpful vote (latest per wallet wins); `clr` is a "looks genuine" vote that offsets flags; `dsp` is the author's dispute (doubles the bar). Hiding is computed from NET weight = flags − clears, author excluded, and flags from a verified publisher of the reviewed app are ignored. Nothing is ever deleted; every state is recomputable from the chain.

**Record ids.** One record per memo instruction. The first memo of a transaction is addressed by the transaction signature; the n-th by `<signature>:<n>` (imports pack several records into one signature).

**Transaction fingerprint.** A transaction that carries both a Lighthouse assertion (`L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95`) and ComputeBudget instructions was signed by the Seeker wallet (observed on every record it produced, 8 Sep 2026). Readers show it as a "Seeker wallet" mark. It is a fingerprint, not a proof; the Genesis Token gate is the proof of a Seeker-provisioned key.

Codec, limits and the 24-tag vocabulary: `protocol/.../ReviewMemo.kt`. Readers reject anything that
does not parse exactly; there is no partial acceptance and no "unknown field" tolerance in v1.

## 3. Identity and weight are read from the chain, never from the payload

- **Who:** the transaction's fee-payer/signer. A payload cannot claim to be someone else.
- **One voice:** the signer must hold a Seeker Genesis Token (`seeker-verify` 1.1.0 read path, as HydroSol /
  Sol Mala). No token → weight 0 → the review is stored on-chain like any other but never counted.
- **Weight:** `min(3, 1 + ln(1 + stakedSKR/1000))` — staked SKR read the way SeekerVerify's
  `Season1RpcClient` reads it (getProgramAccounts, dataSize 64, memcmp claimant @8). Capped so a whale
  is at most 3 ordinary Seekers. The app stakes nothing, unstakes nothing, and never tells anyone to.
- **Dedup:** newest counted review per wallet per package wins. Bayesian prior 3.0 with strength 5.

## 3a. Attested devices — the governed list

"Counted" means the signer holds a credential on the **attestation list** (`app/src/main/assets/attestations.json`):
soulbound Token-2022 collections identified by mint authority (+ metadata). Today the list has one entry, the Seeker
Genesis Token. The list carries a `remoteUrl` slot so Solana Mobile — or an OEM programme — can publish an updated
list and become the root of trust for every SMS phone's reviews; adding a device credential is a JSON entry, not an
app release. Readers record which attestation matched (`SignerFacts.attestation`) so the UI can name it.

## 4. Reader

```
signatures = getSignaturesForAddress(REGISTRY_REFERENCE, until = last seen)     // incremental
for each: getTransaction(sig, jsonParsed) → memo text → ReviewMemo.decode
facts(signer) cached per session: genesis-token lookup + staked-SKR lookup
Ranking.score(entries, facts) → per-package AppScore   (deterministic; any reader agrees)
```

RPC budget: N signatures + N transactions + 2 lookups per distinct signer. Cache entries on-device
(they are immutable) so a warm start fetches only the tail. Helius keys 1 and 2 are exhausted
(memory note) — wire key 3 and the public fallbacks, read-only methods only.

## 5. Trust panel (on-device facts)

`SafetyFacts` from PackageManager for installed apps: signer cert SHA-256, dangerous permissions,
delta vs the device's last snapshot. The panel states facts and deltas only. Words that never appear:
"safe", "unsafe", "scam" (as a verdict), "verified by us".

## 6. Registry address

`findProgramAddress(["review-ledger","v1"], MEMO_PROGRAM)` = **`BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs`**,
bump **255** (derived 8 Sep 2026, pure-Python ed25519 off-curve check; the Memo program id itself
tested on-curve as a sanity control). Off-curve → nobody holds a key → the ledger cannot be captured.
The Android module re-derives it at startup and refuses to post on mismatch.
