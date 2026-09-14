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
import com.undersigned.app.solana.SignerFactsService
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
        var id = 1000
        val follows = UserPreferences.follows.value
        val me = UserPreferences.walletAddress.value
        Log.i(TAG, "following ${follows.size}, wallet ${if (me == null) "none" else "connected"}")
        // (a) Installed apps that updated: ask followers / previous reviewers for a fresh signed review.
        runCatching {
            val reviewedByMe = after.filter { it.signer == me && it.memo is ReviewMemo.Review }.map { (it.memo as ReviewMemo.Review).packageName }.toSet()
            val updated = InstalledApps(ctx).refreshSnapshot(names.keys).filter { it.versionChanged && (it.packageName in follows || it.packageName in reviewedByMe) }
            Log.i(TAG, "update prompts: ${updated.size}")
            updated.forEach { c -> notify(ctx, id++, ctx.getString(R.string.notif_update_title, names[c.packageName] ?: c.packageName, c.versionCode), ctx.getString(R.string.notif_update_body), c.packageName) }
        }.onFailure { Log.w(TAG, "update-prompt pass failed: ${it.message?.take(80)}") }
        // (b) Signed vs store divergence and store review waves, for followed apps only (bounded RPC use).
        runCatching {
            val factsSvc = SignerFactsService(); val store = StoreReviewsClient()
            val cachedFacts = HashMap<String, SignerFacts>()
            val f = { w: String -> cachedFacts.getOrPut(w) { UserPreferences.cachedFacts(w) ?: SignerFacts(false, 0.0) } }
            val scores = Ranking.score(after, facts = f).associateBy { it.packageName }
            for (pkg in follows) {
                val sc = scores[pkg]; val sr = store.rating(pkg)
                Log.i(TAG, "check $pkg: signed=${sc?.countedReviews ?: 0} store=${sr?.total ?: 0}")
                if (sc != null && sr != null && sc.countedReviews >= 3 && sr.total >= 3 && kotlin.math.abs(sc.weightedMean - sr.average) >= 1.5 && UserPreferences.markDivergence(pkg, sc.weightedMean, sr.average))
                    notify(ctx, id++, ctx.getString(R.string.notif_divergence_title, names[pkg] ?: pkg), ctx.getString(R.string.notif_divergence_body, String.format(java.util.Locale.US, "%.1f", sc.weightedMean), sc.countedReviews, String.format(java.util.Locale.US, "%.1f", sr.average), sr.total), pkg)
                val recent = store.reviews(pkg, pageSize = 10, maxPages = 1).filter { r -> runCatching { Instant.parse(r.createdAt).toEpochMilli() }.getOrDefault(0L) > System.currentTimeMillis() - 24L * 3600L * 1000L }
                Log.i(TAG, "  $pkg store reviews in 24h: ${recent.size}")
                if (recent.size >= 5) {
                    val noGenesis = recent.count { r -> r.walletAddress?.let { w -> !(runCatching { factsSvc.facts(w) }.getOrNull()?.hasGenesisToken ?: true) } ?: false }
                    Log.i(TAG, "  $pkg of those, no attested device: $noGenesis")
                    if (noGenesis >= 3 && UserPreferences.markWave(pkg, recent.first().id)) notify(ctx, id++, ctx.getString(R.string.notif_wave_title, names[pkg] ?: pkg), ctx.getString(R.string.notif_wave_body, recent.size, noGenesis), pkg)
                }
            }
        }.onFailure { Log.w(TAG, "divergence/wave pass failed: ${it.message?.take(80)}") }
        val lastSeen = UserPreferences.lastNotifiedSignature
        // First run: mark the current head as seen and notify nothing (avoids a flood on enable).
        if (lastSeen == null) { Log.i(TAG, "first run: marking the head, no per-record alerts"); UserPreferences.lastNotifiedSignature = after.firstOrNull()?.signature; return Result.success() }
        val seen = before.map { it.signature }.toSet() + lastSeen
        val fresh = after.filter { it.signature !in seen }
        Log.i(TAG, "new records since last run: ${fresh.size}")
        if (fresh.isEmpty()) return Result.success()

        val mineBySig = after.filter { it.signer == me && it.memo is ReviewMemo.Review }.associateBy { it.signature }
        for (e in fresh) {
            when (val m = e.memo) {
                is ReviewMemo.Review -> if (m.packageName in follows && e.signer != me)
                    notify(ctx, id++, ctx.getString(R.string.notif_new_review_title, names[m.packageName] ?: m.packageName), ctx.getString(R.string.notif_new_review_body, m.rating, m.tags.joinToString(", ") { it.wire.replace('-', ' ').replaceFirstChar { c -> c.uppercase() } }), m.packageName)
                is ReviewMemo.Reply -> mineBySig[m.reviewSignature]?.let { mine ->
                    if (e.signer != me) { val pkg = (mine.memo as ReviewMemo.Review).packageName; notify(ctx, id++, ctx.getString(R.string.notif_reply_title, names[pkg] ?: pkg), m.text, pkg) }
                }
                else -> {}
            }
        }
        UserPreferences.lastNotifiedSignature = after.firstOrNull()?.signature
        Log.i(TAG, "posted ${id - 1000} notifications")
        return Result.success()
    }

    private fun notify(ctx: Context, id: Int, title: String, body: String, pkg: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP; putExtra(EXTRA_PKG, pkg) }
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notif).setColor(ctx.getColor(R.color.purple)).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true).build()
        try { nm.notify(id, n) } catch (_: SecurityException) { }
    }

    companion object {
        private const val TAG = "LedgerWatch"
        const val CHANNEL = "ledger"
        const val EXTRA_PKG = "pkg"
        private const val PERIODIC = "ledger-watch"
        private val running = java.util.concurrent.atomic.AtomicBoolean(false)

        fun schedule(ctx: Context) {
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            // KEEP, not UPDATE: an existing schedule should survive the toggle rather than be replaced and
            // re-fire immediately alongside the run-now request below.
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LedgerWatchWorker>(6, TimeUnit.HOURS).setConstraints(c).build())
            WorkManager.getInstance(ctx).enqueueUniqueWork("$PERIODIC-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<LedgerWatchWorker>().setConstraints(c).build())
        }
        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC) }
    }
}
