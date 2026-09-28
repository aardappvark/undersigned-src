package com.undersigned.api

import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.Payments
import com.undersigned.protocol.PublisherProof
import com.undersigned.protocol.Ranking
import com.undersigned.protocol.Reputation
import com.undersigned.protocol.ReviewMemo
import com.undersigned.protocol.SignerFacts
import com.undersigned.protocol.StoreNotes
import com.undersigned.protocol.Usage
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Money floors the app reads the ledger with. They MUST equal the app's AppConfig.Payment (ApiConfigParityTest reads
 * that file and fails when they drift): a Pro licence or a paid listing must mean the same thing on both.
 */
object AppFloors {
    const val PRO_SOL = 0.0607
    const val PRO_SKR = 651.0
    /** Earlier Pro prices stay floors (the 276 SKR licence of 15 Sep must still read). */
    val LEGACY_PRO_SKR: List<Double> = listOf(276.0)
    const val LIST_SOL = 0.0607
    const val LIST_SKR = 651.0
    const val LIST_SKR_SGT = 586.0
    val proFloorLamports get() = Config.lamports(PRO_SOL)
    val proFloorSkrRaw get() = (LEGACY_PRO_SKR + PRO_SKR).minOf { Config.skrRaw(it) }
    val listFloorLamports get() = Config.lamports(LIST_SOL)
    val listFloorSkrRaw get() = minOf(Config.skrRaw(LIST_SKR), Config.skrRaw(LIST_SKR_SGT))
    const val TERM_MONTHS = 12
}

data class TxMeta(val fee: Long, val seekerShape: Boolean, val lamports: Long, val skr: Long)

/** Everything a request reads, computed once per sync. Immutable: requests never see a half-built state. */
class State(
    val entries: List<LedgerEntry>,
    val meta: Map<String, TxMeta>,
    val facts: Map<String, SignerFacts>,
    val verified: Map<String, Set<String>>,
    val proofSince: Map<Pair<String, String>, Long>,
    val catalogue: List<CatalogueApp>,
    val details: Map<String, StoreDetails>,
    val builtAt: Long,
) {
    val byPackage: Map<String, CatalogueApp> = catalogue.associateBy { it.packageName }
    val revoked: Set<String> = Ranking.revoked(entries)
    private val f = { w: String -> facts[w] ?: SignerFacts(false, 0.0) }
    val scores = Ranking.score(entries, verified, f).associateBy { it.packageName }
    val standings = Reputation.standing(entries, verified, f)
    val hidden = Reputation.hiddenByConsensus(entries, verified, f)
    val flaggers = Reputation.currentFlaggers(entries)
    val helpful = Reputation.helpfulWeights(entries, f)
    val disputes = Reputation.disputes(entries)
    val similarity = com.undersigned.protocol.Similarity.findings(entries)
    val usage = Usage.index(entries)
    val useTimes = Usage.time(entries)
    val notes = StoreNotes.current(entries, verified)
    fun factsOf(w: String) = f(w)
    /** Paid listings only (see Payments.claims); the Undersigned treasury lists without paying. */
    val claims: List<Payments.Claim> = Payments.claims(entries) { e ->
        e.signer == Config.TREASURY || meta[e.signature.substringBefore(':')]?.let { it.lamports >= AppFloors.listFloorLamports || it.skr >= AppFloors.listFloorSkrRaw } == true
    }
    companion object { val EMPTY = State(emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyMap(), 0L) }
}

class Indexer(private val db: Db, private val rpc: Rpc = Rpc(), private val store: Store = Store()) {
    @Volatile var state: State = State.EMPTY; private set
    @Volatile var lastLedgerSync: Long = 0L; private set
    @Volatile var lastStoreSync: Long = 0L; private set

    // ---- Ledger ---------------------------------------------------------------------------------------------------

