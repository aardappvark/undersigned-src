package com.undersigned.app.data

import android.os.Build
import android.util.Log
import com.undersigned.app.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** One of the native dApp Store's own public reviews. Read-only; the store owns these. */
data class StoreReview(val id: String, val createdAt: String, val rating: Int, val text: String?, val walletAddress: String?, val domain: String?, val publisherReply: String?)
data class StoreRating(val average: Double, val byStar: List<Int>) { val total get() = byStar.sum() }
/** The store's listed publisher and the certificate the store's release was signed with. */
data class StoreDetails(val versionCode: Long, val certFingerprint: String?, val publisherName: String?, val website: String?, val supportEmail: String?)

/**
 * READ-ONLY mirror of the store's public rating and reviews, through the same operations the store
 * app itself issues (`DAppByPackageSummary` rating, `DAppReviews` pages; both answer without any
 * token — probed 8 Sep 2026). Nothing is ever written to the store; there is no store auth in this app.
 * Shown beside the signed ledger so a user sees both, and used to find the user's OWN historical
 * store reviews by wallet address for the opt-in import.
 */
class StoreReviewsClient {
    private val sc: JSONObject get() = JSONObject().put("locale", Locale.getDefault().toLanguageTag()).put("platformSdk", Build.VERSION.SDK_INT).put("pixelDensity", 420).put("model", Build.MODEL ?: "unknown")

    suspend fun rating(pkg: String): StoreRating? = withContext(Dispatchers.IO) {
        runCatching {
            val d = post("DAppByPackageSummary", Q_RATING, JSONObject().put("systemContext", sc).put("androidPackage", pkg))
            val r = d.optJSONObject("dAppByAndroidPackage")?.optJSONObject("rating") ?: return@runCatching null
            val arr = r.optJSONArray("reviewsByRating") ?: return@runCatching null
            StoreRating(r.optDouble("rating", 0.0), (0 until arr.length()).map { arr.optInt(it) })
        }.getOrElse { Log.w(TAG, "rating failed: ${it.message?.take(80)}"); null }
    }

    suspend fun details(pkg: String): StoreDetails? = withContext(Dispatchers.IO) {
        runCatching {
            val d = post("DAppByPackageSummary", Q_DETAILS, JSONObject().put("systemContext", sc).put("androidPackage", pkg))
            val rel = d.optJSONObject("dAppByAndroidPackage")?.optJSONObject("lastRelease") ?: return@runCatching null
            val ad = rel.optJSONObject("androidDetails"); val pd = rel.optJSONObject("publisherDetails")
            StoreDetails(ad?.optLong("versionCode", 0L) ?: 0L, ad?.optString("certFingerprint", null)?.takeIf { it.isNotBlank() }?.lowercase()?.filter { c -> c in "0123456789abcdef" },
                pd?.optString("name", null)?.takeIf { it.isNotBlank() }, pd?.optString("website", null)?.takeIf { it.isNotBlank() }, pd?.optString("supportEmail", null)?.takeIf { it.isNotBlank() })
        }.getOrElse { Log.w(TAG, "details failed: ${it.message?.take(80)}"); null }
    }

    /** Newest first. Stops after [maxPages]; [stopWhen] lets a wallet scan end early. */
    suspend fun reviews(pkg: String, pageSize: Int = 20, maxPages: Int = 3, stopWhen: (StoreReview) -> Boolean = { false }): List<StoreReview> = withContext(Dispatchers.IO) {
        val out = ArrayList<StoreReview>(); var after: String? = null
        try {
            repeat(maxPages) {
                val vars = JSONObject().put("systemContext", sc).put("androidPackage", pkg).put("first", pageSize)
                if (after != null) vars.put("after", after)
                val d = post("DAppReviews", Q_REVIEWS, vars).getJSONObject("dAppReviews")
                val edges = d.getJSONArray("edges")
                for (i in 0 until edges.length()) {
                    val n = edges.getJSONObject(i).getJSONObject("node")
                    val r = StoreReview(n.optString("id"), n.optString("createdAt"), n.optInt("rating"), n.optString("review", null).takeIf { !it.isNullOrBlank() },
                        n.optString("walletAddress", null).takeIf { !it.isNullOrBlank() }, n.optString("domain", null).takeIf { !it.isNullOrBlank() },
                        n.optJSONObject("reply")?.optString("reply", null).takeIf { !it.isNullOrBlank() })
                    out.add(r); if (stopWhen(r)) return@withContext out
                }
                val pi = d.getJSONObject("pageInfo")
                if (!pi.optBoolean("hasNextPage", false)) return@withContext out
                after = pi.optString("endCursor", null) ?: return@withContext out
            }
        } catch (e: Exception) { Log.w(TAG, "reviews failed: ${e.message?.take(80)}") }
        out
    }

    private fun post(op: String, query: String, variables: JSONObject): JSONObject {
        val body = JSONObject().put("operationName", op).put("variables", variables).put("query", query).toString()
        val c = URL(AppConfig.Store.GRAPHQL).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"; c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 10000; c.readTimeout = 20000; c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val resp = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            if (resp.has("errors")) throw IllegalStateException(resp.getJSONArray("errors").toString().take(120))
            return resp.getJSONObject("data")
        } finally { c.disconnect() }
    }

    companion object {
        private const val TAG = "StoreReviews"
        private const val Q_RATING = "query DAppByPackageSummary(\$systemContext: SystemContext!, \$androidPackage: String!) { dAppByAndroidPackage(systemContext: \$systemContext, androidPackage: \$androidPackage) { __typename androidPackage rating { rating reviewsByRating } } }"
        private const val Q_DETAILS = "query DAppByPackageSummary(\$systemContext: SystemContext!, \$androidPackage: String!) { dAppByAndroidPackage(systemContext: \$systemContext, androidPackage: \$androidPackage) { androidPackage lastRelease(systemContext: \$systemContext) { androidDetails { versionCode certFingerprint } publisherDetails { name website contact supportEmail } } } }"
        private const val Q_REVIEWS = "query DAppReviews(\$systemContext: SystemContext!, \$androidPackage: String!, \$first: Int!, \$after: String) { dAppReviews(systemContext: \$systemContext, androidPackage: \$androidPackage, first: \$first, after: \$after) { edges { node { __typename ...DAppReviewFragment } } pageInfo { hasNextPage hasPreviousPage startCursor endCursor } } }  fragment DAppReviewFragment on DAppReview { id createdAt rating review walletAddress domain isReviewerAccountDeleted reply { reply } }"
    }
}
