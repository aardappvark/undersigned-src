package com.undersigned.api

import com.undersigned.protocol.BurstSpark
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.Payments
import com.undersigned.protocol.PublisherProof
import com.undersigned.protocol.RatingSnapshots
import com.undersigned.protocol.Reputation
import com.undersigned.protocol.ReviewBursts
import com.undersigned.protocol.ReviewMemo
import org.json.JSONArray
import org.json.JSONObject

/** Reward periods, from the app's own seasons.json (bundled into the API's resources at build time). */
object Seasons {
    data class Season(val id: String, val label: String, val start: Long, val end: Long, val approximate: Boolean)
    val all: List<Season> by lazy {
        runCatching {
            val arr = JSONObject(Seasons::class.java.getResource("/seasons.json")!!.readText()).getJSONArray("seasons")
            (0 until arr.length()).map { i -> val o = arr.getJSONObject(i)
                Season(o.getString("id"), o.getString("label"), java.time.LocalDate.parse(o.getString("start")).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond(),
                    java.time.LocalDate.parse(o.getString("end")).plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond() - 1, o.optBoolean("approximate")) }
        }.getOrDefault(emptyList())
    }
    fun at(t: Long) = all.filter { t in it.start..it.end }.minByOrNull { it.end - it.start }
}

/** One listed dApp on the payments board, computed in the background (reads the chain; never inside a request). */
data class BoardRow(val claim: Payments.Claim, val name: String, val confirmed: Boolean, val tally: Payments.Tally?, val usdWhenPaid: Double?, val complete: Boolean,
                    val monthUsd: Map<String, Double>, val payers: Set<String>, val unpriced: Int = 0)

class Boards(private val indexer: Indexer, private val board: Board, private val prices: Prices) {
    @Volatile var rows: List<BoardRow> = emptyList(); private set
    fun refresh(s: State) {
        val h = runCatching { prices.history() }.getOrDefault(emptyMap())
        rows = s.claims.map { c ->
            val name = s.byPackage[c.packageName]?.name ?: c.packageName
            val url = s.details[c.packageName]?.website?.let { PublisherProof.wellKnownUrl(it) }
            val body = url?.let { indexer.proofBody(it) }
            val confirmed = body != null && c.treasury in Payments.listedTreasuries(body)
            if (!confirmed) return@map BoardRow(c, name, false, null, null, false, emptyMap(), emptySet())
            val read = runCatching { board.read(c.treasury, c.code) }.getOrElse { return@map BoardRow(c, name, true, null, null, false, emptyMap(), emptySet()) }
            val own = Payments.listedWallets(body!!)
            val tally = Payments.tally(read.payments, c.treasury, own) { s.factsOf(it).hasGenesisToken }
            val real = read.payments.filter { it.payer != c.treasury && it.payer !in own }
            val priced = real.map { it to prices.usdWhenPaid(it, h) }
            val usd = priced.mapNotNull { it.second }.let { l -> if (l.isEmpty() && real.isNotEmpty()) null else l.sum() }
            val fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM").withZone(java.time.ZoneOffset.UTC)
            val months = real.groupBy { fmt.format(java.time.Instant.ofEpochSecond(it.time)) }.mapValues { (_, l) -> l.sumOf { prices.usdWhenPaid(it, h) ?: 0.0 } }
            // A payer is someone who paid a real amount (protocol Payments.meaningful); the tally still counts every unit.
            BoardRow(c, name, true, tally, usd, read.complete, months, real.filter { Prices.meaningful(it) }.map { it.payer }.toSet(), priced.count { it.second == null })
        }.sortedByDescending { it.usdWhenPaid ?: -1.0 }
    }
}

class Views(private val indexer: Indexer, private val boards: Boards, private val db: Db) {
    /** The state a request started with, passed down: a rebuild in the middle of a request must not mix two ledgers. */
    private val s get() = indexer.state

    // ---- Records ------------------------------------------------------------------------------------------------

    fun type(m: ReviewMemo): String = ReviewMemo.encode(m).split('|').getOrElse(1) { "?" }

