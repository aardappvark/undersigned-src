package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.ReviewMemo

/** Pro: the unanswered queue and responsiveness across every dApp the wallet has verified, in one list. */
@Composable
fun PublisherDeskScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onPro: () -> Unit) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsState()
    val verified by vm.verifiedPublishers.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val domainNames by vm.domainNames.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    val pro by vm.pro.collectAsState()
    val desk = remember(entries, verified) { vm.desk() }
    fun name(pkg: String) = catalogue.firstOrNull { it.packageName == pkg }?.name ?: pkg
    @Composable fun bucket(b: String) = when (b) { "hours" -> stringResource(R.string.bucket_hours); "days" -> stringResource(R.string.bucket_days); "weeks" -> stringResource(R.string.bucket_weeks); "months" -> stringResource(R.string.bucket_months); else -> b }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            Text(stringResource(R.string.desk_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Pill(stringResource(R.string.pro_badge), RbColors.purpleLight); Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (pro?.active != true) { Spacer(Modifier.height(8.dp)); ProLock(onPro = onPro); Spacer(Modifier.height(8.dp)) }
            Text(pluralStringResource(R.plurals.desk_across, desk.apps.size, desk.apps.size), color = RbColors.textTertiary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val stats = listOf(
                    desk.unanswered.toString() to stringResource(R.string.desk_unanswered),
                    (desk.medianReplyHours?.let { h -> if (h < 48) stringResource(R.string.desk_hours, h.toInt()) else pluralStringResource(R.plurals.desk_days, (h / 24).toInt(), (h / 24).toInt()) } ?: "—") to stringResource(R.string.desk_median),
                    (desk.answeredPercent?.let { "$it%" } ?: "—") to stringResource(R.string.desk_answered),
                )
                stats.forEach { (v, l) -> GlassCard(Modifier.weight(1f)) { Text(v, color = RbColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text(l, color = RbColors.textTertiary, fontSize = 11.sp) } }
            }
            SectionTitle(stringResource(R.string.desk_your_dapps))
            if (desk.apps.isEmpty()) Text(stringResource(R.string.desk_none), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            desk.apps.forEach { a ->
                GlassCard(Modifier.clickable { onOpen(a.packageName) }, accent = if (a.waiting > 0) RbColors.warning else RbColors.glassBorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DAppIcon(a.packageName, name(a.packageName), icons[a.packageName]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name(a.packageName), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            val r = a.responsiveness
                            Text((a.score?.let { String.format(java.util.Locale.US, "%.1f signed · ", it.weightedMean) } ?: stringResource(R.string.row_no_reviews) + " · ") + (if (r.answered == 0) stringResource(R.string.responsiveness_never) else stringResource(R.string.responsiveness_line, bucket(r.bucket), r.answered, r.total)), color = RbColors.textSecondary, fontSize = 11.sp)
                        }
                        Pill(if (a.waiting > 0) stringResource(R.string.desk_waiting, a.waiting) else stringResource(R.string.desk_clear), if (a.waiting > 0) RbColors.warning else RbColors.green)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            SectionTitle(stringResource(R.string.desk_oldest))
            if (desk.oldest.isEmpty()) Text(stringResource(R.string.desk_none_waiting), color = RbColors.textSecondary, fontSize = 13.sp)
            desk.oldest.take(20).forEach { e ->
                val r = e.memo as ReviewMemo.Review
                val ageDays = ((System.currentTimeMillis() / 1000 - e.blockTime) / 86400L).toInt()
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) { RatingStars(r.rating.toDouble(), size = 12); Spacer(Modifier.width(6.dp)); Text((domainNames[e.signer]?.takeIf { it.isNotEmpty() } ?: com.undersigned.app.solana.shortKey(e.signer, 4, 4)) + " · " + pluralStringResource(R.plurals.desk_days, ageDays, ageDays) + " · " + name(r.packageName), color = RbColors.textTertiary, fontSize = 11.sp) }
                    r.text?.let { Spacer(Modifier.height(4.dp)); Text(it, color = RbColors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp) }
                    Row {
                        TextButton(onClick = { com.undersigned.app.ui.NavIntents.pendingReply.value = e.signature to ""; onOpen(r.packageName) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.reply), color = RbColors.purpleLight, fontSize = 12.sp) }
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { runCatching { context.packageManager.getLaunchIntentForPackage(r.packageName)?.let { context.startActivity(it) } } }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.detail_open_app), color = RbColors.textSecondary, fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.desk_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(32.dp))
        }
    }
}
