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

    /** Total foreground milliseconds over the last two years (Android keeps yearly totals for about that long), or null without access. */
    fun foregroundMs(ctx: Context, pkg: String, now: Long = System.currentTimeMillis()): Long? {
        if (!hasAccess(ctx)) return null
        return runCatching {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            usm.queryAndAggregateUsageStats(now - 730L * 86_400_000L, now)[pkg]?.totalTimeInForeground ?: 0L
        }.getOrNull()
    }
}
