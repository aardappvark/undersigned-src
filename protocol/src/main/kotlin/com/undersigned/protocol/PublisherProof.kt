package com.undersigned.protocol

import java.net.URL

/**
 * Where a publisher's proof lives, and which apps one proof covers. Pure rules shared by the app and the API, so both
 * verify the same way (moved from the app's PublisherVerifier, 28 Sep 2026). Fetching the file is the caller's job.
 */
object PublisherProof {
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

}
