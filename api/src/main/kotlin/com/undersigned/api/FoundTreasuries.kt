package com.undersigned.api

import com.undersigned.protocol.Operator
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipFile

/**
 * Treasuries FOUND IN APP CODE, not confirmed by the publisher (owner, 9 Oct 2026: "scrape suspected treasury wallets",
 * shown as a separate unconfirmed list). Each store build is read for wallet addresses written into it; an address counts
 * only when every one of these holds:
 *  - it is an ordinary wallet on-chain (System-owned, not a program, mint or token account);
 *  - every app it was found in comes from ONE publisher (an address in several publishers' apps is shared: the Seeker
 *    Genesis Token mint authority sat in three unrelated apps when this was measured, 9 Oct 2026);
 *  - it is not an Undersigned operator wallet, not on [NOT_TREASURIES], and not already a confirmed treasury;
 *  - in the transactions read, at least [MIN_PAYERS] different wallets sent it SOL, SKR or USDC.
 * It is never ranked with confirmed treasuries, is labelled as found in code, and drops out when the publisher confirms a
 * treasury for the app (it then shows on the board) or the operator hides it on a dispute.
 */
class FoundTreasuries(private val db: Db, private val rpc: Rpc, private val store: Store) {
    init {
        db.exec("CREATE TABLE IF NOT EXISTS apk_scan (pkg TEXT PRIMARY KEY, version TEXT NOT NULL, at INTEGER NOT NULL, size INTEGER NOT NULL, wallets TEXT NOT NULL, publisher TEXT, note TEXT)")
        db.exec("CREATE TABLE IF NOT EXISTS found_wallet (wallet TEXT PRIMARY KEY, measured_at INTEGER NOT NULL, read INTEGER NOT NULL, payments INTEGER NOT NULL, payers INTEGER NOT NULL, lamports INTEGER NOT NULL, skr INTEGER NOT NULL, usdc INTEGER NOT NULL, first INTEGER, last INTEGER)")
    }

    data class Scan(val pkg: String, val version: String, val wallets: Set<String>, val publisher: String?)
    data class Measure(val read: Int, val payments: Int, val payers: Int, val lamports: Long, val skr: Long, val usdc: Long, val first: Long?, val last: Long?)

    companion object {
        const val MIN_PAYERS = 5
        const val MAX_APK = Config.MAX_APK_BYTES   // 150 MB skipped 3 of the first 16 builds (games), 9 Oct 2026
        const val MAX_ENTRY = 64 * 1024 * 1024
        const val SIGS_READ = 100
        const val TXS_READ = 40
        const val REMEASURE_MS = 7L * 86_400_000
        const val SYSTEM = "11111111111111111111111111111111"
        /** Addresses apps embed for reasons other than taking payment. */
        val NOT_TREASURIES = setOf(
            "GT2zuHVaZQYZSyQMgJPLzvkmyztfyXg2NJunqFp4p3A4",   // Seeker Genesis Token mint authority (apps check holdings)
        )
        private val B58 = Regex("(?<![1-9A-HJ-NP-Za-km-z])[1-9A-HJ-NP-Za-km-z]{32,44}(?![1-9A-HJ-NP-Za-km-z])")
        private val SKIP = listOf(".png", ".webp", ".jpg", ".jpeg", ".gif", ".ogg", ".mp3", ".wav", ".ttf", ".otf", ".mp4")

        /** Every string in the build that decodes to a 32-byte key: the dex files, JS bundles, native libraries and assets. */
        fun addresses(apk: File): Set<String> = ZipFile(apk).use { z ->
            val out = HashSet<String>()
            for (e in z.entries()) {
                val n = e.name.lowercase()
                if (e.isDirectory || n.startsWith("meta-inf/") || SKIP.any { n.endsWith(it) }) continue
                val bytes = runCatching { z.getInputStream(e).use { it.readNBytes(MAX_ENTRY) } }.getOrNull() ?: continue
                // ISO-8859-1 keeps one char per byte, so the pattern sees ASCII runs inside binary files too.
                // A real key uses many of the 58 letters; "2222…2222"-style placeholders (funded on-chain, seen in fun.cherry's build
                // 9 Oct 2026) do not.
                B58.findAll(String(bytes, Charsets.ISO_8859_1)).forEach { m -> if (m.value.toSet().size >= 12 && Base58.decodePubkey(m.value) != null) out += m.value }
            }
            out
        }

        /** SKR and USDC are both classic SPL Token mints (SKR's owner read on-chain 9 Oct 2026); the account is derived under it. */
        fun tokenProgramOf(@Suppress("UNUSED_PARAMETER") mint: String): String = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"

        fun ata(owner: String, mint: String, tokenProgram: String = tokenProgramOf(mint)): String =
            Base58.encode(Pda.find(listOf(Base58.decode(owner), Base58.decode(tokenProgram), Base58.decode(mint)), Base58.decode("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")))
    }

