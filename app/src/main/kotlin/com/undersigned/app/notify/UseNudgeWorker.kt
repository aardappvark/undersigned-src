package com.undersigned.app.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.undersigned.app.R
import com.undersigned.app.data.CatalogueRepository
import com.undersigned.app.data.LedgerCache
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.device.InstalledApps
import com.undersigned.app.device.UsageTime
import com.undersigned.protocol.ReviewMemo
import com.undersigned.protocol.UseBucket
import com.undersigned.protocol.UseTime
import java.util.concurrent.TimeUnit

/**
 * Review nudges (opt-in, off by default; owner's feature list 8 Oct 2026): once a day at most, ONE app from the store
 * that has been on this phone a week or used an hour (the "Not reviewed on this phone" rule) and has no review from
 * this wallet gets a notification that opens its composer. Each app is asked about once, ever. Most used first. No
 * streaks, no counts, nothing to collect: it names an app the owner can actually speak to.
 */
class UseNudgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!UserPreferences.reviewNudge) return Result.success()
        val pick = runCatching { next(ctx) }.getOrNull() ?: return Result.success()
        val (pkg, name) = pick
        if (LedgerWatchWorker.notify(ctx, NotifChannel.REMINDERS, "nudge:$pkg", ctx.getString(R.string.nudge_title, name), ctx.getString(R.string.nudge_body), pkg, extraKey = "review"))
            UserPreferences.nudged = UserPreferences.nudged + pkg
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "review-nudge"

        /** The next app to ask about: in the store, here a week or used an hour, unreviewed by this wallet, never nudged. Most used first. */
        fun next(ctx: Context): Pair<String, String>? {
            val me = UserPreferences.walletAddress.value ?: return null
            val reviewed = LedgerCache(ctx).load().filter { it.signer == me && it.memo is ReviewMemo.Review }.map { (it.memo as ReviewMemo.Review).packageName }.toSet()
            val names = CatalogueRepository(ctx).load().associate { it.packageName to it.name }
            val done = UserPreferences.nudged
            val apps = InstalledApps(ctx); val now = System.currentTimeMillis()
            return apps.installedPackages().mapNotNull { pkg ->
                if (pkg !in names || pkg in reviewed || pkg in done || pkg == ctx.packageName || apps.firstInstallTime(pkg) == null) return@mapNotNull null
                val age = apps.presence(pkg, now)?.first?.let { UseBucket.of(now - it, now) }
                val time = UsageTime.foregroundMs(ctx, pkg)?.let { UseTime.of(it) }
                if ((age != null && age.minDays >= 7) || (time != null && time.minMinutes >= 60)) Triple(pkg, names.getValue(pkg), time?.minMinutes ?: -1) else null
            }.maxByOrNull { it.third }?.let { it.first to it.second }
        }

        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UseNudgeWorker>(1, TimeUnit.DAYS).setInitialDelay(6, TimeUnit.HOURS).build())
        }
        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE) }
    }
}