    fun record(e: LedgerEntry, st: State = indexer.state): JSONObject {
        val o = JSONObject().put("id", e.signature).put("transaction", e.signature.substringBefore(':')).put("signer", e.signer).put("time", e.blockTime).put("type", type(e.memo))
        when (val m = e.memo) {
            is ReviewMemo.Review -> o.put("package", m.packageName).put("rating", m.rating).put("tags", JSONArray(m.tags.map { it.wire })).put("version", m.versionCode).put("text", m.text ?: JSONObject.NULL)
            is ReviewMemo.Revocation -> o.put("target", m.reviewSignature)
            is ReviewMemo.Reply -> o.put("target", m.reviewSignature).put("text", m.text)
            is ReviewMemo.Claim -> o.put("package", m.packageName)
            is ReviewMemo.Flag -> o.put("target", m.recordId).put("reason", m.reason.wire)
            is ReviewMemo.Upvote -> o.put("target", m.recordId)
            is ReviewMemo.Unvote -> o.put("target", m.recordId)
            is ReviewMemo.Unflag -> o.put("target", m.recordId)
            is ReviewMemo.Clear -> o.put("target", m.recordId)
            is ReviewMemo.Dispute -> o.put("target", m.recordId).put("text", m.text)
            is ReviewMemo.Pro -> o.put("months", m.months)
            is ReviewMemo.Use -> o.put("package", m.packageName).put("bucket", m.bucket.wire)
            is ReviewMemo.Time -> o.put("package", m.packageName).put("bucket", m.time.wire)
            is ReviewMemo.StoreNote -> o.put("package", m.packageName).put("store_review", m.reviewId).put("text", m.text)
            is ReviewMemo.Treasury -> o.put("package", m.packageName).put("code", m.code)
            is ReviewMemo.Untreasury -> o.put("package", m.packageName)
            else -> {}
        }
        st.meta[e.signature.substringBefore(':')]?.let { o.put("fee_lamports", it.fee).put("seeker_wallet_shape", it.seekerShape) }
        o.put("withdrawn", e.signature in st.revoked)
        // The operator's hide list covers what THIS site serves: the text goes, the record and its facts stay, and it says so.
        val hiddenWhy = st.operatorHidden[e.signature]
        if (hiddenWhy != null && o.has("text")) o.put("text", JSONObject.NULL)
        o.put("hidden_by_operator", hiddenWhy != null)
        return o
    }

    fun recordById(id: String): JSONObject? { val st = s; return st.entries.firstOrNull { it.signature == id }?.let { record(it, st) } }

    fun records(type: String?, pkg: String?, signer: String?, since: Long?, until: Long?, before: String?, limit: Int): JSONObject {
        val st = s; var l = st.entries.asSequence()
        type?.let { t -> l = l.filter { type(it.memo) == t } }
        pkg?.let { p -> l = l.filter { packageOf(it.memo) == p } }
        signer?.let { w -> l = l.filter { it.signer == w } }
        since?.let { t -> l = l.filter { it.blockTime >= t } }
        until?.let { t -> l = l.filter { it.blockTime <= t } }
        val all = l.toList()
        val start = before?.let { b -> all.indexOfFirst { it.signature == b }.let { if (it < 0) all.size else it + 1 } } ?: 0
        val page = all.drop(start).take(limit)
        return JSONObject().put("records", JSONArray(page.map { record(it, st) })).put("next", if (start + limit < all.size) page.lastOrNull()?.signature ?: JSONObject.NULL else JSONObject.NULL)
    }

    private fun packageOf(m: ReviewMemo): String? = when (m) {
        is ReviewMemo.Review -> m.packageName; is ReviewMemo.Claim -> m.packageName; is ReviewMemo.Use -> m.packageName; is ReviewMemo.Time -> m.packageName
        is ReviewMemo.StoreNote -> m.packageName; is ReviewMemo.Treasury -> m.packageName; is ReviewMemo.Untreasury -> m.packageName; else -> null
    }

    // ---- Ledger-wide ----------------------------------------------------------------------------------------------