    // ---- Reading builds -------------------------------------------------------------------------------------------

    /** The next app to read: never read, or its listing moved since. Null when every listed build has been read. */
    fun nextToScan(catalogue: List<CatalogueApp>): CatalogueApp? {
        val seen = db.query("SELECT pkg, version FROM apk_scan") { it.getString(1) to it.getString(2) }.toMap()
        return catalogue.firstOrNull { seen[it.packageName] == null } ?: catalogue.firstOrNull { it.updated.isNotBlank() && seen[it.packageName] != it.updated }
    }

    /** Reads one app's current store build and keeps the ordinary wallets written into it. */
    fun scan(app: CatalogueApp): Scan? {
        val d = store.details(app.packageName)
        val version = app.updated.ifBlank { d?.versionCode?.toString() ?: "?" }
        val uri = d?.apkUri ?: run { record(app.packageName, version, 0, emptySet(), d?.publisherName, "no build"); return null }
        val tmp = File.createTempFile("undersigned-scan-", ".apk")
        try {
            val c = URL(uri).openConnection() as HttpURLConnection
            var size = 0L
            try {
                c.connectTimeout = 10_000; c.readTimeout = 60_000; c.setRequestProperty("User-Agent", "Undersigned-API/1.0")
                if (c.responseCode != 200) { record(app.packageName, version, 0, emptySet(), d.publisherName, "HTTP ${c.responseCode}"); return null }
                if (c.contentLengthLong > MAX_APK) { record(app.packageName, version, c.contentLengthLong, emptySet(), d.publisherName, "over ${MAX_APK shr 20} MB"); return null }
                c.inputStream.use { inp -> tmp.outputStream().use { out -> val buf = ByteArray(1 shl 16)
                    while (true) { val n = inp.read(buf); if (n < 0) break; size += n
                        if (size > MAX_APK) { record(app.packageName, version, size, emptySet(), d.publisherName, "over ${MAX_APK shr 20} MB"); return null }
                        out.write(buf, 0, n) } } }
            } finally { c.disconnect() }
            val found = runCatching { addresses(tmp) }.getOrElse { record(app.packageName, version, size, emptySet(), d.publisherName, "unreadable"); return null }
            val briefs = runCatching { rpc.briefs(found.toList()) }.getOrElse { log("found treasuries: accounts for ${app.packageName}: ${it.message?.take(60)}"); return null }
            val wallets = briefs.filterValues { it != null && it.owner == SYSTEM && !it.executable }.keys - NOT_TREASURIES
            record(app.packageName, version, size, wallets, d.publisherName, "${found.size} keys")
            return Scan(app.packageName, version, wallets, d.publisherName)
        } catch (e: Exception) { log("found treasuries: ${app.packageName}: ${e.message?.take(80)}"); return null } finally { tmp.delete() }
    }

    private fun record(pkg: String, version: String, size: Long, wallets: Set<String>, publisher: String?, note: String) =
        db.exec("INSERT OR REPLACE INTO apk_scan (pkg, version, at, size, wallets, publisher, note) VALUES (?, ?, ?, ?, ?, ?, ?)",
            pkg, version, System.currentTimeMillis(), size, wallets.joinToString(","), publisher, note)

    // ---- Payments in ---------------------------------------------------------------------------------------------

