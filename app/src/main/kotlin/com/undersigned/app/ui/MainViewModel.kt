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
    private val txService = ReviewTxService()
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
            rank()
            syncLedger()
            if (catalogueRepo.isStale()) catalogueRepo.refresh()?.let { fresh -> catalogue.value = fresh; runCatching { installedApps.refreshSnapshot(fresh.map { it.packageName }) }; changes.value = installedApps.changeLog() }
        }
        viewModelScope.launch { UserPreferences.walletAddress.collect { w -> if (w == null) selfFacts.value = null else loadSelfFacts(w) } }
        // Near-real-time while the app is open: one cheap tail read a minute (until = newest known signature).
        viewModelScope.launch(Dispatchers.IO) { while (isActive) { delay(60_000); if (ledgerStatus.value !is LedgerStatus.Syncing) syncLedger() } }
    }

    fun syncLedger() {
        if (ledgerStatus.value is LedgerStatus.Syncing) return
        viewModelScope.launch(Dispatchers.IO) {
            ledgerStatus.value = LedgerStatus.Syncing(0, 0)
            reader.sync { p -> ledgerStatus.value = LedgerStatus.Syncing(p.fetched, p.total) }.fold(
                onSuccess = { list -> entries.value = list; txMeta.value = ledgerCache.loadMeta(); rank(); val rv = Ranking.revoked(list); ledgerStatus.value = LedgerStatus.Synced(list.count { it.memo is ReviewMemo.Review && it.signature !in rv }, list.filter { it.memo is ReviewMemo.Review }.map { it.signer }.distinct().size) },
                onFailure = { ledgerStatus.value = LedgerStatus.Error(it.message?.take(80) ?: "unknown") },
            )
        }
    }

    /** Facts for every distinct signer (cached, one lookup each), then the deterministic ranking. */
    private suspend fun rank() = withContext(Dispatchers.IO) {
        val list = entries.value
        val map = HashMap(facts.value)
        for (s in list.map { it.signer }.distinct()) if (s !in map) map[s] = runCatching { factsService.facts(s) }.getOrElse { SignerFacts(false, 0.0) }
        facts.value = map
        val f = { w: String -> map[w] ?: SignerFacts(false, 0.0) }
        scores.value = Ranking.score(list, f).associateBy { it.packageName }
        standings.value = Reputation.standing(list, f)
        flagWeights.value = Reputation.flagWeights(list, f)
        hiddenByConsensus.value = Reputation.hiddenByConsensus(list, f)
        helpfulWeights.value = Reputation.helpfulWeights(list, f)
        disputes.value = Reputation.disputes(list)
        similarity.value = com.undersigned.protocol.Similarity.findings(list)
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
        verifiedPublishers.value = out
    }
    fun isVerifiedPublisher(pkg: String, wallet: String) = verifiedPublishers.value[pkg]?.contains(wallet) == true
    fun myClaim(pkg: String): LedgerEntry? = wallet.value?.let { w -> entries.value.firstOrNull { it.signer == w && (it.memo as? ReviewMemo.Claim)?.packageName == pkg } }
    fun versionScores(pkg: String) = Reputation.byVersion(entries.value, pkg) { facts.value[it] ?: SignerFacts(false, 0.0) }
    /** Signer facts for arbitrary wallets (store reviewers), cached; fills [facts]. */
    fun resolveFacts(wallets: Collection<String>) {
        val todo = wallets.distinct().filter { it !in facts.value }
        if (todo.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { for (w in todo) { val f = runCatching { factsService.facts(w) }.getOrElse { SignerFacts(false, 0.0) }; facts.value = facts.value + (w to f) } }
    }
    fun ledgerSigners(): Set<String> = entries.value.filter { it.memo is ReviewMemo.Review }.map { it.signer }.toSet()

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
    fun myUpvote(id: String): Boolean = wallet.value?.let { w -> entries.value.any { it.signer == w && (it.memo as? ReviewMemo.Upvote)?.recordId == id } } ?: false
    fun draftLooksCopied(draft: String) = com.undersigned.protocol.Similarity.similarToExisting(draft, entries.value)
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

    /** Icons for the rows currently on screen; cached results are never refetched. */
    fun resolveIcons(packages: List<String>) { if (packages.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { runCatching { iconCache.resolve(packages) } } }

    fun labelFor(pkg: String, fallback: String): String = installedApps.appLabel(pkg) ?: fallback
    fun deviceFacts(pkg: String) = installedApps.factsWithDelta(pkg)
    fun clearLocalCaches() { ledgerCache.clear(); UserPreferences.clearFacts(); entries.value = emptyList(); scores.value = emptyMap(); facts.value = emptyMap() }
}
