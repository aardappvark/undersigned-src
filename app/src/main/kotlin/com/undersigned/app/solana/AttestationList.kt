package com.undersigned.app.solana

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One accepted attested-device credential: a soulbound Token-2022 collection identified by mint authority (+ metadata address). */
data class Attestation(val id: String, val label: String, val mintAuthority: String, val metadata: String?, val source: String = BUNDLED) {
    companion object { const val BUNDLED = "bundled"; const val REMOTE = "remote" }
}

/**
 * The governed list of attestations that make a wallet "one voice".
 *
 * A copy ships with the app. If that copy names a `remoteUrl` — a URL Solana Mobile or an OEM programme
 * controls — the app refreshes from it, so adding a device credential for a new SMS phone is a JSON entry
 * on their side rather than a release on ours. The remote copy is cached on-device and used when offline.
 *
 * Three rules make trusting a remote list defensible, because this list decides whose voice counts:
 *
 *  1. **HTTPS only**, and only the URL the shipped list names. There is no way to point it elsewhere at runtime.
 *  2. **The bundled entries can never be removed or altered by a remote list.** A remote copy may add
 *     credentials and may not delete the Seeker Genesis gate, so a hostile or broken endpoint cannot
 *     silently disable attestation and turn every wallet into a counted voice.
 *  3. **Every entry is validated** before use, and a list that fails validation is discarded whole rather
 *     than partially applied.
 *
 * Where an entry came from is recorded on it and shown in Settings, so a user can see whether a
 * credential is one the app shipped with or one the list's owner added later.
 */
object AttestationList {
    private const val TAG = "AttestationList"
    private const val CACHE = "attestations_remote.json"
    private const val TTL_MS = 24L * 3600L * 1000L
    private const val MAX_BYTES = 64 * 1024
    private val BASE58 = Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$")

    @Volatile private var cached: List<Attestation>? = null
    @Volatile private var remoteUrl: String? = null

    /** Bundled entries, plus any valid cached remote ones. Synchronous and safe to call on the main thread. */
    fun load(context: Context): List<Attestation> {
        cached?.let { return it }
        val bundled = parse(readBundled(context), Attestation.BUNDLED)
        val remote = parse(readCache(context), Attestation.REMOTE)
        val merged = merge(bundled, remote)
        cached = merged
        return merged
    }

    fun label(context: Context, id: String?): String? = id?.let { i -> load(context).firstOrNull { it.id == i }?.label }

    /** True when the shipped list names a remote source at all. */
    fun isGoverned(context: Context): Boolean { load(context); return remoteUrl != null }

    /**
     * Refresh from the remote list if one is named and the cache is stale. Returns true when the effective
     * list changed. Never throws; a failed refresh leaves the previous list in place.
     */
    suspend fun refresh(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        load(context)
        val url = remoteUrl ?: return@withContext false
        val cacheFile = File(context.filesDir, CACHE)
        if (!force && cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < TTL_MS) return@withContext false
        val body = fetch(url) ?: return@withContext false
        val fresh = parse(body, Attestation.REMOTE)
        if (fresh.isEmpty()) { Log.w(TAG, "remote list had no valid entries; keeping the current list"); return@withContext false }
        val before = cached
        runCatching { cacheFile.writeText(body) }
        val merged = merge(parse(readBundled(context), Attestation.BUNDLED), fresh)
        cached = merged
        val changed = before?.map { it.id }?.toSet() != merged.map { it.id }.toSet()
        Log.i(TAG, "remote attestation list refreshed: ${merged.size} entries (${fresh.size} remote), changed=$changed")
        changed
    }

    /** Bundled always wins on a shared id, and bundled entries are never dropped. */
    private fun merge(bundled: List<Attestation>, remote: List<Attestation>): List<Attestation> {
        val ids = bundled.map { it.id }.toSet()
        return bundled + remote.filter { it.id !in ids }
    }

    private fun readBundled(context: Context): String? =
        runCatching { context.assets.open("attestations.json").bufferedReader().use { it.readText() } }
            .getOrElse { Log.w(TAG, "bundled list unreadable: ${it.message}"); null }

    private fun readCache(context: Context): String? =
        runCatching { File(context.filesDir, CACHE).takeIf { it.exists() }?.readText() }.getOrNull()

    /** Parses a list document. Records `remoteUrl` from the BUNDLED copy only, so a remote list cannot redirect us. */
    private fun parse(body: String?, source: String): List<Attestation> {
        body ?: return emptyList()
        return runCatching {
            val o = JSONObject(body)
            if (source == Attestation.BUNDLED) {
                remoteUrl = o.takeIf { !it.isNull("remoteUrl") }?.optString("remoteUrl")
                    ?.takeIf { it.startsWith("https://", ignoreCase = true) }
            }
            val arr: JSONArray = o.optJSONArray("entries") ?: return emptyList()
            val out = ArrayList<Attestation>(arr.length())
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val id = e.optString("id").takeIf { it.isNotBlank() && it.length <= 64 } ?: return emptyList()
                val label = e.optString("label").takeIf { it.isNotBlank() && it.length <= 64 } ?: return emptyList()
                val authority = e.optString("mintAuthority").takeIf { BASE58.matches(it) } ?: return emptyList()
                val metadata = e.takeIf { !it.isNull("metadata") }?.optString("metadata")?.takeIf { BASE58.matches(it) }
                out.add(Attestation(id, label, authority, metadata, source))
            }
            if (out.map { it.id }.toSet().size != out.size) return emptyList()   // duplicate ids: reject the document
            out
            // The message carries the offending document, so cap it: a rejected list is untrusted input
            // and must never be spilled wholesale into the system log.
        }.getOrElse { Log.w(TAG, "$source list rejected: ${it.message?.take(160)}"); emptyList() }
    }

    /** Reads at most [MAX_BYTES] so a hostile or misconfigured endpoint cannot stream unbounded data at us. */
    private fun fetch(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.instanceFollowRedirects = false
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) {
                Log.w(TAG, "remote list HTTP ${c.responseCode}")
                return@runCatching null
            }
            // Read one char past the cap so an oversized document is REJECTED rather than parsed
            // truncated: a truncated JSON document fails with a confusing "unterminated string".
            val buf = CharArray(8 * 1024)
            val sb = StringBuilder()
            c.inputStream.bufferedReader().use { r ->
                while (sb.length <= MAX_BYTES) {
                    val n = r.read(buf, 0, buf.size)
                    if (n <= 0) break
                    sb.appendRange(buf, 0, n)
                }
            }
            if (sb.length > MAX_BYTES) {
                Log.w(TAG, "remote list is larger than ${MAX_BYTES / 1024} KB; rejected")
                return@runCatching null
            }
            sb.toString().takeIf { it.isNotBlank() }
        } finally {
            c.disconnect()
        }
    }.getOrElse { Log.w(TAG, "remote list fetch failed: ${it.message?.take(80)}"); null }
}
