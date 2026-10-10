package com.undersigned.app.ui.components

import android.content.Context
import android.content.Intent
import com.undersigned.app.R
import com.undersigned.protocol.AppScore
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.ReviewMemo

/**
 * Sharing as plain text through the system sheet: facts and links only. An app's line names both scores with their
 * sources; a review's line quotes it and links its transaction, so anyone can check it was signed. Links are https on the
 * public site (judge, 10 Oct 2026: an `undersigned://` link is dead text in most apps): the app's page, and the page that
 * writes a signed review of it. The site hands a phone with Undersigned on to the app.
 */
object Share {
    fun app(ctx: Context, pkg: String, name: String, score: AppScore?, storeAverage: Double?, storeCount: Int?) {
        val lines = ArrayList<String>()
        lines += name
        lines += if (score != null && score.countedReviews > 0) ctx.getString(R.string.share_signed, String.format(java.util.Locale.US, "%.1f", score.weightedMean), score.countedReviews)
            else ctx.getString(R.string.share_no_signed)
        if (storeAverage != null && storeCount != null && storeCount > 0) lines += ctx.getString(R.string.share_store, String.format(java.util.Locale.US, "%.1f", storeAverage), storeCount)
        lines += appUrl(pkg)
        lines += ctx.getString(R.string.share_review_it, reviewUrl(pkg))
        send(ctx, lines.joinToString("\n"))
    }

    fun review(ctx: Context, name: String, e: LedgerEntry, hideText: Boolean = false) {
        val r = e.memo as? ReviewMemo.Review ?: return
        val tags = r.tags.joinToString(", ") { with(TagLabels) { ctx.label(it.wire) } }
        val body = listOfNotNull("$name · ${"★".repeat(r.rating)}${"☆".repeat(5 - r.rating)} · $tags", r.text.takeUnless { hideText }, ctx.getString(R.string.share_review_signed), "https://solscan.io/tx/${e.signature.substringBefore(':')}", appUrl(r.packageName))
        send(ctx, body.joinToString("\n"))
    }

    fun appUrl(pkg: String) = "${com.undersigned.app.AppConfig.Site.URL}/app/${android.net.Uri.encode(pkg)}"
    fun reviewUrl(pkg: String) = "${com.undersigned.app.AppConfig.Site.URL}/review?package=${android.net.Uri.encode(pkg)}"

    private fun send(ctx: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
