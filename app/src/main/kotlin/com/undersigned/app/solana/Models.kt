package com.undersigned.app.solana

data class Blockhash(val blockhash: String, val lastValidBlockHeight: Long)
data class SimulationResult(val err: String?, val failedInstruction: Int?, val logs: List<String>, val unitsConsumed: Long)
data class SignatureStatus(val confirmationStatus: String, val err: String?)
/** One row of getSignaturesForAddress. [memo] is the node's own summary ("[len] text"), present only when the tx carried a memo. */
data class SignatureRow(val signature: String, val blockTime: Long, val err: Boolean, val memo: String?)
/** What the reader needs from getTransaction: the fee payer (= reviewer identity) and the memo text. */
data class LedgerTx(val signer: String, val blockTime: Long, val memos: List<String>)

fun shortKey(k: String, head: Int = 4, tail: Int = 4): String = if (k.length <= head + tail + 1) k else k.take(head) + "…" + k.takeLast(tail)
