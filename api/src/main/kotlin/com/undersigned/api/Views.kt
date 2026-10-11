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
                    val monthUsd: Map<String, Double>, val payers: Set<String>, val unpriced: Int = 0, val own: Set<String> = emptySet())

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
            BoardRow(c, name, true, tally, usd, read.complete, months, real.filter { Prices.meaningful(it) }.map { it.payer }.toSet(), priced.count { it.second == null }, own)
        }.sortedByDescending { it.usdWhenPaid ?: -1.0 }
    }
}

class Views(private val indexer: Indexer, private val boards: Boards, private val db: Db, val ai: Ai? = null) {
    /** The state a request started with, passed down: a rebuild in the middle of a request must not mix two ledgers. */
    private val s get() = indexer.state

    // ---- Records ------------------------------------------------------------------------------------------------

    fun type(m: ReviewMemo): String = ReviewMemo.encode(m).split('|').getOrElse(1) { "?" }

    fun record(e: LedgerEntry, st: State = indexer.state): JSONObject {
        val o = JSONObject().put("id", e.signature).put("transaction", e.signature.substringBefore(':')).put("signer", e.signer).put("time", e.blockTime).put("type", type(e.memo))
        when (val m = e.memo) {
            is ReviewMemo.Review -> o.put("package", m.packageName).put("rating", m.rating).put("tags", JSONArray(m.tags.map { it.wire })).put("version", m.versionCode).put("text", st.longTexts[e.signature] ?: m.text ?: JSONObject.NULL).apply { if (st.longTexts.containsKey(e.signature)) put("text_preview", m.text).put("long_text", true) }
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
            is ReviewMemo.TreasuryFor -> o.put("package", m.packageName).put("code", m.code).put("treasury", m.treasury)
            is ReviewMemo.Boost -> o.put("target", m.recordId)
            is ReviewMemo.Untreasury -> o.put("package", m.packageName)
            is ReviewMemo.Moderate -> o.put("target", m.recordId).put("reason", m.reason.wire)
            is ReviewMemo.Unmoderate -> o.put("target", m.recordId)
            else -> {}
        }
        st.meta[e.signature.substringBefore(':')]?.let { o.put("fee_lamports", it.fee).put("seeker_wallet_shape", it.seekerShape) }
        o.put("withdrawn", e.signature in st.revoked)
        // The operator's hide list covers what THIS site serves: the text goes, the record and its facts stay, and it says so.
        val hiddenWhy = st.operatorHidden[e.signature]
        if (hiddenWhy != null && o.has("text")) o.put("text", JSONObject.NULL)
        o.put("hidden_by_operator", hiddenWhy != null)
        // A moderator's hide is on the ledger, so every reader applies it; the reason is public.
        st.modHidden[e.signature]?.let { why -> if (o.has("text")) o.put("text", JSONObject.NULL); o.put("hidden_by_moderator", why.wire) }
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
        is ReviewMemo.StoreNote -> m.packageName; is ReviewMemo.Treasury -> m.packageName; is ReviewMemo.TreasuryFor -> m.packageName; is ReviewMemo.Untreasury -> m.packageName; else -> null
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
                .put("rank_score", if (sc.countedReviews > 0) r2(sc.weightedRating) else JSONObject.NULL).put("mean", if (sc.countedReviews > 0) r2(sc.weightedMean) else JSONObject.NULL).put("counted_reviews", sc.countedReviews).put("tags", JSONObject(sc.tagCounts.mapKeys { it.key.wire })) }))
            .put("signers", JSONArray(st.entries.map { it.signer }.distinct().sorted().map { w -> val f = st.countsFor(w)
                JSONObject().put("wallet", w).put("attested_device", f.hasGenesisToken).put("staked_skr", latestStake(st, w)?.let { r2(it.skr) } ?: JSONObject.NULL).put("weight", r2(com.undersigned.protocol.Ranking.weight(f))) }))
    }

    fun stats(): JSONObject {
        val st = s; val all = st.entries
        val live = all.filter { it.memo is ReviewMemo.Review && it.signature !in st.revoked }
        // What the per-app scores count: one newest review per wallet per app, weight above zero, nothing hidden or quick.
        val countedTotal = st.scores.values.sumOf { it.countedReviews }
        val signers = live.map { it.signer }.toSet()
        val byId = all.associateBy { it.signature }
        val replies = all.filter { it.memo is ReviewMemo.Reply }
        val pub = replies.count { r -> val p = (byId[(r.memo as ReviewMemo.Reply).reviewSignature]?.memo as? ReviewMemo.Review)?.packageName; p != null && st.verified[p]?.contains(r.signer) == true }
        val dated = all.filter { it.memo !is ReviewMemo.Pro }
        return JSONObject().put("records", all.size).put("reviews_live", live.size).put("reviews_counted", countedTotal).put("signers", signers.size)
            .put("attested_signers", signers.count { st.factsOf(it).hasGenesisToken && it !in com.undersigned.protocol.Operator.WALLETS }).put("apps_reviewed", live.map { (it.memo as ReviewMemo.Review).packageName }.toSet().size)
            .put("apps_scored", st.scores.values.count { it.countedReviews > 0 }).put("replies", replies.size).put("publisher_replies", pub)
            .put("claims", all.count { it.memo is ReviewMemo.Claim }).put("claims_proven", st.verified.values.sumOf { it.size })
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
            "signed" -> l.sortedByDescending { st.scores[it.packageName]?.takeIf { s -> s.countedReviews > 0 }?.weightedRating ?: -1.0 }
            "updated" -> l.sortedByDescending { it.updated }
            else -> l.sortedBy { it.name.lowercase() }
        }.toList()
        return JSONObject().put("total", sorted.size).put("apps", JSONArray(sorted.take(limit).map { appRow(it.packageName) }))
    }

    private fun appRow(pkg: String): JSONObject {
        val st = s; val c = st.byPackage[pkg]; val sc = st.scores[pkg]
        return JSONObject().put("package", pkg).put("name", c?.name ?: pkg).put("category", c?.category ?: JSONObject.NULL).put("updated", c?.updated ?: JSONObject.NULL)
            .put("signed", if (sc == null) JSONObject.NULL else JSONObject().put("rank_score", if (sc.countedReviews > 0) r2(sc.weightedRating) else JSONObject.NULL).put("mean", if (sc.countedReviews > 0) r2(sc.weightedMean) else JSONObject.NULL).put("counted_reviews", sc.countedReviews))
    }

    fun app(pkg: String): JSONObject? {
        val st = s
        if (pkg !in st.byPackage && st.entries.none { packageOf(it.memo) == pkg }) return null
        val o = appRow(pkg)
        val sc = st.scores[pkg]
        val live = liveReviews(pkg, st)
        val counted = live.filter { it.signature !in st.quickIds && st.countsFor(it.signer).hasGenesisToken && st.weighting.parts(it).weight > 0 }
        o.put("signed", JSONObject()
            // Nothing counted means no score: null, never 0 (reads as zero stars) or the 3.0 ranking prior.
            .put("rank_score", sc?.takeIf { it.countedReviews > 0 }?.weightedRating?.let(::r2) ?: JSONObject.NULL).put("mean", sc?.takeIf { it.countedReviews > 0 }?.weightedMean?.let(::r2) ?: JSONObject.NULL)
            .put("counted_reviews", sc?.countedReviews ?: 0).put("live_reviews", live.size)
            .put("stars", JSONArray((1..5).map { star -> counted.count { (it.memo as ReviewMemo.Review).rating == star } }))
            .put("tags", JSONObject((sc?.tagCounts ?: emptyMap()).mapKeys { it.key.wire }))
            .put("latest_version_reviewed", sc?.latestVersionReviewed ?: JSONObject.NULL)
            // Beside the signed score, never blended into it (protocol Weighting): Pro members' reviews as their own score.
            .put("pro_members", st.segments.pro[pkg]?.let { m -> JSONObject().put("mean", r2(m.weightedMean)).put("counted_reviews", m.countedReviews) } ?: JSONObject.NULL)
            // Reviews their own authors paid to promote: own score, listed after organic reviews (protocol Weighting).
            .put("promoted", st.segments.promoted[pkg]?.let { m -> JSONObject().put("mean", r2(m.weightedMean)).put("counted_reviews", m.countedReviews) } ?: JSONObject.NULL)
            // The operator also publishes apps in this store; on its own apps the page says so (protocol Operator).
            .put("operator_publishes", com.undersigned.protocol.Operator.publishes(pkg, st.verified))
            .put("install_age", JSONObject(live.mapNotNull { st.usage[it.signature]?.wire }.groupingBy { it }.eachCount()))
            .put("usage_time", JSONObject(live.mapNotNull { st.useTimes[it.signature]?.wire }.groupingBy { it }.eachCount()))
            // Pro reviewers' own tags across this app's live reviews, most used first (owner, 11 Oct 2026).
            .put("custom_tags", JSONObject(live.flatMap { st.customTags[it.signature].orEmpty() }.groupingBy { it }.eachCount())))
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
            .put("quick_reviews", st.quickScores[pkg]?.let { q -> JSONObject().put("mean", Math.round(q.mean * 100) / 100.0).put("count", q.count)
                .put("note", "Anonymous reviews signed by a key on the reviewer's phone, fee paid by Undersigned's relayer; counted in no score.") } ?: JSONObject.NULL)
            .put("fixes", com.undersigned.protocol.Threads.stats(st.entries, pkg, st.verified).let { f -> JSONObject().put("announced", f.fixes).put("follow_ups_after_fix", f.followUpsAfterFix)
                .put("mean_star_change_after_fix", f.meanDelta?.let { Math.round(it * 100) / 100.0 } ?: JSONObject.NULL).put("median_hours_to_fix", f.medianHoursToFix?.let { Math.round(it * 10) / 10.0 } ?: JSONObject.NULL) })
            .put("responsiveness", Reputation.responsiveness(st.entries, pkg, vp).let { r -> JSONObject().put("answered", r.answered).put("total", r.total).put("median_hours", r.medianHours?.let(::r2) ?: JSONObject.NULL) })
            .put("feature_requests", Reputation.requests(st.entries, pkg, vp).let { r -> JSONObject().put("total", r.total).put("answered", r.answered).put("will_build", r.willBuild).put("delivered", r.delivered).put("median_decision_hours", r.medianDecisionHours?.let(::r2) ?: JSONObject.NULL).put("median_deliver_hours", r.medianDeliverHours?.let(::r2) ?: JSONObject.NULL).put("by_category", JSONObject().also { o -> r.byCategory.forEach { (c, n) -> o.put(c.wire, n) } }) })
            .put("crashes", Reputation.crashes(st.entries, pkg, vp).let { c -> JSONObject().put("reported", c.total).put("fixed", c.fixed).put("median_fix_hours", c.medianFixHours?.let(::r2) ?: JSONObject.NULL) })
            .put("store_notes", JSONArray(st.notes.filter { it.key.first == pkg }.values.map { n -> val m = n.memo as ReviewMemo.StoreNote
                JSONObject().put("store_review", m.reviewId).put("wallet", n.signer).put("time", n.blockTime).put("text", if (n.signature in st.operatorHidden) JSONObject.NULL else m.text).put("hidden_by_operator", n.signature in st.operatorHidden) })))
        boards.rows.firstOrNull { it.claim.packageName == pkg && it.confirmed }?.let { o.put("payments", boardJson(it, false)) }
        return o
    }

    private fun liveReviews(pkg: String, st: State = indexer.state): List<LedgerEntry> =
        st.entries.filter { (it.memo as? ReviewMemo.Review)?.packageName == pkg && it.signature !in st.revoked }.groupBy { it.signer }.map { (_, l) -> l.maxWith(compareBy<LedgerEntry> { it.blockTime }.thenBy { it.signature }) }

    /** A wallet's stake as last read with one of its reviews (shown, never weighed); null when none was read. */
    private fun latestStake(st: State, wallet: String): com.undersigned.protocol.StakeView.Snapshot? =
        st.entries.filter { it.signer == wallet }.mapNotNull { st.reviewStake[it.signature] }.maxByOrNull { it.readAt }

    /** [sort]: "newest" (default), "helpful", "skr" or "sol" (stake as read when each review appeared; unread last). Never changes a score. */
    fun reviews(pkg: String, limit: Int, offset: Int, sort: String? = null): JSONObject {
        val st = s
        val byTime = liveReviews(pkg, st).sortedByDescending { it.blockTime }
        fun stake(e: LedgerEntry, sol: Boolean) = st.reviewStake[e.signature]?.let { if (sol) it.sol else it.skr } ?: -1.0
        val all = when (sort) {
            "helpful" -> byTime.sortedByDescending { st.helpful[it.signature] ?: 0.0 }
            "skr" -> byTime.sortedByDescending { stake(it, false) }
            "sol" -> byTime.sortedByDescending { stake(it, true) }
            else -> byTime
        }
        val paid = boards.rows.firstOrNull { it.claim.packageName == pkg }?.payers.orEmpty()
        val byTarget = st.entries.filter { it.memo is ReviewMemo.Reply }.groupBy { (it.memo as ReviewMemo.Reply).reviewSignature }
        val vp = st.verified[pkg].orEmpty()
        val list = all.drop(offset).take(limit).map { e ->
            val r = e.memo as ReviewMemo.Review; val f = st.factsOf(e.signer)
            record(e, st).put("reviewer", JSONObject().put("wallet", e.signer).put("skr_name", indexer.names.nameOf(e.signer) ?: JSONObject.NULL).put("attested_device", f.hasGenesisToken)
                    // The reviewer's record (counts only, never a weight): what a reader can weigh for themselves.
                    .put("standing", st.standings[e.signer]?.let { sd -> JSONObject().put("counted_reviews", sd.countedReviews).put("apps_reviewed", sd.appsReviewed).put("follow_ups", sd.followUps).put("follow_ups_after_fix", sd.followUpsAfterFix).put("fixes_confirmed", sd.fixesConfirmed).put("flags_upheld", sd.flagsUpheld).put("helpful_received", r2(sd.helpfulReceived)).put("first_seen", sd.firstSeen) } ?: JSONObject.NULL).put("staked_skr", latestStake(st, e.signer)?.let { r2(it.skr) } ?: JSONObject.NULL).put("staked_sol", latestStake(st, e.signer)?.sol?.let(::r2) ?: JSONObject.NULL)
                    // Shown for transparency, never weighed: the stake as first read after this review appeared, and when.
                    .put("stake_at_review", st.reviewStake[e.signature]?.let { k -> JSONObject().put("skr", r2(k.skr)).put("sol", k.sol?.let(::r2) ?: JSONObject.NULL).put("read_at", k.readAt) } ?: JSONObject.NULL)
                    .put("weight", r2(st.weighting.parts(e).weight)).put("weight_parts", st.weighting.parts(e).let { w -> JSONObject().put("device", r2(w.device)).put("quality", r2(w.quality)).put("pro_member", w.pro).put("promoted", w.promoted) })
                    )
                .put("counted", e.signature !in st.quickIds && st.weighting.parts(e).weight > 0 && st.countsFor(e.signer).hasGenesisToken && !com.undersigned.protocol.Ranking.isSelfPublished(e, st.verified))
                .put("related", when { com.undersigned.protocol.Operator.isOperator(e.signer) -> "operator"; st.weighting.parts(e).gifted -> "pro_from_publisher"; else -> JSONObject.NULL })
                .put("self_published", com.undersigned.protocol.Ranking.isSelfPublished(e, st.verified))
                .put("install_age", st.usage[e.signature]?.wire ?: JSONObject.NULL).put("usage_time", st.useTimes[e.signature]?.wire ?: JSONObject.NULL)
                .put("quick", e.signature in st.quickIds)
                .put("fixed", st.fixes[e.signature]?.let { f -> JSONObject().put("version_code", f.versionCode).put("note", f.text ?: JSONObject.NULL).put("by", f.entry.signer).put("record", f.entry.signature).put("time", f.entry.blockTime) } ?: JSONObject.NULL)
                .put("follow_up_of", st.rounds[e.signature]?.let { rd -> JSONObject().put("original", rd.original.signature).put("stars_before", rd.before).put("stars_after", rd.after).put("change", rd.delta).put("after_fix_version", rd.fix?.versionCode ?: JSONObject.NULL) } ?: JSONObject.NULL)
                .put("activity", st.activity[e.signature]?.let { a -> JSONObject().put("months_active_of_6", a.monthsActive).put("last_used", a.recency.name.lowercase()).put("reinstalls", a.reinstalls) } ?: JSONObject.NULL)
                .put("custom_tags", st.customTags[e.signature]?.let { JSONArray(it) } ?: JSONObject.NULL)
                .put("season", Seasons.at(e.blockTime)?.let { JSONObject().put("id", it.id).put("label", it.label) } ?: JSONObject.NULL)
                .put("paid_for_app", e.signer in paid)
                .put("similar", st.similarity[e.signature]?.let { f -> JSONObject().put("to", JSONArray(f.similarTo)).put("across_apps", f.copiedAcrossApps).put("recent", f.recent)
                    // Who it resembles, so a reader can say "similar text to a review of X signed D" (a fact, not an accusation).
                    .put("matches", JSONArray(f.matches.map { m -> JSONObject().put("record", m.recordId).put("package", m.packageName).put("name", st.byPackage[m.packageName]?.name ?: JSONObject.NULL).put("signer", m.signer).put("time", m.time) })) } ?: JSONObject.NULL)
                .put("flaggers", st.flaggers[e.signature]?.size ?: 0).put("hidden_by_consensus", e.signature in st.hidden)
                .put("helpful_weight", r2(st.helpful[e.signature] ?: 0.0)).put("disputed", st.disputes.containsKey(e.signature))
                .put("replies", JSONArray((byTarget[e.signature] ?: emptyList()).sortedBy { it.blockTime }.filter { it.signature !in st.hidden }.map { rp ->
                    JSONObject().put("id", rp.signature).put("wallet", rp.signer).put("time", rp.blockTime).put("text", if (rp.signature in st.operatorHidden || rp.signature in st.modHidden) JSONObject.NULL else (rp.memo as ReviewMemo.Reply).text)
                        .put("hidden_by_operator", rp.signature in st.operatorHidden).put("hidden_by_moderator", st.modHidden[rp.signature]?.wire ?: JSONObject.NULL).put("publisher", rp.signer in vp) }))
        }
        return JSONObject().put("package", pkg).put("total", all.size).put("reviews", JSONArray(list))
    }

    fun versions(pkg: String): JSONObject { val st = s; return JSONObject().put("package", pkg).put("versions", JSONArray(
        Reputation.byVersion(st.entries, pkg, st.verified) { st.countsFor(it) }.map { JSONObject().put("version", it.versionCode).put("mean", r2(it.mean)).put("count", it.count) })) }

    fun similar(pkg: String): JSONObject { val st = s; return JSONObject().put("package", pkg).put("findings", JSONArray(liveReviews(pkg, st).mapNotNull { e ->
        st.similarity[e.signature]?.let { JSONObject().put("review", e.signature).put("similar_to", JSONArray(it.similarTo)).put("across_apps", it.copiedAcrossApps).put("recent", it.recent) } }))
        // Beyond exact copies: groups an AI suggests say the same thing in near-identical words. A suggestion, never a finding.
        .put("suggested_paraphrase_groups", ai?.paraphrases(pkg, liveReviews(pkg, st).mapNotNull { e -> visibleText(e, st)?.let { e.signature to it } }) ?: JSONArray())
        .put("suggested_label", "Suggested by AI: reviews in a group use near-identical words. A fact about wording, not a verdict about anyone.") }

    fun recordEntry(id: String): LedgerEntry? = s.entries.firstOrNull { it.signature == id }

    /** ISO week key ("2026-W41") of [now]: one digest per week. */
    fun weekKey(now: Long = System.currentTimeMillis() / 1000): String {
        val d = java.time.Instant.ofEpochSecond(now).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        return "%d-W%02d".format(d.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR), d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR))
    }

    /** The last seven days as plain fact lines: store-wide rating bursts and app version / certificate changes. */
    fun digestFacts(now: Long = System.currentTimeMillis() / 1000): Pair<String, Pair<Int, Int>> {
        val st = s; val from = now - 7 * ReviewBursts.DAY
        fun name(p: String) = st.byPackage[p]?.name ?: p
        fun day(t: Long) = java.time.Instant.ofEpochSecond(t).toString().take(10)
        val bursts = indexer.watchHits().filter { it.burst.end >= from }.sortedByDescending { it.burst.intensity }.take(25)
        val changes = db.query("SELECT pkg, at, kind, old, new FROM changes WHERE at >= ? ORDER BY at DESC LIMIT 200", from) { rs -> listOf(rs.getString(1), rs.getLong(2).toString(), rs.getString(3), rs.getString(4) ?: "", rs.getString(5) ?: "") }
        val lines = ArrayList<String>()
        bursts.forEach { h -> val b = h.burst
            lines += "BURST ${name(h.pkg)} (${h.pkg}): ${b.count} ${if (b.kind.name.lowercase() == "high") "5-star" else "1-star"} store ratings ${day(b.start)} to ${day(b.end)}, about ${"%.1f".format(java.util.Locale.US, b.expected)} usual, ${"%.1f".format(java.util.Locale.US, BurstSpark.multiple(b))}x" }
        // A release date and a version code for the same app say the same thing once.
        val versioned = changes.filter { it[2] != "release" }.map { it[0] }.toSet()
        changes.filter { it[2] != "release" || it[0] !in versioned }.forEach { c -> lines += "UPDATE ${name(c[0])} (${c[0]}) on ${day(c[1].toLong())}: ${c[2]} ${c[3]} -> ${c[4]}" }
        return lines.joinToString("\n") to (bursts.size to (lines.size - bursts.size))
    }

    /**
     * This week's digest of the seven days to the start of today (UTC); null when the AI is off. Written once per distinct
     * set of facts and kept: the window moves a day at a time and the store is crawled daily, so a week costs a handful of
     * calls, and a digest written before the week's facts arrived is never left standing.
     */
    fun digest(now: Long = System.currentTimeMillis() / 1000): JSONObject? {
        val ai = ai?.takeIf { it.enabled } ?: return null
        val to = now - now % ReviewBursts.DAY
        val (facts, counts) = digestFacts(to); val week = weekKey(now)
        val text = ai.digest("$week:" + Integer.toHexString(facts.hashCode()), facts) ?: return null
        return JSONObject().put("week", week).put("from", to - 7 * ReviewBursts.DAY).put("to", to).put("text", text)
            .put("bursts", counts.first).put("updates", counts.second)
            .put("label", "Suggested weekly digest, written by AI from the store-wide bursts and app updates seen. Facts, not verdicts.")
    }

    /** A record's public text, or null when there is none to show (no note, withdrawn, or hidden by a moderator or this site). */
    fun visibleText(e: LedgerEntry, st: State = s): String? {
        if (e.signature in st.revoked || st.modHidden.containsKey(e.signature) || st.operatorHidden.containsKey(e.signature)) return null
        return when (val m = e.memo) { is ReviewMemo.Review -> m.text; is ReviewMemo.Reply -> m.text; else -> null }?.takeIf { it.isNotBlank() }
    }

    /** Whether [wallet] holds Pro right now (its own term, or a seat a payer shared), read from the ledger. */
    fun holdsPro(wallet: String, now: Long = System.currentTimeMillis() / 1000): Boolean =
        com.undersigned.protocol.Weighting.proAt(s.entries, wallet, now, s.paidRecord)

    /** A suggested summary of the app's COUNTED reviews with text (Ai.summary), or null when there are too few. */
    fun summary(pkg: String): JSONObject? {
        val st = s
        val counted = liveReviews(pkg, st).filter { e -> st.weighting.parts(e).weight > 0 && st.countsFor(e.signer).hasGenesisToken && !com.undersigned.protocol.Ranking.isSelfPublished(e, st.verified) }
        val rows = counted.mapNotNull { e -> visibleText(e, st)?.let { Triple(e.signature, (e.memo as ReviewMemo.Review).rating, it) } }
        return ai?.summary(pkg, rows)?.put("package", pkg)
    }

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

    fun changes(since: Long?): JSONObject {
        val rows = db.query("SELECT pkg, at, kind, old, new FROM changes WHERE at >= ? ORDER BY at DESC LIMIT 500", since ?: 0L) { rs ->
            JSONObject().put("package", rs.getString(1)).put("time", rs.getLong(2)).put("kind", rs.getString(3)).put("old", rs.getString(4)).put("new", rs.getString(5)) }
        // A plain-language note for the newest changes (cached; at most a few new ones written per request), labelled a suggestion.
        rows.take(Config.AI_NOTES_PER_REQUEST).forEach { o -> changeNote(o)?.let { o.put("suggested_note", it) } }
        return JSONObject().put("changes", JSONArray(rows)).put("suggested_note_label", "Suggested note, written by AI from the store's release notes and the changes seen.")
    }

    fun changeNote(o: JSONObject): String? = ai?.changeNote(o.getString("package"), o.getString("kind"), o.optString("old").ifEmpty { null }, o.optString("new").ifEmpty { null },
        // A release is a moved listing date; the notes held may describe another build, so none are offered for it.
        indexer.state.details[o.getString("package")]?.newInVersion?.takeIf { o.getString("kind") != "release" })

    // ---- Wallets -----------------------------------------------------------------------------------------------------

    fun wallet(addr: String, full: Boolean): JSONObject {
        // A wallet the ledger has not met is answered from the cache only; unknown wallets are queued for the indexer,
        // so a page of random addresses never spends chain reads or holds request threads. null = not read yet.
        val st = s; val f = st.facts[addr] ?: indexer.cachedFacts(addr); val stand = st.standings[addr]
        if (f == null) indexer.wanted.add(addr)
        val mine = st.entries.filter { it.signer == addr }
        val reviews = mine.filter { it.memo is ReviewMemo.Review && it.signature !in st.revoked }
        val o = JSONObject().put("wallet", addr).put("skr_name", indexer.names.cached(addr)?.ifEmpty { null } ?: JSONObject.NULL).put("read", f != null)
            .put("attested_device", f?.hasGenesisToken ?: JSONObject.NULL).put("staked_skr", latestStake(st, addr)?.let { r2(it.skr) } ?: JSONObject.NULL).put("staked_sol", latestStake(st, addr)?.sol?.let(::r2) ?: JSONObject.NULL).put("weight", f?.let { r2(com.undersigned.protocol.Ranking.weight(it)) } ?: JSONObject.NULL)
            .put("standing", stand?.let { JSONObject().put("counted_reviews", it.countedReviews).put("withdrawn", it.withdrawn)
                .put("flags_given", it.flagsGiven).put("flags_upheld", it.flagsUpheld).put("flags_received", it.flagsReceived).put("records_hidden", it.recordsHidden)
                .put("helpful_received", r2(it.helpfulReceived)).put("replies_given", it.repliesGiven).put("replies_received", it.repliesReceived).put("first_seen", it.firstSeen)
                .put("apps_reviewed", it.appsReviewed).put("follow_ups", it.followUps).put("follow_ups_after_fix", it.followUpsAfterFix).put("fixes_confirmed", it.fixesConfirmed) } ?: JSONObject.NULL)
            .put("reviews", reviews.size).put("banned", addr in st.banned).put("publisher_of", JSONArray(st.verified.filterValues { addr in it }.keys.sorted()))
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

    /** Read builds for treasuries found in app code (null when the reader is off). */
    @Volatile var found: FoundTreasuries? = null

    /** Treasuries found in app code, NOT confirmed by the publisher: their own list, by app name, never ranked with the board. */
    fun foundTreasuries(limit: Int): JSONObject {
        val f = found
        val st = s
        val rows = f?.list(st.byPackage.mapValues { it.value.name }, boards.rows.filter { it.confirmed }.map { it.claim.treasury }.toSet(),
            boards.rows.filter { it.confirmed }.map { it.claim.packageName }.toSet(), indexer.operatorHidden().keys) ?: emptyList()
        return JSONObject()
            .put("label", "Found in app code, not confirmed by the publisher. A wallet written into an app's store build that received SOL, SKR or USDC from at least ${FoundTreasuries.MIN_PAYERS} different wallets in the transactions read. It may not be the app's treasury.")
            .put("method", "Each store build is read for wallet addresses; kept only if ordinary wallets, found in one publisher's apps only, not a known shared address, and paid by ${FoundTreasuries.MIN_PAYERS}+ wallets in the newest ${FoundTreasuries.TXS_READ} transactions of the wallet and its SKR and USDC accounts.")
            .put("dispute", "Publishers: confirm a treasury in the app (Payments board > List your treasury) and it moves to the board; if a wallet here is not yours, tell us at ${Config.PUBLIC_URL}/report and it is removed.")
            .put("total", rows.size).put("rows", JSONArray(rows.take(limit)))
            .put("progress", f?.stats() ?: JSONObject.NULL)
    }

    /** One app's found-in-code wallets with their full-history analytics; null when nothing is listed for it. */
    fun foundAnalytics(pkg: String): JSONObject? {
        val f = found ?: return null
        val rows = foundTreasuries(500).getJSONArray("rows").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.filter { it.getString("package") == pkg }
        if (rows.isEmpty()) return null
        return JSONObject().put("package", pkg).put("name", rows.first().getString("name"))
            .put("label", "Found in app code, not confirmed by the publisher. Payments INTO the wallet from other wallets; not a statement about revenue or profit.")
            .put("wallets", JSONArray(rows.map { f.walletAnalytics(it.getString("wallet")) }))
    }

    /** Payment analytics over confirmed treasuries and found wallets (Analytics.kt); its own five-minute cache. */
    val analytics: Analytics by lazy { Analytics(db, Prices(db), boards, { found }, { foundRowsAll() }, { s.catalogue.associate { it.packageName to it.category } }) }
    private fun foundRowsAll(): List<JSONObject> = (foundTreasuries(Int.MAX_VALUE).optJSONArray("rows") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }

    /** Heavy answers built from the whole history, kept five minutes: the history grows by the minute, these are read often. */
    private val heavy = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Any>>()
    fun <T : Any> memo5(key: String, build: () -> T): T = cached5(key, build)
    private fun <T : Any> cached5(key: String, build: () -> T): T {
        val now = System.currentTimeMillis(); val c = heavy[key]
        @Suppress("UNCHECKED_CAST") if (c != null && now - c.first < 300_000) return c.second as T
        return build().also { heavy[key] = now to it }
    }

    fun foundTrends(): JSONObject = cached5("trends") { foundTrendsNow() }
    private fun foundTrendsNow(): JSONObject {
        val f = found ?: return JSONObject()
        val st = s
        // The same filters as the list: confirmed treasuries and the operator's hide list stay out (audit, 10 Oct 2026).
        val shown = f.list(st.byPackage.mapValues { it.value.name }, boards.rows.filter { it.confirmed }.map { it.claim.treasury }.toSet(),
            boards.rows.filter { it.confirmed }.map { it.claim.packageName }.toSet(), indexer.operatorHidden().keys).map { it.getString("wallet") }.toSet()
        return f.trends(shown)
            .put("label", "Monthly payments into every wallet found in app code (not confirmed), each wallet once. USD on the day of each payment; unpriced payments are counted, not valued.")
    }

    // ---- The publisher plan's report (owner, 10 Oct 2026) --------------------------------------------------------

    /**
     * Everything a publisher needs about one app, in one answer: the app's public page data, its reviews waiting for an
     * answer, the store's rating trend from the daily snapshots, bursts, certificate and permission changes, scores by
     * version, and where the app sits in its category. Built from public data; the plan pays for the work of putting it
     * together, the alerts and the API around it. Nothing in it changes any score.
     */
    fun publisherReport(pkg: String, now: Long = System.currentTimeMillis() / 1000): JSONObject? {
        val st = s
        val page = app(pkg) ?: return null
        val vp = st.verified[pkg].orEmpty()
        val live = liveReviews(pkg, st)
        val answered = st.entries.filter { e -> (e.memo as? ReviewMemo.Reply)?.let { it.reviewSignature } != null && e.signer in vp }
            .map { (it.memo as ReviewMemo.Reply).reviewSignature }.toSet()
        val waiting = live.filter { it.signer !in vp && it.signature !in answered }.sortedBy { it.blockTime }
        val snaps = indexer.snapshots(pkg)
        fun avg(st5: List<Int>) = if (st5.sum() == 0) null else st5.withIndex().sumOf { (i, n) -> (i + 1) * n }.toDouble() / st5.sum()
        val trend = JSONObject().put("snapshot_days", snaps.size)
        if (snaps.size >= 2) {
            val a = snaps.first(); val b = snaps.last(); val days = maxOf(1.0, (b.time - a.time) / 86_400.0)
            trend.put("from", java.time.Instant.ofEpochSecond(a.time).toString().take(10)).put("to", java.time.Instant.ofEpochSecond(b.time).toString().take(10))
                .put("ratings_added", b.byStar.sum() - a.byStar.sum()).put("ratings_per_day", r2((b.byStar.sum() - a.byStar.sum()) / days))
                .put("average_then", avg(a.byStar)?.let(::r2) ?: JSONObject.NULL).put("average_now", avg(b.byStar)?.let(::r2) ?: JSONObject.NULL)
                .put("new_by_star", JSONArray((0..4).map { b.byStar[it] - a.byStar[it] }))
        }
        // The category, from the latest snapshot of every app in it: medians, and this app's place.
        val cat = st.byPackage[pkg]?.category
        val peers = if (cat == null) emptyList() else st.catalogue.filter { it.category == cat }.mapNotNull { c -> indexer.latestRating(c.packageName)?.second?.let { c.packageName to it } }
        fun median(v: List<Double>) = v.sorted().let { if (it.isEmpty()) null else if (it.size % 2 == 1) it[it.size / 2] else (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
        fun rank(v: Double?, all: List<Double>) = v?.let { x -> if (all.isEmpty()) null else r2(100.0 * all.count { it < x } / all.size) }
        val rated = peers.filter { it.second.sum() > 0 }
        val avgs = rated.mapNotNull { avg(it.second) }; val counts = rated.map { it.second.sum().toDouble() }
        val mine = indexer.latestRating(pkg)?.second
        val bench = JSONObject().put("category", cat ?: JSONObject.NULL).put("apps_rated", rated.size)
            .put("median_store_rating", median(avgs)?.let(::r2) ?: JSONObject.NULL).put("median_store_ratings", median(counts)?.toLong() ?: JSONObject.NULL)
            .put("store_rating_percentile", rank(mine?.let { avg(it) }, avgs) ?: JSONObject.NULL)
            .put("store_ratings_percentile", rank(mine?.sum()?.toDouble(), counts) ?: JSONObject.NULL)
            .put("note", "Percentile: the share of rated apps in the category below this one.")
        val changes = db.query("SELECT at, kind, old, new FROM changes WHERE pkg = ? ORDER BY at DESC LIMIT 50", pkg) { rs ->
            JSONObject().put("time", rs.getLong(1)).put("kind", rs.getString(2)).put("old", rs.getString(3)).put("new", rs.getString(4)) }
        return JSONObject().put("package", pkg).put("generated_at", now)
            .put("app", page)
            .put("waiting_for_an_answer", JSONObject().put("count", waiting.size).put("reviews", JSONArray(waiting.take(50).map { record(it, st) })))
            .put("versions", versions(pkg).optJSONArray("versions") ?: JSONArray())
            .put("store_rating_trend", trend)
            .put("bursts", bursts(pkg))
            .put("changes", JSONArray(changes))
            .put("benchmark", bench)
            .put("payments_over_time", analytics.appSeries(pkg, "week")?.let { s -> JSONObject().put("chart_svg", "${Config.PUBLIC_URL}/v1/payments/chart/app/$pkg.svg?by=week").put("windows", s.optJSONObject("windows")) } ?: JSONObject.NULL)
            .put("label", "Built from public data: the ledger on Solana and the store's public catalogue and ratings. A fact sheet, never a verdict.")
    }

    // ---- The data plan's exports ----------------------------------------------------------------------------------

    private fun csv(header: List<String>, rows: List<List<Any?>>): String {
        // A cell that a spreadsheet would run as a formula (=, +, -, @, tab) is prefixed with an apostrophe: names and version
        // strings come from publishers. Numbers are written as numbers, so a negative number stays one.
        fun cell(v: Any?): String { val t = v?.toString() ?: ""; val safe = if (v is String && t.isNotEmpty() && t[0] in "=+-@\t\r") "'$t" else t
            return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe }
        return (listOf(header) + rows).joinToString("\n") { r -> r.joinToString(",") { cell(it) } } + "\n"
    }

    val EXPORTS = mapOf(
        "ratings" to "The store's star counts per app per day, from Undersigned's daily snapshots",
        "changes" to "Version, certificate and permission changes seen in the store, per app",
        "apps" to "Every dApp in the store: category, signed score and counts, the store's latest rating",
        "found" to "Wallets found in apps' store builds (not confirmed), with what they received",
    )

    fun export(dataset: String): String? = if (dataset !in EXPORTS) null else cached5("export:$dataset") { exportNow(dataset)!! }
    private fun exportNow(dataset: String): String? = when (dataset) {
        "ratings" -> csv(listOf("package", "day", "stars_1", "stars_2", "stars_3", "stars_4", "stars_5"),
            db.query("SELECT pkg, day, s1, s2, s3, s4, s5 FROM ratings ORDER BY day, pkg") { rs -> listOf(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7)) })
        "changes" -> csv(listOf("package", "time_utc", "kind", "old", "new"),
            db.query("SELECT pkg, at, kind, old, new FROM changes ORDER BY at") { rs -> listOf(rs.getString(1), java.time.Instant.ofEpochSecond(rs.getLong(2)).toString(), rs.getString(3), rs.getString(4), rs.getString(5)) })
        "apps" -> { val st = s; csv(listOf("package", "name", "category", "signed_mean", "counted_reviews", "store_rating", "store_ratings", "store_rating_day"),
            st.catalogue.map { c -> val sc = st.scores[c.packageName]?.takeIf { it.countedReviews > 0 }; val r = indexer.latestRating(c.packageName)
                listOf(c.packageName, c.name, c.category, sc?.weightedMean?.let(::r2), sc?.countedReviews ?: 0,
                    r?.second?.let { st5 -> if (st5.sum() == 0) null else r2(st5.withIndex().sumOf { (i, n) -> (i + 1) * n }.toDouble() / st5.sum()) }, r?.second?.sum(), r?.first) }) }
        "found" -> { val rows = foundTreasuries(Int.MAX_VALUE).optJSONArray("rows") ?: JSONArray()
            csv(listOf("package", "name", "wallet", "payers", "payments", "sol", "skr", "usdc", "first", "last", "transactions_read"),
                (0 until rows.length()).map { i -> val o = rows.getJSONObject(i); listOf(o.optString("package"), o.optString("name"), o.optString("wallet"), o.optInt("payers"), o.optInt("payments"),
                    o.optDouble("sol"), o.optDouble("skr"), o.optDouble("usdc"), o.opt("first"), o.opt("last"), o.optInt("transactions_read")) }) }
        else -> null
    }

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
