package com.undersigned.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
    val importScan = MutableStateFlow<String?>(null)   // progress text while scanning
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
        viewModelScope.launch { UserPreferences.walletAddress.collect { w -> recomputePro(); if (w == null) selfFacts.value = null else loadSelfFacts(w) } }
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
        val changed = verifiedPublishers.value != out
        verifiedPublishers.value = out
        UserPreferences.saveVerifiedPublishers(out)
        if (changed) { val f = { w: String -> facts.value[w] ?: SignerFacts(false, 0.0) }; scores.value = Ranking.score(list, out, f).associateBy { it.packageName }; standings.value = Reputation.standing(list, out, f); flagWeights.value = Reputation.flagWeights(list, out, f); hiddenByConsensus.value = Reputation.hiddenByConsensus(list, out, f) }
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
            store.rating(pkg)?.let { r -> storeRatings.value = storeRatings.value + (pkg to r) }
            if (!storeDetails.value.containsKey(pkg)) store.details(pkg)?.let { d -> storeDetails.value = storeDetails.value + (pkg to d) }
            val list = store.reviews(pkg, pageSize = 10, maxPages = 1)
            storeReviews.value = storeReviews.value + (pkg to list)
            wallet.value?.let { w -> list.firstOrNull { it.walletAddress == w }?.let { mine -> myStoreReviews.value = myStoreReviews.value + (pkg to mine) } }
        }
    }

    fun resolveDomains(wallets: Collection<String>) {
        val todo = wallets.distinct().filter { it !in domainNames.value }
        if (todo.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { for (w in todo) { val n = domains.mainDomain(w) ?: ""; domainNames.value = domainNames.value + (w to n) } }
    }
    fun displayName(wallet: String): String = domainNames.value[wallet]?.takeIf { it.isNotEmpty() } ?: com.undersigned.app.solana.shortKey(wallet, 4, 4)

    /** Scan installed + followed apps' public store reviews for ones signed by MY wallet (paged, stops on hit). */
    fun scanMyStoreReviews() {
        val w = wallet.value ?: return
        if (importScan.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val targets = (installed.value + follows.value).filter { p -> catalogue.value.any { it.packageName == p } }.distinct()
            val found = HashMap(myStoreReviews.value)
            targets.forEachIndexed { i, pkg ->
                importScan.value = "${i + 1}/${targets.size} · $pkg"
                val hit = store.reviews(pkg, pageSize = 20, maxPages = 25) { it.walletAddress == w }.firstOrNull { it.walletAddress == w }
                if (hit != null) found[pkg] = hit
            }
            myStoreReviews.value = found
            importScan.value = null
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
    fun clearLocalCaches() { ledgerCache.clear(); UserPreferences.clearFacts(); entries.value = emptyList(); scores.value = emptyMap(); facts.value = emptyMap(); txMeta.value = emptyMap(); pro.value = null }

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
        fun kind(m: ReviewMemo) = when (m) { is ReviewMemo.Review -> "review"; is ReviewMemo.Revocation -> "revocation"; is ReviewMemo.Reply -> "reply"; is ReviewMemo.Claim -> "claim"; is ReviewMemo.Flag -> "flag"; is ReviewMemo.Unflag -> "unflag"; is ReviewMemo.Upvote -> "helpful"; is ReviewMemo.Unvote -> "unhelpful"; is ReviewMemo.Clear -> "clear"; is ReviewMemo.Dispute -> "dispute"; is ReviewMemo.Pro -> "licence"; is ReviewMemo.Share -> "share"; is ReviewMemo.Unshare -> "unshare" }
        fun target(m: ReviewMemo) = when (m) { is ReviewMemo.Revocation -> m.reviewSignature; is ReviewMemo.Reply -> m.reviewSignature; is ReviewMemo.Flag -> m.recordId; is ReviewMemo.Unflag -> m.recordId; is ReviewMemo.Upvote -> m.recordId; is ReviewMemo.Unvote -> m.recordId; is ReviewMemo.Clear -> m.recordId; is ReviewMemo.Dispute -> m.recordId; else -> "" }
        fun text(m: ReviewMemo) = when (m) { is ReviewMemo.Review -> m.text ?: ""; is ReviewMemo.Reply -> m.text; is ReviewMemo.Dispute -> m.text; is ReviewMemo.Flag -> m.reason.wire; else -> "" }
        val rows = entries.value.sortedBy { it.blockTime }
        val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        if (json) {
            val arr = org.json.JSONArray()
            rows.forEach { e -> val m = e.memo; arr.put(org.json.JSONObject().put("id", e.signature).put("signer", e.signer).put("time", iso.format(java.util.Date(e.blockTime * 1000))).put("kind", kind(m))
                .put("package", (m as? ReviewMemo.Review)?.packageName ?: (m as? ReviewMemo.Claim)?.packageName ?: "").put("rating", (m as? ReviewMemo.Review)?.rating ?: org.json.JSONObject.NULL)
                .put("tags", (m as? ReviewMemo.Review)?.tags?.joinToString(",") { it.wire } ?: "").put("version", (m as? ReviewMemo.Review)?.versionCode ?: org.json.JSONObject.NULL).put("target", target(m)).put("text", text(m))) }
            return arr.toString(2)
        }
        fun q(v: Any?) = "\"" + v.toString().replace("\"", "\"\"") + "\""
        val sb = StringBuilder("id,signer,time,kind,package,rating,tags,version,target,text\n")
        rows.forEach { e -> val m = e.memo; sb.append(listOf(e.signature, e.signer, iso.format(java.util.Date(e.blockTime * 1000)), kind(m), (m as? ReviewMemo.Review)?.packageName ?: (m as? ReviewMemo.Claim)?.packageName ?: "", (m as? ReviewMemo.Review)?.rating ?: "", (m as? ReviewMemo.Review)?.tags?.joinToString(",") { it.wire } ?: "", (m as? ReviewMemo.Review)?.versionCode ?: "", target(m), text(m)).joinToString(",") { q(it) }).append('\n') }
        return sb.toString()
    }
}
