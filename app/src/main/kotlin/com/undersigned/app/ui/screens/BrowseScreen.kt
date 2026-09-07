package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.data.CatalogueApp
import com.undersigned.app.ui.LedgerStatus
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.DAppIcon
import com.undersigned.app.ui.components.Pill
import com.undersigned.app.ui.components.RatingStars
import com.undersigned.app.ui.components.TagChip
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.AppScore

private enum class Tab { INSTALLED, SIGNED, ALL }

@Composable
fun BrowseScreen(vm: MainViewModel, onOpen: (String) -> Unit, onChanges: () -> Unit, onSettings: () -> Unit) {
    val catalogue by vm.catalogue.collectAsState()
    val installed by vm.installed.collectAsState()
    val scores by vm.scores.collectAsState()
    val status by vm.ledgerStatus.collectAsState()
    val changes by vm.changes.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Tab.INSTALLED) }

    val rows = remember(catalogue, installed, scores, query, tab) {
        val q = query.trim().lowercase()
        catalogue.asSequence()
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
            .filter { when (tab) { Tab.INSTALLED -> it.packageName in installed; Tab.SIGNED -> scores[it.packageName] != null; Tab.ALL -> true } }
            .sortedWith(compareByDescending<CatalogueApp> { scores[it.packageName]?.weightedRating ?: -1.0 }.thenByDescending { it.updated })
            .toList()
    }

    LaunchedEffect(rows) { vm.resolveIcons(rows.take(60).map { it.packageName }) }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.browse_title), color = RbColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onChanges) {
                BadgedBox(badge = { if (changes.any { !it.firstSeen }) Badge(containerColor = RbColors.warning) }) { Icon(Icons.Default.Update, contentDescription = stringResource(R.string.changes_title), tint = RbColors.textSecondary) }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = RbColors.textSecondary) }
        }
        Text(
            when (val s = status) {
                is LedgerStatus.Syncing -> stringResource(R.string.ledger_syncing) + if (s.total > 0) " ${s.done}/${s.total}" else ""
                is LedgerStatus.Synced -> stringResource(R.string.ledger_synced, s.reviews, s.signers)
                is LedgerStatus.Error -> stringResource(R.string.ledger_error, s.message)
                LedgerStatus.Idle -> stringResource(R.string.catalogue_count, catalogue.size)
            }, color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp),
        )
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.browse_search_hint), color = RbColors.textTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RbColors.textTertiary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight),
        )
        TabRow(selectedTabIndex = tab.ordinal, containerColor = RbColors.background, contentColor = RbColors.purpleLight) {
            Tab(selected = tab == Tab.INSTALLED, onClick = { tab = Tab.INSTALLED }, text = { Text(stringResource(R.string.tab_installed) + " · ${installed.count { p -> catalogue.any { it.packageName == p } }}", fontSize = 13.sp) })
            Tab(selected = tab == Tab.SIGNED, onClick = { tab = Tab.SIGNED }, text = { Text(stringResource(R.string.tab_signed) + " · ${scores.size}", fontSize = 13.sp) })
            Tab(selected = tab == Tab.ALL, onClick = { tab = Tab.ALL }, text = { Text(stringResource(R.string.tab_all), fontSize = 13.sp) })
        }
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(when { query.isNotBlank() -> stringResource(R.string.empty_search); tab == Tab.INSTALLED -> stringResource(R.string.empty_installed); tab == Tab.SIGNED -> stringResource(R.string.empty_signed); else -> stringResource(R.string.empty_search) }, color = RbColors.textSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(rows, key = { it.packageName }) { app -> AppRow(app, scores[app.packageName], app.packageName in installed, icons[app.packageName]?.takeIf { it.isNotEmpty() }) { onOpen(app.packageName) } }
            }
        }
    }
}

@Composable
private fun AppRow(app: CatalogueApp, score: AppScore?, installed: Boolean, iconUrl: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DAppIcon(app.packageName, app.name, iconUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(app.packageName, color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            if (installed) Pill(stringResource(R.string.tab_installed), RbColors.green)
            app.category?.let { Spacer(Modifier.width(6.dp)); Pill(it) }
        }
        Spacer(Modifier.height(6.dp))
        if (score != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RatingStars(score.weightedMean)
                Spacer(Modifier.width(6.dp))
                Text(String.format(java.util.Locale.US, "%.1f", score.weightedMean), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.row_signed_count, score.countedReviews), color = RbColors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                score.tagCounts.entries.sortedByDescending { it.value }.take(2).forEach { (t, n) -> TagChip(t.wire, n); Spacer(Modifier.width(4.dp)) }
            }
        } else Text(stringResource(R.string.row_no_reviews), color = RbColors.textTertiary, fontSize = 12.sp)
    }
    HorizontalDivider(color = RbColors.surfaceVariant, thickness = 1.dp)
}
