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

    /**
     * Hosts whose owner is not the app's publisher: social profiles, link pages, app-store links and ecosystem sites many
     * unrelated apps list as their "website". A proof file there would make the HOST's owner the verified publisher of every
     * app listing it, so none of them verifies through the website (hardening, 28 Sep 2026).
     */
    val SHARED_HOSTS = setOf("x.com", "twitter.com", "t.me", "telegram.me", "telegram.org", "discord.gg", "discord.com", "linktr.ee", "linktree.com",
        "medium.com", "youtube.com", "instagram.com", "facebook.com", "tiktok.com", "play.google.com", "apps.apple.com", "solana.com", "solanamobile.com",
        "dappstore.solanamobile.com", "google.com", "sites.google.com", "notion.so", "notion.site", "gitbook.io", "substack.com", "reddit.com", "zealy.io", "galxe.com")

    fun isSharedHost(host: String): Boolean { val h = host.lowercase().removePrefix("www."); return h in SHARED_HOSTS }

    fun wellKnownUrl(website: String): String? {
        val u = runCatching { URL(if (website.startsWith("http")) website else "https://$website") }.getOrNull() ?: return null
        if (isSharedHost(u.host)) return null
        if (u.host.equals("github.com", true)) {
            val parts = u.path.trim('/').split('/'); if (parts.size < 2) return null
            return "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/HEAD/.well-known/undersigned.txt"
        }
        return "https://${u.host}/.well-known/undersigned.txt"
    }

    /** The proof a website stands for: two websites with the same key are proven by the same file. Case-insensitive. */
    fun proofKey(website: String?): String? = website?.let { wellKnownUrl(it) }?.lowercase()

    /**
     * A wallet verified for one app is verified for every app whose store-listed website resolves to the SAME proof
     * file — it has already proven control of exactly that site. Without this, self-review detection was per claimed
     * app only: the airdropper, verified for the Toolkit, could post a COUNTED 5★ of DivineQuotes, which the store lists
     * under the same publisher and the same website, because nobody had claimed DivineQuotes (found 26 Sep 2026).
     * Keyed on the proof file, never the publisher NAME: a name proves nothing; a website does.
     */
    fun expandToSameProof(verified: Map<String, Set<String>>, packages: Collection<String>, websiteOf: (String) -> String?): Map<String, Set<String>> {
        val byKey = HashMap<String, MutableSet<String>>()
        for ((pkg, wallets) in verified) proofKey(websiteOf(pkg))?.let { k -> byKey.getOrPut(k) { HashSet() }.addAll(wallets) }
        val out = HashMap<String, MutableSet<String>>()
        verified.forEach { (k, v) -> out.getOrPut(k) { HashSet() }.addAll(v) }
        for (pkg in packages) proofKey(websiteOf(pkg))?.let { k -> byKey[k]?.let { w -> out.getOrPut(pkg) { HashSet() }.addAll(w) } }
        return out.filterValues { it.isNotEmpty() }
    }

    /**
     * A treasury claim is confirmed by the publisher when the same file names the wallet on a `treasury: <address>` line
     * (see Payments.listedTreasuries). A bare address line does NOT count: it says "I publish this", not "publish what I
     * am paid". Cached 24 h like claims, under its own key.
     */
    suspend fun verifyTreasury(pkg: String, treasury: String, details: StoreDetails?, force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!force) UserPreferences.cachedClaim(pkg, "trs:$treasury", TTL)?.let { return@withContext it }
        if (force) bodies.clear()
        val url = details?.website?.let { wellKnownUrl(it) } ?: return@withContext false.also { UserPreferences.cacheClaim(pkg, "trs:$treasury", false) }
        val ok = fetch(url)?.let { treasury in com.undersigned.protocol.Payments.listedTreasuries(it) } ?: false
        UserPreferences.cacheClaim(pkg, "trs:$treasury", ok); ok
    }

    private val bodies = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()
    /** Every wallet the store-listed website's proof file names: the publisher's own wallets. Cached 24 h in memory. */
    suspend fun listedWallets(details: StoreDetails?): Set<String> = withContext(Dispatchers.IO) {
        val url = details?.website?.let { wellKnownUrl(it) } ?: return@withContext emptySet()
        val body = bodies[url]?.takeIf { System.currentTimeMillis() - it.first < TTL }?.second ?: fetch(url)?.also { bodies[url] = System.currentTimeMillis() to it } ?: return@withContext emptySet()
        com.undersigned.protocol.Payments.listedWallets(body)
    }

    private fun fetch(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000; c.readTimeout = 8000; c.instanceFollowRedirects = true
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        } finally { c.disconnect() }
    } catch (e: Exception) { Log.w(TAG, "well-known fetch failed: ${e.message?.take(60)}"); null }

    suspend fun verify(pkg: String, wallet: String, details: StoreDetails?): Boolean = withContext(Dispatchers.IO) {
        UserPreferences.cachedClaim(pkg, wallet, TTL)?.let { return@withContext it }
        val url = details?.website?.let { wellKnownUrl(it) } ?: return@withContext false.also { UserPreferences.cacheClaim(pkg, wallet, false) }
        val body = fetch(url)
        val ok = body?.lines()?.any { it.trim() == wallet } ?: false
        // When a wallet first appeared in the proof file, as this device saw it: shown beside the badge, so a claim that
        // appeared yesterday on a domain that changed hands reads differently from one that has stood for months.
        if (ok) UserPreferences.proofFirstSeen(url, wallet)
        UserPreferences.cacheClaim(pkg, wallet, ok); ok
    }
}
