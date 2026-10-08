package com.undersigned.api

import com.undersigned.protocol.Apk
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
    const val PRO_SKR = 369.0
    /** Promoting one's own review (the app's BOOST_* pair, parity-tested). */
    const val BOOST_SOL = 0.00607
    const val BOOST_SKR = 37.0
    val boostFloorLamports get() = Config.lamports(BOOST_SOL)
    val boostFloorSkrRaw get() = Config.skrRaw(BOOST_SKR)
    /** Earlier Pro prices stay floors (the 276 SKR licence of 15 Sep must still read). */
    val LEGACY_PRO_SKR: List<Double> = listOf(276.0)
    const val LIST_SOL = 0.0607
    const val LIST_SKR = 369.0
    const val LIST_SKR_SGT = 332.0
    /** Earlier listing prices stay floors, exactly as the app's LEGACY_LIST_* lists (parity-tested). */
    val LEGACY_LIST_SOL: List<Double> = emptyList()
    val LEGACY_LIST_SKR: List<Double> = emptyList()
    val proFloorLamports get() = Config.lamports(PRO_SOL)
    val proFloorSkrRaw get() = (LEGACY_PRO_SKR + PRO_SKR).minOf { Config.skrRaw(it) }
    val listFloorLamports get() = (LEGACY_LIST_SOL + LIST_SOL).minOf { Config.lamports(it) }
    val listFloorSkrRaw get() = (LEGACY_LIST_SKR + LIST_SKR + LIST_SKR_SGT).minOf { Config.skrRaw(it) }

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
    /** (package, wallet) → "store_build" or "website": how each verified claim was proven. */
    val proofHow: Map<Pair<String, String>, String> = emptyMap(),
    /** Record ids whose TEXT this site does not show (the operator's hide list): the record itself stays listed and on-chain. */
    val operatorHidden: Map<String, String> = emptyMap(),
    /** Review id → the reviewer's stake as first read after the review appeared (shown, never weighed). */
    val reviewStake: Map<String, com.undersigned.protocol.StakeView.Snapshot> = emptyMap(),
) {
    val byPackage: Map<String, CatalogueApp> = catalogue.associateBy { it.packageName }
    val revoked: Set<String> = Ranking.revoked(entries)
    /** Raw facts, for display; [f] is what every score, flag, vote and hide reads (the operator's wallets count nowhere). */
    private val raw = { w: String -> facts[w] ?: SignerFacts(false, 0.0) }
    private val f = com.undersigned.protocol.Operator.gate(raw)
    /**
     * The app's reading of "paid" for the shared weighting: a `pro` row here is already a payment (see [Indexer.syncLedger]);
     * a `bst` row counts when its transaction credited the treasury with at least the boost price.
     */
    val paidRecord: (LedgerEntry) -> Boolean = { e -> when (e.memo) {
        is ReviewMemo.Pro -> true
        is ReviewMemo.Boost -> meta[e.signature.substringBefore(':')]?.let { it.lamports >= AppFloors.boostFloorLamports || it.skr >= AppFloors.boostFloorSkrRaw } == true
        else -> false } }
    val weighting = com.undersigned.protocol.Weighting.Context(entries, Reputation.hiddenByConsensus(entries, verified, f), f, paidRecord, verified)
    val scores = Ranking.score(entries, verified, facts = f).associateBy { it.packageName }
    /** Beside [scores], never blended into it: Pro members' reviews and promoted reviews as their own scores. */
    val segments = Ranking.segments(entries, verified, paidRecord, f)
    val standings = Reputation.standing(entries, verified, f)
    val hidden = Reputation.hiddenByConsensus(entries, verified, f)
    /** Text a moderator hides (protocol Moderation): the record, rating and tags stay. */
    val modHidden: Map<String, ReviewMemo.ModReason> = com.undersigned.protocol.Moderation.textHidden(entries, verified)
    val flaggers = Reputation.currentFlaggers(entries)
    val helpful = Reputation.helpfulWeights(entries, f)
    val disputes = Reputation.disputes(entries)
    val similarity = com.undersigned.protocol.Similarity.findings(entries)
    val usage = Usage.index(entries)
    val useTimes = Usage.time(entries)
    val notes = StoreNotes.current(entries, verified)
    fun factsOf(w: String) = raw(w)
    fun countsFor(w: String) = f(w)
    /** Paid listings only (see Payments.claims); the Undersigned treasury lists without paying. */
    val claims: List<Payments.Claim> = Payments.claims(entries, paid = { e ->
        e.signer == Config.TREASURY || meta[e.signature.substringBefore(':')]?.let { it.lamports >= AppFloors.listFloorLamports || it.skr >= AppFloors.listFloorSkrRaw } == true
    }, isPublisher = { w, pkg -> w in verified[pkg].orEmpty() })
    companion object { val EMPTY = State(emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyList(), emptyMap(), 0L) }
}

