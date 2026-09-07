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
| Revocation | `SRL1\|rvk\|<signature>` | Only honoured if the same wallet signed the review it names |

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
