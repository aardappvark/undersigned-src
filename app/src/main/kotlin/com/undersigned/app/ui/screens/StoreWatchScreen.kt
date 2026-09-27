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
import androidx.compose.ui.res.stringResource
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

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            Text(stringResource(R.string.watch_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.watch_toggle), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = watch.enabled, onCheckedChange = { vm.setStoreWatch(it) })
                }
                Text(stringResource(R.string.watch_toggle_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                progress?.let { Text(stringResource(R.string.watch_reading, it), color = RbColors.purpleLight, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                watch.at?.let { Text(stringResource(R.string.watch_last, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)), watch.apps), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
                watch.days.firstOrNull()?.let { d -> val shown = runCatching { df.format(Date(java.time.LocalDate.parse(d).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())) }.getOrDefault(d)
                    Text(stringResource(R.string.watch_since, shown, watch.days.size), color = RbColors.textTertiary, fontSize = 11.sp) }
            }
            Spacer(Modifier.height(10.dp))
            when {
                watch.days.size <= ReviewBursts.MIN_SNAPSHOT_DAYS -> Text(stringResource(R.string.watch_needs, ReviewBursts.MIN_SNAPSHOT_DAYS + 1), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                watch.hits.isEmpty() -> Text(stringResource(R.string.watch_empty), color = RbColors.textSecondary, fontSize = 13.sp)
            }
            watch.hits.forEach { h ->
                val b = h.burst
                GlassCard(modifier = Modifier.clickable { onOpen(h.pkg) }, accent = RbColors.warning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DAppIcon(h.pkg, h.name, icons[h.pkg]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(h.name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(stringResource(if (b.kind == ReviewBursts.Kind.HIGH) R.string.burst_high_ratings else R.string.burst_low_ratings, b.count, df.format(Date(b.start * 1000)), df.format(Date(b.end * 1000)), String.format(java.util.Locale.getDefault(), "%.1f", b.expected)),
                        color = RbColors.warning, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(stringResource(R.string.burst_causes), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(32.dp))
        }
    }
}
