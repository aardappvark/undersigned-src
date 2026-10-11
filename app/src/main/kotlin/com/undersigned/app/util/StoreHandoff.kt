package com.undersigned.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.undersigned.app.AppConfig

/**
 * The one way this app hands a review to the dApp Store: copy the note and open the listing. Where to paste is shown
 * BEFORE this runs (StoreHandoffDialog, or the copy-all dialog in Settings): the store has no deep link to its review
 * composer — its manifest registers only `details` and `welcome_pack` (v1.59.0, checked 25 Sep 2026) — and a toast
 * over the store is half-hidden by Android's clipboard preview.
 */
object StoreHandoff {
    fun copyAndOpen(context: Context, pkg: String, text: String?): Boolean = try {
        if (!text.isNullOrBlank()) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("review", text.trim()))
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.Store.DEEP_LINK + pkg)))
        com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.STORE_HANDOFF)
        true
    } catch (e: Exception) {
        Log.w("StoreHandoff", "could not open the store listing for $pkg: ${e.message?.take(80)}")
        false
    }
}
