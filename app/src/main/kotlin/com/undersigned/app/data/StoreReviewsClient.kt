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

/**
 * A JSON string field, or null. [JSONObject.optString] with a null fallback returns the LITERAL string
 * "null" when the value is JSON null (it stringifies JSONObject.NULL), which is how a reviewer with no
 * `.skr` domain rendered as "null" on the app page. [JSONObject.isNull] is true for both missing and
 * JSON-null, so it is the only safe gate.
 */
private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

/** One of the native dApp Store's own public reviews. Read-only; the store owns these. */
data class StoreReview(val id: String, val createdAt: String, val rating: Int, val text: String?, val walletAddress: String?, val domain: String?, val publisherReply: String?)
data class StoreRating(val average: Double, val byStar: List<Int>) { val total get() = byStar.sum() }
/** The store's listed publisher and the certificate the store's release was signed with. */
/** [complete]: no request failed. [exhausted]: read to the last review, so "not found" really means none. */
data class ReviewsRead(val items: List<StoreReview>, val complete: Boolean, val exhausted: Boolean)

/** [newInVersion] is the publisher's "What's new" for [versionCode] ONLY — the store's latest release, not the installed one. */
data class StoreDetails(val versionCode: Long, val certFingerprint: String?, val publisherName: String?, val website: String?, val supportEmail: String?, val newInVersion: String? = null)

/**
 * The store's "What's new" text is the publisher's own, unlocalised, and sometimes raw GitHub markdown (the dApp Store's
 * own notes begin "## What's Changed\r\n* Automatic PR from…"). Shown as plain text: headings and bullet markers go,
 * blank lines collapse, links keep their words. Null when nothing readable is left.
 */
fun cleanReleaseNotes(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val lines = raw.replace("\r\n", "\n").replace('\r', '\n').lines().map { l ->
        l.trim()
            .replace(Regex("^#{1,6}\\s*"), "")               // ## Heading
            .replace(Regex("^[*\\-+]\\s+"), "• ")           // * item, - item
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")  // [words](url) → words
            .replace(Regex("[*`]{1,3}([^*`]+)[*`]{1,3}"), "$1") // **bold**, `code` — not _, which is common inside real words
    }
    val out = StringBuilder(); var blank = false
    for (l in lines) { if (l.isBlank()) { blank = out.isNotEmpty() } else { if (out.isNotEmpty()) out.append(if (blank) "\n\n" else "\n"); out.append(l); blank = false } }
    return out.toString().trim().takeIf { it.isNotEmpty() }
}

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
            StoreDetails(ad?.optLong("versionCode", 0L) ?: 0L, ad?.str("certFingerprint")?.lowercase()?.filter { c -> c in "0123456789abcdef" },
                pd?.str("name"), pd?.str("website"), pd?.str("supportEmail"), cleanReleaseNotes(rel.str("newInVersion")))
        }.getOrElse { Log.w(TAG, "details failed: ${it.message?.take(80)}"); null }
    }

    /** Newest first. Stops after [maxPages]; [stopWhen] lets a wallet scan end early. Partial on failure — see [reviewsChecked]. */
    suspend fun reviews(pkg: String, pageSize: Int = MAX_REVIEW_PAGE, maxPages: Int = 3, stopWhen: (StoreReview) -> Boolean = { false }): List<StoreReview> =
        reviewsChecked(pkg, pageSize, maxPages, stopWhen = stopWhen).items

    /**
     * As [reviews], plus whether the read COMPLETED. A scan must not report "you have no store reviews" when the store
     * simply refused it: after store v1.59.0 a page of more than 10 reviews returns a bare {"message":"error"}, every
     * import-scan request asked for 20, and the scan said "No store reviews by this wallet" for every wallet (found
     * 26 Sep 2026). The page size is clamped to [MAX_REVIEW_PAGE]; callers asking for more get more pages instead.
     */
    suspend fun reviewsChecked(pkg: String, pageSize: Int = MAX_REVIEW_PAGE, maxPages: Int = 3, pauseMs: Long = 0, stopWhen: (StoreReview) -> Boolean = { false }): ReviewsRead = withContext(Dispatchers.IO) {
        val out = ArrayList<StoreReview>(); var after: String? = null
        val first = pageSize.coerceIn(1, MAX_REVIEW_PAGE)
        try {
            repeat(maxPages) {
                val vars = JSONObject().put("systemContext", sc).put("androidPackage", pkg).put("first", first)
                if (after != null) { vars.put("after", after); if (pauseMs > 0) kotlinx.coroutines.delay(pauseMs) }
                val d = post("DAppReviews", Q_REVIEWS, vars).getJSONObject("dAppReviews")
                val edges = d.getJSONArray("edges")
                for (i in 0 until edges.length()) {
                    val n = edges.getJSONObject(i).getJSONObject("node")
                    val r = StoreReview(n.optString("id"), n.optString("createdAt"), n.optInt("rating"), n.str("review"),
                        n.str("walletAddress"), n.str("domain"), n.optJSONObject("reply")?.str("reply"))
                    out.add(r); if (stopWhen(r)) return@withContext ReviewsRead(out, complete = true, exhausted = false)
                }
                val pi = d.getJSONObject("pageInfo")
                if (!pi.optBoolean("hasNextPage", false)) return@withContext ReviewsRead(out, complete = true, exhausted = true)
                after = pi.str("endCursor") ?: return@withContext ReviewsRead(out, complete = true, exhausted = true)
            }
            ReviewsRead(out, complete = true, exhausted = false)   // stopped at maxPages: a depth limit, not a failure — and NOT the end
        } catch (e: Exception) { Log.w(TAG, "reviews failed: ${e.message?.take(80)}"); ReviewsRead(out, complete = false, exhausted = false) }
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
        private const val Q_DETAILS = "query DAppByPackageSummary(\$systemContext: SystemContext!, \$androidPackage: String!) { dAppByAndroidPackage(systemContext: \$systemContext, androidPackage: \$androidPackage) { androidPackage lastRelease(systemContext: \$systemContext) { newInVersion androidDetails { versionCode certFingerprint } publisherDetails { name website contact supportEmail } } } }"
        /** The store's largest accepted page of reviews (v1.59.0: 11+ returns a bare error; measured 26 Sep 2026). */
        const val MAX_REVIEW_PAGE = 10
        private const val Q_REVIEWS = "query DAppReviews(\$systemContext: SystemContext!, \$androidPackage: String!, \$first: Int!, \$after: String) { dAppReviews(systemContext: \$systemContext, androidPackage: \$androidPackage, first: \$first, after: \$after) { edges { node { __typename ...DAppReviewFragment } } pageInfo { hasNextPage hasPreviousPage startCursor endCursor } } }  fragment DAppReviewFragment on DAppReview { id createdAt rating review walletAddress domain isReviewerAccountDeleted reply { reply } }"
    }
}