class Indexer(private val db: Db, private val rpc: Rpc = Rpc(), private val store: Store = Store()) {
    val names = Names(db, rpc)
    @Volatile var state: State = State.EMPTY; private set
    @Volatile var lastLedgerSync: Long = 0L; private set
    @Volatile var lastStoreSync: Long = 0L; private set
    @Volatile var lastError: String? = null; private set
    @Volatile var skippedTx: Int = 0; private set
    /** Wallets a request asked about that the ledger has not met: read by the next round, never inside a request. */
    val wanted: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    // ---- Ledger ---------------------------------------------------------------------------------------------------

    /** Reads every registry transaction not yet held (whole history on the first run, the tail after). Returns new records. */
    fun syncLedger(): Int {
        val head = db.get("ledger_head")
        // Held already: a transaction with records, or one read but recording nothing (a pass payment, a bare UNS), or one
        // given up on after three failed rounds. Without the last two, the tail was re-fetched every five minutes forever.
        val known = (db.query("SELECT DISTINCT sig FROM records") { it.getString(1) } + db.query("SELECT sig FROM txmeta") { it.getString(1) } +
            db.query("SELECT k FROM kv WHERE k LIKE 'ledger_gaveup:%'") { it.getString(1).removePrefix("ledger_gaveup:") }).toHashSet()
        var before: String? = null; var seenHead: String? = null; var readToEnd = false; var skipped = 0; var added = 0
        while (true) {
            val page = rpc.signatures(Config.REGISTRY, before = before, until = head)
            if (page.isEmpty()) { readToEnd = true; break }
            if (seenHead == null) seenHead = page.first().signature
            for (row in page) {
                if (row.err || row.memo == null || !(row.memo.contains("SRL1|") || row.memo.contains(Config.PAYMENT_MEMO))) continue
                if (row.signature in known) continue
                val tx = retry { rpc.tx(row.signature) } ?: run {
                    val fails = (db.get("ledger_fail:${row.signature}")?.toIntOrNull() ?: 0) + 1
                    if (fails >= 3) { db.put("ledger_gaveup:${row.signature}", fails.toString()); log("ledger: gave up on ${row.signature.take(10)} after $fails rounds") } else { db.put("ledger_fail:${row.signature}", fails.toString()); skipped++ }
                    null } ?: continue
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
                    if (records == 0 && Config.API_REFERENCE !in tx.accountKeys && (tx.lamportsIn >= AppFloors.proFloorLamports || skr >= AppFloors.proFloorSkrRaw)) {   // an API pass is never a licence
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
        skippedTx = skipped; lastLedgerSync = System.currentTimeMillis()
        return added
    }

    fun loadEntries(): Pair<List<LedgerEntry>, Map<String, TxMeta>> {
        val entries = db.query("SELECT id, signer, block_time, memo FROM records") { rs ->
            ReviewMemo.decode(rs.getString(4))?.let { LedgerEntry(rs.getString(1), rs.getString(2), rs.getLong(3), it) }
        }.filterNotNull().sortedWith(compareByDescending<LedgerEntry> { it.blockTime }.thenBy { it.signature })
        val meta = db.query("SELECT sig, fee, seeker, lamports, skr FROM txmeta") { it.getString(1) to TxMeta(it.getLong(2), it.getInt(3) == 1, it.getLong(4), it.getLong(5)) }.toMap()
        return entries to meta
    }

    // ---- Signer facts: attested device (Seeker Genesis Token), and staked SKR and SOL shown (never weighed) --------

    /** What the cache holds for [wallet], however old, or null when it was never read. Never touches the chain. */
    fun cachedFacts(wallet: String): SignerFacts? = db.query("SELECT sgt, staked, sol FROM facts WHERE wallet = ?", wallet) { SignerFacts(it.getInt(1) == 1, it.getDouble(2), stakedSol = it.getObject(3) as? Double) }.firstOrNull()

    fun facts(wallet: String, force: Boolean = false): SignerFacts {
        val row = db.query("SELECT sgt, staked, at, sol FROM facts WHERE wallet = ?", wallet) { listOf(it.getInt(1) == 1, it.getDouble(2), it.getLong(3), it.getObject(4) as? Double) }.firstOrNull()
        fun of(r: List<Any?>) = SignerFacts(r[0] as Boolean, r[1] as Double, stakedSol = r[3] as Double?)
        if (!force && row != null && System.currentTimeMillis() - (row[2] as Long) < Config.FACTS_TTL_MS) return of(row)
        // A read that fails keeps what was known: an RPC outage must not turn every reviewer unattested until the next good round.
        val sgt = runCatching { rpc.holdsSeekerGenesisToken(wallet) }.getOrElse { return row?.let(::of) ?: SignerFacts(false, 0.0) }
        // Stake is no longer weighed (9 Oct 2026), so a refresh reads the Genesis Token only; stake is read ONCE per review,
        // when it first appears ([stakeNow] in rebuild), and kept with it. No repeated stake scans.
        db.exec("INSERT OR REPLACE INTO facts (wallet, sgt, staked, at, sol) VALUES (?, ?, 0, ?, NULL)", wallet, if (sgt) 1 else 0, System.currentTimeMillis())
        return SignerFacts(sgt, 0.0)
    }

    /** The wallet's staked SKR and SOL now (one read), for a review's at-the-time snapshot; null when the chain would not answer. */
    private fun stakeNow(wallet: String): com.undersigned.protocol.StakeView.Snapshot? {
        val skr = runCatching { stakedSkr(wallet) }.getOrNull() ?: return null
        val sol = runCatching { com.undersigned.protocol.StakeView.solStaked(rpc.stakeAccounts(wallet)) }.getOrNull()
        return com.undersigned.protocol.StakeView.Snapshot(skr, sol, System.currentTimeMillis() / 1000)
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

    private class Proof(val at: Long, val body: String?, val lastGood: String?)
    private val bodies = java.util.concurrent.ConcurrentHashMap<String, Proof>()
    /** A proof file, at most [Config.MAX_PROOF_BYTES]. A good read is kept 6 h; a failed one is retried after 10 min and the last good body stands in meanwhile. */
    fun proofBody(url: String): String? {
        val now = System.currentTimeMillis(); val c0 = bodies[url]
        if (c0 != null && now - c0.at < (if (c0.body != null) 6 * 3600_000L else 10 * 60_000L)) return c0.body ?: c0.lastGood
        val body = runCatching {
            val c = URL(url).openConnection() as HttpURLConnection
            try { c.connectTimeout = 8000; c.readTimeout = 8000; c.instanceFollowRedirects = true
                if (c.responseCode !in 200..299) null else c.inputStream.use { inp -> val b = inp.readNBytes(Config.MAX_PROOF_BYTES + 1); if (b.size > Config.MAX_PROOF_BYTES) null else String(b, Charsets.UTF_8) } } finally { c.disconnect() }
        }.getOrNull()
        bodies[url] = Proof(now, body, body ?: c0?.lastGood)
        return body ?: c0?.lastGood
    }

    /** Website-proven claims (the API cannot see an installed APK), expanded to every app listing the same proof file. */
    /**
     * Proven claims, strongest first: (1) the store's own current build names the wallet and is signed by the certificate
     * the store lists (read from the APK the store serves; see Apk); (2) the store-listed website's proof file, expanded to
     * every app listing the same file. [how] says which, per (package, wallet).
     */
    private fun verifyClaims(entries: List<LedgerEntry>, details: Map<String, StoreDetails>): Triple<Map<String, Set<String>>, Map<Pair<String, String>, Long>, Map<Pair<String, String>, String>> {
        val out = HashMap<String, MutableSet<String>>(); val since = HashMap<Pair<String, String>, Long>(); val how = HashMap<Pair<String, String>, String>()
        for (c in entries.filter { it.memo is ReviewMemo.Claim }) {
            val pkg = (c.memo as ReviewMemo.Claim).packageName
            val d = details[pkg]
            val build = d?.let { apkFacts(pkg, it.versionCode) }
            if (build != null && c.signer in build.declared && d.certFingerprint != null && d.certFingerprint in build.certSha256) {
                out.getOrPut(pkg) { HashSet() }.add(c.signer); how[pkg to c.signer] = "store_build"; continue
            }
            val url = details[pkg]?.website?.let { PublisherProof.wellKnownUrl(it) } ?: continue
            val body = proofBody(url) ?: continue
            if (body.lines().none { it.trim() == c.signer }) continue
            out.getOrPut(pkg) { HashSet() }.add(c.signer); how[pkg to c.signer] = "website"
            db.exec("INSERT OR IGNORE INTO proofs (url, wallet, first_seen) VALUES (?, ?, ?)", url, c.signer, System.currentTimeMillis() / 1000)
            db.query("SELECT first_seen FROM proofs WHERE url = ? AND wallet = ?", url, c.signer) { it.getLong(1) }.firstOrNull()?.let { since[pkg to c.signer] = it }
        }
        val reviewed = entries.mapNotNull { (it.memo as? ReviewMemo.Review)?.packageName }.toSet()
        return Triple(PublisherProof.expandToSameProof(out, reviewed) { details[it]?.website }, since, how)
    }

    /**
     * Claimed apps whose current store build has not been read: fetch their details (one store request each) and read the
     * build. Each app is tried at most every 6 hours, so a new claim is checked within minutes, not at tomorrow's crawl.
     */
    fun syncClaimedBuilds(): Int {
        val (entries, _) = loadEntries()
        val claimed = entries.mapNotNull { (it.memo as? ReviewMemo.Claim)?.packageName }.toSet()
        var read = 0
        for (pkg in claimed) {
            val known = loadDetails()[pkg]
            if (known != null && apkFacts(pkg, known.versionCode) != null) continue
            val key = "build_tried:$pkg"
            if (System.currentTimeMillis() - (db.get(key)?.toLongOrNull() ?: 0L) < 6 * 3600_000L) continue
            db.put(key, System.currentTimeMillis().toString())
            val d = store.details(pkg) ?: continue
            upsertDetails(pkg, d, System.currentTimeMillis())
            readStoreBuild(pkg, d); read++
        }
        return read
    }

    /** Writes an app's store details and records a version or certificate change against what was held, wherever it is seen. */
    fun upsertDetails(pkg: String, d: StoreDetails, now: Long) {
        loadDetails()[pkg]?.let { o ->
            if (o.versionCode != d.versionCode && d.versionCode > 0) db.exec("INSERT INTO changes (pkg, at, kind, old, new) VALUES (?, ?, 'version', ?, ?)", pkg, now / 1000, o.versionCode.toString(), d.versionCode.toString())
            if (o.certFingerprint != null && d.certFingerprint != null && o.certFingerprint != d.certFingerprint) db.exec("INSERT INTO changes (pkg, at, kind, old, new) VALUES (?, ?, 'certificate', ?, ?)", pkg, now / 1000, o.certFingerprint, d.certFingerprint)
        }
        db.exec("INSERT OR REPLACE INTO details (pkg, version, cert, publisher, website, email, notes, at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", pkg, d.versionCode, d.certFingerprint, d.publisherName, d.website, d.supportEmail, d.newInVersion, now)
    }

    /** What one store build says, once read: cached per (package, versionCode). Null when not read yet. */
    fun apkFacts(pkg: String, version: Long): Apk.Facts? = db.query("SELECT cert, declared FROM apk_proofs WHERE pkg = ? AND version = ?", pkg, version) { rs ->
        Apk.Facts(rs.getString(2).split(',').filter { it.isNotEmpty() }.toSet(), rs.getString(1).split(',').filter { it.isNotEmpty() }.toSet()) }.firstOrNull()

    /** Downloads and reads a claimed app's current store build, once per version. Paced; at most 300 MB. */
    private fun readStoreBuild(pkg: String, d: StoreDetails) {
        val uri = d.apkUri ?: return
        if (apkFacts(pkg, d.versionCode) != null) return
        val tmp = java.io.File.createTempFile("undersigned-", ".apk")
        try {
            val c = URL(uri).openConnection() as HttpURLConnection
            try {
                c.connectTimeout = 10000; c.readTimeout = 60000; c.setRequestProperty("User-Agent", "Undersigned-API/1.0")
                if (c.responseCode != 200) { log("store build $pkg: HTTP ${c.responseCode}"); return }
                c.inputStream.use { inp -> tmp.outputStream().use { out -> val buf = ByteArray(1 shl 16); var total = 0L
                    while (true) { val n = inp.read(buf); if (n < 0) break; total += n; if (total > Config.MAX_APK_BYTES) { log("store build $pkg: over ${Config.MAX_APK_BYTES shr 20} MB, skipped"); return }; out.write(buf, 0, n) } } }
            } finally { c.disconnect() }
            val f = runCatching { Apk.read(tmp) }.getOrElse { e ->
                log("store build $pkg v${d.versionCode}: unreadable (${e.message?.take(60)}); recorded as proving nothing")
                db.exec("INSERT OR REPLACE INTO apk_proofs (pkg, version, cert, declared, at) VALUES (?, ?, '', '', ?)", pkg, d.versionCode, System.currentTimeMillis() / 1000); return }
            db.exec("INSERT OR REPLACE INTO apk_proofs (pkg, version, cert, declared, at) VALUES (?, ?, ?, ?, ?)", pkg, d.versionCode, f.certSha256.joinToString(","), f.declared.joinToString(","), System.currentTimeMillis() / 1000)
            log("store build $pkg v${d.versionCode}: ${f.declared.size} declared wallet(s), cert ${if (d.certFingerprint in f.certSha256) "matches" else "DOES NOT match"} the store")
        } catch (e: Exception) { log("store build $pkg: ${e.message?.take(80)}") } finally { tmp.delete() }
    }

    // ---- The store ------------------------------------------------------------------------------------------------

    /** The dApp list the Android app ships (app/src/main/assets/catalogue_snapshot.json, copied in by the build). */
    fun shippedCatalogue(): Map<String, CatalogueApp> = runCatching {
        val text = javaClass.getResourceAsStream("/catalogue_snapshot.json")?.bufferedReader()?.use { it.readText() } ?: return emptyMap()
        val arr = JSONArray(text)
        (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); CatalogueApp(o.getString("p"), o.optString("n").ifEmpty { o.getString("p") }, o.optString("u"), o.optString("c").takeIf { it.isNotEmpty() && it != "null" }) }
            .associateBy { it.packageName }
    }.getOrDefault(emptyMap())

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
        val shipped = shippedCatalogue()
        val have = loadCatalogue().size
        if (!force && now - (db.get("store_synced")?.toLongOrNull() ?: 0L) < Config.CATALOGUE_EVERY_MS && have >= shipped.size) return
        log("store sync: $have known, ${shipped.size} shipped${if (force) ", forced" else ""}")
        // Union, never replace (7 Oct 2026): listings in no category are invisible to the crawl. Keep what was known,
        // discover more from the portal's Arweave records, and drop an app only when the store answered without it.
        store.catalogue()?.let { crawl ->
            val out = LinkedHashMap<String, CatalogueApp>(); crawl.forEach { out[it.packageName] = it }
            // The app's shipped list counts as known: its apps are checked live below like any other, and kept if the store is silent.
            val known = (shipped + loadCatalogue().associateBy { it.packageName })
            val extra = (known.keys - out.keys) + store.discover().filter { it !in out && it !in known }
            var answered = true
            for (chunk in extra.toList().chunked(Config.STORE_BATCH)) {
                val live = store.live(chunk) ?: run { answered = false; null } ?: continue
                live.values.forEach { a -> out[a.packageName] = a.copy(category = known[a.packageName]?.category) }
            }
            if (!answered) known.values.forEach { if (it.packageName !in out) out[it.packageName] = it }
            val arr = JSONArray(); out.values.forEach { arr.put(JSONObject().put("p", it.packageName).put("n", it.name).put("u", it.updated).put("c", it.category)) }
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
        for (pkg in wanted) {
            val d = store.details(pkg) ?: continue
            upsertDetails(pkg, d, now)
            if (entries.any { (it.memo as? ReviewMemo.Claim)?.packageName == pkg }) { readStoreBuild(pkg, d); Thread.sleep(Config.STORE_PACE_MS) }
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

    // ---- The operator's hide list: what this SITE shows, never what the chain holds ----------------------------------
    fun operatorHidden(): Map<String, String> = db.query("SELECT id, reason FROM operator_hidden") { it.getString(1) to it.getString(2) }.toMap()
    fun hide(id: String, reason: String) { db.exec("INSERT OR REPLACE INTO operator_hidden (id, reason, at) VALUES (?, ?, ?)", id, reason, System.currentTimeMillis() / 1000) }
    fun unhide(id: String): Boolean = db.exec("DELETE FROM operator_hidden WHERE id = ?", id) == 1

    /** One copy a day beside the database, the newest 14 kept. */
    @Volatile var lastBackup: String? = null; private set
    fun backupDaily() {
        val dir = java.io.File(java.io.File(Config.DB_PATH).absoluteFile.parentFile, "backups")
        val day = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        val f = java.io.File(dir, "undersigned-$day.db")
        if (!f.exists()) { db.backupTo(f); log("backup: ${f.name} (${f.length() / 1024} KB)") }
        dir.listFiles { x -> x.name.startsWith("undersigned-") && x.name.endsWith(".db") }?.sortedByDescending { it.name }?.drop(14)?.forEach { it.delete() }
        lastBackup = day
    }

    /** Days on which the store-wide snapshot ran, oldest first (Store watch needs 8). */
    fun snapshotDays(): List<String> = db.query("SELECT DISTINCT day FROM ratings ORDER BY day") { it.getString(1) }

    /** One rating burst across the store, with its daily series, as the watch page and the hooks show it. */
    data class WatchHit(val pkg: String, val burst: com.undersigned.protocol.ReviewBursts.Burst, val spark: com.undersigned.protocol.BurstSpark.Spark)
    @Volatile private var watchCache: Pair<String, List<WatchHit>> = "" to emptyList()
    /**
     * Every rating burst in the store, strongest first, computed once per snapshot day (the inputs only change when a
     * new day of snapshots lands) instead of once per request: a free page used to recompute every app's bursts.
     */
    fun watchHits(): List<WatchHit> {
        val key = snapshotDays().lastOrNull() ?: return emptyList()
        watchCache.takeIf { it.first == key }?.let { return it.second }
        val hits = ratingPackages().flatMap { pkg ->
            val pts = com.undersigned.protocol.RatingSnapshots.toPoints(snapshots(pkg))
            com.undersigned.protocol.ReviewBursts.find(pts, com.undersigned.protocol.ReviewBursts.MIN_SNAPSHOT_DAYS).bursts.map { WatchHit(pkg, it, com.undersigned.protocol.BurstSpark.of(pts, it)) }
        }.sortedByDescending { it.burst.intensity }
        watchCache = key to hits
        return hits
    }

    // ---- Rebuild ----------------------------------------------------------------------------------------------------

    /** Recomputes [state] from the database: facts for every signer, claims, scores. */
    /** Called after each rebuild with the records the previous state did not have (none on the very first build). */
    @Volatile var onRebuilt: ((List<LedgerEntry>) -> Unit)? = null

    fun rebuild() {
        val before = state
        val (entries, meta) = loadEntries()
        val signers = entries.map { it.signer }.distinct()
        val facts = signers.associateWith { facts(it) }
        // Each review's stake as first read after it appeared (owner, 9 Oct 2026): shown beside it, never weighed.
        val haveStake = db.query("SELECT id FROM review_stake") { it.getString(1) }.toHashSet()
        // One stake read per wallet per rebuild, only for wallets with a review not yet snapshotted; never again for that review.
        val readNow = HashMap<String, com.undersigned.protocol.StakeView.Snapshot?>()
        entries.filter { it.memo is ReviewMemo.Review && it.signature !in haveStake && facts[it.signer]?.hasGenesisToken == true }.forEach { e ->
            val k = readNow.getOrPut(e.signer) { stakeNow(e.signer) } ?: return@forEach   // unknown: try again next rebuild
            db.exec("INSERT OR IGNORE INTO review_stake (id, skr, sol, read_at) VALUES (?, ?, ?, ?)", e.signature, k.skr, k.sol, k.readAt)
        }
        val reviewStake = db.query("SELECT id, skr, sol, read_at FROM review_stake") { it.getString(1) to com.undersigned.protocol.StakeView.Snapshot(it.getDouble(2), it.getObject(3) as? Double, it.getLong(4)) }.toMap()
        signers.forEach { runCatching { names.resolve(it) } }   // cached a week; only new signers cost a lookup
        val details = loadDetails()
        val (verified, since, how) = verifyClaims(entries, details)
        // Recusal (compliance review, 9 Oct 2026): the operator's site-only hide list never applies to an app the operator publishes.
        val byId = entries.associateBy { it.signature }
        fun pkgOfRecord(id: String): String? = when (val m = byId[id]?.memo) {
            is ReviewMemo.Review -> m.packageName
            is ReviewMemo.Reply -> (byId[m.reviewSignature]?.memo as? ReviewMemo.Review)?.packageName
            is ReviewMemo.StoreNote -> m.packageName
            else -> null
        }
        val hides = operatorHidden().filterKeys { id -> pkgOfRecord(id)?.let { com.undersigned.protocol.Operator.publishes(it, verified) } != true }
        state = State(entries, meta, facts, verified, since, loadCatalogue(), details, System.currentTimeMillis(), how, hides, reviewStake)
        if (before.builtAt > 0) { val had = before.entries.map { it.signature }.toHashSet(); entries.filter { it.signature !in had }.let { fresh -> runCatching { onRebuilt?.invoke(fresh) }.onFailure { log("after rebuild: ${it.message?.take(80)}") } } }
    }

    /** The background loop: ledger every 5 minutes, the store once a day. Errors are logged and retried next round. */
    fun start() {
        Thread({
            while (true) {
                runCatching { val n = syncLedger(); lastError = null; if (n > 0 || state.builtAt == 0L) log("ledger: +$n records") }.onFailure { lastError = "ledger: ${it.message?.take(100)}"; log("ledger sync failed: ${it.message?.take(100)}") }
                // Wallets pages asked about: read now (at most 50 a round), then they answer from cache.
                val ask = wanted.take(50); ask.forEach { w -> wanted.remove(w); runCatching { facts(w); names.resolve(w) } }
                // Serve the new records at once; the daily store crawl takes minutes and must not hold them back.
                runCatching { rebuild() }.onFailure { log("rebuild failed: ${it.message?.take(100)}") }
                runCatching { if (syncClaimedBuilds() > 0) rebuild() }.onFailure { log("store builds failed: ${it.message?.take(100)}") }
                val before = lastStoreSync
                runCatching { syncStore() }.onFailure { lastError = "store: ${it.message?.take(100)}"; log("store sync failed: ${it.message?.take(100)}") }
                runCatching { watchHits() }.onFailure { log("watch: ${it.message?.take(80)}") }
                runCatching { backupDaily() }.onFailure { lastError = "backup: ${it.message?.take(100)}"; log("backup failed: ${it.message?.take(100)}") }
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
