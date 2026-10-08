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
        // No head yet (a fresh cache, or one written before the head existed): list the WHOLE registry once, fetching only
        // transactions the cache does not hold. A cache built by the old reader could hold a permanent hole — vitalik's had
        // 13 of the registry's 14 transactions, 16 of 17 records (28 Sep 2026) — and starting from its newest entry, as the
        // old reader did, would never look behind it.
        // A cache written by an older DECODER dropped the record types it did not know (an old build read popcat's review +
        // install age + usage time as two records, and kept it that way): re-read every transaction once, skipping nothing.
        val redecode = cache.decoder < DECODER
        val newest = if (redecode) null else cache.head
        val haveTx = if (redecode) HashSet() else known.map { it.signature.substringBefore(':') }.toHashSet()
        var seenHead: String? = null
        var readToEnd = false; var skipped = 0
        val fresh = ArrayList<LedgerEntry>()
        var before: String? = null
        try {
            while (true) {
                val page = rpc.getSignaturesForAddress(AppConfig.Ledger.REGISTRY, before = before, until = newest).getOrThrow()
                if (page.isEmpty()) { readToEnd = true; break }
                if (seenHead == null) seenHead = page.first().signature
                // A Pro payment's only memo is the fleet code (see TxBuilder), so the protocol tag alone would
                // skip it and the buyer would never see the licence they paid for.
                val candidates = page.filter { !it.err && it.memo != null && (it.memo.contains(AppConfig.Ledger.PROTOCOL + "|") || it.memo.contains(AppConfig.Payment.PAYMENT_MEMO)) }
                for ((i, row) in candidates.withIndex()) {
                    onProgress(Progress(fresh.size + i, fresh.size + candidates.size))
                    if (newest == null && row.signature in haveTx && meta.containsKey(row.signature)) continue   // already held
                    val tx = rpc.getLedgerTx(row.signature).getOrNull() ?: run { skipped++; null } ?: continue
                    meta[row.signature] = com.undersigned.app.solana.TxMeta(tx.fee, tx.seekerWallet, tx.paidLamports, tx.paidSkr, tx.tipLamports, tx.tipSkr)
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
                if (page.size < AppConfig.Ledger.PAGE) { readToEnd = true; break }
                before = page.last().signature
            }
        } catch (e: Exception) {
            Log.w(TAG, "ledger sync stopped: ${e.message?.take(100)}")
            if (fresh.isEmpty() && known.isEmpty()) return Result.failure(e)
        }
        // fresh is newest-first; keep the cache newest-first too
        val merged = (fresh + known).distinctBy { it.signature }.sortedWith(compareByDescending<LedgerEntry> { it.blockTime }.thenBy { it.signature })
        cache.save(merged); cache.saveMeta(meta)
        // Advance only when the whole tail was read and no transaction in it failed to load; otherwise the next sync reads
        // the same tail again (already-cached records merge by id, so nothing doubles).
        if (seenHead != null && readToEnd && skipped == 0) { cache.head = seenHead; cache.decoder = DECODER }
        return Result.success(merged)
    }

    companion object {
        private const val TAG = "LedgerReader"
        /** Bump whenever ReviewMemo learns a new record type, so caches written by older builds re-read once. 3: use, tim, snt, trs, trx. */
        const val DECODER = 3
    }
}
