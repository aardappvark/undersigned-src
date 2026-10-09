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
    /**
     * Pays the network fee for quick reviews (owner, 10 Oct 2026): the phone's own app key signs the records, this wallet
     * only pays. A transaction whose fee payer is this wallet and that has a second signer is AUTHORED by that second
     * signer; readers attribute it so (see [author]).
     */
    const val QUICK_RELAYER = "5fnAzQBqUK3cF82G8tbmhRMPSECQwgKk2XjYRNseYyN"

    /** Who wrote a transaction's records: its fee payer, or for a relayed quick review the second signer. */
    fun author(signers: List<String>): String? = if (signers.size >= 2 && signers[0] == QUICK_RELAYER) signers[1] else signers.firstOrNull()
    /** Zero-lamport self-transfer: no value moves, the wallet only pays the network fee. */
    const val CARRIER_LAMPORTS = 0L
}