    /**
     * The open dataset: every decoded record, each app's signed score, and the public facts about every signer, as of the
     * last rebuild. Public data only (the chain and the store's own catalogue); the operator's hide list applies to text.
     */
    fun dataset(): JSONObject {
        val st = s
        return JSONObject().put("generated_at", st.builtAt / 1000).put("registry", Config.REGISTRY).put("licence", "Public on-chain data; use freely. Scores follow docs/SPEC.md.")
            .put("records", JSONArray(st.entries.map { record(it, st) }))
            .put("apps", JSONArray(st.scores.values.sortedBy { it.packageName }.map { sc -> JSONObject().put("package", sc.packageName).put("name", st.byPackage[sc.packageName]?.name ?: JSONObject.NULL)
                .put("rank_score", r2(sc.weightedRating)).put("mean", r2(sc.weightedMean)).put("counted_reviews", sc.countedReviews).put("tags", JSONObject(sc.tagCounts.mapKeys { it.key.wire })) }))
            .put("signers", JSONArray(st.entries.map { it.signer }.distinct().sorted().map { w -> val f = st.factsOf(w)
                JSONObject().put("wallet", w).put("attested_device", f.hasGenesisToken).put("staked_skr", r2(f.stakedSkr)).put("weight", r2(com.undersigned.protocol.Ranking.weight(f))) }))
    }

    fun stats(): JSONObject {
        val st = s; val all = st.entries
        val live = all.filter { it.memo is ReviewMemo.Review && it.signature !in st.revoked }
        val counted = live.filter { st.factsOf(it.signer).hasGenesisToken && !com.undersigned.protocol.Ranking.isSelfPublished(it, st.verified) }
        val signers = live.map { it.signer }.toSet()
        val byId = all.associateBy { it.signature }
        val replies = all.filter { it.memo is ReviewMemo.Reply }
        val pub = replies.count { r -> val p = (byId[(r.memo as ReviewMemo.Reply).reviewSignature]?.memo as? ReviewMemo.Review)?.packageName; p != null && st.verified[p]?.contains(r.signer) == true }
        val dated = all.filter { it.memo !is ReviewMemo.Pro }
        return JSONObject().put("records", all.size).put("reviews_live", live.size).put("reviews_counted", counted.size).put("signers", signers.size)
            .put("attested_signers", signers.count { st.factsOf(it).hasGenesisToken }).put("apps_reviewed", live.map { (it.memo as ReviewMemo.Review).packageName }.toSet().size)
            .put("apps_scored", st.scores.size).put("replies", replies.size).put("publisher_replies", pub)
            .put("claims", all.count { it.memo is ReviewMemo.Claim }).put("claims_verified", st.verified.values.sumOf { it.size })
            .put("flags", all.count { it.memo is ReviewMemo.Flag }).put("clears", all.count { it.memo is ReviewMemo.Clear }).put("disputes", all.count { it.memo is ReviewMemo.Dispute })
            .put("hidden_by_consensus", st.hidden.size).put("withdrawn", st.revoked.size).put("licences", all.count { it.memo is ReviewMemo.Pro })
            .put("treasury_listings", st.claims.size).put("first_record", dated.minOfOrNull { it.blockTime } ?: JSONObject.NULL).put("latest_record", dated.maxOfOrNull { it.blockTime } ?: JSONObject.NULL)
            .put("catalogue_apps", st.catalogue.size).put("indexed_at", st.builtAt / 1000)
    }

    fun tags(): JSONObject {
        val counts = HashMap<String, Int>()
        val st = s
        st.entries.filter { it.signature !in st.revoked }.forEach { e -> (e.memo as? ReviewMemo.Review)?.tags?.forEach { counts[it.wire] = (counts[it.wire] ?: 0) + 1 } }
        return JSONObject().put("tags", JSONArray(ReviewMemo.Tag.entries.map { JSONObject().put("wire", it.wire).put("count", counts[it.wire] ?: 0) }))
    }

    // ---- Apps -------------------------------------------------------------------------------------------------------

    fun apps(q: String?, category: String?, sort: String?, limit: Int): JSONObject {
        val st = s; val qq = q?.trim()?.lowercase()
        var l = st.catalogue.asSequence()
        if (!qq.isNullOrEmpty()) l = l.filter { it.name.lowercase().contains(qq) || it.packageName.lowercase().contains(qq) || it.category?.lowercase()?.contains(qq) == true }
        category?.let { c -> l = l.filter { it.category.equals(c, true) } }
        val sorted = when (sort) {
            "signed" -> l.sortedByDescending { st.scores[it.packageName]?.weightedRating ?: -1.0 }
            "updated" -> l.sortedByDescending { it.updated }
            else -> l.sortedBy { it.name.lowercase() }
        }.toList()
        return JSONObject().put("total", sorted.size).put("apps", JSONArray(sorted.take(limit).map { appRow(it.packageName) }))
    }

