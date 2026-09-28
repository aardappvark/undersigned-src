package com.undersigned.api

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * A wallet's `.skr` Seeker ID name from AllDomains' Alt Name Service — a port of the app's DomainClient, with its three
 * gates, because a plausible wrong name is worse than none: more than one record → no name; the label must hash back to
 * the name account; the forward record's owner must be the wallet. A refused RPC read is "unknown", never cached.
 * A name is a label, not proof of who holds the device today.
 */
class Names(private val db: Db, private val rpc: Rpc) {
    init { db.exec("CREATE TABLE IF NOT EXISTS names (wallet TEXT PRIMARY KEY, name TEXT NOT NULL, at INTEGER NOT NULL)") }

    /** Cached name ("" = known to have none), or null when not looked up yet. Never touches the network. */
    fun cached(wallet: String): String? = db.query("SELECT name, at FROM names WHERE wallet = ?", wallet) { it.getString(1) to it.getLong(2) }.firstOrNull()
        ?.takeIf { System.currentTimeMillis() - it.second < TTL_MS }?.first

    fun nameOf(wallet: String): String? = cached(wallet)?.ifEmpty { null }

    /** Looks the wallet up when the cache has nothing fresh. Returns the name, or null (none, or unknown for now). */
    fun resolve(wallet: String): String? {
        cached(wallet)?.let { return it.ifEmpty { null } }
        val r = runCatching { lookup(wallet) }.getOrNull() ?: return null          // unavailable: retry another time
        db.exec("INSERT OR REPLACE INTO names (wallet, name, at) VALUES (?, ?, ?)", wallet, r, System.currentTimeMillis())
        return r.ifEmpty { null }
    }

    /** "" = no name; a name; or throws when the chain could not be read. */
    private fun lookup(wallet: String): String {
        val walletBytes = runCatching { Base58.decode(wallet) }.getOrNull()?.takeIf { it.size == 32 } ?: return ""
        val filters = JSONArray()
            .put(JSONObject().put("dataSize", RECORD_HEADER))
            .put(JSONObject().put("memcmp", JSONObject().put("offset", 0).put("bytes", Base58.encode(DISCRIMINATOR))))
            .put(JSONObject().put("memcmp", JSONObject().put("offset", 8).put("bytes", SKR_PARENT)))
            .put(JSONObject().put("memcmp", JSONObject().put("offset", 40).put("bytes", wallet)))
        val accounts = rpc.programAccountKeys(ANS, filters)
        if (accounts.size != 1) return ""                                                   // gate 1
        val nameAccount = accounts.single()
        val reverse = Pda.find(listOf(hashed(nameAccount), Base58.decode(SKR_TLD_STATE), ByteArray(32)), Base58.decode(ANS))
        val rev = rpc.accountData(Base58.encode(reverse)) ?: return ""
        if (rev.size <= RECORD_HEADER) return ""
        val label = String(rev, RECORD_HEADER, rev.size - RECORD_HEADER, Charsets.UTF_8).trimEnd(' ')   // exactly the app's rule: API and phone must agree on every name
        if (!LABEL.matches(label)) return ""
        if (Base58.encode(Pda.find(listOf(hashed(label), ByteArray(32), Base58.decode(SKR_PARENT)), Base58.decode(ANS))) != nameAccount) return ""   // gate 2
        val fwd = rpc.accountData(nameAccount) ?: return ""
        if (fwd.size < 72 || !fwd.copyOfRange(40, 72).contentEquals(walletBytes)) return ""  // gate 3
        return "$label.skr"
    }

    private fun hashed(s: String): ByteArray = MessageDigest.getInstance("SHA-256").digest((HASH_PREFIX + s).toByteArray(Charsets.UTF_8))

    companion object {
        const val TTL_MS = 7L * 24 * 3600 * 1000
        const val ANS = "ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK"
        const val SKR_PARENT = "F3A8kuikEiu6k2399oSJ1PWfcJYDHqpwoQ2e8psSDNuF"
        const val SKR_TLD_STATE = "4RKP4BEMu5sXBfXSH7xN2owtQrnAJvhhwtBBmj9JEYkA"
        const val HASH_PREFIX = "ALT Name Service"
        const val RECORD_HEADER = 200
        val DISCRIMINATOR = byteArrayOf(68, 72, 88, 44, 15, 167.toByte(), 103, 243.toByte())
        private val LABEL = Regex("^[a-z0-9-]{1,20}$")
    }
}
