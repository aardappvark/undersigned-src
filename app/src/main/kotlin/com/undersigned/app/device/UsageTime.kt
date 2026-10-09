package com.undersigned.app.device

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Foreground time Android recorded for an app, through the usage-access permission the user switches on in system
 * settings (it is not a runtime pop-up). Read on request only, for one app at a time, when the reviewer chooses to add
 * it to a review; nothing is collected in the background and nothing leaves the phone except the rounded bucket the
 * reviewer signs.
 */
object UsageTime {
    fun hasAccess(ctx: Context): Boolean = runCatching {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
            else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun settingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * In how many of the last six calendar months Android recorded at least a minute in front, and when the app was last
     * in front (ms, 0 when never). Monthly totals are what Android keeps for about six months; both read on request only,
     * like [foregroundMs]. Null without usage access.
     */
    fun activity(ctx: Context, pkg: String, now: Long = System.currentTimeMillis()): Pair<Int, Long>? {
        if (!hasAccess(ctx)) return null
        return runCatching {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val zone = java.time.ZoneId.systemDefault()
            val months = usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, now - 183L * 86_400_000L, now)
                .filter { it.packageName == pkg && it.totalTimeInForeground >= 60_000L }
                .map { java.time.Instant.ofEpochMilli(maxOf(it.firstTimeStamp, it.lastTimeUsed.coerceAtLeast(it.firstTimeStamp))).atZone(zone).let { d -> d.year * 12 + d.monthValue } }
                .toSet().size.coerceAtMost(com.undersigned.protocol.Usage.ACTIVE_MONTHS_WINDOW)
            val last = usm.queryAndAggregateUsageStats(now - 730L * 86_400_000L, now)[pkg]?.lastTimeUsed ?: 0L
            months to last
        }.getOrNull()
    }

    /** Total foreground milliseconds over the last two years (Android keeps yearly totals for about that long), or null without access. */
    fun foregroundMs(ctx: Context, pkg: String, now: Long = System.currentTimeMillis()): Long? {
        if (!hasAccess(ctx)) return null
        return runCatching {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            usm.queryAndAggregateUsageStats(now - 730L * 86_400_000L, now)[pkg]?.totalTimeInForeground ?: 0L
        }.getOrNull()
    }
}
