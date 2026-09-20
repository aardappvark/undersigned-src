package com.undersigned.app.ledger

import android.util.Log
import com.undersigned.app.AppConfig
import com.undersigned.app.data.LedgerCache
import com.undersigned.app.solana.SolanaRpcClient
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.ReviewMemo

/**
 * Reads the ledger incrementally: every signature that references the registry address, newest
 * first, stopping at the newest signature already cached. Entries are immutable once confirmed, so
 * the cache only ever grows. Rows whose node-side memo summary does not even start with the protocol
 * tag are skipped without a getTransaction call.
 */
class LedgerReader(private val rpc: SolanaRpcClient = SolanaRpcClient(), private val cache: LedgerCache) {

    data class Progress(val fetched: Int, val total: Int)

    suspend fun sync(onProgress: (Progress) -> Unit = {}): Result<List<LedgerEntry>> {
        val known = cache.load()
        val meta = HashMap(cache.loadMeta())
        val newest = known.firstOrNull()?.signature
        val fresh = ArrayList<LedgerEntry>()
        var before: String? = null
        try {
            while (true) {
                val page = rpc.getSignaturesForAddress(AppConfig.Ledger.REGISTRY, before = before, until = newest).getOrThrow()
                if (page.isEmpty()) break
                // A Pro payment's only memo is the fleet code (see TxBuilder), so the protocol tag alone would
                // skip it and the buyer would never see the licence they paid for.
                val candidates = page.filter { !it.err && it.memo != null && (it.memo.contains(AppConfig.Ledger.PROTOCOL + "|") || it.memo.contains(AppConfig.Payment.PAYMENT_MEMO)) }
                for ((i, row) in candidates.withIndex()) {
                    onProgress(Progress(fresh.size + i, fresh.size + candidates.size))
                    val tx = rpc.getLedgerTx(row.signature).getOrNull() ?: continue
                    meta[row.signature] = com.undersigned.app.solana.TxMeta(tx.fee, tx.seekerWallet, tx.paidLamports, tx.paidSkr)
                    var records = 0
                    tx.memos.forEachIndexed { idx, raw ->
                        val memo = ReviewMemo.decode(raw) ?: return@forEachIndexed
                        fresh.add(LedgerEntry(ReviewMemo.recordId(row.signature, idx), tx.signer, if (tx.blockTime > 0) tx.blockTime else row.blockTime, memo))
                        records++
                    }
                    // A licence IS its payment: a transfer to the treasury of at least the floor, carrying this app's
                    // memo and reference. The term comes from the amount, the fleet way, not from anything written
                    // in the memo. Licences bought before 20 Sep 2026 carry a decoded `pro` record instead and are
                    // honoured through that branch above.
                    if (records == 0 && AppConfig.Payment.isPaidLicence(tx.paidLamports, tx.paidSkr))
                        fresh.add(LedgerEntry(row.signature, tx.signer, if (tx.blockTime > 0) tx.blockTime else row.blockTime, ReviewMemo.Pro(AppConfig.Payment.TERM_MONTHS)))
                }
                if (page.size < AppConfig.Ledger.PAGE) break
                before = page.last().signature
            }
        } catch (e: Exception) {
            Log.w(TAG, "ledger sync stopped: ${e.message?.take(100)}")
            if (fresh.isEmpty() && known.isEmpty()) return Result.failure(e)
        }
        // fresh is newest-first; keep the cache newest-first too
        val merged = (fresh + known).distinctBy { it.signature }.sortedWith(compareByDescending<LedgerEntry> { it.blockTime }.thenBy { it.signature })
        cache.save(merged); cache.saveMeta(meta)
        return Result.success(merged)
    }

    companion object { private const val TAG = "LedgerReader" }
}
