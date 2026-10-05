package com.undersigned.app.notify

import android.content.Context
import androidx.work.Constraints
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
 * The weekly reminder (opt-in, off by default): at most one notification a week saying how many apps on this phone the
 * wallet has not reviewed, by the same rule as the "Not reviewed on this phone" list. No counts of reviews written, no
 * streaks, nothing to collect: it names a fact and opens the list.
 */
class ReviewReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!UserPreferences.reviewReminder) return Result.success()
        val n = runCatching { count(ctx) }.getOrDefault(0)
        if (n > 0) LedgerWatchWorker.notify(ctx, "remind:${System.currentTimeMillis() / (7L * 86_400_000)}", ctx.getString(R.string.remind_title),
            ctx.resources.getQuantityString(R.plurals.remind_body, n, n), "to-review", extraKey = "screen")
        return Result.success()
    }

    companion object {
        private const val UNIQUE = "review-reminder"

        /** Apps installed here a week or more, or used an hour or more, that this wallet has not reviewed (the list's rule). */
        fun count(ctx: Context): Int {
            val me = UserPreferences.walletAddress.value ?: return 0
            val reviewed = LedgerCache(ctx).load().filter { it.signer == me && it.memo is ReviewMemo.Review }.map { (it.memo as ReviewMemo.Review).packageName }.toSet()
            val inStore = CatalogueRepository(ctx).load().map { it.packageName }.toSet()
            val apps = InstalledApps(ctx); val now = System.currentTimeMillis()
            return apps.installedPackages().count { pkg ->
                if (pkg !in inStore || pkg in reviewed || pkg == ctx.packageName || apps.firstInstallTime(pkg) == null) return@count false
                val age = apps.presence(pkg, now)?.first?.let { UseBucket.of(now - it, now) }
                val time = UsageTime.foregroundMs(ctx, pkg)?.let { UseTime.of(it) }
                (age != null && age.minDays >= 7) || (time != null && time.minMinutes >= 60)
            }
        }

        fun schedule(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReviewReminderWorker>(7, TimeUnit.DAYS).setInitialDelay(1, TimeUnit.DAYS).setConstraints(Constraints.Builder().build()).build())
        }
        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE) }
    }
}
