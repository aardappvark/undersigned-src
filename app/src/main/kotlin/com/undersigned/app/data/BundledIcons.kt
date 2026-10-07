package com.undersigned.app.data

import android.content.Context

/**
 * Every listed dApp's icon, shipped inside the app as a 128 px WebP (tools/bundle_icons.py; owner, 8 Oct 2026: icons
 * "should be loaded and saved on the device"). The store's own icons average ~330 KB, so over roaming LTE they arrived
 * slowly or not at all, and a 64 MB cache held only ~190 of them. index.tsv maps package -> the store address the shipped
 * icon was made from: when the store's address has changed since, the new one is fetched and the shipped icon stands in
 * while it loads or if it fails.
 */
object BundledIcons {
    @Volatile private var index: Map<String, String>? = null

    fun index(c: Context): Map<String, String> = index ?: synchronized(this) {
        index ?: runCatching {
            c.applicationContext.assets.open("icons/index.tsv").bufferedReader().useLines { lines ->
                lines.mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            }
        }.getOrDefault(emptyMap()).also { index = it }
    }

    /** The store address the shipped icon was made from, or null when none is shipped for [pkg]. */
    fun source(c: Context, pkg: String): String? = index(c)[pkg]

    fun model(pkg: String): String = "file:///android_asset/icons/$pkg.webp"

    /** True when the shipped icon is current: nothing newer is known, or the known address is the one it was made from. */
    fun isCurrent(c: Context, pkg: String, knownUrl: String?): Boolean = source(c, pkg)?.let { knownUrl == null || knownUrl == it } ?: false
}
