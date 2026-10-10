package com.undersigned.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.undersigned.app.R
import com.undersigned.app.ui.theme.RbColors

@Composable
fun GlassCard(modifier: Modifier = Modifier, accent: Color = RbColors.glassBorder, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RbColors.card)
            .border(1.dp, accent, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            // Pure white: the off-white text was 4.2:1 on this purple, under the 4.5:1 AA minimum; white is 4.52:1.
            containerColor = RbColors.purple, contentColor = Color.White,
            disabledContainerColor = RbColors.surfaceVariant, disabledContentColor = RbColors.textTertiary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = RbColors.textPrimary, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        // One line, always: these buttons have a fixed height, so a second line is clipped, never shown.
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = RbColors.purpleLight, disabledContentColor = RbColors.textTertiary),
    ) { Text(text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color = RbColors.textPrimary, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RbColors.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = RbColors.textTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
fun Pill(text: String, color: Color = RbColors.purpleLight, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = RbColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun RatingStars(rating: Double, modifier: Modifier = Modifier, size: Int = 14) {
    // Rounded, not floored: 4.9 drew four stars (UI review 6 Oct 2026). Read as one phrase, not five glyph names.
    val a11y = androidx.compose.ui.res.stringResource(R.string.stars_a11y, String.format(java.util.Locale.US, "%.1f", rating))
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = a11y }, verticalAlignment = Alignment.CenterVertically) {
        val full = kotlin.math.round(rating).toInt()
        repeat(5) { i ->
            Text(if (i < full) "★" else "☆", color = if (i < full) RbColors.warning else RbColors.textTertiary, fontSize = size.sp)
        }
    }
}

@Composable
fun TagChip(text: String, count: Int? = null, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val bg = if (selected) RbColors.purple.copy(alpha = 0.35f) else RbColors.surfaceVariant
    val fg = if (selected) RbColors.textPrimary else RbColors.textSecondary
    // A tappable chip keeps its compact look but gets a 48 dp touch target and says whether it is selected.
    val m = (if (onClick != null) Modifier.minimumInteractiveComponentSize() else Modifier).clip(RoundedCornerShape(50)).background(bg)
        .let { if (onClick != null) it.semantics { this.selected = selected }.clickable(role = Role.Button) { onClick() } else it }.padding(horizontal = 10.dp, vertical = 5.dp)
    Text(if (count != null) "$text · $count" else text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = m)
}

/** Display label for a protocol tag, in the reader's language. The wire value never changes. */
@Composable
fun com.undersigned.protocol.ReviewMemo.Tag.label(): String = LocalContext.current.tagLabel(wire)

/** Tag labels for code outside Compose (notifications): the same localised strings the chips show. */
object TagLabels { fun Context.label(wire: String): String = tagLabel(wire) }

/** String resource for a tag wire value, or null for a wire value this build doesn't know. */
fun tagLabelRes(wire: String): Int? = when (wire) {
    "works" -> R.string.tag_works
    "crashes" -> R.string.tag_crashes
    "wallet-ok" -> R.string.tag_wallet_ok
    "wallet-fails" -> R.string.tag_wallet_fails
    "fast" -> R.string.tag_fast
    "slow" -> R.string.tag_slow
    "battery" -> R.string.tag_battery
    "ads" -> R.string.tag_ads
    "paywall" -> R.string.tag_paywall
    "fair-price" -> R.string.tag_fair_price
    "overpriced" -> R.string.tag_overpriced
    "perms-ok" -> R.string.tag_perms_ok
    "perms-excess" -> R.string.tag_perms_excess
    "spam-reviews" -> R.string.tag_spam_reviews
    "misleading" -> R.string.tag_misleading
    "suspected-scam" -> R.string.tag_suspected_scam
    "abandoned" -> R.string.tag_abandoned
    "active-dev" -> R.string.tag_active_dev
    "onboarding-ok" -> R.string.tag_onboarding_ok
    "confusing" -> R.string.tag_confusing
    "offline-ok" -> R.string.tag_offline_ok
    "needs-account" -> R.string.tag_needs_account
    "seeker-native" -> R.string.tag_seeker_native
    "webview" -> R.string.tag_webview
    else -> null
}

/** Display label for a tag wire value. Wire values are on-chain vocabulary and never change; labels can.
 *  An unknown wire value (from a newer client) falls back to "wallet-fails" → "Wallet fails". */
fun Context.tagLabel(wire: String): String =
    tagLabelRes(wire)?.let(::getString) ?: wire.replace('-', ' ').replaceFirstChar { it.uppercase() }

/**
 * A dApp Store timestamp ("2026-02-13T09:41:00.000Z") as the phone's own medium date ("13 Feb 2026" / "Feb 13, 2026").
 * Store dates were shown raw ("2026-02-13") right beside ledger dates in the phone's format (seen 26 Sep 2026).
 * Falls back to the raw day if the store ever sends something unparseable.
 */
fun storeDate(iso: String): String = runCatching {
    // A full timestamp, or a plain day ("2026-09-04", the catalogue's "updated"), both in the phone's format.
    val df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
    val ms = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrElse {
        df.timeZone = java.util.TimeZone.getTimeZone("UTC")   // a calendar day has no time zone: never shift it by one
        java.time.LocalDate.parse(iso).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }
    df.format(java.util.Date(ms))
}.getOrElse { iso.take(10) }

/** The reader's words for an install-age bucket. */
@androidx.compose.runtime.Composable
fun useLabel(b: com.undersigned.protocol.UseBucket): String = androidx.compose.ui.res.stringResource(when (b) {
    com.undersigned.protocol.UseBucket.UNDER_DAY -> com.undersigned.app.R.string.use_d0
    com.undersigned.protocol.UseBucket.DAYS -> com.undersigned.app.R.string.use_d1
    com.undersigned.protocol.UseBucket.WEEKS -> com.undersigned.app.R.string.use_w1
    com.undersigned.protocol.UseBucket.MONTHS -> com.undersigned.app.R.string.use_m1
    com.undersigned.protocol.UseBucket.LONG -> com.undersigned.app.R.string.use_m3
})

/** The reader's words for a foreground-time bucket. */
@androidx.compose.runtime.Composable
fun timeLabel(t: com.undersigned.protocol.UseTime): String = androidx.compose.ui.res.stringResource(when (t) {
    com.undersigned.protocol.UseTime.UNDER_10M -> com.undersigned.app.R.string.time_t0
    com.undersigned.protocol.UseTime.UNDER_HOUR -> com.undersigned.app.R.string.time_t1
    com.undersigned.protocol.UseTime.HOURS -> com.undersigned.app.R.string.time_t2
    com.undersigned.protocol.UseTime.MANY_HOURS -> com.undersigned.app.R.string.time_t3
    com.undersigned.protocol.UseTime.DAYS_OF_USE -> com.undersigned.app.R.string.time_t4
})


/**
 * The review tags by theme (protocol TagGroups), with opposite pairs that release each other: choosing "Crashes" drops
 * "Works". Used by the composer, the batch composer and the importer, so every review is tagged the same way.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(selected: List<com.undersigned.protocol.ReviewMemo.Tag>, enabled: Boolean = true, collapsible: Boolean = false, onChange: (List<com.undersigned.protocol.ReviewMemo.Tag>) -> Unit) {
    val ctx = LocalContext.current
    // Collapsed (judge, 10 Oct 2026): the first six tags and any already chosen, then "More tags". Expanding never unchooses.
    var expanded by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(!collapsible) }
    val all = com.undersigned.protocol.TagGroups.groups.flatMap { it.second }
    val visible = if (expanded) all.toSet() else (all.take(COLLAPSED_TAGS) + selected).toSet()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.undersigned.protocol.TagGroups.groups.forEach { (g, groupTags) ->
            val tags = groupTags.filter { it in visible }
            if (tags.isNotEmpty()) Column {
                Text(ctx.getString(when (g) {
                    com.undersigned.protocol.TagGroups.Group.USE -> com.undersigned.app.R.string.tag_group_use
                    com.undersigned.protocol.TagGroups.Group.WALLET -> com.undersigned.app.R.string.tag_group_wallet
                    com.undersigned.protocol.TagGroups.Group.MONEY -> com.undersigned.app.R.string.tag_group_money
                    com.undersigned.protocol.TagGroups.Group.TRUST -> com.undersigned.app.R.string.tag_group_trust
                    com.undersigned.protocol.TagGroups.Group.BUILD -> com.undersigned.app.R.string.tag_group_build
                }), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { t -> TagChip(ctx.tagLabel(t.wire), selected = t in selected) { if (enabled) onChange(com.undersigned.protocol.TagGroups.toggle(selected, t)) } }
                }
            }
        }
        if (collapsible) {
            val hidden = all.size - visible.size
            if (!expanded && hidden > 0) TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(ctx.getString(R.string.tags_more, hidden), color = RbColors.purpleLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            else if (expanded) TextButton(onClick = { expanded = false }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                Text(ctx.getString(R.string.show_less), color = RbColors.purpleLight, fontSize = 13.sp) }
        }
    }
}

/** How many tags the collapsed picker shows before "More tags". */
private const val COLLAPSED_TAGS = 6

/** The back arrow every screen's header uses, with its screen-reader label (all 16 had none, UI review 6 Oct 2026). */
@Composable
fun BackButton(onClick: () -> Unit, enabled: Boolean = true, tint: Color = RbColors.textSecondary) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(R.string.back), tint = tint)
    }
}

