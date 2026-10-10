package com.undersigned.app.ledger

import android.util.Log
import com.undersigned.app.data.StoreDetails
import com.undersigned.app.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A publisher claim (`SRL1|clm|<package>`) is VERIFIED when the website the STORE lists for that package
 * serves `/.well-known/undersigned.txt` containing the claimant's wallet address. Domain control of the
 * listed site is the proof; Undersigned never decides who a publisher is, the store's listing does.
 * GitHub-hosted sites are read raw from the default branch. Results cached per (package, wallet) for 24 h.
 */
object PublisherVerifier {
    private const val TAG = "PublisherVerifier"
    private const val TTL = 24L * 3600L * 1000L

    // The rules live in :protocol (PublisherProof) so the API applies exactly the same ones.
    val SHARED_HOSTS get() = com.undersigned.protocol.PublisherProof.SHARED_HOSTS
    fun isSharedHost(host: String) = com.undersigned.protocol.PublisherProof.isSharedHost(host)
    fun wellKnownUrl(website: String) = com.undersigned.protocol.PublisherProof.wellKnownUrl(website)
    fun proofKey(website: String?) = com.undersigned.protocol.PublisherProof.proofKey(website)
    fun expandToSameProof(verified: Map<String, Set<String>>, packages: Collection<String>, websiteOf: (String) -> String?) =
        com.undersigned.protocol.PublisherProof.expandToSameProof(verified, packages, websiteOf)

    /**
     * A treasury claim is confirmed by the publisher when the same file names the wallet on a `treasury: <address>` line
     * (see Payments.listedTreasuries). A bare address line does NOT count: it says "I publish this", not "publish what I
     * am paid". Cached 24 h like claims, under its own key.
     */
    suspend fun verifyTreasury(pkg: String, treasury: String, details: StoreDetails?, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force) UserPreferences.cachedClaim(pkg, "trs:$treasury", TTL)?.let { return@withContext it }
        if (force) bodies.clear()
        // Unread (no store details, or the site did not answer) is not cached: offline must not read "unconfirmed" for a day (1.1.5).
        val url = details?.website?.let { wellKnownUrl(it) } ?: return@withContext false.also { if (details != null) UserPreferences.cacheClaim(pkg, "trs:$treasury", false) }
        val r = fetchResult(url)
        if (r.isFailure) return@withContext false
        val ok = r.getOrNull()?.let { treasury in com.undersigned.protocol.Payments.listedTreasuries(it) } ?: false
        UserPreferences.cacheClaim(pkg, "trs:$treasury", ok); ok
    }

    private val bodies = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()
    /** Every wallet the store-listed website's proof file names: the publisher's own wallets. Cached 24 h in memory. */
    suspend fun listedWallets(details: StoreDetails?): Set<String> = withContext(Dispatchers.IO) {
        val url = details?.website?.let { wellKnownUrl(it) } ?: return@withContext emptySet()
        val body = bodies[url]?.takeIf { System.currentTimeMillis() - it.first < TTL }?.second ?: fetch(url)?.also { bodies[url] = System.currentTimeMillis() to it } ?: return@withContext emptySet()
        com.undersigned.protocol.Payments.listedWallets(body)
    }

    private fun fetch(url: String): String? = fetchResult(url).getOrNull()

    /** The proof file's body; null when the site answered without one (not 2xx); a failure when it did not answer. */
    private fun fetchResult(url: String): Result<String?> = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000; c.readTimeout = 8000; c.instanceFollowRedirects = true
            val code = c.responseCode
            com.undersigned.app.util.NetStatus.ok()
            Result.success(if (code in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null)
        } finally { c.disconnect() }
    } catch (e: Exception) { Log.w(TAG, "well-known fetch failed: ${e.message?.take(60)}"); com.undersigned.app.util.NetStatus.failed(e); Result.failure(e) }

    suspend fun verify(pkg: String, wallet: String, details: StoreDetails?): Boolean = verifyOrNull(pkg, wallet, details) ?: false

    /**
     * As [verify], but null when the proof could not be READ (no store details, or the proof file's site did not answer):
     * "not checked yet", which is not "not proven" (1.1.5). An unread result is never cached, so the next look asks again;
     * before 1.1.5 a phone offline for a moment cached "not proven" for 24 hours.
     */
    suspend fun verifyOrNull(pkg: String, wallet: String, details: StoreDetails?): Boolean? = withContext(Dispatchers.IO) {
        UserPreferences.cachedClaim(pkg, wallet, TTL)?.let { return@withContext it }
        val url = details?.website?.let { wellKnownUrl(it) } ?: return@withContext if (details == null) null else false.also { UserPreferences.cacheClaim(pkg, wallet, false) }
        val r = fetchResult(url)
        if (r.isFailure) return@withContext null
        val ok = r.getOrNull()?.lines()?.any { it.trim() == wallet } ?: false
        // When a wallet first appeared in the proof file, as this device saw it: shown beside the badge, so a claim that
        // appeared yesterday on a domain that changed hands reads differently from one that has stood for months.
        if (ok) UserPreferences.proofFirstSeen(url, wallet)
        UserPreferences.cacheClaim(pkg, wallet, ok); ok
    }
}