    private fun appRow(pkg: String): JSONObject {
        val st = s; val c = st.byPackage[pkg]; val sc = st.scores[pkg]
        return JSONObject().put("package", pkg).put("name", c?.name ?: pkg).put("category", c?.category ?: JSONObject.NULL).put("updated", c?.updated ?: JSONObject.NULL)
            .put("signed", if (sc == null) JSONObject.NULL else JSONObject().put("rank_score", r2(sc.weightedRating)).put("mean", r2(sc.weightedMean)).put("counted_reviews", sc.countedReviews))
    }

    fun app(pkg: String): JSONObject? {
        val st = s
        if (pkg !in st.byPackage && st.entries.none { packageOf(it.memo) == pkg }) return null
        val o = appRow(pkg)
        val sc = st.scores[pkg]
        val live = liveReviews(pkg, st)
        val counted = live.filter { st.factsOf(it.signer).hasGenesisToken }
        o.put("signed", JSONObject()
            .put("rank_score", sc?.weightedRating?.let(::r2) ?: JSONObject.NULL).put("mean", sc?.weightedMean?.let(::r2) ?: JSONObject.NULL)
            .put("counted_reviews", sc?.countedReviews ?: 0).put("live_reviews", live.size)
            .put("stars", JSONArray((1..5).map { star -> counted.count { (it.memo as ReviewMemo.Review).rating == star } }))
            .put("tags", JSONObject((sc?.tagCounts ?: emptyMap()).mapKeys { it.key.wire }))
            .put("latest_version_reviewed", sc?.latestVersionReviewed ?: JSONObject.NULL)
            .put("install_age", JSONObject(live.mapNotNull { st.usage[it.signature]?.wire }.groupingBy { it }.eachCount()))
            .put("usage_time", JSONObject(live.mapNotNull { st.useTimes[it.signature]?.wire }.groupingBy { it }.eachCount())))
        st.details[pkg]?.let { d -> o.put("store", JSONObject().put("version", d.versionCode).put("certificate_sha256", d.certFingerprint ?: JSONObject.NULL)
            .put("publisher", d.publisherName ?: JSONObject.NULL).put("website", d.website ?: JSONObject.NULL).put("support_email", d.supportEmail ?: JSONObject.NULL)
            .put("whats_new", d.newInVersion ?: JSONObject.NULL)) }
        indexer.latestRating(pkg)?.let { (day, stars) -> o.put("store_rating", JSONObject().put("as_of", day).put("stars", JSONArray(stars)).put("count", stars.sum())
            .put("average", if (stars.sum() == 0) JSONObject.NULL else r2(stars.withIndex().sumOf { (i, n) -> (i + 1) * n }.toDouble() / stars.sum()))) }
        val vp = st.verified[pkg].orEmpty()
        o.put("publisher", JSONObject()
            .put("claims", JSONArray(st.entries.filter { (it.memo as? ReviewMemo.Claim)?.packageName == pkg }.map { c -> JSONObject().put("wallet", c.signer).put("time", c.blockTime)
                .put("verified", c.signer in vp).put("proof", if (c.signer in vp) st.proofHow[pkg to c.signer] ?: "same_website" else JSONObject.NULL).put("proof_first_seen", st.proofSince[pkg to c.signer] ?: JSONObject.NULL) }))
            .put("verified_wallets", JSONArray(vp.toList()))
            .put("responsiveness", Reputation.responsiveness(st.entries, pkg, vp).let { r -> JSONObject().put("answered", r.answered).put("total", r.total).put("median_hours", r.medianHours?.let(::r2) ?: JSONObject.NULL) })
            .put("store_notes", JSONArray(st.notes.filter { it.key.first == pkg }.values.map { n -> val m = n.memo as ReviewMemo.StoreNote
                JSONObject().put("store_review", m.reviewId).put("wallet", n.signer).put("time", n.blockTime).put("text", if (n.signature in st.operatorHidden) JSONObject.NULL else m.text).put("hidden_by_operator", n.signature in st.operatorHidden) })))
        boards.rows.firstOrNull { it.claim.packageName == pkg && it.confirmed }?.let { o.put("payments", boardJson(it, false)) }
        return o
    }