    /** Reads every registry transaction not yet held (whole history on the first run, the tail after). Returns new records. */
    fun syncLedger(): Int {
        val head = db.get("ledger_head")
        val known = db.query("SELECT DISTINCT sig FROM records") { it.getString(1) }.toHashSet()
        var before: String? = null; var seenHead: String? = null; var readToEnd = false; var skipped = 0; var added = 0
        while (true) {
            val page = rpc.signatures(Config.REGISTRY, before = before, until = head)
            if (page.isEmpty()) { readToEnd = true; break }
            if (seenHead == null) seenHead = page.first().signature
            for (row in page) {
                if (row.err || row.memo == null || !(row.memo.contains("SRL1|") || row.memo.contains(Config.PAYMENT_MEMO))) continue
                if (row.signature in known) continue
                val tx = retry { rpc.tx(row.signature) } ?: run { skipped++; null } ?: continue
                if (!tx.ok) continue
                val skr = tx.tokensIn[Config.SKR_MINT] ?: 0L
                val time = if (tx.blockTime > 0) tx.blockTime else row.blockTime
                db.tx {
                    db.exec("INSERT OR REPLACE INTO txmeta (sig, fee, seeker, lamports, skr) VALUES (?, ?, ?, ?, ?)", row.signature, tx.fee, if (tx.seekerShape) 1 else 0, tx.lamportsIn, skr)
                    var records = 0
                    tx.memos.forEachIndexed { idx, raw ->
                        val memo = ReviewMemo.decode(raw) ?: return@forEachIndexed
                        db.exec("INSERT OR IGNORE INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", ReviewMemo.recordId(row.signature, idx), row.signature, tx.feePayer, time, ReviewMemo.encode(memo))
                        records++; added++
                    }
                    // A licence IS its payment (the app's rule): no SRL1 record, a treasury credit at or above the Pro floor.
                    if (records == 0 && (tx.lamportsIn >= AppFloors.proFloorLamports || skr >= AppFloors.proFloorSkrRaw)) {
                        db.exec("INSERT OR IGNORE INTO records (id, sig, signer, block_time, memo) VALUES (?, ?, ?, ?, ?)", row.signature, row.signature, tx.feePayer, time, ReviewMemo.encode(ReviewMemo.Pro(AppFloors.TERM_MONTHS)))
                        added++
                    }
                }
            }
            if (page.size < 1000) { readToEnd = true; break }
            before = page.last().signature
        }
        // Advance only when the whole tail was read with nothing skipped, so a failed read is retried next time.
        if (seenHead != null && readToEnd && skipped == 0) db.put("ledger_head", seenHead)
        lastLedgerSync = System.currentTimeMillis()
        return added
    }

    fun loadEntries(): Pair<List<LedgerEntry>, Map<String, TxMeta>> {
        val entries = db.query("SELECT id, signer, block_time, memo FROM records") { rs ->
            ReviewMemo.decode(rs.getString(4))?.let { LedgerEntry(rs.getString(1), rs.getString(2), rs.getLong(3), it) }
        }.filterNotNull().sortedWith(compareByDescending<LedgerEntry> { it.blockTime }.thenBy { it.signature })
        val meta = db.query("SELECT sig, fee, seeker, lamports, skr FROM txmeta") { it.getString(1) to TxMeta(it.getLong(2), it.getInt(3) == 1, it.getLong(4), it.getLong(5)) }.toMap()
        return entries to meta
    }

    // ---- Signer facts: attested device (Seeker Genesis Token) and staked SKR, as the app weighs them ----------------

    fun facts(wallet: String, force: Boolean = false): SignerFacts {
        if (!force) db.query("SELECT sgt, staked, at FROM facts WHERE wallet = ?", wallet) { Triple(it.getInt(1) == 1, it.getDouble(2), it.getLong(3)) }.firstOrNull()
            ?.takeIf { System.currentTimeMillis() - it.third < Config.FACTS_TTL_MS }?.let { return SignerFacts(it.first, it.second) }
        val sgt = runCatching { rpc.holdsTokenFrom(wallet, Config.SGT_MINT_AUTHORITY) }.getOrElse { return SignerFacts(false, 0.0) }   // unknown: not cached
        val staked = if (sgt) runCatching { stakedSkr(wallet) }.getOrDefault(0.0) else 0.0
        db.exec("INSERT OR REPLACE INTO facts (wallet, sgt, staked, at) VALUES (?, ?, ?, ?)", wallet, if (sgt) 1 else 0, staked, System.currentTimeMillis())
        return SignerFacts(sgt, staked)
    }

