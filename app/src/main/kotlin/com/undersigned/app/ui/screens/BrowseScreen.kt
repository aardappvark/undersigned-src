package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.undersigned.app.R
import com.undersigned.app.data.CatalogueApp
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.ui.LedgerStatus
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.ChipRow
import com.undersigned.app.ui.components.DAppIcon
import com.undersigned.app.ui.components.EmptyState
import com.undersigned.app.ui.components.Pill
import com.undersigned.app.ui.components.RatingStars
import com.undersigned.app.ui.components.TagChip
import com.undersigned.app.ui.components.label
import com.undersigned.app.ui.components.tagLabel
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.AppScore

private enum class Tab { INSTALLED, SIGNED, ALL }
private enum class Sort { SCORE, NEW, CATEGORY, NAME }

/** A row of the list, or a category head when sorted by category. */
private sealed class Row { data class App(val app: CatalogueApp) : Row(); data class Head(val category: String, val count: Int) : Row() }

@Composable
fun BrowseScreen(vm: MainViewModel, onOpen: (String) -> Unit, onChanges: () -> Unit, onSettings: () -> Unit, onInbox: () -> Unit = {}, onProfile: (String) -> Unit = {}, onStats: () -> Unit = {}, onMyReviews: () -> Unit = {}) {
    val catalogue by vm.catalogue.collectAsState()
    val installed by vm.installed.collectAsState()
    val scores by vm.scores.collectAsState()
    val status by vm.ledgerStatus.collectAsState()
    val changes by vm.changes.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    val dense by UserPreferences.dense.collectAsState()
    val refreshing by vm.catalogueRefreshing.collectAsState()
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Tab.INSTALLED) }
    var sort by remember { mutableStateOf(Sort.SCORE) }
    val uncategorised = stringResource(R.string.category_none)
    val domainNames by vm.domainNames.collectAsState()
    val entriesForSigners by vm.entries.collectAsState()
    val signerRows = remember(query, domainNames, entriesForSigners) { vm.signerMatches(query) }
    val scope = rememberCoroutineScope()
    var signerNote by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun searchSigners() { scope.launch { val w = vm.resolveWallet(query); if (w != null) onProfile(w) else signerNote = context.getString(R.string.search_signers_none) } }

    val apps = remember(catalogue, installed, scores, query, tab, sort) {
        val q = query.trim().lowercase()
        catalogue.asSequence()
            .filter { when (tab) { Tab.INSTALLED -> it.packageName in installed; Tab.SIGNED -> scores[it.packageName] != null; Tab.ALL -> true } }
            // Search matches name, package, category and the tags owners applied (design: Screens E).
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q) || (it.category?.lowercase()?.contains(q) == true) || (scores[it.packageName]?.tagCounts?.keys?.any { t -> context.tagLabel(t.wire).lowercase().contains(q) || t.wire.contains(q) } == true) }
            .sortedWith(when (sort) {
                Sort.SCORE -> compareByDescending<CatalogueApp> { scores[it.packageName]?.weightedRating ?: -1.0 }.thenByDescending { it.updated }
                Sort.NEW -> compareByDescending<CatalogueApp> { it.updated }.thenBy { it.name.lowercase() }
                Sort.CATEGORY -> compareBy<CatalogueApp> { it.category ?: "￿" }.thenByDescending { scores[it.packageName]?.weightedRating ?: -1.0 }
                Sort.NAME -> compareBy { it.name.lowercase() }
            }).toList()
    }
    val rows = remember(apps, sort) {
        if (sort != Sort.CATEGORY) apps.map { Row.App(it) }
        else apps.groupBy { it.category ?: uncategorised }.flatMap { (c, l) -> listOf(Row.Head(c, l.size)) + l.map { Row.App(it) } }
    }

    LaunchedEffect(apps) { vm.resolveIcons(apps.take(60).map { it.packageName }) }
    val listState = rememberLazyListState()
    val firstKey = rows.firstOrNull()?.let { r -> when (r) { is Row.App -> r.app.packageName; is Row.Head -> "head:" + r.category } }
    LaunchedEffect(firstKey, tab, sort) { if (!listState.isScrollInProgress) listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.browse_title), color = RbColors.textPrimary, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
            val entriesAll by vm.entries.collectAsState()
            val inboxCount = remember(entriesAll) { vm.unanswered().size + vm.repliesToMe().size }
            IconButton(onClick = onMyReviews) { Icon(Icons.Default.RateReview, contentDescription = stringResource(R.string.my_reviews_title), tint = RbColors.textSecondary) }
            IconButton(onClick = onInbox) {
                BadgedBox(badge = { if (inboxCount > 0) Badge(containerColor = RbColors.purple) { Text("$inboxCount") } }) { Icon(Icons.Default.Inbox, contentDescription = stringResource(R.string.inbox), tint = RbColors.textSecondary) }
            }
            IconButton(onClick = onChanges) {
                BadgedBox(badge = { if (changes.any { !it.firstSeen }) Badge(containerColor = RbColors.warning) }) { Icon(Icons.Default.Update, contentDescription = stringResource(R.string.changes_title), tint = RbColors.textSecondary) }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = RbColors.textSecondary) }
        }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (val s = status) {
                    is LedgerStatus.Syncing -> stringResource(R.string.ledger_syncing) + if (s.total > 0) " ${s.done}/${s.total}" else ""
                    is LedgerStatus.Synced -> stringResource(R.string.ledger_synced, pluralStringResource(R.plurals.ledger_reviews, s.reviews, s.reviews), pluralStringResource(R.plurals.ledger_seekers, s.signers, s.signers))
                    is LedgerStatus.Error -> stringResource(R.string.ledger_error, s.message)
                    LedgerStatus.Idle -> stringResource(R.string.catalogue_count, catalogue.size)
                }, color = if (status is LedgerStatus.Error) RbColors.warning else RbColors.textTertiary, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            // The single point of failure in a serverless app gets its retry where the failure is read (design: Screens E).
            if (status is LedgerStatus.Error) TextButton(onClick = { vm.syncLedger() }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(stringResource(R.string.ledger_retry), color = RbColors.purpleLight, fontSize = 12.sp) }
            else TextButton(onClick = onStats, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(stringResource(R.string.stats_open), color = RbColors.purpleLight, fontSize = 12.sp) }
        }
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
        ChipRow(stringResource(R.string.sort), listOf(Sort.SCORE to stringResource(R.string.sort_score), Sort.NEW to stringResource(R.string.sort_new), Sort.CATEGORY to stringResource(R.string.sort_category), Sort.NAME to stringResource(R.string.sort_name)), sort, { sort = it }, Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        when {
            rows.isEmpty() && query.isNotBlank() && signerRows.isEmpty() -> Column(Modifier.fillMaxSize()) {
                EmptyState(R.drawable.photo_empty_search, stringResource(R.string.empty_search_title), signerNote ?: stringResource(R.string.empty_search_body, query.trim()),
                    action = stringResource(R.string.search_signers), onAction = { searchSigners() }, modifier = Modifier.weight(1f))
                if (!refreshing) TextButton(onClick = { vm.refreshCatalogue() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.reread_catalogue), color = RbColors.purpleLight, fontSize = 13.sp) }
            }
            rows.isEmpty() && tab == Tab.SIGNED -> EmptyState(R.drawable.photo_empty_signed, stringResource(R.string.empty_signed))
            rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.empty_installed), color = RbColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center) }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                // Signers whose name or address matches the query, above the dApps (design: Screens E "Search signers").
                if (signerRows.isNotEmpty()) {
                    item(key = "signers-head") { CategoryHead(stringResource(R.string.signers_heading), signerRows.size) }
                    items(signerRows, key = { "signer:" + it.first }) { (w, name) ->
                        val st = vm.standings.collectAsState().value[w]
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clickable { onProfile(w) }.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.undersigned.app.ui.components.LetterBadge(name, Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(stringResource(R.string.signer_row_sub, st?.countedReviews ?: 0), color = RbColors.textTertiary, fontSize = 11.sp) }
                        }
                        HorizontalDivider(color = RbColors.surfaceVariant, thickness = 1.dp)
                    }
                }
                items(rows, key = { r -> when (r) { is Row.App -> r.app.packageName; is Row.Head -> "head:" + r.category } }) { r ->
                    when (r) {
                        is Row.Head -> CategoryHead(r.category, r.count)
                        is Row.App -> {
                            val app = r.app
                            val iconUrl = icons[app.packageName]?.takeIf { it.isNotEmpty() }
                            if (dense) DenseRow(app, scores[app.packageName], app.packageName in installed && tab != Tab.INSTALLED, iconUrl) { onOpen(app.packageName) }
                            else AppRow(app, scores[app.packageName], app.packageName in installed && tab != Tab.INSTALLED, sort != Sort.CATEGORY, iconUrl) { onOpen(app.packageName) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryHead(category: String, count: Int) {
    Row(Modifier.fillMaxWidth().background(RbColors.background).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(category, color = RbColors.purpleLight, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, modifier = Modifier.weight(1f))
        Text(if (count == 1) stringResource(R.string.group_one) else stringResource(R.string.group_many, count), color = RbColors.textTertiary, fontSize = 11.sp)
    }
}

/** Balanced: two lines, 44 dp icon, chips with counts, the category as a pill in a right-hand column. */
@Composable
private fun AppRow(app: CatalogueApp, score: AppScore?, showInstalled: Boolean, showCategory: Boolean, iconUrl: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DAppIcon(app.packageName, app.name, iconUrl)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (showCategory) app.category?.let { Pill(it) }
                if (showInstalled) { if (showCategory && app.category != null) Spacer(Modifier.height(4.dp)); Pill(stringResource(R.string.tab_installed), RbColors.green) }
            }
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
                score.tagCounts.entries.sortedByDescending { it.value }.take(2).forEach { (t, n) -> TagChip(t.label(), n); Spacer(Modifier.width(4.dp)) }
            }
        } else Text(stringResource(R.string.row_no_reviews), color = RbColors.textTertiary, fontSize = 12.sp)
    }
    HorizontalDivider(color = RbColors.surfaceVariant, thickness = 1.dp)
}

/** Dense: one baseline, 32 dp icon, tags as dot-separated text, installed as a green dot, score in a fixed column. */
@Composable
private fun DenseRow(app: CatalogueApp, score: AppScore?, showInstalled: Boolean, iconUrl: String?, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { onClick() }.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        DAppIcon(app.packageName, app.name, iconUrl, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showInstalled) { Box(Modifier.size(6.dp).clip(CircleShape).background(RbColors.green)); Spacer(Modifier.width(6.dp)) }
                Text(app.name, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(score?.tagCounts?.entries?.sortedByDescending { it.value }?.take(3)?.joinToString(" · ") { context.tagLabel(it.key.wire) }?.ifEmpty { null } ?: stringResource(R.string.row_no_reviews),
                color = RbColors.textTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(96.dp)) {
            if (score != null) {
                RatingStars(score.weightedMean, size = 10)
                Text(String.format(java.util.Locale.US, "%.1f · %d", score.weightedMean, score.countedReviews), color = RbColors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else Text("—", color = RbColors.textDim, fontSize = 11.sp)
        }
    }
    HorizontalDivider(color = RbColors.surfaceVariant, thickness = 1.dp)
}