    private fun liveReviews(pkg: String, st: State = indexer.state): List<LedgerEntry> =
        st.entries.filter { (it.memo as? ReviewMemo.Review)?.packageName == pkg && it.signature !in st.revoked }.groupBy { it.signer }.map { (_, l) -> l.maxWith(compareBy<LedgerEntry> { it.blockTime }.thenBy { it.signature }) }

    fun reviews(pkg: String, limit: Int, offset: Int): JSONObject {
        val st = s
        val all = liveReviews(pkg, st).sortedByDescending { it.blockTime }
        val paid = boards.rows.firstOrNull { it.claim.packageName == pkg }?.payers.orEmpty()
        val byTarget = st.entries.filter { it.memo is ReviewMemo.Reply }.groupBy { (it.memo as ReviewMemo.Reply).reviewSignature }
        val vp = st.verified[pkg].orEmpty()
        val list = all.drop(offset).take(limit).map { e ->
            val r = e.memo as ReviewMemo.Review; val f = st.factsOf(e.signer)
            record(e, st).put("reviewer", JSONObject().put("wallet", e.signer).put("skr_name", indexer.names.nameOf(e.signer) ?: JSONObject.NULL).put("attested_device", f.hasGenesisToken).put("staked_skr", r2(f.stakedSkr)).put("weight", r2(com.undersigned.protocol.Ranking.weight(f)))
                    .put("credibility", st.standings[e.signer]?.credibility ?: JSONObject.NULL))
                .put("counted", f.hasGenesisToken && !com.undersigned.protocol.Ranking.isSelfPublished(e, st.verified))
                .put("self_published", com.undersigned.protocol.Ranking.isSelfPublished(e, st.verified))
                .put("install_age", st.usage[e.signature]?.wire ?: JSONObject.NULL).put("usage_time", st.useTimes[e.signature]?.wire ?: JSONObject.NULL)
                .put("season", Seasons.at(e.blockTime)?.let { JSONObject().put("id", it.id).put("label", it.label) } ?: JSONObject.NULL)
                .put("paid_for_app", e.signer in paid)
                .put("similar", st.similarity[e.signature]?.let { JSONObject().put("to", JSONArray(it.similarTo)).put("across_apps", it.copiedAcrossApps) } ?: JSONObject.NULL)
                .put("flaggers", st.flaggers[e.signature]?.size ?: 0).put("hidden_by_consensus", e.signature in st.hidden)
                .put("helpful_weight", r2(st.helpful[e.signature] ?: 0.0)).put("disputed", st.disputes.containsKey(e.signature))
                .put("replies", JSONArray((byTarget[e.signature] ?: emptyList()).sortedBy { it.blockTime }.filter { it.signature !in st.hidden }.map { rp ->
                    JSONObject().put("id", rp.signature).put("wallet", rp.signer).put("time", rp.blockTime).put("text", if (rp.signature in st.operatorHidden) JSONObject.NULL else (rp.memo as ReviewMemo.Reply).text)
                        .put("hidden_by_operator", rp.signature in st.operatorHidden).put("publisher", rp.signer in vp) }))
        }
        return JSONObject().put("package", pkg).put("total", all.size).put("reviews", JSONArray(list))
    }

    fun versions(pkg: String): JSONObject { val st = s; return JSONObject().put("package", pkg).put("versions", JSONArray(
        Reputation.byVersion(st.entries, pkg, st.verified) { st.factsOf(it) }.map { JSONObject().put("version", it.versionCode).put("mean", r2(it.mean)).put("count", it.count) })) }

    fun similar(pkg: String): JSONObject { val st = s; return JSONObject().put("package", pkg).put("findings", JSONArray(liveReviews(pkg, st).mapNotNull { e ->
        st.similarity[e.signature]?.let { JSONObject().put("review", e.signature).put("similar_to", JSONArray(it.similarTo)).put("across_apps", it.copiedAcrossApps) } })) }

    // ---- Bursts ------------------------------------------------------------------------------------------------------

