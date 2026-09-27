package com.undersigned.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.undersigned.app.MainActivity
import com.undersigned.app.R
import com.undersigned.app.data.CatalogueRepository
import com.undersigned.app.data.StoreReviewsClient
import com.undersigned.app.device.InstalledApps
import com.undersigned.protocol.Ranking
import com.undersigned.protocol.SignerFacts
import java.time.Instant
import com.undersigned.app.data.LedgerCache
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.ledger.LedgerReader
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.ReviewMemo
import java.util.concurrent.TimeUnit

/**
 * No server, so "notifications" are the device re-reading the ledger tail on a schedule (one
 * getSignaturesForAddress call when nothing is new) and raising LOCAL notifications for:
 *  - a new signed review on an app you follow (a publisher follows their own apps), and
 *  - a signed reply to a review you wrote.
 * Nothing leaves the device; the alert text names the app and nothing else.
 */
class LedgerWatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        // The periodic schedule and the run-now request are separate unique work names, so both can fire
        // at once (observed 9 Sep 2026: two concurrent runs after toggling alerts, each doing a full
        // ledger sync and store query). One at a time is enough.
        if (!running.compareAndSet(false, true)) { Log.i(TAG, "another run is in progress; skipping"); return Result.success() }
        try {
            return runOnce()
        } finally {
            running.set(false)
        }
    }

    private suspend fun runOnce(): Result {
        val ctx = applicationContext
        try { UserPreferences.init(ctx) } catch (_: Exception) { }
        if (!UserPreferences.alertsEnabled.value) { Log.i(TAG, "alerts off; nothing to do"); return Result.success() }
        val cache = LedgerCache(ctx)
        val before = cache.load()
        val after = LedgerReader(cache = cache).sync().getOrElse { Log.w(TAG, "ledger sync failed, will retry: ${it.message?.take(80)}"); return Result.retry() }
        Log.i(TAG, "run: ${before.size} cached -> ${after.size} records")
        val names = runCatching { CatalogueRepository(ctx).load().associate { it.packageName to it.name } }.getOrDefault(emptyMap())
        // Notification ids are derived from WHAT the alert is about, never from a per-run counter. The counter
        // restarted at 1000 on every run, so the first alert of one run silently REPLACED the first alert of the
        // previous run — measured on the owner's Seeker 20 Sep 2026: an update prompt posted on 18 Sep had taken
        // the place of a reply alert posted on 17 Sep, both under id 1000. A stable id also makes re-posting the
        // same record idempotent instead of stacking duplicates.
        var posted = 0
        fun post(key: String, title: String, body: String, pkg: String) { if (notify(ctx, key, title, body, pkg)) posted++ }
        val follows = UserPreferences.follows.value
        val me = UserPreferences.walletAddress.value
        Log.i(TAG, "following ${follows.size}, wallet ${if (me == null) "none" else "connected"}")
        // (a) Installed apps that updated: ask followers / previous reviewers for a fresh signed review.
        runCatching {
            val reviewedByMe = after.filter { it.signer == me && it.memo is ReviewMemo.Review }.map { (it.memo as ReviewMemo.Review).packageName }.toSet()
            val updated = InstalledApps(ctx).refreshSnapshot(names.keys).filter { it.versionChanged && (it.packageName in follows || it.packageName in reviewedByMe) }
            Log.i(TAG, "update prompts: ${updated.size}")
            updated.forEach { c -> post("upd:${c.packageName}:${c.versionCode}", ctx.getString(R.string.notif_update_title, names[c.packageName] ?: c.packageName, c.versionCode), ctx.getString(R.string.notif_update_body), c.packageName) }
        }.onFailure { Log.w(TAG, "update-prompt pass failed: ${it.message?.take(80)}") }
        // (b) Signed vs store divergence and store review waves, for followed apps only (bounded RPC use).
        runCatching {
            val store = StoreReviewsClient()
            val cachedFacts = HashMap<String, SignerFacts>()
            val f = { w: String -> cachedFacts.getOrPut(w) { UserPreferences.cachedFacts(w) ?: SignerFacts(false, 0.0) } }
            val scores = Ranking.score(after, facts = f).associateBy { it.packageName }
            for (pkg in follows) {
                val sc = scores[pkg]; val sr = store.rating(pkg)
                Log.i(TAG, "check $pkg: signed=${sc?.countedReviews ?: 0} store=${sr?.total ?: 0}")
                if (sc != null && sr != null && sc.countedReviews >= 3 && sr.total >= 3 && kotlin.math.abs(sc.weightedMean - sr.average) >= 1.5 && UserPreferences.markDivergence(pkg, sc.weightedMean, sr.average))
                    post("div:$pkg", ctx.getString(R.string.notif_divergence_title, names[pkg] ?: pkg), ctx.getString(R.string.notif_divergence_body, String.format(java.util.Locale.US, "%.1f", sc.weightedMean), sc.countedReviews, String.format(java.util.Locale.US, "%.1f", sr.average), sr.total), pkg)
                // Review bursts (see protocol ReviewBursts), from the listed reviews and from this device's snapshots of the
                // star counts. This replaced the 24-hour "wave" rule (5+ reviews, 3+ unattested), which fired on any busy
                // day and could not see a campaign by attested wallets — CrushCoin's was 67 of 67 attested (27 Sep 2026).
                sr?.let { UserPreferences.recordRatingSnapshot(pkg, it.byStar) }
                val read = store.reviewsChecked(pkg, maxPages = 3, pauseMs = 1000)
                val pts = read.items.mapNotNull { r -> runCatching { Instant.parse(r.createdAt).epochSecond }.getOrNull()?.let { com.undersigned.protocol.ReviewBursts.Point(it, r.rating, r.text) } }
                val fresh = System.currentTimeMillis() / 1000 - 3 * com.undersigned.protocol.ReviewBursts.DAY
                val listed = com.undersigned.protocol.ReviewBursts.find(pts).bursts.filter { it.end >= fresh }.map { it to false }
                val snaps = (com.undersigned.app.notify.StoreWatchWorker.archive(ctx).snapshots(pkg) + UserPreferences.ratingSnapshots(pkg)).sortedBy { it.time }
                val rated = com.undersigned.protocol.ReviewBursts.find(com.undersigned.protocol.RatingSnapshots.toPoints(snaps), com.undersigned.protocol.ReviewBursts.MIN_SNAPSHOT_DAYS).bursts.filter { it.end >= fresh }.map { it to true }
                Log.i(TAG, "  $pkg recent bursts: listed ${listed.size}, ratings ${rated.size}")
                for ((b, fromRatings) in listed + rated) {
                    val key = "burst:$pkg:${b.kind}:${if (fromRatings) "r" else "l"}:${b.start / com.undersigned.protocol.ReviewBursts.DAY}"
                    if (!UserPreferences.markBurst(key)) continue
                    val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
                    val pace = String.format(java.util.Locale.getDefault(), "%.1f", b.expected)
                    val res = when { fromRatings && b.kind == com.undersigned.protocol.ReviewBursts.Kind.HIGH -> R.string.burst_high_ratings; fromRatings -> R.string.burst_low_ratings
                        b.kind == com.undersigned.protocol.ReviewBursts.Kind.HIGH -> R.string.burst_high; else -> R.string.burst_low }
                    post(key, ctx.getString(R.string.notif_burst_title, names[pkg] ?: pkg), ctx.getString(res, b.count, df.format(java.util.Date(b.start * 1000)), df.format(java.util.Date(b.end * 1000)), pace), pkg)
                }
                kotlinx.coroutines.delay(1000)
            }
        }.onFailure { Log.w(TAG, "divergence/wave pass failed: ${it.message?.take(80)}") }
        val lastSeen = UserPreferences.lastNotifiedSignature
        // First run: mark the current head as seen and notify nothing (avoids a flood on enable).
        if (lastSeen == null) { Log.i(TAG, "first run: marking the head, no per-record alerts"); UserPreferences.lastNotifiedSignature = after.firstOrNull()?.signature; return Result.success() }
        // Everything newer than the last record we notified about. The old rule skipped any record the foreground app had
        // already pulled into the shared cache, so an open app silenced the alert for a reply it had just synced.
        val idx = after.indexOfFirst { it.signature == lastSeen }
        val fresh = if (idx < 0) after.filter { it.signature !in before.map { b -> b.signature }.toSet() } else after.take(idx)
        Log.i(TAG, "new records since last run: ${fresh.size}")
        if (fresh.isEmpty()) return Result.success()

        val mineBySig = after.filter { it.signer == me && it.memo is ReviewMemo.Review }.associateBy { it.signature }
        for (e in fresh) {
            when (val m = e.memo) {
                is ReviewMemo.Review -> if (m.packageName in follows && e.signer != me)
                    post("rev:${e.signature}", ctx.getString(R.string.notif_new_review_title, names[m.packageName] ?: m.packageName), ctx.getString(R.string.notif_new_review_body, m.rating, m.tags.joinToString(", ") { it.wire.replace('-', ' ').replaceFirstChar { c -> c.uppercase() } }), m.packageName)
                is ReviewMemo.Reply -> mineBySig[m.reviewSignature]?.let { mine ->
                    if (e.signer != me) { val pkg = (mine.memo as ReviewMemo.Review).packageName; post("rep:${e.signature}", ctx.getString(R.string.notif_reply_title, names[pkg] ?: pkg), m.text, pkg) }
                }
                else -> {}
            }
        }
        UserPreferences.lastNotifiedSignature = after.firstOrNull()?.signature
        // A term bought or run out since the last schedule changes the cadence; re-evaluate without a run-now.
        if (UserPreferences.alertsEnabled.value) runCatching { schedule(ctx, runNow = false) }
        Log.i(TAG, "posted $posted notifications")
        return Result.success()
    }

    /**
     * Posts one alert. [key] identifies the THING the alert is about ("rep:<signature>", "upd:<package>:<version>"),
     * and the notification id is its hash, so alerts from different runs sit side by side and the same record
     * never stacks a duplicate. Returns false when the system refused it (permission revoked while running).
     */

    companion object {
        private const val TAG = "LedgerWatch"

        /**
         * The notification id for an alert key. Content-derived so alerts from different runs coexist and the
         * same record never posts twice; see the note in [runOnce] for the regression this replaced.
         */
        fun alertId(key: String): Int = key.hashCode()

        /** Posts one alert. [extraKey]/[extraValue] say where a tap lands: "pkg" → an app page, "screen" → a screen. */
        fun notify(ctx: Context, key: String, title: String, body: String, extraValue: String, extraKey: String = EXTRA_PKG): Boolean {
            val id = alertId(key)
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT))
            val intent = Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP; putExtra(extraKey, extraValue) }
            val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notif).setColor(ctx.getColor(R.color.purple)).setContentTitle(title).setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true).build()
            return try { nm.notify(id, n); Log.i(TAG, "alert $key"); true } catch (_: SecurityException) { false }
        }

        const val CHANNEL = "ledger"
        const val EXTRA_PKG = "pkg"
        private const val PERIODIC = "ledger-watch"
        private val running = java.util.concurrent.atomic.AtomicBoolean(false)

        /** Hours between runs: one on a wallet holding Pro (the hourly certificate watch), six otherwise. Read from the ledger cache, never from a flag. */
        fun intervalHours(ctx: Context): Long {
            val cache = com.undersigned.app.data.LedgerCache(ctx)
            val pro = runCatching { com.undersigned.app.solana.ProLicence.of(cache.load(), cache.loadMeta(), UserPreferences.walletAddress.value, UserPreferences.granters.value) }.getOrNull()
            return if (pro?.active == true) 1L else 6L
        }

        fun schedule(ctx: Context, runNow: Boolean = true) {
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val hours = intervalHours(ctx)
            // KEEP, not UPDATE: an existing schedule should survive the toggle rather than be replaced and
            // re-fire immediately alongside the run-now request below. UPDATE only when the interval changed
            // (Pro bought or run out), which keeps the existing period's timing and swaps the request.
            val policy = if (UserPreferences.watchIntervalHours != hours) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP
            UserPreferences.watchIntervalHours = hours
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, policy,
                PeriodicWorkRequestBuilder<LedgerWatchWorker>(hours, TimeUnit.HOURS).setConstraints(c).build())
            if (runNow) WorkManager.getInstance(ctx).enqueueUniqueWork("$PERIODIC-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<LedgerWatchWorker>().setConstraints(c).build())
        }
        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC) }
    }
}
