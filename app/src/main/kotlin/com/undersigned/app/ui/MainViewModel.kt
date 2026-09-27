package com.undersigned.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.undersigned.app.AppConfig
import com.undersigned.app.data.CatalogueApp
import com.undersigned.app.data.CatalogueRepository
import com.undersigned.app.data.DAppIconCache
import com.undersigned.app.data.StoreDetails
import com.undersigned.app.data.StoreRating
import com.undersigned.app.ledger.PublisherVerifier
import com.undersigned.app.solana.TxMeta
import com.undersigned.protocol.Reputation
import com.undersigned.protocol.Responsiveness
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.undersigned.protocol.SignerStanding
import com.undersigned.app.data.StoreReview
import com.undersigned.app.data.StoreReviewsClient
import com.undersigned.app.solana.DomainClient
import com.undersigned.app.data.LedgerCache
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.device.DeviceChange
import com.undersigned.app.device.InstalledApps
import com.undersigned.app.ledger.LedgerReader
import com.undersigned.app.solana.ReviewTxService
import com.undersigned.app.solana.SignerFactsService
import com.undersigned.app.wallet.WalletSession
import com.undersigned.protocol.AppScore
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.Ranking
import com.undersigned.protocol.ReviewMemo
import com.undersigned.protocol.SignerFacts
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Store review pages read per opened app for the burst check (50 reviews; paced, 700 ms apart). */
const val HISTORY_PAGES = 5
/** "Check older reviews": 300 reviews, about 21 s at the store's pace. CrushCoin's documented campaign sat 300 back. */
const val DEEP_HISTORY_PAGES = 30

sealed class LedgerStatus {
    object Idle : LedgerStatus()
    data class Syncing(val done: Int, val total: Int) : LedgerStatus()
    data class Synced(val reviews: Int, val signers: Int) : LedgerStatus()
    data class Error(val message: String) : LedgerStatus()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val catalogueRepo = CatalogueRepository(app)
    private val installedApps = InstalledApps(app)
    private val ledgerCache = LedgerCache(app)
    private val reader = LedgerReader(cache = ledgerCache)
    private val factsService = SignerFactsService(attestations = com.undersigned.app.solana.AttestationList.load(app))
    val attestations = MutableStateFlow(com.undersigned.app.solana.AttestationList.load(app))
    val attestationsGoverned = com.undersigned.app.solana.AttestationList.isGoverned(app)
    private val txService = ReviewTxService()
    private val proService = com.undersigned.app.solana.ProPaymentService()
    /** The wallet's Pro term, read from the ledger (a paid `pro` record); null when it never bought one. */
    val pro = MutableStateFlow<com.undersigned.app.solana.ProStatus?>(null)
    val isPro: Boolean get() = pro.value?.active == true
    private fun recomputePro() { pro.value = com.undersigned.app.solana.ProLicence.of(entries.value, txMeta.value, wallet.value, UserPreferences.granters.value) }
    val granters: StateFlow<Set<String>> = UserPreferences.granters
    /** A typed wallet address or `.skr` name → the address, or null. */
    suspend fun resolveWallet(input: String): String? {
        val t = input.trim()
        if (Regex("^[1-9A-HJ-NP-Za-km-z]{32,44}$").matches(t) && runCatching { com.undersigned.app.util.Base58.decode(t).size == 32 }.getOrDefault(false)) return t
        return domains.walletOf(t)
    }
    fun addGranter(w: String) { UserPreferences.addGranter(w); recomputePro() }
    fun removeGranter(w: String) { UserPreferences.removeGranter(w); recomputePro() }
    /** The seats this wallet currently grants (tags), longest-seated first, with the local label of each. */
    fun mySeats(): List<Pair<String, String?>> = wallet.value?.let { w -> val labels = UserPreferences.seatLabels(); com.undersigned.protocol.ProSharing.seatOrder(com.undersigned.protocol.ProSharing.recordsOf(entries.value, w)).map { it to labels[it] } } ?: emptyList()
    fun wouldDisplace(): String? = wallet.value?.let { w -> com.undersigned.protocol.ProSharing.wouldDisplace(com.undersigned.protocol.ProSharing.recordsOf(entries.value, w)) }
    val iconCache = DAppIconCache(app)
    private val store = StoreReviewsClient()
    private val domains = DomainClient()

    /** Native store mirror per package (read-only): rating summary + first page of public reviews. */
    val storeRatings = MutableStateFlow<Map<String, StoreRating>>(emptyMap())
    val storeReviews = MutableStateFlow<Map<String, List<StoreReview>>>(emptyMap())
    /** wallet → main .skr name ("" = none). */
    val domainNames = MutableStateFlow<Map<String, String>>(emptyMap())
    /** My own historical store reviews found by wallet: package → review. */
    val myStoreReviews = MutableStateFlow<Map<String, StoreReview>>(emptyMap())
    /** When this wallet's store reviews were last scanned (ms), or null if never. */
    val myStoreScannedAt = MutableStateFlow<Long?>(null)
    val importScan = MutableStateFlow<String?>(null)   // progress text while scanning
    /** Null until a scan has run; then (apps the store could not be read for, apps scanned). */
    val importScanResult = MutableStateFlow<Pair<Int, Int>?>(null)
    val importScanApp = MutableStateFlow<String?>(null)   // display name of the app being scanned
    val storeDetails = MutableStateFlow<Map<String, StoreDetails>>(emptyMap())
    /** package → wallets whose publisher claim verified against the store-listed website. */
    val verifiedPublishers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val txMeta = MutableStateFlow<Map<String, TxMeta>>(emptyMap())
    val standings = MutableStateFlow<Map<String, SignerStanding>>(emptyMap())
    val flagWeights = MutableStateFlow<Map<String, Double>>(emptyMap())
    val hiddenByConsensus = MutableStateFlow<Set<String>>(emptySet())
    val helpfulWeights = MutableStateFlow<Map<String, Double>>(emptyMap())
    val currentFlaggers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val currentClearers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val disputes = MutableStateFlow<Map<String, LedgerEntry>>(emptyMap())
    val similarity = MutableStateFlow<Map<String, com.undersigned.protocol.Similarity.Finding>>(emptyMap())
    val starred: StateFlow<Set<String>> = UserPreferences.starred

