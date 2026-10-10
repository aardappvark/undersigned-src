package com.undersigned.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.undersigned.app.R

/**
 * Android notification channels, one per group of alerts (1.1.6), so the system's own settings can mute a whole group.
 * The in-app per-kind switches ([NotifKind]) still decide whether an alert is posted at all; a channel only decides how
 * (or whether) the system shows it. Ids are stable: renaming one would orphan the user's choices for it.
 */
enum class NotifChannel(val id: String, val label: Int, val description: Int) {
    REVIEWS("reviews", R.string.notif_ch_reviews, R.string.notif_ch_reviews_desc),
    PUBLISHER("publisher", R.string.notif_ch_publisher, R.string.notif_ch_publisher_desc),
    STORE("store_watch", R.string.notif_ch_store, R.string.notif_ch_store_desc),
    REMINDERS("reminders", R.string.notif_ch_reminders, R.string.notif_ch_reminders_desc);

    companion object {
        /** The single channel every alert used up to 1.1.5. Removed so the system list shows only the four groups. */
        private const val LEGACY = "ledger"

        /** Creates (or renames, after a locale change) all four channels. Idempotent; cheap enough to call before each post. */
        fun ensure(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannels(entries.map { c ->
                NotificationChannel(c.id, ctx.getString(c.label), NotificationManager.IMPORTANCE_DEFAULT).apply { description = ctx.getString(c.description) }
            })
            if (nm.getNotificationChannel(LEGACY) != null) nm.deleteNotificationChannel(LEGACY)
        }
    }
}
