package com.undersigned.protocol

/**
 * Where the ledger lives on-chain. There is no server: a review is a transaction the reviewer
 * signs in their own wallet, made of (1) an SPL Memo carrying the [ReviewMemo] payload and
 * (2) a zero-lamport System transfer from the reviewer to themselves that lists
 * [REGISTRY_REFERENCE] as an extra read-only account — the fleet "reference key" pattern
 * (RentBack Sol `ReclaimTxBuilder.systemTransfer`). Because the reference key appears in the
 * transaction, `getSignaturesForAddress(REGISTRY_REFERENCE)` enumerates every review ever
 * posted, in order, from any RPC, and `getTransaction` yields the memo. Nobody needs to run
 * anything for the ledger to exist, and nobody — including the developer — can edit or delete
 * an entry; readers honour [ReviewMemo.Revocation] instead.
 *
 * The reference address must be one NO ONE controls, so the ledger cannot be captured:
 * derive a PDA once off-device (e.g. `findProgramAddress(["seeker-review-ledger", "v1"],
 * MEMO_PROGRAM)`), paste the base58 here, and record the seeds in docs/SPEC.md. The
 * placeholder below is deliberately invalid so a build cannot ship without that step.
 */
object Registry {
    const val PROTOCOL = "SRL1"
    /**
     * findProgramAddress(["review-ledger", "v1"], MEMO_PROGRAM) → bump 255. Derived 8 Sep 2026 with a
     * pure-Python ed25519 off-curve check; the Android module MUST re-derive this at startup with its
     * own ed25519 library and refuse to post if the two disagree (belt and braces on the one constant
     * the whole ledger hangs on). Nobody holds a key for this address.
     */
    const val REGISTRY_REFERENCE = "BtbL6dWhavkwZABZ2pGa7MrbTsshz6rsiw4DQQuE79Fs"
    const val REGISTRY_BUMP = 255
    val REGISTRY_SEEDS = listOf("review-ledger", "v1")
    const val MEMO_PROGRAM = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
    /** Zero-lamport self-transfer: no value moves, the wallet only pays the network fee. */
    const val CARRIER_LAMPORTS = 0L
}
