package com.undersigned.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.undersigned.app.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Resolves dApp icon URIs by package through the store's public GraphQL (the Toolkit's
 * `DAppsByPackagesSummary` shape) and persists `package → uri` across launches. Ported from the
 * AarDappvark Toolkit `DAppIconCache` + `DAppStoreGraphQLClient`:
 *  - the server answers ≤20 packages completely and silently DROPS THE HEAD above that, so batches
 *    stay at 15; a failed chunk fails the whole request rather than passing a partial answer;
 *  - "" is stored for known-missing so a package the store does not know is not re-polled every
 *    session; a network failure stores nothing so the next pass retries.
 */
class DAppIconCache(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val locale = Locale.getDefault().toLanguageTag()
    private val density = context.resources.displayMetrics.densityDpi
    private val _state = MutableStateFlow(load())
    val state: StateFlow<Map<String, String>> = _state.asStateFlow()

    fun cached(pkg: String): String? = _state.value[pkg]?.takeIf { it.isNotEmpty() }

    /** What the last lookups did, for Settings ("1,203 of 1,539 icons · last lookup: refused by the store"). */
    @Volatile var lastError: String? = null; private set
    /** After the store refuses (403/429) no lookup is sent until this time: asking again only extends the block. */
    @Volatile private var pausedUntil = 0L
    private val missingAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A package marked "no icon" is asked about again after this long: an old answer may have been wrong. */
    private fun stale(pkg: String) = _state.value[pkg] == "" && System.currentTimeMillis() - missingAt.getLong("m:$pkg", 0L) > MISSING_RETRY_MS

    /** Icon addresses the dApp list already carries: filled in where this cache has none (or "no icon"), no request sent. */
    suspend fun seed(known: Map<String, String>) {
        if (known.isEmpty()) return
        mutex.withLock {
            val merged = _state.value.toMutableMap(); var changed = false
            known.forEach { (pkg, uri) -> if (merged[pkg].isNullOrEmpty() || merged[pkg] != uri) { merged[pkg] = uri; changed = true } }
            if (changed) { _state.value = merged; persist(merged) }
        }
    }

    suspend fun resolve(packages: Collection<String>, paceMs: Long = 0L) {
        val unknown = packages.distinct().filter { it !in _state.value || stale(it) }
        if (unknown.isEmpty()) return
        for ((i, batch) in unknown.chunked(BATCH).withIndex()) {
            if (System.currentTimeMillis() < pausedUntil) return
            if (i > 0 && paceMs > 0) kotlinx.coroutines.delay(paceMs)   // the store's CDN blocks fast readers
            val resolved = fetch(batch) ?: continue
            mutex.withLock {
                val merged = _state.value.toMutableMap()
                val now = System.currentTimeMillis(); val ed = missingAt.edit()
                batch.forEach { pkg -> val uri = resolved[pkg]
                    if (uri != null) merged[pkg] = uri
                    else if (pkg !in merged || merged[pkg] == "") { merged[pkg] = ""; ed.putLong("m:$pkg", now) } }
                ed.apply()
                _state.value = merged; persist(merged)
            }
        }
    }

    private suspend fun fetch(packages: List<String>): Map<String, String>? = withContext(Dispatchers.IO) {
        val sc = JSONObject().put("locale", locale).put("platformSdk", Build.VERSION.SDK_INT).put("pixelDensity", density).put("model", Build.MODEL ?: "unknown")
        val body = JSONObject().put("operationName", "DAppsByPackagesSummary").put("variables", JSONObject().put("systemContext", sc).put("androidPackages", JSONArray(packages))).put("query", QUERY).toString()
        val c = URL(AppConfig.Store.GRAPHQL).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 15000; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode == 403 || c.responseCode == 429) { pausedUntil = System.currentTimeMillis() + 10 * 60_000L; lastError = "refused by the store (HTTP ${c.responseCode}), pausing 10 min"; return@withContext null }
            if (c.responseCode !in 200..299) { lastError = "HTTP ${c.responseCode}"; return@withContext null }
            val root = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            if (root.has("errors")) { lastError = "the store returned an error"; return@withContext null }
            lastError = null
            val arr = root.optJSONObject("data")?.optJSONArray("dAppsByAndroidPackages") ?: return@withContext null
            buildMap {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val pkg = item.optString("androidPackage").takeIf { it.isNotEmpty() } ?: continue
                    val uri = item.optJSONObject("lastRelease")?.optJSONObject("icon")?.optString("uri")?.takeIf { it.isNotEmpty() } ?: continue
                    put(pkg, uri)
                }
            }
        } catch (e: Exception) { lastError = e.javaClass.simpleName + (e.message?.let { ": " + it.take(60) } ?: ""); null } finally { c.disconnect() }
    }

    private fun load(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString(KEY, null) ?: return emptyMap())
        buildMap { o.keys().forEach { put(it, o.optString(it, "")) } }
    }.getOrDefault(emptyMap())

    private fun persist(map: Map<String, String>) = runCatching {
        val o = JSONObject(); map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    companion object {
        private const val TAG = "DAppIconCache"
        private const val PREFS = "undersigned_dapp_icons"
        private const val KEY = "icon_map_v1"
        private const val BATCH = 15
        private const val MISSING_RETRY_MS = 3L * 86_400_000L
        private const val QUERY = "query DAppsByPackagesSummary(\$systemContext: SystemContext!, \$androidPackages: [String!]!) { dAppsByAndroidPackages(systemContext: \$systemContext, androidPackages: \$androidPackages) { __typename androidPackage lastRelease(systemContext: \$systemContext) { icon { uri } } } }"
    }
}
