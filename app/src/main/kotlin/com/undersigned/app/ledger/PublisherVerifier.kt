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

    fun wellKnownUrl(website: String): String? {
        val u = runCatching { URL(if (website.startsWith("http")) website else "https://$website") }.getOrNull() ?: return null
        if (u.host.equals("github.com", true)) {
            val parts = u.path.trim('/').split('/'); if (parts.size < 2) return null
            return "https://raw.githubusercontent.com/${parts[0]}/${parts[1]}/HEAD/.well-known/undersigned.txt"
        }
        return "https://${u.host}/.well-known/undersigned.txt"
    }

    suspend fun verify(pkg: String, wallet: String, details: StoreDetails?): Boolean = withContext(Dispatchers.IO) {
        UserPreferences.cachedClaim(pkg, wallet, TTL)?.let { return@withContext it }
        val url = details?.website?.let { wellKnownUrl(it) } ?: return@withContext false.also { UserPreferences.cacheClaim(pkg, wallet, false) }
        val ok = try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000; c.instanceFollowRedirects = true
            val body = if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else ""
            c.disconnect()
            body.lines().any { it.trim() == wallet }
        } catch (e: Exception) { Log.w(TAG, "well-known fetch failed: ${e.message?.take(60)}"); false }
        UserPreferences.cacheClaim(pkg, wallet, ok); ok
    }
}
