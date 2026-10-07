package com.undersigned.app.data

import android.content.Context
import android.util.Log
import com.undersigned.app.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class CatalogueApp(val packageName: String, val name: String, val updated: String, val category: String?)

/**
 * The store's public catalogue: a bundled snapshot for instant first paint, refreshed in the
 * background through the same GraphQL feed the AarDappvark Toolkit has used in production since its
 * store approval (category enumeration, 20 per page). READ ONLY — this app never writes to the store.
 */
class CatalogueRepository(private val context: Context) {
    private val file = File(context.filesDir, "catalogue.json")

    fun load(): List<CatalogueApp> {
        val text = runCatching { if (file.exists()) file.readText() else null }.getOrNull()
            ?: runCatching { context.assets.open(AppConfig.Store.SNAPSHOT_ASSET).bufferedReader().use { it.readText() } }.getOrNull()
            ?: return emptyList()
        return parse(text)
    }

    private fun parse(text: String): List<CatalogueApp> = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val p = o.optString("p").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            CatalogueApp(p, o.optString("n", p), o.optString("u", ""), o.optString("c", null).takeIf { !it.isNullOrEmpty() && it != "null" })
        }
    }.getOrDefault(emptyList())

    fun isStale(): Boolean = System.currentTimeMillis() - UserPreferences.catalogueRefreshedAt > AppConfig.Store.REFRESH_INTERVAL_MS

    /**
     * Full refresh; returns the merged list or null on failure (the caller keeps what it has).
     *
     * UNION, never replace (7 Oct 2026, owner: "it only has a small selection"). The category walk cannot see a listing
     * that sits in no category: measured that day, 15 live apps already bundled and 37 more found only through the store's
     * public portal records on Arweave. So: walk the categories, add every app this phone already knew, discover new
     * packages from the portal records, then ask the store which of the extra ones are live. An app is dropped only when the
     * store answered and did not list it.
     */
    suspend fun refresh(onProgress: (String) -> Unit = {}): List<CatalogueApp>? = withContext(Dispatchers.IO) {
        try {
            // What this phone knew plus the list this version shipped with: an older replace-style refresh may have dropped
            // category-less apps from the saved list, and the shipped list still names them.
            val bundled = runCatching { parse(context.assets.open(AppConfig.Store.SNAPSHOT_ASSET).bufferedReader().use { it.readText() }) }.getOrDefault(emptyList())
            val known = (bundled + load()).associateBy { it.packageName }
            val sc = JSONObject().put("locale", "en-US").put("platformSdk", 35).put("pixelDensity", 420).put("model", "Seeker")
            val cats = post("TopCategories", Q_CATEGORIES, JSONObject().put("systemContext", sc))
                .getJSONObject("topCategories").getJSONArray("edges")
            val out = LinkedHashMap<String, CatalogueApp>()
            for (c in 0 until cats.length()) {
                val node = cats.getJSONObject(c).getJSONObject("node")
                val id = node.getString("id"); val name = node.getString("name")
                val shelf = name in CURATED_SHELVES
                onProgress("${c + 1}/${cats.length()}")
                var after: String? = null; var pages = 0
                while (pages++ < 200) {
                    val vars = JSONObject().put("systemContext", sc).put("categoryId", id).put("first", AppConfig.Store.PAGE_SIZE)
                    if (after != null) vars.put("after", after)
                    val d = post("DAppsCategory", Q_CATEGORY_DAPPS, vars).getJSONObject("dAppsCategory").getJSONObject("dApps")
                    val edges = d.getJSONArray("edges")
                    for (e in 0 until edges.length()) {
                        val n = edges.getJSONObject(e).getJSONObject("node")
                        val pkg = n.optString("androidPackage").takeIf { it.isNotEmpty() } ?: continue
                        if (EXCLUDED.any { pkg.startsWith(it) }) continue
                        val rel = n.optJSONObject("lastRelease")
                        val existing = out[pkg]
                        out[pkg] = CatalogueApp(pkg, rel?.optString("displayName")?.takeIf { it.isNotEmpty() } ?: existing?.name ?: pkg,
                            rel?.optString("updatedOn")?.take(10) ?: existing?.updated ?: "",
                            // First REAL category wins. A curated shelf ("Top Picks") is listed first by the store, so it
                            // used to become the label of every app on it; a shelf only fills in if nothing better exists.
                            existing?.category?.takeUnless { it in CURATED_SHELVES } ?: if (shelf) existing?.category ?: name else name)
                    }
                    val pi = d.getJSONObject("pageInfo")
                    if (!pi.optBoolean("hasNextPage", false)) break
                    // optString(..., null) yields the literal "null" for a JSON null cursor; isNull is the safe gate.
                    after = (if (pi.isNull("endCursor")) null else pi.optString("endCursor").takeIf { it.isNotBlank() }) ?: break
                }
            }
            if (out.size < 100) throw IllegalStateException("catalogue crawl returned only ${out.size} apps")   // truncated storefront → keep the old data
            onProgress("+")
            val extra = ((known.keys - out.keys) + discover().filter { it !in out && it !in known }).filter { p -> EXCLUDED.none { p.startsWith(it) } }
            val live = live(extra)
            if (live == null) known.values.forEach { if (it.packageName !in out) out[it.packageName] = it }   // could not ask: keep what we had
            else extra.forEach { p -> live[p]?.let { l -> out[p] = CatalogueApp(p, l.name, l.updated.ifEmpty { known[p]?.updated ?: "" }, known[p]?.category) } }
            val arr = JSONArray(); out.values.forEach { arr.put(JSONObject().put("p", it.packageName).put("n", it.name).put("u", it.updated).put("c", it.category)) }
            file.writeText(arr.toString())
            UserPreferences.catalogueRefreshedAt = System.currentTimeMillis()
            out.values.toList()
        } catch (e: Exception) { Log.w(TAG, "catalogue refresh failed: ${e.message?.take(100)}"); null }
    }

    /** Packages the store's publisher portal has recorded on Arweave (public, read-only); empty if Arweave cannot be read. */
    private fun discover(): Set<String> = runCatching {
        val found = HashSet<String>(); var after: String? = null
        for (page in 0 until 80) {
            val cursor = after?.let { ", after: \"$it\"" } ?: ""
            val q = "query { transactions(first: 100$cursor, tags: [{name: \"App-Name\", values: [\"solana-mobile-dapp-publisher-portal\"]}, {name: \"Type\", values: [\"app-nft-metadata\", \"release-nft-metadata\"]}]) { pageInfo { hasNextPage } edges { cursor node { tags { name value } } } } }"
            val t = postTo(AppConfig.Store.ARWEAVE_GRAPHQL, JSONObject().put("query", q).toString()).getJSONObject("transactions")
            val edges = t.getJSONArray("edges")
            for (i in 0 until edges.length()) { val tags = edges.getJSONObject(i).getJSONObject("node").getJSONArray("tags")
                for (j in 0 until tags.length()) tags.getJSONObject(j).takeIf { it.optString("name") == "Android-Package" }?.optString("value")?.takeIf { it.isNotEmpty() }?.let(found::add) }
            if (!t.getJSONObject("pageInfo").optBoolean("hasNextPage") || edges.length() == 0) break
            after = edges.getJSONObject(edges.length() - 1).getString("cursor")
        }
        found
    }.getOrElse { Log.w(TAG, "discovery skipped: ${it.message?.take(80)}"); emptySet() }

    private class Live(val name: String, val updated: String)

    /** Which of [pkgs] the store lists now (name, last update), 20 per request; null if any request failed. */
    private suspend fun live(pkgs: Collection<String>): Map<String, Live>? = runCatching {
        val sc = JSONObject().put("locale", "en-US").put("platformSdk", 35).put("pixelDensity", 420).put("model", "Seeker")
        val out = HashMap<String, Live>()
        pkgs.chunked(20).forEachIndexed { i, chunk ->
            if (i > 0) kotlinx.coroutines.delay(1500)   // paced: the store's CDN blocks fast readers
            val arr = post("DAppsByPackages", Q_BATCH, JSONObject().put("systemContext", sc).put("androidPackages", JSONArray(chunk))).getJSONArray("dAppsByAndroidPackages")
            for (k in 0 until arr.length()) { val a = arr.getJSONObject(k); val rel = a.optJSONObject("lastRelease")
                out[a.getString("androidPackage")] = Live(rel?.optString("displayName")?.takeIf { it.isNotEmpty() } ?: a.getString("androidPackage"), rel?.optString("updatedOn")?.take(10) ?: "") }
        }
        out
    }.getOrElse { Log.w(TAG, "liveness check failed: ${it.message?.take(80)}"); null }

    private fun postTo(url: String, body: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 10000; c.readTimeout = 20000; c.doOutput = true
            OutputStreamWriter(c.outputStream).use { it.write(body); it.flush() }
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return JSONObject(BufferedReader(InputStreamReader(c.inputStream)).use { it.readText() }).getJSONObject("data")
        } finally { c.disconnect() }
    }

    private fun post(op: String, query: String, variables: JSONObject): JSONObject {
        val body = JSONObject().put("operationName", op).put("variables", variables).put("query", query).toString()
        val c = URL(AppConfig.Store.GRAPHQL).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 10000; c.readTimeout = 20000; c.doOutput = true
            OutputStreamWriter(c.outputStream).use { it.write(body); it.flush() }
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val resp = JSONObject(BufferedReader(InputStreamReader(c.inputStream)).use { it.readText() })
            if (resp.has("errors")) throw IllegalStateException(resp.getJSONArray("errors").toString().take(120))
            return resp.getJSONObject("data")
        } finally { c.disconnect() }
    }

    companion object {
        private const val TAG = "Catalogue"
        /**
         * Store categories that are curated shelves, not categories. Since the store reshuffle (v1.59.0, 22 Sep 2026) its
         * category list opens with "Top Picks", and first-seen-wins labelled every app on that shelf "Top Picks".
         */
        val CURATED_SHELVES = setOf("Top Picks")
        private val EXCLUDED = listOf("com.google.", "com.android.", "com.mediatek.", "com.samsung.", "com.qualcomm.", "com.sec.", "com.friendly.", "org.chromium.")
        private const val Q_CATEGORIES = "query TopCategories(\$systemContext: SystemContext!) { topCategories(systemContext: \$systemContext, first: 20) { edges { node { id name } } } }"
        private const val Q_BATCH = "query DAppsByPackages(\$systemContext: SystemContext!, \$androidPackages: [String!]!) { dAppsByAndroidPackages(systemContext: \$systemContext, androidPackages: \$androidPackages) { androidPackage lastRelease(systemContext: \$systemContext) { displayName updatedOn } } }"
        private const val Q_CATEGORY_DAPPS = "query DAppsCategory(\$systemContext: SystemContext!, \$categoryId: ID!, \$first: Int, \$after: String) { dAppsCategory { dApps(systemContext: \$systemContext, categoryId: \$categoryId, first: \$first, after: \$after) { edges { node { androidPackage lastRelease(systemContext: \$systemContext) { displayName updatedOn installFile { size } } } } pageInfo { hasNextPage endCursor } } } }"
    }
}