    /** Guardian-pool stake, valued at the current share price (the app's SignerFactsService layout). */
    private fun stakedSkr(wallet: String): Double {
        val cfg = rpc.accountData(STAKE_CONFIG) ?: return 0.0
        val price = if (cfg.size >= 153) ByteBuffer.wrap(cfg).order(ByteOrder.LITTLE_ENDIAN).getLong(137).takeIf { it > 0 } ?: PRECISION else PRECISION
        fun value(data: ByteArray): Double {
            if (data.size < 169) return 0.0
            val shares = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getLong(105)   // u128 LE, low word
            if (shares <= 0) return 0.0
            return (shares.toBigInteger() * price.toBigInteger() / PRECISION.toBigInteger()).toLong() / 1_000_000.0
        }
        // Every guardian pool first (as the app does); the Solana Mobile pool's PDA when the node refuses getProgramAccounts.
        runCatching { rpc.programAccounts(STAKING_PROGRAM, 169, 41, wallet) }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it.sumOf(::value) }
        val seeds = listOf("user_stake".toByteArray(), Base58.decode(STAKE_CONFIG), Base58.decode(wallet), Base58.decode(GUARDIAN_POOL))
        val data = rpc.accountData(Base58.encode(Pda.find(seeds, Base58.decode(STAKING_PROGRAM)))) ?: return 0.0
        return value(data)
    }

    // ---- Publisher proofs -----------------------------------------------------------------------------------------

    private val bodies = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String?>>()
    fun proofBody(url: String): String? {
        bodies[url]?.takeIf { System.currentTimeMillis() - it.first < 6 * 3600_000L }?.let { return it.second }
        val body = runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try { c.connectTimeout = 8000; c.readTimeout = 8000; c.instanceFollowRedirects = true; if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null } finally { c.disconnect() }
        }.getOrNull()
        bodies[url] = System.currentTimeMillis() to body
        return body
    }

    /** Website-proven claims (the API cannot see an installed APK), expanded to every app listing the same proof file. */
    private fun verifyClaims(entries: List<LedgerEntry>, details: Map<String, StoreDetails>): Pair<Map<String, Set<String>>, Map<Pair<String, String>, Long>> {
        val out = HashMap<String, MutableSet<String>>(); val since = HashMap<Pair<String, String>, Long>()
        for (c in entries.filter { it.memo is ReviewMemo.Claim }) {
            val pkg = (c.memo as ReviewMemo.Claim).packageName
            val url = details[pkg]?.website?.let { PublisherProof.wellKnownUrl(it) } ?: continue
            val body = proofBody(url) ?: continue
            if (body.lines().none { it.trim() == c.signer }) continue
            out.getOrPut(pkg) { HashSet() }.add(c.signer)
            db.exec("INSERT OR IGNORE INTO proofs (url, wallet, first_seen) VALUES (?, ?, ?)", url, c.signer, System.currentTimeMillis() / 1000)
            db.query("SELECT first_seen FROM proofs WHERE url = ? AND wallet = ?", url, c.signer) { it.getLong(1) }.firstOrNull()?.let { since[pkg to c.signer] = it }
        }
        val reviewed = entries.mapNotNull { (it.memo as? ReviewMemo.Review)?.packageName }.toSet()
        return PublisherProof.expandToSameProof(out, reviewed) { details[it]?.website } to since
    }

    // ---- The store ------------------------------------------------------------------------------------------------

    fun loadCatalogue(): List<CatalogueApp> = runCatching {
        val arr = JSONArray(db.get("catalogue") ?: return emptyList())
        (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); CatalogueApp(o.getString("p"), o.getString("n"), o.optString("u"), o.optString("c").takeIf { it.isNotEmpty() && it != "null" }) }
    }.getOrDefault(emptyList())

    fun loadDetails(): Map<String, StoreDetails> = db.query("SELECT pkg, version, cert, publisher, website, email, notes FROM details") { rs ->
        rs.getString(1) to StoreDetails(rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7))
    }.toMap()

    /** Daily: the catalogue, every app's star counts (Store watch), and details for every app the ledger mentions. */
    fun syncStore(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - (db.get("store_synced")?.toLongOrNull() ?: 0L) < Config.CATALOGUE_EVERY_MS) return
        store.catalogue()?.let { cat ->
            val arr = JSONArray(); cat.forEach { arr.put(JSONObject().put("p", it.packageName).put("n", it.name).put("u", it.updated).put("c", it.category)) }
            db.put("catalogue", arr.toString())
        }
        val cat = loadCatalogue()
        val day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        for (chunk in cat.map { it.packageName }.chunked(Config.STORE_BATCH)) {
            val r = store.ratings(chunk) ?: continue
            db.tx { r.forEach { (pkg, s) -> db.exec("INSERT OR REPLACE INTO ratings (pkg, day, s1, s2, s3, s4, s5) VALUES (?, ?, ?, ?, ?, ?, ?)", pkg, day, s[0], s[1], s[2], s[3], s[4]) } }
        }
        val (entries, _) = loadEntries()
        val wanted = entries.mapNotNull { when (val m = it.memo) { is ReviewMemo.Review -> m.packageName; is ReviewMemo.Claim -> m.packageName; is ReviewMemo.Treasury -> m.packageName; else -> null } }.toSet()
        val old = loadDetails()
        for (pkg in wanted) {
            val d = store.details(pkg) ?: continue
            old[pkg]?.let { o ->
                if (o.versionCode != d.versionCode && d.versionCode > 0) db.exec("INSERT INTO changes (pkg, at, kind, old, new) VALUES (?, ?, 'version', ?, ?)", pkg, now / 1000, o.versionCode.toString(), d.versionCode.toString())
                if (o.certFingerprint != null && d.certFingerprint != null && o.certFingerprint != d.certFingerprint) db.exec("INSERT INTO changes (pkg, at, kind, old, new) VALUES (?, ?, 'certificate', ?, ?)", pkg, now / 1000, o.certFingerprint, d.certFingerprint)
            }
            db.exec("INSERT OR REPLACE INTO details (pkg, version, cert, publisher, website, email, notes, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", pkg, d.versionCode, d.certFingerprint, d.publisherName, d.website, d.supportEmail, d.newInVersion, now)
        }
        db.put("store_synced", now.toString())
        lastStoreSync = now
    }

    fun snapshots(pkg: String): List<com.undersigned.protocol.RatingSnapshots.Snapshot> =
        db.query("SELECT day, s1, s2, s3, s4, s5 FROM ratings WHERE pkg = ? ORDER BY day", pkg) { rs ->
            com.undersigned.protocol.RatingSnapshots.Snapshot(java.time.LocalDate.parse(rs.getString(1)).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond(), (2..6).map { rs.getInt(it) })
        }

    fun latestRating(pkg: String): Pair<String, List<Int>>? =
        db.query("SELECT day, s1, s2, s3, s4, s5 FROM ratings WHERE pkg = ? ORDER BY day DESC LIMIT 1", pkg) { rs -> rs.getString(1) to (2..6).map { rs.getInt(it) } }.firstOrNull()

    fun ratingPackages(): List<String> = db.query("SELECT DISTINCT pkg FROM ratings") { it.getString(1) }

    // ---- Rebuild ----------------------------------------------------------------------------------------------------

    /** Recomputes [state] from the database: facts for every signer, claims, scores. */
    fun rebuild() {
        val (entries, meta) = loadEntries()
        val signers = entries.map { it.signer }.distinct()
        val facts = signers.associateWith { facts(it) }
        val details = loadDetails()
        val (verified, since) = verifyClaims(entries, details)
        state = State(entries, meta, facts, verified, since, loadCatalogue(), details, System.currentTimeMillis())
    }

    /** The background loop: ledger every 5 minutes, the store once a day. Errors are logged and retried next round. */
    fun start() {
        Thread({
            while (true) {
                runCatching { val n = syncLedger(); if (n > 0 || state.builtAt == 0L) log("ledger: +$n records") }.onFailure { log("ledger sync failed: ${it.message?.take(100)}") }
                // Serve the new records at once; the daily store crawl takes minutes and must not hold them back.
                runCatching { rebuild() }.onFailure { log("rebuild failed: ${it.message?.take(100)}") }
                val before = lastStoreSync
                runCatching { syncStore() }.onFailure { log("store sync failed: ${it.message?.take(100)}") }
                if (lastStoreSync != before) runCatching { rebuild(); log("store: ${state.catalogue.size} apps, ${state.details.size} details") }.onFailure { log("rebuild failed: ${it.message?.take(100)}") }
                Thread.sleep(Config.LEDGER_EVERY_MS)
            }
        }, "indexer").apply { isDaemon = true }.start()
    }

    private fun <T> retry(block: () -> T?): T? {
        repeat(4) { attempt -> runCatching { block() }.getOrNull()?.let { return it }; Thread.sleep(600L * (attempt + 1)) }
        return null
    }

    companion object {
        const val STAKING_PROGRAM = "SKRskrmtL83pcL4YqLWt6iPefDqwXQWHSw9S9vz94BZ"
        const val STAKE_CONFIG = "4HQy82s9CHTv1GsYKnANHMiHfhcqesYkK6sB3RDSYyqw"
        const val GUARDIAN_POOL = "DPJ58trLsF9yPrBa2pk6UaRkvqW8hWUYjawe788WBuqr"
        const val PRECISION = 1_000_000_000L
    }
}
