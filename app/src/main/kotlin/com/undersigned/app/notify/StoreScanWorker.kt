package com.undersigned.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.undersigned.app.R
import com.undersigned.app.data.CatalogueRepository
import com.undersigned.app.data.LedgerCache
import com.undersigned.app.data.StoreReviewsClient
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.device.InstalledApps
import com.undersigned.protocol.ReviewMemo

/**
 * Finds the signed-in wallet's own dApp Store reviews, in the background. The scan is paced (~1 page a second, because
 * the store's CDN blocks a whole network after a few hundred quick reads) and so takes minutes; run inside the screen
 * it stopped whenever the user left the app — on 26 Sep 2026 another app came to the front mid-scan and nothing was
 * saved. As a job it finishes on its own and says so in a notification that opens "My reviews".
 *
 * Saving is safe even when some apps could not be read: a failed read never removes a cached review (only a read that
 * reached the LAST review may), so the cache only ever gains what was actually confirmed.
 */
class StoreScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        try { UserPreferences.init(ctx) } catch (_: Exception) { }
        val w = UserPreferences.walletAddress.value ?: return Result.success()
        val names = runCatching { CatalogueRepository(ctx).load().associate { it.packageName to it.name } }.getOrDefault(emptyMap())
        val signed = runCatching { LedgerCache(ctx).load().filter { it.signer == w && it.memo is ReviewMemo.Review }.map { (it.memo as ReviewMemo.Review).packageName } }.getOrDefault(emptyList())
        val targets = (runCatching { InstalledApps(ctx).installedPackages() }.getOrDefault(emptySet()) + UserPreferences.follows.value + signed).filter { it in names }.distinct()
        val found = HashMap(UserPreferences.loadMyStoreReviews(w)?.first ?: emptyMap()); var failed = 0
        val store = StoreReviewsClient()
        targets.forEachIndexed { i, pkg ->
            if (isStopped) return Result.success(workDataOf(OUT_FAILED to targets.size - i + failed, OUT_TOTAL to targets.size))
            setProgress(workDataOf(P_DONE to i + 1, P_TOTAL to targets.size, P_APP to (names[pkg] ?: pkg)))
            // Newest 300 reviews per app (covers the Toolkit's 279).
            val read = store.reviewsChecked(pkg, pageSize = 10, maxPages = 30, pauseMs = 700) { it.walletAddress == w }
            if (!read.complete) failed++
            val hit = read.items.firstOrNull { it.walletAddress == w }
            if (hit != null) found[pkg] = hit else if (read.exhausted) found.remove(pkg)
        }
        UserPreferences.saveMyStoreReviews(w, found)
        val title = ctx.getString(R.string.scan_done_title)
        val body = if (failed > 0) ctx.getString(R.string.import_incomplete, failed, targets.size)
            else ctx.resources.getQuantityString(R.plurals.scan_done_body, found.size, found.size)
        LedgerWatchWorker.notify(ctx, "store-scan:$w", title, body, extraValue = "my-reviews", extraKey = "screen")
        return Result.success(workDataOf(OUT_FAILED to failed, OUT_TOTAL to targets.size))
    }

    companion object {
        const val UNIQUE = "store-scan"
        const val P_DONE = "done"; const val P_TOTAL = "total"; const val P_APP = "app"
        const val OUT_FAILED = "failed"; const val OUT_TOTAL = "total"
    }
}