/** A star rating the user sets: each star is a radio button with its own label and a 48 dp target. */
@Composable
fun RatingInput(rating: Int, enabled: Boolean = true, size: Int = 34, onChange: (Int) -> Unit) {
    Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        (1..5).forEach { i ->
            val label = androidx.compose.ui.res.pluralStringResource(R.plurals.rate_stars, i, i)
            Box(Modifier.minimumInteractiveComponentSize().selectable(selected = rating == i, enabled = enabled, role = Role.RadioButton) { onChange(i) }
                .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
                Text(if (i <= rating) "★" else "☆", color = if (i <= rating) RbColors.warning else RbColors.textTertiary, fontSize = size.sp)
            }
        }
    }
}

/** How quickly a publisher answers, in the reader's words; one mapping instead of four copies. */
@Composable
fun bucketLabel(bucket: String): String = when (bucket) {
    "hours" -> androidx.compose.ui.res.stringResource(R.string.bucket_hours)
    "days" -> androidx.compose.ui.res.stringResource(R.string.bucket_days)
    "weeks" -> androidx.compose.ui.res.stringResource(R.string.bucket_weeks)
    "months" -> androidx.compose.ui.res.stringResource(R.string.bucket_months)
    else -> bucket
}

/**
 * A failure in the reader's language where it can be recognised (declined in the wallet, not enough funds, no network,
 * timed out); otherwise the wallet's or RPC's own message, and never an empty line.
 */
