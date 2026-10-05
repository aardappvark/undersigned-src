package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.ReviewBursts
import java.text.DateFormat
import java.util.Date

/**
 * Store watch: apps across the whole dApp Store whose star ratings burst in the last 3 days, from this phone's own daily
 * snapshots of the store's public counts. Facts only, strongest first; each row opens the app's page, where the review
 * bursts and the reviewers' attested devices sit beside it.
 */
@Composable
fun StoreWatchScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val watch by vm.watch.collectAsState()
    val progress by vm.watchProgress.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    LaunchedEffect(Unit) { vm.loadWatch() }
    LaunchedEffect(watch.hits) { vm.resolveIcons(watch.hits.map { it.pkg }) }
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val need = ReviewBursts.MIN_SNAPSHOT_DAYS.toInt() + 1
    fun dayMs(d: String) = runCatching { java.time.LocalDate.parse(d).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull()
    val lastRead = watch.at?.let { android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString() }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(stringResource(R.string.watch_title), color = RbColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // G6 — the switch, with the cost stated before switching it on.
            GlassCard {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = watch.enabled, role = androidx.compose.ui.semantics.Role.Switch) { vm.setStoreWatch(it) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.watch_toggle), color = RbColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, modifier = Modifier.weight(1f))
                    Switch(checked = watch.enabled, onCheckedChange = null)
                }
                if (!watch.enabled) {
                    Text(stringResource(R.string.watch_toggle_body), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    KeyValueGrid(listOf(stringResource(R.string.watch_label_requests) to "~72", stringResource(R.string.watch_label_kept) to pluralStringResource(R.plurals.watch_days_n, 60, 60),
                        stringResource(R.string.watch_label_first) to pluralStringResource(R.plurals.watch_day_n, need, need)), columns = 3, valueSize = 15)
                }
                progress?.let { Text(stringResource(R.string.watch_reading, it), color = RbColors.purpleLight, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            }
            Spacer(Modifier.height(12.dp))
            if (watch.enabled && watch.days.size < need) {
                // G7 — collecting: one segment per daily snapshot, and the date results begin.
                GlassCard {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${watch.days.size}", color = RbColors.textPrimary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp)
                        Text("  " + pluralStringResource(R.plurals.watch_days_of, need, need), color = RbColors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(need) { i -> Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(if (i < watch.days.size) RbColors.purple else RbColors.surfaceVariant)) }
                    }
                    Text(stringResource(R.string.watch_needs, need), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(12.dp))
                    val first = watch.days.firstOrNull()?.let { dayMs(it) }
                    KeyValueGrid(listOfNotNull(stringResource(R.string.watch_label_last) to (lastRead ?: "—"), stringResource(R.string.watch_label_apps) to "${watch.apps}",
                        first?.let { stringResource(R.string.watch_label_since) to df.format(Date(it)) },
                        first?.let { stringResource(R.string.watch_label_first) to df.format(Date(it + (need - 1) * 86_400_000L)) }), columns = 2, valueSize = 14)
                }
            } else if (watch.enabled && watch.hits.isNotEmpty()) {
                // G8 — results, strongest first.
                lastRead?.let { Text(stringResource(R.string.watch_last, it, watch.apps), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                GlassCard(accent = RbColors.warning) {
                    watch.hits.forEachIndexed { i, h ->
                        if (i > 0) HorizontalDivider(color = RbColors.surfaceVariant)
                        val b = h.burst
                        Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable { onOpen(h.pkg) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            DAppIcon(h.pkg, h.name, icons[h.pkg]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(40.dp)); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(h.name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(pluralStringResource(if (b.kind == ReviewBursts.Kind.HIGH) R.plurals.watch_count_high else R.plurals.watch_count_low, b.count, b.count), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("  ×" + String.format(java.util.Locale.US, "%.0f", com.undersigned.protocol.BurstSpark.multiple(b).coerceAtLeast(1.0)), color = RbColors.warning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Text(android.text.format.DateUtils.formatDateRange(androidx.compose.ui.platform.LocalContext.current, b.start * 1000, b.end * 1000 + 1, android.text.format.DateUtils.FORMAT_ABBREV_MONTH or android.text.format.DateUtils.FORMAT_SHOW_YEAR or android.text.format.DateUtils.FORMAT_SHOW_DATE),
                                    color = RbColors.textTertiary, fontSize = 12.sp)
                            }
                            h.spark?.let { sp -> Sparkline(sp.counts, sp.from, sp.to, b.expected / 3.0,
                                stringResource(if (b.kind == ReviewBursts.Kind.HIGH) R.string.burst_high_ratings else R.string.burst_low_ratings, b.count, df.format(Date(b.start * 1000)), df.format(Date(b.end * 1000)), String.format(java.util.Locale.US, "%.1f", b.expected)),
                                Modifier.size(64.dp, 28.dp)) }
                        }
                    }
                }
                Text(stringResource(R.string.burst_causes), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp))
            } else if (watch.enabled) {
                // G8 — nothing qualifies.
                GlassCard {
                    Text(stringResource(R.string.watch_empty), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    lastRead?.let { Text(stringResource(R.string.watch_last, it, watch.apps), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
