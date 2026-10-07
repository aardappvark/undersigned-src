package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CatalogueApp(val packageName: String, val name: String, val updated: String, val category: String?)
/** [apkUri]: the store's public download of its current build (read for the publisher proof, never redistributed). */
data class StoreDetails(val versionCode: Long, val certFingerprint: String?, val publisherName: String?, val website: String?, val supportEmail: String?, val newInVersion: String?, val apkUri: String? = null)

/**
 * READ-ONLY client for the dApp Store's public GraphQL, through the same operations the store app issues (ports of the
 * app's CatalogueRepository and StoreReviewsClient). Every request waits [Config.STORE_PACE_MS] after the previous one:
 * the store's CDN blocks a whole network for minutes after a burst (measured Sep 2026). Nothing is written to the store.
 */
class Store(private val paceMs: Long = Config.STORE_PACE_MS) {
    private val sc = JSONObject().put("locale", "en-US").put("platformSdk", 35).put("pixelDensity", 420).put("model", "Seeker")
    private var lastAt = 0L

    /** Full crawl by category, 20 per page. Null on failure or a truncated storefront (fewer than 100 apps). */
    fun catalogue(): List<CatalogueApp>? = runCatching {
        val cats = post("TopCategories", Q_CATEGORIES, JSONObject().put("systemContext", sc)).getJSONObject("topCategories").getJSONArray("edges")
        val out = LinkedHashMap<String, CatalogueApp>()
        for (c in 0 until cats.length()) {
            val node = cats.getJSONObject(c).getJSONObject("node"); val id = node.getString("id"); val name = node.getString("name")
            val shelf = name in CURATED_SHELVES
            var after: String? = null; var pages = 0
            while (pages++ < 200) {
                val vars = JSONObject().put("systemContext", sc).put("categoryId", id).put("first", 20); after?.let { vars.put("after", it) }
                val d = post("DAppsCategory", Q_CATEGORY_DAPPS, vars).getJSONObject("dAppsCategory").getJSONObject("dApps")
                val edges = d.getJSONArray("edges")
                for (e in 0 until edges.length()) {
                    val n = edges.getJSONObject(e).getJSONObject("node")
                    val pkg = n.optString("androidPackage").takeIf { it.isNotEmpty() } ?: continue
                    if (EXCLUDED.any { pkg.startsWith(it) }) continue
                    val rel = n.optJSONObject("lastRelease"); val existing = out[pkg]
                    out[pkg] = CatalogueApp(pkg, rel?.optString("displayName")?.takeIf { it.isNotEmpty() } ?: existing?.name ?: pkg,
                        rel?.optString("updatedOn")?.take(10) ?: existing?.updated ?: "",
                        existing?.category?.takeUnless { it in CURATED_SHELVES } ?: if (shelf) existing?.category ?: name else name)
                }
                val pi = d.getJSONObject("pageInfo")
                if (!pi.optBoolean("hasNextPage", false)) break
                after = (if (pi.isNull("endCursor")) null else pi.optString("endCursor").takeIf { it.isNotBlank() }) ?: break
            }
        }
        check(out.size >= 100) { "catalogue crawl returned only ${out.size} apps" }
        out.values.toList()
    }.onFailure { log("catalogue failed: ${it.message?.take(100)}") }.getOrNull()

    /** Packages the store's publisher portal recorded on Arweave: finds listings that sit in no category. Empty on failure. */
    fun discover(): Set<String> = runCatching {
        val found = HashSet<String>(); var after: String? = null
        for (page in 0 until 80) {
            val cursor = after?.let { ", after: \"$it\"" } ?: ""
            val q = "query { transactions(first: 100$cursor, tags: [{name: \"App-Name\", values: [\"solana-mobile-dapp-publisher-portal\"]}, {name: \"Type\", values: [\"app-nft-metadata\", \"release-nft-metadata\"]}]) { pageInfo { hasNextPage } edges { cursor node { tags { name value } } } } }"
            val c = java.net.URI("https://arweave.net/graphql").toURL().openConnection() as java.net.HttpURLConnection
            val t = try {
                c.requestMethod = "POST"; c.setRequestProperty("Content-Type", "application/json"); c.connectTimeout = 10000; c.readTimeout = 20000; c.doOutput = true
                c.outputStream.use { it.write(JSONObject().put("query", q).toString().toByteArray()) }
                JSONObject(c.inputStream.bufferedReader().use { it.readText() }).getJSONObject("data").getJSONObject("transactions")
            } finally { c.disconnect() }
            val edges = t.getJSONArray("edges")
            for (i in 0 until edges.length()) { val tags = edges.getJSONObject(i).getJSONObject("node").getJSONArray("tags")
                for (j in 0 until tags.length()) tags.getJSONObject(j).takeIf { it.optString("name") == "Android-Package" }?.optString("value")?.takeIf { it.isNotEmpty() }?.let(found::add) }
            if (!t.getJSONObject("pageInfo").optBoolean("hasNextPage") || edges.length() == 0) break
            after = edges.getJSONObject(edges.length() - 1).getString("cursor")
        }
        found.filterTo(HashSet()) { p -> EXCLUDED.none { p.startsWith(it) } }
    }.onFailure { log("discovery skipped: ${it.message?.take(80)}") }.getOrDefault(emptySet())

