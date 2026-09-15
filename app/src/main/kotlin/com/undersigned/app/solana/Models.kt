package com.undersigned.app.solana

data class Blockhash(val blockhash: String, val lastValidBlockHeight: Long)
data class SimulationResult(val err: String?, val failedInstruction: Int?, val logs: List<String>, val unitsConsumed: Long)
data class SignatureStatus(val confirmationStatus: String, val err: String?)
/** One row of getSignaturesForAddress. [memo] is the node's own summary ("[len] text"), present only when the tx carried a memo. */
data class SignatureRow(val signature: String, val blockTime: Long, val err: Boolean, val memo: String?)
/** What the reader needs from getTransaction: the fee payer (= reviewer identity) and the memo text. */
/** [seekerWallet] = the tx carries the Lighthouse guard + ComputeBudget instructions the Seeker wallet appends to everything it signs (observed 8 Sep 2026). A fingerprint, shown as such. */
/** [paidLamports] / [paidSkr]: what this transaction credited the developer's treasury (SOL wallet / SKR token account), read from balances. 0 for every ordinary record. */
data class LedgerTx(val signer: String, val blockTime: Long, val memos: List<String>, val fee: Long, val seekerWallet: Boolean, val paidLamports: Long = 0L, val paidSkr: Long = 0L)
data class TxMeta(val fee: Long, val seekerWallet: Boolean, val paidLamports: Long = 0L, val paidSkr: Long = 0L)

fun shortKey(k: String, head: Int = 4, tail: Int = 4): String = if (k.length <= head + tail + 1) k else k.take(head) + "…" + k.takeLast(tail)
