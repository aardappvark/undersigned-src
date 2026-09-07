package com.undersigned.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.undersigned.app.data.CatalogueApp
import com.undersigned.app.data.CatalogueRepository
import com.undersigned.app.data.DAppIconCache
import com.undersigned.app.data.StoreRating
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
    private val factsService = SignerFactsService()
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
            entries.value = ledgerCache.load()
            rank()
            syncLedger()
            if (catalogueRepo.isStale()) catalogueRepo.refresh()?.let { fresh -> catalogue.value = fresh; runCatching { installedApps.refreshSnapshot(fresh.map { it.packageName }) }; changes.value = installedApps.changeLog() }
        }
        viewModelScope.launch { UserPreferences.walletAddress.collect { w -> if (w == null) selfFacts.value = null else loadSelfFacts(w) } }
    }

    fun syncLedger() {
        if (ledgerStatus.value is LedgerStatus.Syncing) return
        viewModelScope.launch(Dispatchers.IO) {
            ledgerStatus.value = LedgerStatus.Syncing(0, 0)
            reader.sync { p -> ledgerStatus.value = LedgerStatus.Syncing(p.fetched, p.total) }.fold(
                onSuccess = { list -> entries.value = list; rank(); val rv = Ranking.revoked(list); ledgerStatus.value = LedgerStatus.Synced(list.count { it.memo is ReviewMemo.Review && it.signature !in rv }, list.filter { it.memo is ReviewMemo.Review }.map { it.signer }.distinct().size) },
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
        scores.value = Ranking.score(list) { map[it] ?: SignerFacts(false, 0.0) }.associateBy { it.packageName }
    }

    private fun loadSelfFacts(address: String) {
        viewModelScope.launch(Dispatchers.IO) {
            selfFactsLoading.value = true
            selfFacts.value = runCatching { factsService.facts(address, force = true) }.getOrNull()
            selfFactsLoading.value = false
        }
    }

    fun loadStoreMirror(pkg: String) {
        if (storeRatings.value.containsKey(pkg)) return
        viewModelScope.launch(Dispatchers.IO) {
            store.rating(pkg)?.let { r -> storeRatings.value = storeRatings.value + (pkg to r) }
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

    /** Live (not revoked, not hidden) reviews for a package, newest first, with each signer's weight. */
    fun reviewsFor(pkg: String): List<Pair<LedgerEntry, Double>> {
        val revoked = revokedSignatures(); val hid = hidden.value
        return entries.value.filter { it.memo is ReviewMemo.Review && (it.memo as ReviewMemo.Review).packageName == pkg && it.signature !in revoked && it.signature !in hid }
            .map { it to Ranking.weight(facts.value[it.signer] ?: SignerFacts(false, 0.0)) }
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