    /** Which of up to 20 packages the store lists now, with name and last update; null on failure. */
    fun live(pkgs: List<String>): Map<String, CatalogueApp>? {
        require(pkgs.size <= Config.STORE_BATCH)
        return runCatching {
            val arr = post("DAppsByPackagesLive", Q_LIVE, JSONObject().put("systemContext", sc).put("androidPackages", JSONArray(pkgs))).getJSONArray("dAppsByAndroidPackages")
            (0 until arr.length()).associate { i -> val o = arr.getJSONObject(i); val p = o.getString("androidPackage"); val rel = o.optJSONObject("lastRelease")
                p to CatalogueApp(p, rel?.optString("displayName")?.takeIf { it.isNotEmpty() } ?: p, rel?.optString("updatedOn")?.take(10) ?: "", null) }
        }.onFailure { log("live check failed: ${it.message?.take(80)}") }.getOrNull()
    }

    fun details(pkg: String): StoreDetails? = runCatching {
        val rel = post("DAppByPackageSummary", Q_DETAILS, JSONObject().put("systemContext", sc).put("androidPackage", pkg))
            .optJSONObject("dAppByAndroidPackage")?.optJSONObject("lastRelease") ?: return null
        val ad = rel.optJSONObject("androidDetails"); val pd = rel.optJSONObject("publisherDetails")
        StoreDetails(ad?.optLong("versionCode", 0L) ?: 0L, ad?.str("certFingerprint")?.lowercase()?.filter { it in "0123456789abcdef" },
            pd?.str("name"), pd?.str("website"), pd?.str("supportEmail"), rel.str("newInVersion"), rel.optJSONObject("installFile")?.str("uri")?.takeIf { it.startsWith("https://") })
    }.onFailure { log("details $pkg failed: ${it.message?.take(80)}") }.getOrNull()

    /** Star counts (1★..5★) for up to 20 packages; larger batches come back silently partial. Null on failure. */
    fun ratings(pkgs: List<String>): Map<String, List<Int>>? {
        require(pkgs.size <= Config.STORE_BATCH)
        return runCatching {
            val arr = post("DAppsByPackagesSummary", Q_BATCH, JSONObject().put("systemContext", sc).put("androidPackages", JSONArray(pkgs))).getJSONArray("dAppsByAndroidPackages")
            (0 until arr.length()).mapNotNull { i -> val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val r = o.optJSONObject("rating")?.optJSONArray("reviewsByRating") ?: return@mapNotNull null
                if (r.length() != 5) null else o.optString("androidPackage") to (0 until 5).map { r.optInt(it) } }.toMap()
        }.onFailure { log("ratings failed: ${it.message?.take(80)}") }.getOrNull()
    }

    @Synchronized private fun post(op: String, query: String, variables: JSONObject): JSONObject {
        val wait = lastAt + paceMs - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        val body = JSONObject().put("operationName", op).put("variables", variables).put("query", query).toString()
        val c = URL(Config.STORE_GRAPHQL).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 10000; c.readTimeout = 20000; c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val resp = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            if (resp.has("errors")) throw IllegalStateException(resp.getJSONArray("errors").toString().take(120))
            return resp.getJSONObject("data")
        } finally { c.disconnect(); lastAt = System.currentTimeMillis() }
    }

    private fun JSONObject.str(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        val CURATED_SHELVES = setOf("Top Picks")
        private val EXCLUDED = listOf("com.google.", "com.android.", "com.mediatek.", "com.samsung.", "com.qualcomm.", "com.sec.", "com.friendly.", "org.chromium.")
        private const val Q_CATEGORIES = "query TopCategories(\$systemContext: SystemContext!) { topCategories(systemContext: \$systemContext, first: 20) { edges { node { id name } } } }"
        private const val Q_CATEGORY_DAPPS = "query DAppsCategory(\$systemContext: SystemContext!, \$categoryId: ID!, \$first: Int, \$after: String) { dAppsCategory { dApps(systemContext: \$systemContext, categoryId: \$categoryId, first: \$first, after: \$after) { edges { node { androidPackage lastRelease(systemContext: \$systemContext) { displayName updatedOn installFile { size } } } } pageInfo { hasNextPage endCursor } } } }"
        private const val Q_DETAILS = "query DAppByPackageSummary(\$systemContext: SystemContext!, \$androidPackage: String!) { dAppByAndroidPackage(systemContext: \$systemContext, androidPackage: \$androidPackage) { androidPackage lastRelease(systemContext: \$systemContext) { newInVersion installFile { uri size } androidDetails { versionCode certFingerprint } publisherDetails { name website contact supportEmail } } } }"
        private const val Q_LIVE = "query DAppsByPackagesLive(\$systemContext: SystemContext!, \$androidPackages: [String!]!) { dAppsByAndroidPackages(systemContext: \$systemContext, androidPackages: \$androidPackages) { androidPackage lastRelease(systemContext: \$systemContext) { displayName updatedOn } } }"
        private const val Q_BATCH = "query DAppsByPackagesSummary(\$systemContext: SystemContext!, \$androidPackages: [String!]!) { dAppsByAndroidPackages(systemContext: \$systemContext, androidPackages: \$androidPackages) { androidPackage rating { rating reviewsByRating } } }"
    }
}

fun log(msg: String) = System.err.println("[${java.time.Instant.now().toString().take(19)}] $msg")
