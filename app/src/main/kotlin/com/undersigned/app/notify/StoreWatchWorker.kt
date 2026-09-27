package com.undersigned.app.notify

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.undersigned.app.data.CatalogueRepository
import com.undersigned.app.data.StoreRatingsArchive
import com.undersigned.app.data.StoreReviewsClient
import com.undersigned.app.data.UserPreferences
import com.undersigned.protocol.RatingSnapshots
import com.undersigned.protocol.ReviewBursts
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Store watch (opt-in): once a day, every catalogue app's star counts in batches of 20 (72 requests for 1,428 apps,
 * 1.5 s apart — the store's CDN blocks a whole network that reads it fast), kept 60 days on the phone; then every app
 * is checked for a burst in its ratings that ended in the last 3 days. The result is a list the Store watch screen
 * shows. Nothing is sent anywhere but the store's own public feed.
 */
class StoreWatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!running.compareAndSet(false, true)) return Result.success()
        try { return run() } finally { running.set(false) }
    }

    private suspend fun run(): Result {
        val ctx = applicationContext
        try { UserPreferences.init(ctx) } catch (_: Exception) { }
        if (!UserPreferences.storeWatch) return Result.success()
        val pkgs = runCatching { CatalogueRepository(ctx).load().map { it.packageName } }.getOrDefault(emptyList())
        if (pkgs.isEmpty()) return Result.retry()
        val store = StoreReviewsClient()
        val counts = HashMap<String, List<Int>>(); var failed = 0
        for ((i, chunk) in pkgs.chunked(StoreReviewsClient.MAX_BATCH).withIndex()) {
            if (isStopped) return Result.success()
            if (i > 0) delay(PAUSE_MS)
            store.ratingsBatch(chunk)?.let { counts.putAll(it) } ?: failed++
            setProgress(androidx.work.workDataOf("done" to (i + 1) * StoreReviewsClient.MAX_BATCH, "total" to pkgs.size))
        }
        Log.i(TAG, "read ${counts.size} of ${pkgs.size} apps, $failed failed batches")
        // A read that mostly failed is not a day's snapshot: it would look like every app lost its ratings.
        if (counts.size < pkgs.size / 2) return Result.retry()
        val archive = archive(ctx)
        val now = System.currentTimeMillis() / 1000
        archive.record(counts, now)
        write(ctx, scan(archive, now), counts.size, archive.days())
        return Result.success()
    }

    companion object {
        private const val TAG = "StoreWatch"
        private const val UNIQUE = "store-watch"
        private const val PAUSE_MS = 1500L
        private val running = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile private var shared: StoreRatingsArchive? = null
        /** One instance per process, so its parsed cache is reused (a new one per call re-read 60 files every time). */
        fun archive(ctx: Context): StoreRatingsArchive = shared ?: synchronized(this) { shared ?: StoreRatingsArchive(File(ctx.applicationContext.filesDir, "ratings")).also { shared = it } }

        /**
         * One app's star-count snapshots: the store-wide archive merged with the per-app ones, at least 20 h apart. The
         * screen and the background alert both use this, so they cannot disagree about a burst.
         */
        fun snapshots(ctx: Context, pkg: String): List<RatingSnapshots.Snapshot> {
            val all = (archive(ctx).snapshots(pkg) + UserPreferences.ratingSnapshots(pkg)).sortedBy { it.time }
            val out = ArrayList<RatingSnapshots.Snapshot>()
            for (s in all) if (out.isEmpty() || s.time - out.last().time >= 20 * 3600) out.add(s)
            return out
        }
        fun resultFile(ctx: Context) = File(ctx.filesDir, "store_watch.json")

        /** Rating bursts that ended within the last 3 days, strongest first. */
        fun scan(archive: StoreRatingsArchive, now: Long): List<Pair<String, ReviewBursts.Burst>> {
            val fresh = now - 3 * ReviewBursts.DAY
            return archive.all().flatMap { (pkg, snaps) ->
                ReviewBursts.find(RatingSnapshots.toPoints(snaps), ReviewBursts.MIN_SNAPSHOT_DAYS).bursts.filter { it.end >= fresh }.map { pkg to it }
            }.sortedByDescending { it.second.intensity }
        }

        private fun write(ctx: Context, hits: List<Pair<String, ReviewBursts.Burst>>, apps: Int, days: List<String>) {
            val arr = JSONArray()
            hits.forEach { (pkg, b) -> arr.put(JSONObject().put("p", pkg).put("k", b.kind.name).put("s", b.start).put("e", b.end).put("n", b.count).put("t", b.total).put("x", b.expected)) }
            resultFile(ctx).writeText(JSONObject().put("at", System.currentTimeMillis()).put("apps", apps).put("days", JSONArray(days)).put("hits", arr).toString())
        }

        /**
         * One periodic job; its first run starts at once. A separate run-now request fired alongside it and the store was
         * read twice in parallel, 144 requests instead of 72 (measured 28 Sep 2026); the worker also refuses to overlap.
         */
        fun schedule(ctx: Context) {
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<StoreWatchWorker>(24, TimeUnit.HOURS).setConstraints(c).build())
        }
        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE) }
        fun work(ctx: Context) = WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow(UNIQUE)
    }
}