    fun bursts(pkg: String): JSONObject {
        val st = s
        val live = liveReviews(pkg, st)
        val points = live.map { e -> val r = e.memo as ReviewMemo.Review; ReviewBursts.Point(e.blockTime, r.rating, r.text) }
        val signed = ReviewBursts.find(points)
        val ratingPoints = RatingSnapshots.toPoints(indexer.snapshots(pkg))
        val ratings = ReviewBursts.find(ratingPoints, ReviewBursts.MIN_SNAPSHOT_DAYS)
        fun burstJson(b: ReviewBursts.Burst, pts: List<ReviewBursts.Point>, withAttested: Boolean): JSONObject {
            val o = JSONObject().put("kind", b.kind.name.lowercase()).put("start", b.start).put("end", b.end).put("count", b.count).put("total_in_window", b.total)
                .put("expected", r2(b.expected)).put("multiple", r2(BurstSpark.multiple(b))).put("same_text", b.sameText)
                .put("season", Seasons.all.firstOrNull { it.start <= b.end && b.start <= it.end }?.label ?: JSONObject.NULL)
            BurstSpark.of(pts, b).let { sp -> o.put("spark", JSONObject().put("daily_counts", JSONArray(sp.counts)).put("burst_from", sp.from).put("burst_to", sp.to)) }
            if (withAttested) {
                val inWin = live.filter { it.blockTime in b.start..b.end && (it.memo as ReviewMemo.Review).rating == b.kind.stars }
                o.put("attested", inWin.count { st.factsOf(it.signer).hasGenesisToken }).put("of", inWin.size)
            }
            return o
        }
        return JSONObject().put("package", pkg)
            .put("signed_reviews", JSONObject().put("checked", signed.checked).put("bursts", JSONArray(signed.bursts.map { burstJson(it, points, true) })))
            .put("store_ratings", JSONObject().put("snapshot_days", indexer.snapshots(pkg).size).put("bursts", JSONArray(ratings.bursts.map { burstJson(it, ratingPoints, false) })))
            .put("note", "A burst is a fact about timing, not a verdict about the reviews.")
    }

    fun storeRatings(pkg: String): JSONObject = JSONObject().put("package", pkg).put("snapshots", JSONArray(indexer.snapshots(pkg).map {
        JSONObject().put("day", java.time.Instant.ofEpochSecond(it.time).toString().take(10)).put("stars", JSONArray(it.byStar)) }))

    /**
     * The weekly Store watch report: every store app whose star counts jumped in the last [days], strongest first, each
     * with its 30-day daily series. Before 8 days of snapshots exist it says how far collection has got instead.
     */
    /**
     * The weekly Store watch report: every store app whose star counts jumped in the last [days], strongest first, each
     * with its 30-day daily series. Computed by the indexer once per snapshot day (Indexer.watchHits), never per request.
     * Before 8 days of snapshots exist it says how far collection has got instead.
     */
    fun watchReport(days: Int = 7): JSONObject {
        val st = s
        val snapDays = indexer.snapshotDays()
        val need = ReviewBursts.MIN_SNAPSHOT_DAYS.toInt() + 1
        val o = JSONObject().put("days", days).put("snapshot_days", snapDays.size).put("needed", need)
            .put("first_day", snapDays.firstOrNull() ?: JSONObject.NULL).put("last_day", snapDays.lastOrNull() ?: JSONObject.NULL)
            .put("apps_read", indexer.ratingPackages().size)
        if (snapDays.size < need) return o.put("ready", false).put("hits", JSONArray())
        val fresh = System.currentTimeMillis() / 1000 - days * ReviewBursts.DAY
        return o.put("ready", true).put("hits", JSONArray(indexer.watchHits().filter { it.burst.end >= fresh }.map { h -> val b = h.burst
            JSONObject().put("package", h.pkg).put("name", st.byPackage[h.pkg]?.name ?: h.pkg).put("category", st.byPackage[h.pkg]?.category ?: JSONObject.NULL)
                .put("kind", b.kind.name.lowercase()).put("start", b.start).put("end", b.end).put("count", b.count).put("expected", r2(b.expected)).put("multiple", r2(BurstSpark.multiple(b)))
                .put("spark", JSONObject().put("daily_counts", JSONArray(h.spark.counts)).put("burst_from", h.spark.from).put("burst_to", h.spark.to))
        }))
    }

