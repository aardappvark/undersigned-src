package com.undersigned.app.data

import android.content.Context
import android.util.Log
import com.undersigned.app.solana.TxMeta
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Immutable ledger entries cached on-device as JSON (they never change once confirmed). Newest first. */
class LedgerCache(context: Context) {
    private val file = File(context.filesDir, "ledger_v1.json")
    private val metaFile = File(context.filesDir, "ledger_meta_v1.json")
    private val headFile = File(context.filesDir, "ledger_head_v1.txt")
    private val decoderFile = File(context.filesDir, "ledger_decoder_v1.txt")
    /** The decoder version that wrote this cache (see LedgerReader.DECODER); 0 when unknown. */
    var decoder: Int
        get() = runCatching { decoderFile.readText().trim().toInt() }.getOrDefault(0)
        set(v) { runCatching { decoderFile.writeText(v.toString()) } }
    /**
     * The newest registry signature the READER has listed, the `until` of the next tail read. Not the cache's first entry:
     * a record the app inserts right after signing sits at the top before the reader has seen anything older, and using
     * it as `until` skipped every record confirmed between the last read and that transaction (found in review, 28 Sep
     * 2026). A batched record's id ("sig:1") is not a signature either.
     */
    var head: String?
        get() = runCatching { headFile.readText().trim().takeIf { it.isNotEmpty() } }.getOrNull()
        set(v) { runCatching { if (v == null) headFile.delete() else headFile.writeText(v) } }

    fun loadMeta(): Map<String, TxMeta> = runCatching {
        if (!metaFile.exists()) return emptyMap()
        val o = JSONObject(metaFile.readText()); o.keys().asSequence().associateWith { k -> val m = o.getJSONObject(k); TxMeta(m.optLong("f"), m.optBoolean("s"), m.optLong("p"), m.optLong("k"), m.optLong("tl"), m.optLong("ts")) }
    }.getOrDefault(emptyMap())
    fun saveMeta(meta: Map<String, TxMeta>) { runCatching { val o = JSONObject(); meta.forEach { (k, v) -> o.put(k, JSONObject().put("f", v.fee).put("s", v.seekerWallet).put("p", v.paidLamports).put("k", v.paidSkr).put("tl", v.tipLamports).put("ts", v.tipSkr)) }; metaFile.writeText(o.toString()) } }

    fun load(): List<LedgerEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val memo = ReviewMemo.decode(o.optString("m")) ?: return@mapNotNull null
            LedgerEntry(o.optString("s"), o.optString("w"), o.optLong("t"), memo)
        }
    }.getOrDefault(emptyList())

    /**
     * Encoding happens INSIDE the guard: a single record the codec cannot write must cost that record, not the
     * process. It used to sit outside, so one entry threw on Dispatchers.IO and killed the app on launch.
     */
    fun save(entries: List<LedgerEntry>) {
        val arr = JSONArray()
        entries.forEach { e ->
            runCatching { ReviewMemo.encode(e.memo) }
                .onSuccess { wire -> arr.put(JSONObject().put("s", e.signature).put("w", e.signer).put("t", e.blockTime).put("m", wire)) }
                .onFailure { Log.w("LedgerCache", "skipping a record the codec cannot write: ${it.message?.take(80)}") }
        }
        runCatching { file.writeText(arr.toString()) }
    }

    fun clear() { runCatching { file.delete(); metaFile.delete(); headFile.delete(); decoderFile.delete() } }
}
