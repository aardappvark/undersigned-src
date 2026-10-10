package com.undersigned.app.notify

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.undersigned.app.R
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.data.WeeklyCatalogue
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Pro: runs [WeeklyCatalogue] every Monday from 01:00 plus this phone's offset (ported from the Toolkit's
 * WeeklyCatalogueWorker). A chain of one-time jobs, not a 7-day periodic one: periodic work drifts, so "Monday 01:00" would
 * become "Tuesday afternoon" within weeks. A running job books the FOLLOWING week, never its own. If the phone was off,
 * [ensureScheduled] (every app start) queues a catch-up once this phone's start time has passed.
 */
class CatalogueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext
        if (!allowed()) return Result.success()
        val mode = if (tags.contains(TAG_CATCHUP)) WeeklyCatalogue.Mode.CATCH_UP else WeeklyCatalogue.Mode.SCHEDULED
        val bookedFor = inputData.getString(KEY_WEEK)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (mode == WeeklyCatalogue.Mode.SCHEDULED) scheduleNext(app, notBefore = WeeklyCatalogue.followingWeek(bookedFor, LocalDate.now()))
        return when (val o = WeeklyCatalogue.run(app, mode)) {
            is WeeklyCatalogue.Outcome.Ok -> { notifyNew(app, o.added.map { it.packageName to it.name }); Result.success() }
            is WeeklyCatalogue.Outcome.Skipped -> Result.success()
            is WeeklyCatalogue.Outcome.Failed -> if (o.retryable && runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "WeeklyCatalogue"
        private const val TAG_CATCHUP = "catalogue_catchup"
        private const val KEY_WEEK = "week"
        private val ALL_TAG: String = CatalogueWorker::class.java.name

        /** The Settings switch is on (default on) AND Pro is active: the weekly update is a Pro perk. */
        fun allowed(): Boolean = UserPreferences.catalogueAuto && UserPreferences.proActiveCached

        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun scheduleNext(ctx: Context, notBefore: LocalDate? = null) {
            val zone = ZoneId.systemDefault(); val now = ZonedDateTime.now(zone)
            val next = WeeklyCatalogue.nextStart(now.toLocalDateTime(), WeeklyCatalogue.offsetMinutes(ctx), notBefore)
            val req = OneTimeWorkRequestBuilder<CatalogueWorker>()
                .setInputData(workDataOf(KEY_WEEK to WeeklyCatalogue.weekBatch(next.toLocalDate()).toString()))
                .setInitialDelay(Duration.between(now, next.atZone(zone)).toMillis().coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setConstraints(constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx).enqueueUniqueWork("catalogue_week_${next.toLocalDate()}", ExistingWorkPolicy.KEEP, req)
        }

        fun cancelAll(ctx: Context) {
            WorkManager.getInstance(ctx).cancelAllWorkByTag(ALL_TAG)
            WorkManager.getInstance(ctx).cancelUniqueWork("catalogue-weekly")   // the 6–7 Oct periodic job, if booked
        }

        /** Every app start and Settings change: book next Monday, and catch up if this week's check hasn't run. */
        fun ensureScheduled(ctx: Context) {
            try {
                WorkManager.getInstance(ctx).cancelUniqueWork("catalogue-weekly")   // the 6–7 Oct periodic job: replaced by this chain
                if (!allowed()) { cancelAll(ctx); return }
                scheduleNext(ctx)
                if (!WeeklyCatalogue.doneThisWeek(ctx) && !WeeklyCatalogue.gaveUpThisWeek(ctx) &&
                    WeeklyCatalogue.catchUpDue(java.time.LocalDateTime.now(), WeeklyCatalogue.offsetMinutes(ctx))) {
                    val req = OneTimeWorkRequestBuilder<CatalogueWorker>().setConstraints(constraints())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 60, TimeUnit.MINUTES).addTag(TAG_CATCHUP).build()
                    WorkManager.getInstance(ctx).enqueueUniqueWork("catalogue_catchup_${WeeklyCatalogue.weekBatch(LocalDate.now())}", ExistingWorkPolicy.KEEP, req)
                }
            } catch (e: Exception) { Log.w(TAG, "could not schedule the weekly check", e) }
        }

        /** "3 new dApps this week: A, B, C" — one opens its page, several open Browse's New list. */
        fun notifyNew(ctx: Context, added: List<Pair<String, String>>) {
            if (added.isEmpty()) return
            val names = added.take(4).joinToString(", ") { it.second } + if (added.size > 4) "…" else ""
            val title = ctx.resources.getQuantityString(R.plurals.catalogue_notif_title, added.size, added.size)
            if (added.size == 1) LedgerWatchWorker.notify(ctx, NotifChannel.STORE, "catalogue:${LocalDate.now()}", title, names, added[0].first)
            else LedgerWatchWorker.notify(ctx, NotifChannel.STORE, "catalogue:${LocalDate.now()}", title, names, "new", extraKey = "screen")
        }
    }
}