    val catalogue = MutableStateFlow<List<CatalogueApp>>(emptyList())
    val installed = MutableStateFlow<Set<String>>(emptySet())
    val entries = MutableStateFlow<List<LedgerEntry>>(emptyList())
    val scores = MutableStateFlow<Map<String, AppScore>>(emptyMap())
    val facts = MutableStateFlow<Map<String, SignerFacts>>(emptyMap())
    val ledgerStatus = MutableStateFlow<LedgerStatus>(LedgerStatus.Idle)
    val selfFacts = MutableStateFlow<SignerFacts?>(null)
    val selfFactsLoading = MutableStateFlow(false)
    val changes = MutableStateFlow<List<DeviceChange>>(emptyList())
    val wallet: StateFlow<String?> = UserPreferences.walletAddress
    val hidden: StateFlow<Set<String>> = UserPreferences.hidden
    val follows: StateFlow<Set<String>> = UserPreferences.follows

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val cat = catalogueRepo.load(); catalogue.value = cat
            installed.value = installedApps.installedPackages()
            runCatching { iconCache.resolve(cat.map { it.packageName }.filter { it in installed.value }) }
            runCatching { installedApps.refreshSnapshot(cat.map { it.packageName }) }
            changes.value = installedApps.changeLog()
            entries.value = ledgerCache.load(); txMeta.value = ledgerCache.loadMeta()
            verifiedPublishers.value = UserPreferences.loadVerifiedPublishers()
            rank()
            // Show what the cached ledger already says instead of the catalogue count, until the live read reports.
            if (entries.value.isNotEmpty() && ledgerStatus.value == LedgerStatus.Idle) ledgerStatus.value = syncedStatus(entries.value)
            syncLedger()
        }
        // The daily catalogue refresh runs on its own coroutine, like the attestation refresh below. At the end of the
        // chain above it waited on the whole ledger sync, and a phone opened briefly never got to it: the airdropper
        // was still on the bundled 8 Sep catalogue — old category names, the dApp Store labelled "Games" — days
        // after install (24 Sep 2026).
        viewModelScope.launch(Dispatchers.IO) {
            if (catalogueRepo.isStale()) catalogueRepo.refresh()?.let { fresh -> catalogue.value = fresh; runCatching { installedApps.refreshSnapshot(fresh.map { it.packageName }) }; changes.value = installedApps.changeLog() }
        }
        // The governed attestation list decides whose voice counts, so it refreshes on its own coroutine.
        // Sequencing it after the catalogue crawl starved it: that crawl is hundreds of requests and the
        // refresh never ran within a session (measured 9 Sep 2026).
        viewModelScope.launch(Dispatchers.IO) {
            if (com.undersigned.app.solana.AttestationList.refresh(app)) {
                UserPreferences.clearFacts(); facts.value = emptyMap()
                attestations.value = com.undersigned.app.solana.AttestationList.load(app)
                rank()
            }
        }
        watchStoreScan()
        followWatch()
        viewModelScope.launch { UserPreferences.walletAddress.collect { w -> recomputePro(); if (w == null) selfFacts.value = null else loadSelfFacts(w)
            // Store reviews belong to ONE wallet: restore that wallet's last scan, or start empty.
            val cached = w?.let { UserPreferences.loadMyStoreReviews(it) }
            myStoreReviews.value = cached?.first ?: emptyMap(); myStoreScannedAt.value = cached?.second } }
        viewModelScope.launch { UserPreferences.granters.collect { recomputePro() } }
        // Near-real-time while the app is open: one cheap tail read a minute (until = newest known signature).
        viewModelScope.launch(Dispatchers.IO) { while (isActive) { delay(60_000); if (ledgerStatus.value !is LedgerStatus.Syncing) syncLedger() } }
    }

    fun syncLedger() {
        if (ledgerStatus.value is LedgerStatus.Syncing) return
        viewModelScope.launch(Dispatchers.IO) {
            ledgerStatus.value = LedgerStatus.Syncing(0, 0)
            reader.sync { p -> ledgerStatus.value = LedgerStatus.Syncing(p.fetched, p.total) }.fold(
                onSuccess = { list -> entries.value = list; txMeta.value = ledgerCache.loadMeta(); rank(); ledgerStatus.value = syncedStatus(list) },
                onFailure = { ledgerStatus.value = LedgerStatus.Error(it.message?.take(80) ?: "unknown") },
            )
        }
    }

    /** Facts for every distinct signer (cached, one lookup each), then the deterministic ranking. */
    private suspend fun rank() = withContext(Dispatchers.IO) {
        val list = entries.value
        val map = HashMap(facts.value)
        for (s in list.map { it.signer }.distinct()) if (s !in map) map[s] = runCatching { factsService.facts(s) }.getOrElse { SignerFacts(false, 0.0) }
        facts.update { it + map }  // merge: store-reviewer lookups may have landed while this ran
        val f = { w: String -> map[w] ?: SignerFacts(false, 0.0) }
        val vp = verifiedPublishers.value
        scores.value = Ranking.score(list, vp, f).associateBy { it.packageName }
        standings.value = Reputation.standing(list, vp, f)
        flagWeights.value = Reputation.flagWeights(list, vp, f)
        hiddenByConsensus.value = Reputation.hiddenByConsensus(list, vp, f)
        currentFlaggers.value = Reputation.currentFlaggers(list)
        currentClearers.value = Reputation.currentClearers(list)
        helpfulWeights.value = Reputation.helpfulWeights(list, f)
        disputes.value = Reputation.disputes(list)
        similarity.value = com.undersigned.protocol.Similarity.findings(list)
        usage.value = com.undersigned.protocol.Usage.index(list)
        useTimes.value = com.undersigned.protocol.Usage.time(list)
        recomputePro()
        verifyClaims(list)
    }

    private fun loadSelfFacts(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            selfFactsLoading.value = true
            selfFacts.value = runCatching { factsService.facts(address, force = true) }.getOrNull()
            selfFactsLoading.value = false
        }
    }

    /** Every claim on-chain is checked against the store-listed website (cached 24 h). */
    private suspend fun verifyClaims(list: List<LedgerEntry>) {
        val claims = list.filter { it.memo is ReviewMemo.Claim }
        if (claims.isEmpty()) return
        val out = HashMap<String, MutableSet<String>>()
        for (c in claims) {
            val pkg = (c.memo as ReviewMemo.Claim).packageName
            val d = storeDetails.value[pkg] ?: store.details(pkg)?.also { storeDetails.value = storeDetails.value + (pkg to it) }
            if (PublisherVerifier.verify(pkg, c.signer, d)) out.getOrPut(pkg) { HashSet() }.add(c.signer)
        }
        // Extend each verification to every reviewed app that lists the same website, so a publisher's review of their
        // own OTHER app is not counted as independent. Store details for those apps are fetched once, paced.
        val reviewed = list.mapNotNull { (it.memo as? ReviewMemo.Review)?.packageName }.distinct()
        if (out.isNotEmpty()) for (pkg in reviewed) if (!storeDetails.value.containsKey(pkg)) {
            store.details(pkg)?.let { d -> storeDetails.update { it + (pkg to d) } }; kotlinx.coroutines.delay(250)
        }
        val expanded = PublisherVerifier.expandToSameProof(out, reviewed) { storeDetails.value[it]?.website }
        val changed = verifiedPublishers.value != expanded
        verifiedPublishers.value = expanded
        UserPreferences.saveVerifiedPublishers(expanded)
        if (changed) { val f = { w: String -> facts.value[w] ?: SignerFacts(false, 0.0) }; scores.value = Ranking.score(list, expanded, f).associateBy { it.packageName }; standings.value = Reputation.standing(list, expanded, f); flagWeights.value = Reputation.flagWeights(list, expanded, f); hiddenByConsensus.value = Reputation.hiddenByConsensus(list, expanded, f) }
    }
    private fun syncedStatus(list: List<LedgerEntry>): LedgerStatus.Synced {
        // Both counts over the SAME set: live (non-withdrawn) reviews. Counting signers over every review ever written
        // read "1 review from 2 Seekers" once the publisher withdrew theirs (24 Sep 2026).
        val rv = Ranking.revoked(list)
        val live = list.filter { it.memo is ReviewMemo.Review && it.signature !in rv }
        return LedgerStatus.Synced(live.size, live.map { it.signer }.distinct().size)
    }
    fun isVerifiedPublisher(pkg: String, wallet: String) = verifiedPublishers.value[pkg]?.contains(wallet) == true
    fun myClaim(pkg: String): LedgerEntry? = wallet.value?.let { w -> entries.value.firstOrNull { it.signer == w && (it.memo as? ReviewMemo.Claim)?.packageName == pkg } }
    fun versionScores(pkg: String) = Reputation.byVersion(entries.value, pkg, verifiedPublishers.value) { facts.value[it] ?: SignerFacts(false, 0.0) }
    fun isSelfPublished(e: LedgerEntry) = Ranking.isSelfPublished(e, verifiedPublishers.value)
    /** Signer facts for arbitrary wallets (store reviewers), cached; fills [facts]. */
    private val factsInFlight = java.util.Collections.synchronizedSet(HashSet<String>())
    private val factsGate = kotlinx.coroutines.sync.Semaphore(3)
    /**
     * Genesis + stake lookups for store reviewers, three at a time. One at a time, the first "Attested device" pill on
     * an app page appeared ~18 s after opening it (measured 24 Sep 2026), long after a reader had scrolled past. Three,
     * not more: shared RPC keys fail on concurrency before they fail on volume. In-flight wallets are skipped, so a
     * recomposition mid-lookup does not start the same lookups again.
     */
    fun resolveFacts(wallets: Collection<String>) {
        val todo = wallets.distinct().filter { it !in facts.value && factsInFlight.add(it) }
        if (todo.isEmpty()) return
        for (w in todo) viewModelScope.launch(Dispatchers.IO) {
            try {
                factsGate.acquire()
                val f = try { runCatching { factsService.facts(w) }.getOrElse { SignerFacts(false, 0.0) } } finally { factsGate.release() }
                facts.update { it + (w to f) }
            } finally { factsInFlight.remove(w) }
        }
    }
    fun ledgerSigners(): Set<String> = entries.value.filter { it.memo is ReviewMemo.Review }.map { it.signer }.toSet()

    private val detailsInFlight = java.util.Collections.synchronizedSet(HashSet<String>())
    /** Store listing details only (version, certificate, publisher, release notes) — no ratings or reviews. */
    fun loadStoreDetails(pkg: String) {
        if (storeDetails.value.containsKey(pkg) || !detailsInFlight.add(pkg)) return
        viewModelScope.launch(Dispatchers.IO) {
            try { store.details(pkg)?.let { d -> storeDetails.update { it + (pkg to d) } } } finally { detailsInFlight.remove(pkg) }
        }
    }
    fun loadStoreMirror(pkg: String) {
        if (storeRatings.value.containsKey(pkg)) return
        viewModelScope.launch(Dispatchers.IO) {
            store.rating(pkg)?.let { r -> storeRatings.value = storeRatings.value + (pkg to r); UserPreferences.recordRatingSnapshot(pkg, r.byStar) }
            if (!storeDetails.value.containsKey(pkg)) store.details(pkg)?.let { d -> storeDetails.value = storeDetails.value + (pkg to d) }
            // Five pages, paced for the store's CDN: the first shows at once, all five feed the burst check.
            val read = store.reviewsChecked(pkg, maxPages = HISTORY_PAGES, pauseMs = 700, onPage = { l -> if (l.size <= StoreReviewsClient.MAX_REVIEW_PAGE) storeReviews.update { it + (pkg to l) } })
            storeHistory.update { it + (pkg to read) }
            wallet.value?.let { w -> read.items.firstOrNull { it.walletAddress == w }?.let { mine -> myStoreReviews.value = myStoreReviews.value + (pkg to mine) } }
        }
    }

    // ---- Review bursts ------------------------------------------------------------------------------------

    /** Up to [HISTORY_PAGES] pages of each opened app's store reviews, newest first, for the burst check. */
    val storeHistory = MutableStateFlow<Map<String, com.undersigned.app.data.ReviewsRead>>(emptyMap())
    private fun storePoints(pkg: String) = storeHistory.value[pkg]?.items?.mapNotNull { r -> runCatching { java.time.Instant.parse(r.createdAt).epochSecond }.getOrNull()?.let { com.undersigned.protocol.ReviewBursts.Point(it, r.rating, r.text) } }
    fun storeBursts(pkg: String): com.undersigned.protocol.ReviewBursts.Findings? = storePoints(pkg)?.let { com.undersigned.protocol.ReviewBursts.find(it) }
    /** Signed reviews: the newest per wallet, withdrawn ones out, so an edit is not a second review. */
    fun ledgerBursts(pkg: String): com.undersigned.protocol.ReviewBursts.Findings {
        val revoked = revokedSignatures()
        val pts = entries.value.filter { (it.memo as? ReviewMemo.Review)?.packageName == pkg && it.signature !in revoked }.groupBy { it.signer }.map { (_, l) -> l.maxBy { it.blockTime } }
            .map { e -> val r = e.memo as ReviewMemo.Review; com.undersigned.protocol.ReviewBursts.Point(e.blockTime, r.rating, r.text) }
        return com.undersigned.protocol.ReviewBursts.find(pts)
    }
    val historyLoading = MutableStateFlow<Set<String>>(emptySet())
    /** Deeper read on request: up to [DEEP_HISTORY_PAGES] pages, paced, replacing the five-page read. */
    fun loadOlderStoreHistory(pkg: String) {
        if (pkg in historyLoading.value) return
        historyLoading.update { it + pkg }
        viewModelScope.launch(Dispatchers.IO) {
            try { val read = store.reviewsChecked(pkg, maxPages = DEEP_HISTORY_PAGES, pauseMs = 700); if (read.items.size >= (storeHistory.value[pkg]?.items?.size ?: 0)) storeHistory.update { it + (pkg to read) } }
            finally { historyLoading.update { it - pkg } }
        }
    }
    /** Bursts in the store's star COUNTS, from this device's daily snapshots (followed apps, and apps opened). */
    /** This app's star-count snapshots: the store-wide daily archive (Store watch) merged with the per-app ones, 20 h apart at least. */
    fun ratingSnapshots(pkg: String): List<com.undersigned.protocol.RatingSnapshots.Snapshot> {
        val all = (com.undersigned.app.notify.StoreWatchWorker.archive(getApplication()).snapshots(pkg) + UserPreferences.ratingSnapshots(pkg)).sortedBy { it.time }
        val out = ArrayList<com.undersigned.protocol.RatingSnapshots.Snapshot>()
        for (sn in all) if (out.isEmpty() || sn.time - out.last().time >= 20 * 3600) out.add(sn)
        return out
    }
    fun ratingBursts(pkg: String): com.undersigned.protocol.ReviewBursts.Findings = com.undersigned.protocol.ReviewBursts.find(com.undersigned.protocol.RatingSnapshots.toPoints(ratingSnapshots(pkg)), com.undersigned.protocol.ReviewBursts.MIN_SNAPSHOT_DAYS)

    // ---- Store watch ----------------------------------------------------------------------------------------

    data class WatchHit(val pkg: String, val name: String, val burst: com.undersigned.protocol.ReviewBursts.Burst)
    data class Watch(val enabled: Boolean, val at: Long?, val apps: Int, val days: List<String>, val hits: List<WatchHit>)
    val watch = MutableStateFlow(Watch(false, null, 0, emptyList(), emptyList()))
    /** "done/total" while today's read runs, else null. */
    val watchProgress = MutableStateFlow<String?>(null)
    fun loadWatch() = viewModelScope.launch(Dispatchers.IO) {
        val f = com.undersigned.app.notify.StoreWatchWorker.resultFile(getApplication())
        val names = catalogue.value.associate { it.packageName to it.name }
        watch.value = runCatching {
            val o = org.json.JSONObject(f.readText()); val h = o.getJSONArray("hits")
            Watch(UserPreferences.storeWatch, o.getLong("at"), o.getInt("apps"), o.getJSONArray("days").let { a -> (0 until a.length()).map { a.getString(it) } },
                (0 until h.length()).map { i -> val x = h.getJSONObject(i); WatchHit(x.getString("p"), names[x.getString("p")] ?: x.getString("p"),
                    com.undersigned.protocol.ReviewBursts.Burst(com.undersigned.protocol.ReviewBursts.Kind.valueOf(x.getString("k")), x.getLong("s"), x.getLong("e"), x.getInt("n"), x.getInt("t"), x.getDouble("x"), 0)) })
        }.getOrElse { Watch(UserPreferences.storeWatch, null, 0, com.undersigned.app.notify.StoreWatchWorker.archive(getApplication()).days(), emptyList()) }
    }
    fun setStoreWatch(on: Boolean) {
        UserPreferences.storeWatch = on
        if (on) com.undersigned.app.notify.StoreWatchWorker.schedule(getApplication()) else com.undersigned.app.notify.StoreWatchWorker.cancel(getApplication())
        loadWatch()
    }
    private fun followWatch() = viewModelScope.launch {
        com.undersigned.app.notify.StoreWatchWorker.work(getApplication()).collect { infos ->
            val i = infos.lastOrNull() ?: return@collect
            when (i.state) {
                androidx.work.WorkInfo.State.RUNNING -> { val t = i.progress.getInt("total", 0); watchProgress.value = if (t > 0) "${minOf(i.progress.getInt("done", 0), t)}/$t" else "…" }
                // A periodic job goes back to ENQUEUED after each run: that is the moment to reload the result.
                else -> { if (watchProgress.value != null) loadWatch(); watchProgress.value = null }
            }
        }
    }
    /** Store reviewer wallets inside a burst, matching its star count. */
    fun burstWallets(pkg: String, b: com.undersigned.protocol.ReviewBursts.Burst): List<String> = storeHistory.value[pkg]?.items.orEmpty()
        .filter { r -> r.rating == b.kind.stars && (runCatching { java.time.Instant.parse(r.createdAt).epochSecond }.getOrNull() ?: -1L) in b.start..b.end }.mapNotNull { it.walletAddress }.distinct()

    // ---- Install age ---------------------------------------------------------------------------------------

    /** Review record id → the install age its own transaction reported (see protocol Usage). */
    val usage = MutableStateFlow<Map<String, com.undersigned.protocol.UseBucket>>(emptyMap())
    /** Total time on this phone across reinstalls (Undersigned's own log), bucketed; null when not installed now. */
    fun installAge(pkg: String): com.undersigned.protocol.UseBucket? {
        if (installedApps.firstInstallTime(pkg) == null) return null
        val now = System.currentTimeMillis()
        val total = installedApps.presence(pkg, now)?.first ?: return null
        return com.undersigned.protocol.UseBucket.of(now - total, now)
    }
    fun reinstalls(pkg: String): Int = installedApps.presence(pkg)?.second ?: 0
    val useTimes = MutableStateFlow<Map<String, com.undersigned.protocol.UseTime>>(emptyMap())
    fun useTime(pkg: String): com.undersigned.protocol.UseTime? = com.undersigned.app.device.UsageTime.foregroundMs(getApplication(), pkg)?.let { com.undersigned.protocol.UseTime.of(it) }

    /**
     * A review, plus its install-age record in the SAME transaction when [use] is given — one signature either way. If
     * the pair would not fit one transaction the review goes alone: a `use` record in another transaction would attach
     * to nothing.
     */
    fun publishReview(sender: ActivityResultSender, memo: ReviewMemo.Review, use: com.undersigned.protocol.UseBucket?, time: com.undersigned.protocol.UseTime? = null, onStage: (ReviewTxService.Stage) -> Unit, onResult: (Result<String>) -> Unit) {
        val w = wallet.value ?: return onResult(Result.failure(Exception("Sign in with your wallet first")))
        val adapter = WalletSession.adapter ?: return onResult(Result.failure(Exception("Wallet adapter unavailable")))
        val extras = listOfNotNull(use?.let { ReviewMemo.Use(memo.packageName, it) }, time?.let { ReviewMemo.Time(memo.packageName, it) })
        val signer = com.undersigned.app.util.Base58.decode(w)
        // The review and its extras must share ONE transaction; drop extras from the end until they fit.
        var texts = runCatching { (listOf<ReviewMemo>(memo) + extras).map { ReviewMemo.encode(it) } }.getOrElse { return onResult(Result.failure(it)) }
        while (texts.size > 1 && com.undersigned.app.solana.TxBuilder.chunkForBatches(signer, texts).size != 1) texts = texts.dropLast(1)
        if (texts.size == 1) return publish(sender, memo, onStage, onResult)
        viewModelScope.launch {
            if (com.undersigned.app.util.WalletScreening.isSanctioned(w)) return@launch onResult(Result.failure(Exception(getApplication<Application>().getString(com.undersigned.app.R.string.wallet_sanctioned))))
            val r = txService.publishBatch(adapter, sender, w, texts, onStage)
            r.onSuccess { sigs ->
                val sig = sigs.first().first
                UserPreferences.follow(memo.packageName)
                val now = System.currentTimeMillis() / 1000
                val fresh = texts.mapIndexedNotNull { i, t -> ReviewMemo.decode(t)?.let { LedgerEntry(ReviewMemo.recordId(sig, i), w, now, it) } }
                entries.value = (fresh + entries.value).distinctBy { it.signature }
                ledgerCache.save(entries.value)
                launch(Dispatchers.IO) { rank() }
            }
            onResult(r.map { it.first().first })
        }
    }

    // ---- Payment board -------------------------------------------------------------------------------------

    private val board = com.undersigned.app.ledger.PaymentBoard(app)
    /** One listing: a treasury claim, whether the store-listed website confirms it, and what it has been paid. */
    /** [payers] = wallets that paid this listing's treasury under its code; a review by one of them carries "Paid for this app". */
    data class BoardRow(val claim: com.undersigned.protocol.Payments.Claim, val name: String, val confirmed: Boolean, val tally: com.undersigned.protocol.Payments.Tally?, val usd: Double?, val complete: Boolean, val payers: Set<String> = emptySet())
    val boardRows = MutableStateFlow<List<BoardRow>>(emptyList())
    val boardLoading = MutableStateFlow(false)
    val boardPreview = MutableStateFlow<BoardRow?>(null)
    val quotes = MutableStateFlow<Map<String, com.undersigned.app.ledger.Prices.Quote>>(emptyMap())
    private var boardAt = 0L

    fun treasuryClaims(): List<com.undersigned.protocol.Payments.Claim> = com.undersigned.protocol.Payments.claims(entries.value)
    fun myTreasuryClaims(): List<com.undersigned.protocol.Payments.Claim> = wallet.value?.let { w -> treasuryClaims().filter { it.treasury == w } } ?: emptyList()
    fun boardFor(pkg: String): BoardRow? = boardRows.value.firstOrNull { it.claim.packageName == pkg && it.confirmed && it.tally != null }

    private suspend fun attested(w: String): Boolean = (facts.value[w] ?: runCatching { factsService.facts(w) }.getOrNull()?.also { f -> facts.update { it + (w to f) } })?.hasGenesisToken == true

    private suspend fun tallyRow(claim: com.undersigned.protocol.Payments.Claim, confirmed: Boolean): BoardRow {
        val name = catalogue.value.firstOrNull { it.packageName == claim.packageName }?.name ?: claim.packageName
        if (!confirmed) return BoardRow(claim, name, false, null, null, true)
        val read = board.read(claim.treasury, claim.code)
        val att = HashMap<String, Boolean>(); for (p in read.payments.map { it.payer }.distinct()) att[p] = attested(p)
        val t = com.undersigned.protocol.Payments.tally(read.payments, claim.treasury) { att[it] == true }
        val q = com.undersigned.app.ledger.Prices.quotes(listOf(AppConfig.Solana.SKR_MINT, com.undersigned.app.ledger.PaymentBoard.USDC_MINT)).also { quotes.value = it }
        return BoardRow(claim, name, true, t, com.undersigned.app.ledger.Prices.usd(t.lamports, t.tokens, q), read.complete, read.payments.map { it.payer }.filter { it != claim.treasury }.toSet())
    }

    /** Every claim on the ledger, checked against its website, then read. At most once every ten minutes unless [force]. */
    fun loadBoard(force: Boolean = false) {
        if (boardLoading.value || (!force && System.currentTimeMillis() - boardAt < 10 * 60_000L)) return
        viewModelScope.launch(Dispatchers.IO) {
            boardLoading.value = true
            try {
                val rows = ArrayList<BoardRow>()
                for (c in treasuryClaims()) {
                    val d = storeDetails.value[c.packageName] ?: store.details(c.packageName)?.also { d -> storeDetails.update { it + (c.packageName to d) } }
                    val ok = PublisherVerifier.verifyTreasury(c.packageName, c.treasury, d)
                    rows.add(runCatching { tallyRow(c, ok) }.getOrElse { BoardRow(c, c.packageName, ok, null, null, false) })
                    boardRows.value = rows.sortedWith(compareByDescending<BoardRow> { it.usd ?: -1.0 }.thenBy { it.name })
                }
                boardRows.value = rows.sortedWith(compareByDescending<BoardRow> { it.usd ?: -1.0 }.thenBy { it.name })
                boardAt = System.currentTimeMillis()
            } finally { boardLoading.value = false }
        }
    }

    /** What a treasury has been paid under a code, WITHOUT listing it: public data, shown to whoever asks, attributed to no app. */
    fun previewTreasury(treasury: String, code: String) {
        if (boardLoading.value) return
        viewModelScope.launch(Dispatchers.IO) {
            boardLoading.value = true
            try {
                val claim = com.undersigned.protocol.Payments.Claim("", treasury, code, 0L, "")
                boardPreview.value = runCatching { tallyRow(claim, true).copy(name = code) }.getOrNull()
            } finally { boardLoading.value = false }
        }
    }

    fun resolveDomains(wallets: Collection<String>) {
        val todo = wallets.distinct().filter { it !in domainNames.value }
        if (todo.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { for (w in todo) { val n = domains.mainDomain(w) ?: ""; domainNames.value = domainNames.value + (w to n) } }
    }
    fun displayName(wallet: String): String = domainNames.value[wallet]?.takeIf { it.isNotEmpty() } ?: com.undersigned.app.solana.shortKey(wallet, 4, 4)

    /** Scan installed + followed apps' public store reviews for ones signed by MY wallet (paged, stops on hit). */
    /**
     * Starts the background store scan (StoreScanWorker). Progress, the result and the saved reviews come back through
     * [watchStoreScan], so the scan survives leaving the app and the screen just follows it.
     */
    fun scanMyStoreReviews() {
        if (wallet.value == null || importScan.value != null) return
        importScan.value = "…"
        val req = androidx.work.OneTimeWorkRequestBuilder<com.undersigned.app.notify.StoreScanWorker>()
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()).build()
        androidx.work.WorkManager.getInstance(getApplication()).enqueueUniqueWork(com.undersigned.app.notify.StoreScanWorker.UNIQUE, androidx.work.ExistingWorkPolicy.KEEP, req)
    }
    private fun watchStoreScan() = viewModelScope.launch {
        val W = com.undersigned.app.notify.StoreScanWorker
        androidx.work.WorkManager.getInstance(getApplication()).getWorkInfosForUniqueWorkFlow(W.UNIQUE).collect { infos ->
            val info = infos.lastOrNull() ?: return@collect
            when (info.state) {
                androidx.work.WorkInfo.State.ENQUEUED, androidx.work.WorkInfo.State.BLOCKED -> importScan.value = "…"
                androidx.work.WorkInfo.State.RUNNING -> {
                    val p = info.progress; val total = p.getInt(W.P_TOTAL, 0)
                    importScan.value = if (total > 0) "${p.getInt(W.P_DONE, 0)}/$total" else "…"
                    importScanApp.value = p.getString(W.P_APP)
                }
                androidx.work.WorkInfo.State.SUCCEEDED -> {
                    wallet.value?.let { w -> UserPreferences.loadMyStoreReviews(w)?.let { (m, at) -> myStoreReviews.value = m; myStoreScannedAt.value = at } }
                    val out = info.outputData
                    if (out.keyValueMap.isNotEmpty()) importScanResult.value = out.getInt(W.OUT_FAILED, 0) to out.getInt(W.OUT_TOTAL, 0)
                    importScan.value = null; importScanApp.value = null
                }
                else -> { importScan.value = null; importScanApp.value = null }
            }
        }
    }

    /** Re-sign chosen store reviews as ledger records — one wallet approval for the lot. */
    fun importStoreReviews(sender: ActivityResultSender, chosen: List<Pair<String, StoreReview>>, onStage: (ReviewTxService.Stage) -> Unit, onResult: (Result<Int>) -> Unit) {
        val w = wallet.value ?: return onResult(Result.failure(Exception("Sign in with your wallet first")))
        val adapter = WalletSession.adapter ?: return onResult(Result.failure(Exception("Wallet adapter unavailable")))
        val memos = chosen.map { (pkg, r) -> ReviewMemo.Review(pkg, r.rating.coerceIn(1, 5), listOf(ReviewMemo.Tag.WORKS).takeIf { r.rating >= 4 } ?: listOf(ReviewMemo.Tag.CONFUSING), 1L, r.text?.take(ReviewMemo.MAX_TEXT_CHARS)?.replace('|', '/')?.replace('\n', ' ')?.let { t -> var x = t; while (x.toByteArray().size > ReviewMemo.MAX_TEXT_BYTES) x = x.dropLast(1); x.trim().ifEmpty { null } }) }
        val texts = memos.map { runCatching { ReviewMemo.encode(it) }.getOrElse { return onResult(Result.failure(it)) } }
        viewModelScope.launch {
            if (com.undersigned.app.util.WalletScreening.isSanctioned(w)) return@launch onResult(Result.failure(Exception(getApplication<Application>().getString(com.undersigned.app.R.string.wallet_sanctioned))))
            val r = txService.publishBatch(adapter, sender, w, texts, onStage)
            r.onSuccess { sigs ->
                val now = System.currentTimeMillis() / 1000
                val fresh = ArrayList<LedgerEntry>()
                sigs.forEach { (sig, chunk) -> chunk.forEachIndexed { idx, t -> ReviewMemo.decode(t)?.let { fresh.add(LedgerEntry(ReviewMemo.recordId(sig, idx), w, now, it)) } } }
                entries.value = (fresh + entries.value).distinctBy { it.signature }
                ledgerCache.save(entries.value)
                memos.forEach { UserPreferences.follow(it.packageName) }
                launch(Dispatchers.IO) { rank() }
            }
            onResult(r.map { it.sumOf { c -> c.second.size } })
        }
    }

    fun revokedSignatures(): Set<String> = Ranking.revoked(entries.value)

    /** Live (not revoked, not locally hidden) reviews for a package, newest first, with each signer's weight. Consensus-hidden ones are returned too; the UI collapses them. */
    fun reviewsFor(pkg: String): List<Pair<LedgerEntry, Double>> {
        val revoked = revokedSignatures(); val hid = hidden.value
        return entries.value.filter { it.memo is ReviewMemo.Review && (it.memo as ReviewMemo.Review).packageName == pkg && it.signature !in revoked && it.signature !in hid }
            .sortedWith(compareByDescending<LedgerEntry> { helpfulWeights.value[it.signature] ?: 0.0 }.thenByDescending { it.blockTime })
            .map { it to Ranking.weight(facts.value[it.signer] ?: SignerFacts(false, 0.0)) }
    }
    fun toggleStar(id: String) = UserPreferences.toggleStar(id)
    /** Whether MY wallet's latest up/unvote on [id] is an upvote. Facts are forced to weight 1 so an unattested wallet still sees its own vote state. */
    fun myUpvote(id: String): Boolean = wallet.value?.let { w -> Reputation.helpfulWeights(entries.value.filter { it.signer == w || it.signature == id }) { SignerFacts(true, 0.0) }.containsKey(id) } ?: false
    fun myFlag(id: String): Boolean = wallet.value?.let { w -> currentFlaggers.value[id]?.contains(w) == true } ?: false
    fun myClear(id: String): Boolean = wallet.value?.let { w -> currentClearers.value[id]?.contains(w) == true } ?: false
    fun draftLooksCopied(draft: String, pkg: String) = com.undersigned.protocol.Similarity.similarToExisting(draft, entries.value, author = wallet.value, packageName = pkg)
    fun packageOf(recordId: String): String? = (entries.value.firstOrNull { it.signature == recordId }?.memo as? ReviewMemo.Review)?.packageName
    fun myVerifiedPackages(): Set<String> = wallet.value?.let { w -> verifiedPublishers.value.filter { w in it.value }.keys } ?: emptySet()
    fun responsiveness(pkg: String): Responsiveness = Reputation.responsiveness(entries.value, pkg, verifiedPublishers.value[pkg].orEmpty())
    fun entriesBy(wallet: String): List<LedgerEntry> = entries.value.filter { it.signer == wallet && it.memo is ReviewMemo.Review && it.signature !in revokedSignatures() }
    fun publishedBy(wallet: String): List<String> = verifiedPublishers.value.filter { wallet in it.value }.keys.sorted()

    /** Publisher queue: signed reviews on my verified apps with no reply from me. */
    fun unanswered(): List<LedgerEntry> {
        val w = wallet.value ?: return emptyList(); val mine = myVerifiedPackages(); if (mine.isEmpty()) return emptyList()
        val revoked = revokedSignatures()
        val myReplies = entries.value.filter { it.signer == w && it.memo is ReviewMemo.Reply }.map { (it.memo as ReviewMemo.Reply).reviewSignature }.toSet()
        return entries.value.filter { it.memo is ReviewMemo.Review && it.signature !in revoked && (it.memo as ReviewMemo.Review).packageName in mine && it.signer != w && it.signature !in myReplies }
    }
    /** Replies by others on my records, newest first. */
    fun repliesToMe(): List<Pair<LedgerEntry, LedgerEntry>> {
        val w = wallet.value ?: return emptyList()
        val mine = entries.value.filter { it.signer == w }.associateBy { it.signature }
        return entries.value.filter { it.signer != w && it.memo is ReviewMemo.Reply && (it.memo as ReviewMemo.Reply).reviewSignature in mine }
            .map { it to mine[(it.memo as ReviewMemo.Reply).reviewSignature]!! }
    }

    /** One row per dApp the signed-in wallet has reviewed anywhere: its newest live signed review and its store review. */
    data class MyDapp(val pkg: String, val name: String, val signed: LedgerEntry?, val store: StoreReview?) {
        val lastAt: Long get() = maxOf(signed?.blockTime ?: 0L, store?.createdAt?.let { runCatching { java.time.Instant.parse(it).epochSecond }.getOrNull() } ?: 0L)
        /** Stars or text differ between the two, e.g. a typo fixed on one side only. */
        val differs: Boolean get() { val r = signed?.memo as? ReviewMemo.Review ?: return false; val s = store ?: return false
            return r.rating != s.rating || (r.text ?: "").trim() != (s.text ?: "").trim() }
    }
    fun myDapps(): List<MyDapp> {
        val w = wallet.value ?: return emptyList()
        val signed = entriesBy(w).groupBy { (it.memo as ReviewMemo.Review).packageName }.mapValues { (_, l) -> l.maxBy { it.blockTime } }
        val store = myStoreReviews.value
        return (signed.keys + store.keys).map { pkg -> MyDapp(pkg, catalogue.value.firstOrNull { it.packageName == pkg }?.name ?: labelFor(pkg, pkg), signed[pkg], store[pkg]) }
            .sortedByDescending { it.lastAt }
    }
    fun myReview(pkg: String): LedgerEntry? {
        val w = wallet.value ?: return null
        val revoked = revokedSignatures()
        return entries.value.filter { it.signer == w && it.signature !in revoked && (it.memo as? ReviewMemo.Review)?.packageName == pkg }.maxByOrNull { it.blockTime }
    }

    fun hide(signature: String) = UserPreferences.hide(signature)
    fun unhide(signature: String) = UserPreferences.unhide(signature)
    fun entryOf(signature: String): LedgerEntry? = entries.value.firstOrNull { it.signature == signature }
    fun toggleFollow(pkg: String) { if (pkg in follows.value) UserPreferences.unfollow(pkg) else UserPreferences.follow(pkg) }

    /** Signed replies under one review, oldest first, hidden ones removed. */
    fun repliesFor(reviewSignature: String): List<LedgerEntry> {
        val hid = hidden.value
        return entries.value.filter { (it.memo as? ReviewMemo.Reply)?.reviewSignature == reviewSignature && it.signature !in hid }.sortedBy { it.blockTime }
    }

    fun publish(sender: ActivityResultSender, memo: ReviewMemo, onStage: (ReviewTxService.Stage) -> Unit, onResult: (Result<String>) -> Unit) {
        val w = wallet.value ?: return onResult(Result.failure(Exception("Sign in with your wallet first")))
        val adapter = WalletSession.adapter ?: return onResult(Result.failure(Exception("Wallet adapter unavailable")))
        val text = runCatching { ReviewMemo.encode(memo) }.getOrElse { return onResult(Result.failure(it)) }
        viewModelScope.launch {
            // Fleet standard: screen the wallet again before every transaction it signs, not only at sign-in.
            if (com.undersigned.app.util.WalletScreening.isSanctioned(w)) return@launch onResult(Result.failure(Exception(getApplication<Application>().getString(com.undersigned.app.R.string.wallet_sanctioned))))
            val r = txService.publish(adapter, sender, w, text, onStage)
            r.onSuccess { sig ->
                (memo as? ReviewMemo.Review)?.let { UserPreferences.follow(it.packageName) }   // you hear about replies and other owners' reviews
                // Optimistic local insert so the user sees their own review immediately; the reader will confirm it.
                entries.value = (listOf(LedgerEntry(sig, w, System.currentTimeMillis() / 1000, memo)) + entries.value).distinctBy { it.signature }
                ledgerCache.save(entries.value)
                launch(Dispatchers.IO) { rank() }
                Log.i("MainViewModel", "published $sig")
            }
            onResult(r)
        }
    }

    /** "Re-read the catalogue" from the empty search state: a forced store crawl, then the device snapshot follows it. */
    val catalogueRefreshing = MutableStateFlow(false)
    fun refreshCatalogue() {
        if (catalogueRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            catalogueRefreshing.value = true
            catalogueRepo.refresh()?.let { fresh -> catalogue.value = fresh; runCatching { installedApps.refreshSnapshot(fresh.map { it.packageName }) }; changes.value = installedApps.changeLog() }
            catalogueRefreshing.value = false
        }
    }

    /** Ledger signers whose known name or address matches a typed query: instant, from what the device already knows. */
    fun signerMatches(query: String): List<Pair<String, String>> {
        val q = query.trim().lowercase().removeSuffix(".skr"); if (q.length < 2) return emptyList()
        val names = domainNames.value
        return ledgerSigners().filter { w -> (names[w]?.lowercase()?.contains(q) == true) || w.lowercase().startsWith(q) }
            .map { w -> w to (names[w]?.takeIf { it.isNotEmpty() } ?: com.undersigned.app.solana.shortKey(w, 4, 4)) }.take(5)
    }

    data class Stats(val records: Int, val reviewsLive: Int, val reviewsCounted: Int, val signers: Int, val attestedSigners: Int, val seekerSigned: Int, val seekerTotal: Int,
        val apps: Int, val appsScored: Int, val replies: Int, val publisherReplies: Int, val claims: Int, val claimsVerified: Int, val flags: Int, val clears: Int, val disputes: Int,
        val hidden: Int, val withdrawn: Int, val licences: Int, val first: Long, val latest: Long)

    /** The curator's numbers, recomputed from the registry on this device. */
    fun stats(): Stats {
        val all = entries.value; val revoked = revokedSignatures(); val f = facts.value; val vp = verifiedPublishers.value
        val live = all.filter { it.memo is ReviewMemo.Review && it.signature !in revoked }
        val counted = live.filter { (f[it.signer]?.hasGenesisToken == true) && !isSelfPublished(it) }
        val signers = live.map { it.signer }.toSet()
        val replies = all.filter { it.memo is ReviewMemo.Reply }
        val byId = all.associateBy { it.signature }
        val pubReplies = replies.count { r -> val pkg = (byId[(r.memo as ReviewMemo.Reply).reviewSignature]?.memo as? ReviewMemo.Review)?.packageName; pkg != null && vp[pkg]?.contains(r.signer) == true }
        val meta = txMeta.value
        val ordered = all.filter { it.memo !is ReviewMemo.Pro && it.memo !is ReviewMemo.Share && it.memo !is ReviewMemo.Unshare }
        return Stats(all.size, live.size, counted.size, signers.size, signers.count { f[it]?.hasGenesisToken == true }, meta.values.count { it.seekerWallet }, meta.size,
            live.map { (it.memo as ReviewMemo.Review).packageName }.toSet().size, scores.value.size, replies.size, pubReplies,
            all.count { it.memo is ReviewMemo.Claim }, vp.values.sumOf { it.size }, all.count { it.memo is ReviewMemo.Flag }, all.count { it.memo is ReviewMemo.Clear }, all.count { it.memo is ReviewMemo.Dispute },
            hiddenByConsensus.value.size, revoked.size, all.count { it.memo is ReviewMemo.Pro },
            ordered.minOfOrNull { it.blockTime } ?: 0L, ordered.maxOfOrNull { it.blockTime } ?: 0L)
    }

    /** Icons for the rows currently on screen; cached results are never refetched. */
    fun resolveIcons(packages: List<String>) { if (packages.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { runCatching { iconCache.resolve(packages) } } }

    fun labelFor(pkg: String, fallback: String): String = installedApps.appLabel(pkg) ?: fallback
    fun deviceFacts(pkg: String) = installedApps.factsWithDelta(pkg)
    fun clearLocalCaches() { board.clear(); ledgerCache.clear(); UserPreferences.clearFacts(); entries.value = emptyList(); scores.value = emptyMap(); facts.value = emptyMap(); txMeta.value = emptyMap(); pro.value = null }

    // ---- Pro ----------------------------------------------------------------------------------------------

    /** The one payment. On confirmation the licence and what it paid are inserted locally so the term shows at once; the reader confirms it. */
    fun payPro(sender: ActivityResultSender, rail: com.undersigned.app.solana.ProPaymentService.Rail, onStage: (ReviewTxService.Stage) -> Unit, onResult: (Result<String>) -> Unit) {
        val w = wallet.value ?: return onResult(Result.failure(Exception(getApplication<Application>().getString(com.undersigned.app.R.string.pro_needs_wallet))))
        val adapter = WalletSession.adapter ?: return onResult(Result.failure(Exception("Wallet adapter unavailable")))
        viewModelScope.launch {
            val r = proService.pay(getApplication(), adapter, sender, w, rail, onStage)
            r.onSuccess { rc ->
                entries.value = (listOf(LedgerEntry(rc.signature, w, System.currentTimeMillis() / 1000, ReviewMemo.Pro(com.undersigned.app.AppConfig.Payment.TERM_MONTHS))) + entries.value).distinctBy { it.signature }
                txMeta.value = txMeta.value + (rc.signature to TxMeta(0L, true, rc.paidLamports, rc.paidSkr))
                ledgerCache.save(entries.value); ledgerCache.saveMeta(txMeta.value)
                recomputePro()
                if (UserPreferences.alertsEnabled.value) com.undersigned.app.notify.LedgerWatchWorker.schedule(getApplication(), runNow = false)
                Log.i("MainViewModel", "pro licence ${rc.signature} (${rc.rail})")
            }
            onResult(r.map { it.signature })
        }
    }

    /** My signed reviews with no store review by this wallet on the same dApp (as far as the mirror has looked). */
    fun copyQueue(): List<LedgerEntry> = wallet.value?.let { w -> entriesBy(w).filter { (it.memo as ReviewMemo.Review).packageName !in myStoreReviews.value }.sortedByDescending { it.blockTime } } ?: emptyList()

    data class DeskApp(val packageName: String, val score: AppScore?, val responsiveness: Responsiveness, val waiting: Int)
    data class Desk(val apps: List<DeskApp>, val unanswered: Int, val medianReplyHours: Double?, val answeredPercent: Int?, val oldest: List<LedgerEntry>)

    /** The publisher desk: every verified dApp, the unanswered queue oldest first, and the reply gap as readers see it. */
    fun desk(): Desk {
        val w = wallet.value ?: return Desk(emptyList(), 0, null, null, emptyList())
        val mine = myVerifiedPackages().sorted()
        val queue = unanswered()
        val apps = mine.map { pkg -> DeskApp(pkg, scores.value[pkg], responsiveness(pkg), queue.count { (it.memo as ReviewMemo.Review).packageName == pkg }) }
        val revoked = revokedSignatures()
        val reviews = entries.value.filter { it.memo is ReviewMemo.Review && it.signature !in revoked && (it.memo as ReviewMemo.Review).packageName in mine && it.signer != w }
        val gaps = reviews.mapNotNull { r -> entries.value.filter { it.signer == w && (it.memo as? ReviewMemo.Reply)?.reviewSignature == r.signature }.minOfOrNull { it.blockTime }?.let { t -> (t - r.blockTime).coerceAtLeast(0L) } }.sorted()
        val median = if (gaps.isEmpty()) null else (if (gaps.size % 2 == 1) gaps[gaps.size / 2].toDouble() else (gaps[gaps.size / 2 - 1] + gaps[gaps.size / 2]) / 2.0) / 3600.0
        val pct = if (reviews.isEmpty()) null else (gaps.size * 100 / reviews.size)
        return Desk(apps, queue.size, median, pct, queue.sortedBy { it.blockTime })
    }

    /** The whole ledger as this device holds it, CSV or JSON. Public data; nothing about the exporter is in it. */
    fun exportLedger(json: Boolean): String {
        fun kind(m: ReviewMemo) = when (m) { is ReviewMemo.Review -> "review"; is ReviewMemo.Revocation -> "revocation"; is ReviewMemo.Reply -> "reply"; is ReviewMemo.Claim -> "claim"; is ReviewMemo.Flag -> "flag"; is ReviewMemo.Unflag -> "unflag"; is ReviewMemo.Upvote -> "helpful"; is ReviewMemo.Unvote -> "unhelpful"; is ReviewMemo.Clear -> "clear"; is ReviewMemo.Dispute -> "dispute"; is ReviewMemo.Pro -> "licence"; is ReviewMemo.Share -> "share"; is ReviewMemo.Unshare -> "unshare"; is ReviewMemo.Use -> "install-age"; is ReviewMemo.Time -> "use-time"; is ReviewMemo.Treasury -> "treasury"; is ReviewMemo.Untreasury -> "treasury-withdrawn" }
        fun target(m: ReviewMemo) = when (m) { is ReviewMemo.Revocation -> m.reviewSignature; is ReviewMemo.Reply -> m.reviewSignature; is ReviewMemo.Flag -> m.recordId; is ReviewMemo.Unflag -> m.recordId; is ReviewMemo.Upvote -> m.recordId; is ReviewMemo.Unvote -> m.recordId; is ReviewMemo.Clear -> m.recordId; is ReviewMemo.Dispute -> m.recordId; else -> "" }
        fun text(m: ReviewMemo) = when (m) { is ReviewMemo.Review -> m.text ?: ""; is ReviewMemo.Reply -> m.text; is ReviewMemo.Dispute -> m.text; is ReviewMemo.Flag -> m.reason.wire; is ReviewMemo.Use -> m.bucket.wire; is ReviewMemo.Time -> m.time.wire; is ReviewMemo.Treasury -> m.code; else -> "" }
        fun pkgOf(m: ReviewMemo) = when (m) { is ReviewMemo.Review -> m.packageName; is ReviewMemo.Claim -> m.packageName; is ReviewMemo.Use -> m.packageName; is ReviewMemo.Time -> m.packageName; is ReviewMemo.Treasury -> m.packageName; is ReviewMemo.Untreasury -> m.packageName; else -> "" }
        val rows = entries.value.sortedBy { it.blockTime }
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        if (json) {
            val arr = org.json.JSONArray()
            rows.forEach { e -> val m = e.memo; arr.put(org.json.JSONObject().put("id", e.signature).put("signer", e.signer).put("time", iso.format(java.util.Date(e.blockTime * 1000))).put("kind", kind(m))
                .put("package", pkgOf(m)).put("rating", (m as? ReviewMemo.Review)?.rating ?: org.json.JSONObject.NULL)
                .put("tags", (m as? ReviewMemo.Review)?.tags?.joinToString(",") { it.wire } ?: "").put("version", (m as? ReviewMemo.Review)?.versionCode ?: org.json.JSONObject.NULL).put("target", target(m)).put("text", text(m))) }
            return arr.toString(2)
        }
        fun q(v: Any?) = "\"" + v.toString().replace("\"", "\"\"") + "\""
        val sb = StringBuilder("id,signer,time,kind,package,rating,tags,version,target,text\n")
        rows.forEach { e -> val m = e.memo; sb.append(listOf(e.signature, e.signer, iso.format(java.util.Date(e.blockTime * 1000)), kind(m), pkgOf(m), (m as? ReviewMemo.Review)?.rating ?: "", (m as? ReviewMemo.Review)?.tags?.joinToString(",") { it.wire } ?: "", (m as? ReviewMemo.Review)?.versionCode ?: "", target(m), text(m)).joinToString(",") { q(it) }).append('\n') }
        return sb.toString()
    }
}