    /**
     * What [wallet] received in its newest transactions: SOL to the wallet and SKR/USDC to its associated token accounts
     * (token payments are signed against the token account, never the wallet), each from a payer other than the wallet.
     */
    fun measure(wallet: String): Measure {
        val sigs = (listOf(wallet, ata(wallet, Config.SKR_MINT), ata(wallet, Config.USDC_MINT)).flatMap { a -> runCatching { rpc.signatures(a, SIGS_READ) }.getOrDefault(emptyList()) })
            .filter { !it.err }.distinctBy { it.signature }.sortedByDescending { it.blockTime }.take(TXS_READ)
        var payments = 0; val payers = HashSet<String>(); var lamports = 0L; var skr = 0L; var usdc = 0L; var first: Long? = null; var last: Long? = null
        sigs.forEach { s ->
            val t = runCatching { rpc.tx(s.signature, wallet) }.getOrNull() ?: return@forEach
            if (!t.ok || t.feePayer == wallet) return@forEach
            val inSkr = t.tokensIn[Config.SKR_MINT] ?: 0L; val inUsdc = t.tokensIn[Config.USDC_MINT] ?: 0L
            if (t.lamportsIn <= 0 && inSkr <= 0 && inUsdc <= 0) return@forEach
            payments++; payers += t.feePayer; lamports += maxOf(0, t.lamportsIn); skr += maxOf(0, inSkr); usdc += maxOf(0, inUsdc)
            first = minOf(first ?: t.blockTime, t.blockTime); last = maxOf(last ?: t.blockTime, t.blockTime)
        }
        val m = Measure(sigs.size, payments, payers.size, lamports, skr, usdc, first, last)
        db.exec("INSERT OR REPLACE INTO found_wallet (wallet, measured_at, read, payments, payers, lamports, skr, usdc, first, last) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            wallet, System.currentTimeMillis(), m.read, m.payments, m.payers, m.lamports, m.skr, m.usdc, m.first, m.last)
        return m
    }

    /** One round: read the next build, then measure any wallet not measured in a week. */
    fun tick(catalogue: List<CatalogueApp>) {
        nextToScan(catalogue)?.let { scan(it) }
        val now = System.currentTimeMillis()
        val measured = db.query("SELECT wallet, measured_at FROM found_wallet") { it.getString(1) to it.getLong(2) }.toMap()
        candidates().keys.filter { (measured[it] ?: 0L) < now - REMEASURE_MS }.take(3).forEach { w -> runCatching { measure(w) }.onFailure { log("found treasuries: measure: ${it.message?.take(60)}") } }
    }

    /** Wallet → (packages it was found in, publishers of those packages), before the payment test. */
    fun candidates(): Map<String, Pair<Set<String>, Set<String>>> {
        val rows = db.query("SELECT pkg, wallets, publisher FROM apk_scan WHERE wallets != ''") { Triple(it.getString(1), it.getString(2).split(',').filter(String::isNotBlank), it.getString(3)) }
        val out = HashMap<String, Pair<MutableSet<String>, MutableSet<String>>>()
        rows.forEach { (pkg, ws, pub) -> ws.forEach { w -> val e = out.getOrPut(w) { mutableSetOf<String>() to mutableSetOf() }; e.first += pkg; e.second += (pub?.trim()?.lowercase() ?: "?:$pkg") } }
        return out.filterKeys { it !in NOT_TREASURIES && !Operator.isOperator(it) }
    }

    /**
     * The list: one row per app and wallet that passes every test in the header, by app name (never ranked). [confirmed]
     * holds treasuries already confirmed for any app; [hidden] the operator's hide ids ("treasury:<pkg>:<wallet>").
     */
    fun list(names: Map<String, String>, confirmed: Set<String>, confirmedApps: Set<String>, hidden: Set<String>): List<JSONObject> {
        val m = db.query("SELECT wallet, measured_at, read, payments, payers, lamports, skr, usdc, first, last FROM found_wallet") { rs ->
            rs.getString(1) to JSONObject().put("measured_at", rs.getLong(2) / 1000).put("transactions_read", rs.getInt(3)).put("payments", rs.getInt(4)).put("payers", rs.getInt(5))
                .put("sol", rs.getLong(6) / 1e9).put("skr", rs.getLong(7) / 1e6).put("usdc", rs.getLong(8) / 1e6)
                .put("first", rs.getLong(9).takeIf { !rs.wasNull() } ?: JSONObject.NULL).put("last", rs.getLong(10).takeIf { !rs.wasNull() } ?: JSONObject.NULL)
        }.toMap()
        return candidates().flatMap { (w, v) ->
            val (pkgs, pubs) = v
            val meas = m[w] ?: return@flatMap emptyList()
            if (pubs.size != 1 || w in confirmed || meas.getInt("payers") < MIN_PAYERS) return@flatMap emptyList()
            pkgs.filter { it !in confirmedApps && "treasury:$it:$w" !in hidden }.map { pkg ->
                JSONObject(meas.toString()).put("package", pkg).put("name", names[pkg] ?: pkg).put("wallet", w).put("found_in", "the app's store build")
            }
        }.sortedBy { it.getString("name").lowercase() }
    }

    fun stats(): JSONObject = JSONObject()
        .put("builds_read", db.query("SELECT count(*) FROM apk_scan WHERE note LIKE '% keys'") { it.getInt(1) }.first())
        .put("builds_skipped", db.query("SELECT count(*) FROM apk_scan WHERE note NOT LIKE '% keys'") { it.getInt(1) }.first())
        .put("wallets_found", candidates().size)
}