    fun watch(days: Int): JSONObject {
        val st = s
        val fresh = System.currentTimeMillis() / 1000 - days * ReviewBursts.DAY
        return JSONObject().put("days", days).put("hits", JSONArray(indexer.watchHits().filter { it.burst.end >= fresh }.map { h -> val b = h.burst
            JSONObject().put("package", h.pkg).put("name", st.byPackage[h.pkg]?.name ?: h.pkg)
            .put("kind", b.kind.name.lowercase()).put("start", b.start).put("end", b.end).put("count", b.count).put("expected", r2(b.expected)).put("multiple", r2(BurstSpark.multiple(b))) }))
            .put("note", "Store watch needs ${ReviewBursts.MIN_SNAPSHOT_DAYS + 1} days of daily snapshots before it can show anything.")
    }

    fun changes(since: Long?): JSONObject = JSONObject().put("changes", JSONArray(
        db.query("SELECT pkg, at, kind, old, new FROM changes WHERE at >= ? ORDER BY at DESC LIMIT 500", since ?: 0L) { rs ->
            JSONObject().put("package", rs.getString(1)).put("time", rs.getLong(2)).put("kind", rs.getString(3)).put("old", rs.getString(4)).put("new", rs.getString(5)) }))

    // ---- Wallets -----------------------------------------------------------------------------------------------------

    fun wallet(addr: String, full: Boolean): JSONObject {
        // A wallet the ledger has not met is answered from the cache only; unknown wallets are queued for the indexer,
        // so a page of random addresses never spends chain reads or holds request threads. null = not read yet.
        val st = s; val f = st.facts[addr] ?: indexer.cachedFacts(addr); val stand = st.standings[addr]
        if (f == null) indexer.wanted.add(addr)
        val mine = st.entries.filter { it.signer == addr }
        val reviews = mine.filter { it.memo is ReviewMemo.Review && it.signature !in st.revoked }
        val o = JSONObject().put("wallet", addr).put("skr_name", indexer.names.cached(addr)?.ifEmpty { null } ?: JSONObject.NULL).put("read", f != null)
            .put("attested_device", f?.hasGenesisToken ?: JSONObject.NULL).put("staked_skr", f?.let { r2(it.stakedSkr) } ?: JSONObject.NULL).put("weight", f?.let { r2(com.undersigned.protocol.Ranking.weight(it)) } ?: JSONObject.NULL)
            .put("standing", stand?.let { JSONObject().put("credibility", it.credibility).put("counted_reviews", it.countedReviews).put("withdrawn", it.withdrawn)
                .put("flags_given", it.flagsGiven).put("flags_upheld", it.flagsUpheld).put("flags_received", it.flagsReceived).put("records_hidden", it.recordsHidden)
                .put("helpful_received", r2(it.helpfulReceived)).put("replies_given", it.repliesGiven).put("replies_received", it.repliesReceived).put("first_seen", it.firstSeen) } ?: JSONObject.NULL)
            .put("reviews", reviews.size).put("publisher_of", JSONArray(st.verified.filterValues { addr in it }.keys.sorted()))
            .put("treasury_listings", JSONArray(st.claims.filter { it.treasury == addr }.map { JSONObject().put("package", it.packageName).put("code", it.code) }))
            .put("pro_licence", mine.filter { it.memo is ReviewMemo.Pro }.maxByOrNull { it.blockTime }?.let { e -> JSONObject().put("paid_at", e.blockTime).put("months", (e.memo as ReviewMemo.Pro).months).put("ends_at", java.time.Instant.ofEpochSecond(e.blockTime).atZone(java.time.ZoneOffset.UTC).plusMonths((e.memo as ReviewMemo.Pro).months.toLong()).toEpochSecond()) } ?: JSONObject.NULL)
        if (full) o.put("records", JSONArray(mine.take(500).map { record(it, st) }))
        return o
    }

    // ---- Rankings and payments ----------------------------------------------------------------------------------------

