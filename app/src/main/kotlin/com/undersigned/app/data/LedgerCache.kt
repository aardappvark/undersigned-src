package com.undersigned.app.data

import android.content.Context
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Immutable ledger entries cached on-device as JSON (they never change once confirmed). Newest first. */
class LedgerCache(context: Context) {
    private val file = File(context.filesDir, "ledger_v1.json")

    fun load(): List<LedgerEntry> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val memo = ReviewMemo.decode(o.optString("m")) ?: return@mapNotNull null
            LedgerEntry(o.optString("s"), o.optString("w"), o.optLong("t"), memo)
        }
    }.getOrDefault(emptyList())

    fun save(entries: List<LedgerEntry>) {
        val arr = JSONArray()
        entries.forEach { arr.put(JSONObject().put("s", it.signature).put("w", it.signer).put("t", it.blockTime).put("m", ReviewMemo.encode(it.memo))) }
        runCatching { file.writeText(arr.toString()) }
    }

    fun clear() { runCatching { file.delete() } }
}
