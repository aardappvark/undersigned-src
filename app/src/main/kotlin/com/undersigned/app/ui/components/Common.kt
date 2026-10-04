package com.undersigned.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            containerColor = RbColors.purple, contentColor = RbColors.textPrimary,
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
fun Pill(text: String, color: Color = RbColors.purpleLight) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
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
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val full = kotlin.math.floor(rating).toInt()
        repeat(5) { i ->
            Text(if (i < full) "★" else "☆", color = if (i < full) RbColors.warning else RbColors.textTertiary, fontSize = size.sp)
        }
    }
}

@Composable
fun TagChip(text: String, count: Int? = null, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val bg = if (selected) RbColors.purple.copy(alpha = 0.35f) else RbColors.surfaceVariant
    val fg = if (selected) RbColors.textPrimary else RbColors.textSecondary
    val m = Modifier.clip(RoundedCornerShape(50)).background(bg).let { if (onClick != null) it.clickable { onClick() } else it }.padding(horizontal = 10.dp, vertical = 5.dp)
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
    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(java.time.Instant.parse(iso).toEpochMilli()))
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