    fun leaderboard(metric: String, category: String?, limit: Int): JSONObject {
        val st = s
        val pkgs = st.catalogue.filter { category == null || it.category.equals(category, true) }.map { it.packageName }
        val rows: List<Pair<String, Double>> = when (metric) {
            "signed_score" -> pkgs.mapNotNull { p -> st.scores[p]?.takeIf { it.countedReviews >= 2 }?.let { p to it.weightedRating } }
            "signed_reviews" -> pkgs.mapNotNull { p -> st.scores[p]?.let { p to it.countedReviews.toDouble() } }
            "store_rating" -> pkgs.mapNotNull { p -> indexer.latestRating(p)?.second?.takeIf { it.sum() >= 5 }?.let { st2 -> p to st2.withIndex().sumOf { (i, n) -> (i + 1) * n }.toDouble() / st2.sum() } }
            "store_reviews" -> pkgs.mapNotNull { p -> indexer.latestRating(p)?.second?.let { p to it.sum().toDouble() } }
            "revenue" -> boards.rows.filter { it.confirmed && it.claim.packageName in pkgs }.mapNotNull { r -> r.usdWhenPaid?.let { r.claim.packageName to it } }
            "payers" -> boards.rows.filter { it.confirmed && it.claim.packageName in pkgs }.mapNotNull { r -> r.tally?.let { r.claim.packageName to it.payers.toDouble() } }
            "growth" -> { val m = java.time.YearMonth.now(java.time.ZoneOffset.UTC); boards.rows.filter { it.confirmed && it.claim.packageName in pkgs }
                .mapNotNull { r -> val now = r.monthUsd[m.toString()] ?: 0.0; val prev = r.monthUsd[m.minusMonths(1).toString()] ?: 0.0; if (prev <= 0.0) null else r.claim.packageName to (now - prev) / prev } }
            else -> return JSONObject().put("error", "unknown metric").put("metrics", JSONArray(METRICS))
        }
        return JSONObject().put("metric", metric).put("category", category ?: JSONObject.NULL).put("rows", JSONArray(rows.sortedByDescending { it.second }.take(limit).mapIndexed { i, (p, v) ->
            JSONObject().put("rank", i + 1).put("package", p).put("name", st.byPackage[p]?.name ?: p).put("value", r2(v)) }))
    }

    fun paymentsBoard(limit: Int): JSONObject = JSONObject()
        .put("note", "Payments received on-chain by publisher-confirmed treasuries, in USD on the day of each payment. Off-chain income is invisible to this; nothing here is a statement about profit.")
        .put("rows", JSONArray(boards.rows.filter { it.confirmed }.take(limit).map { boardJson(it, false) }))
        .put("unconfirmed", JSONArray(boards.rows.filter { !it.confirmed }.map { JSONObject().put("package", it.claim.packageName).put("treasury", it.claim.treasury) }))

    fun appPayments(pkg: String): JSONObject? = boards.rows.firstOrNull { it.claim.packageName == pkg && it.confirmed }?.let { boardJson(it, true) }

    private fun boardJson(r: BoardRow, months: Boolean): JSONObject {
        val t = r.tally
        val o = JSONObject().put("package", r.claim.packageName).put("name", r.name).put("treasury", r.claim.treasury).put("code", r.claim.code).put("by_reference", r.claim.byReference)
            .put("usd_when_paid", r.usdWhenPaid?.let(::r2) ?: JSONObject.NULL).put("unpriced_payments", r.unpriced).put("complete", r.complete)
        if (t != null) o.put("payments", t.payments).put("payers", t.payers).put("attested_payers", t.attestedPayers).put("lamports", t.lamports)
            .put("skr_raw", t.tokens[Config.SKR_MINT] ?: 0L).put("usdc_raw", t.tokens[Config.USDC_MINT] ?: 0L).put("first", t.first ?: JSONObject.NULL).put("last", t.last ?: JSONObject.NULL)
            .put("own_wallet_payments_excluded", t.ownPayments)
        if (months && t != null) o.put("months", JSONArray(t.months.map { m -> JSONObject().put("month", m.key).put("payments", m.payments).put("lamports", m.lamports)
            .put("skr_raw", m.tokens[Config.SKR_MINT] ?: 0L).put("usdc_raw", m.tokens[Config.USDC_MINT] ?: 0L).put("usd_when_paid", r.monthUsd[m.key]?.let(::r2) ?: JSONObject.NULL) }))
        return o
    }

    companion object {
        val METRICS = listOf("signed_score", "signed_reviews", "store_rating", "store_reviews", "revenue", "payers", "growth")
        fun r2(v: Double) = Math.round(v * 100.0) / 100.0
    }
}
