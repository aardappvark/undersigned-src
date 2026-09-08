package com.undersigned.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
        val ctx = applicationContext
        try { UserPreferences.init(ctx) } catch (_: Exception) { }
        if (!UserPreferences.alertsEnabled.value) return Result.success()
        val cache = LedgerCache(ctx)
        val before = cache.load()
        val after = LedgerReader(cache = cache).sync().getOrElse { return Result.retry() }
        val names = runCatching { CatalogueRepository(ctx).load().associate { it.packageName to it.name } }.getOrDefault(emptyMap())
        var id = 1000
        val follows = UserPreferences.follows.value
        val me = UserPreferences.walletAddress.value
        // (a) Installed apps that updated: ask followers / previous reviewers for a fresh signed review.
        runCatching {
            val reviewedByMe = after.filter { it.signer == me && it.memo is ReviewMemo.Review }.map { (it.memo as ReviewMemo.Review).packageName }.toSet()
            InstalledApps(ctx).refreshSnapshot(names.keys).filter { it.versionChanged && (it.packageName in follows || it.packageName in reviewedByMe) }
                .forEach { c -> notify(ctx, id++, ctx.getString(R.string.notif_update_title, names[c.packageName] ?: c.packageName, c.versionCode), ctx.getString(R.string.notif_update_body), c.packageName) }
        }
        // (b) Signed vs store divergence and store review waves, for followed apps only (bounded RPC use).
        runCatching {
            val factsSvc = SignerFactsService(); val store = StoreReviewsClient()
            val cachedFacts = HashMap<String, SignerFacts>()
            val f = { w: String -> cachedFacts.getOrPut(w) { UserPreferences.cachedFacts(w) ?: SignerFacts(false, 0.0) } }
            val scores = Ranking.score(after, f).associateBy { it.packageName }
            for (pkg in follows) {
                val sc = scores[pkg]; val sr = store.rating(pkg)
                if (sc != null && sr != null && sc.countedReviews >= 3 && sr.total >= 3 && kotlin.math.abs(sc.weightedMean - sr.average) >= 1.5 && UserPreferences.markDivergence(pkg, sc.weightedMean, sr.average))
                    notify(ctx, id++, ctx.getString(R.string.notif_divergence_title, names[pkg] ?: pkg), ctx.getString(R.string.notif_divergence_body, String.format(java.util.Locale.US, "%.1f", sc.weightedMean), sc.countedReviews, String.format(java.util.Locale.US, "%.1f", sr.average), sr.total), pkg)
                val recent = store.reviews(pkg, pageSize = 10, maxPages = 1).filter { r -> runCatching { Instant.parse(r.createdAt).toEpochMilli() }.getOrDefault(0L) > System.currentTimeMillis() - 24L * 3600L * 1000L }
                if (recent.size >= 5) {
                    val noGenesis = recent.count { r -> r.walletAddress?.let { w -> !(runCatching { factsSvc.facts(w) }.getOrNull()?.hasGenesisToken ?: true) } ?: false }
                    if (noGenesis >= 3 && UserPreferences.markWave(pkg, recent.first().id)) notify(ctx, id++, ctx.getString(R.string.notif_wave_title, names[pkg] ?: pkg), ctx.getString(R.string.notif_wave_body, recent.size, noGenesis), pkg)
                }
            }
        }
        val lastSeen = UserPreferences.lastNotifiedSignature
        // First run: mark the current head as seen and notify nothing (avoids a flood on enable).
        if (lastSeen == null) { UserPreferences.lastNotifiedSignature = after.firstOrNull()?.signature; return Result.success() }
        val seen = before.map { it.signature }.toSet() + lastSeen
        val fresh = after.filter { it.signature !in seen }
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
        return Result.success()
    }

    private fun notify(ctx: Context, id: Int, title: String, body: String, pkg: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP; putExtra(EXTRA_PKG, pkg) }
        val pi = PendingIntent.getActivity(ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_notif).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true).build()
        try { nm.notify(id, n) } catch (_: SecurityException) { }
    }

    companion object {
        const val CHANNEL = "ledger"
        const val EXTRA_PKG = "pkg"
        private const val PERIODIC = "ledger-watch"
        fun schedule(ctx: Context) {
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LedgerWatchWorker>(6, TimeUnit.HOURS).setConstraints(c).build())
            WorkManager.getInstance(ctx).enqueueUniqueWork("$PERIODIC-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<LedgerWatchWorker>().setConstraints(c).build())
        }
        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(PERIODIC) }
    }
}
