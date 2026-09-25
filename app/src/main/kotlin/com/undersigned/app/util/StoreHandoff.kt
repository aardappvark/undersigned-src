package com.undersigned.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.undersigned.app.AppConfig
import com.undersigned.app.R

/**
 * The one way this app hands a review to the dApp Store: copy the note, open the listing, and say where to paste.
 *
 * The store has no deep link to its review composer — its manifest registers only `details` and `welcome_pack`
 * (store v1.59.0, checked 25 Sep 2026) — and "Review this dApp" sits under "Ratings & Reviews", three scrolls down
 * the listing. The guidance used to appear only inside this app, which the user leaves the moment the store opens,
 * so it was never on screen when it was needed (seen on a Seeker, 26 Sep 2026). A toast survives the switch and
 * sits over the store for a few seconds. It is shown only AFTER the store opened, so a failure never points the user
 * at a screen that is not there.
 */
object StoreHandoff {
    fun copyAndOpen(context: Context, pkg: String, text: String?): Boolean = try {
        if (!text.isNullOrBlank()) {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("review", text.trim()))
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.Store.DEEP_LINK + pkg)))
        Toast.makeText(context, R.string.pro_copy_toast, Toast.LENGTH_LONG).show()
        true
    } catch (e: Exception) {
        Log.w("StoreHandoff", "could not open the store listing for $pkg: ${e.message?.take(80)}")
        false
    }
}
