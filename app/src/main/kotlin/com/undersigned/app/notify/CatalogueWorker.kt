package com.undersigned.app.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.undersigned.app.data.CatalogueRepository
import com.undersigned.app.data.UserPreferences
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Pro: the whole dApp Store list, downloaded in the background every Monday morning (owner, 7 Oct 2026). Everyone else
 * gets the same refresh from the Update button, which Browse offers once the list is older than this week's Monday.
 */
class CatalogueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!UserPreferences.catalogueAuto) return Result.success()
        return if (CatalogueRepository(applicationContext).refresh() != null) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE = "catalogue-weekly"
        /** Monday 07:00 on this phone's clock. */
        private const val HOUR = 7

        /** The most recent Monday 07:00 at or before now: a list refreshed before it is "last week's". */
        fun weekStart(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
            val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).withHour(HOUR).withMinute(0).withSecond(0).withNano(0)
            return if (monday.isAfter(now)) monday.minusWeeks(1) else monday
        }

        fun isLastWeeks(refreshedAt: Long, now: ZonedDateTime = ZonedDateTime.now()): Boolean = refreshedAt < weekStart(now).toInstant().toEpochMilli()

        fun schedule(ctx: Context) {
            val now = ZonedDateTime.now()
            val next = weekStart(now).plusWeeks(1)
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val req = PeriodicWorkRequestBuilder<CatalogueWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(java.time.Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS).setConstraints(c).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(UNIQUE, ExistingPeriodicWorkPolicy.KEEP, req)
        }
        fun cancel(ctx: Context) { WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE) }
    }
}