fun Context.errorText(e: Throwable?): String {
    val m = (e?.message ?: "").lowercase()
    return when {
        e is java.net.UnknownHostException || e is java.net.ConnectException || "unable to resolve host" in m -> getString(R.string.err_network)
        e is java.net.SocketTimeoutException || "timeout" in m || "timed out" in m -> getString(R.string.err_timeout)
        "declin" in m || "reject" in m || "cancel" in m || "not authorized" in m -> getString(R.string.err_declined)
        "insufficient" in m -> getString(R.string.err_funds)
        m.isBlank() -> getString(R.string.action_failed)
        else -> e!!.message!!
    }
}

/** The reader's words for a moderation reason (the same four as a report). */
@Composable
fun modReasonLabel(r: com.undersigned.protocol.ReviewMemo.ModReason): String = androidx.compose.ui.res.stringResource(when (r) {
    com.undersigned.protocol.ReviewMemo.ModReason.ILLEGAL -> R.string.report_illegal
    com.undersigned.protocol.ReviewMemo.ModReason.PERSONAL -> R.string.report_personal
    com.undersigned.protocol.ReviewMemo.ModReason.ABUSE -> R.string.report_abuse
    com.undersigned.protocol.ReviewMemo.ModReason.IMPERSONATION -> R.string.report_impersonation
})

/** In place of text a moderator hid: the reason, never the text (no "show anyway": the point is not to show it). */
@Composable
fun ModHiddenText(reason: com.undersigned.protocol.ReviewMemo.ModReason, fontSize: Int = 12) {
    Text(androidx.compose.ui.res.stringResource(R.string.mod_hidden, modReasonLabel(reason)), color = RbColors.textTertiary, fontSize = fontSize.sp, lineHeight = (fontSize + 5).sp,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
}


/** "5 of the last 6 months · this week · reinstalled 2×": the activity a review carries, in the reader's words. */
@Composable
fun activityLabel(a: com.undersigned.protocol.ReviewMemo.Activity): String {
    val last = androidx.compose.ui.res.stringResource(when (a.recency) {
        com.undersigned.protocol.Recency.TODAY -> com.undersigned.app.R.string.recency_r0
        com.undersigned.protocol.Recency.WEEK -> com.undersigned.app.R.string.recency_r1
        com.undersigned.protocol.Recency.MONTH -> com.undersigned.app.R.string.recency_r2
        com.undersigned.protocol.Recency.QUARTER -> com.undersigned.app.R.string.recency_r3
        com.undersigned.protocol.Recency.OLDER -> com.undersigned.app.R.string.recency_r4
        com.undersigned.protocol.Recency.NEVER -> com.undersigned.app.R.string.recency_rn
    })
    val months = androidx.compose.ui.res.stringResource(com.undersigned.app.R.string.act_months, a.monthsActive)
    val re = if (a.reinstalls > 0) " · " + androidx.compose.ui.res.stringResource(com.undersigned.app.R.string.act_reinstalls, if (a.reinstalls >= com.undersigned.protocol.Usage.MAX_REINSTALLS) "9+" else a.reinstalls.toString()) else ""
    return "$months · $last$re"
}

/**
 * One quiet line at the top of a screen that fetches, while the last fetch failed for want of a connection (1.1.5): the
 * screen shows what this phone already holds, and says so. Nothing when online.
 */
@Composable
fun OfflineBanner(offline: Boolean, modifier: Modifier = Modifier) {
    if (!offline) return
    Text(androidx.compose.ui.res.stringResource(R.string.offline_banner), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(RbColors.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp))
}

/** "Couldn't load …" and a Try again button, where an empty-state sentence would otherwise follow a FAILED fetch (1.1.5). */
@Composable
fun LoadFailed(text: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(text, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            Text(androidx.compose.ui.res.stringResource(R.string.retry), color = RbColors.purpleLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
    }
}

/** "0 / 2,000 characters" under a text field, in the phone's number format; red once over the limit (1.1.5). */
@Composable
fun CharCounter(used: Int, max: Int) {
    val nf = java.text.NumberFormat.getIntegerInstance()
    Text(androidx.compose.ui.res.stringResource(R.string.review_note_counter, nf.format(used), nf.format(max)), color = if (used > max) RbColors.negative else RbColors.textTertiary,
        fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
}
