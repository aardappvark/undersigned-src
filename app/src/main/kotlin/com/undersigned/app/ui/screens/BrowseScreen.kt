package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import coil.imageLoader
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.undersigned.app.ui.components.AlphaRail
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.undersigned.app.ui.components.GlassCard
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
    // Saveable: coming back from an app page used to clear the search, tab, sort and filter (UI review 6 Oct 2026).
    var query by rememberSaveable { mutableStateOf("") }
    // Until the user picks a tab: Installed, or Signed when no store app is installed (a fresh phone opened on an empty list).
    var tabPicked by rememberSaveable { mutableStateOf<Tab?>(null) }
    val installedCount = remember(installed, catalogue) { val c = catalogue.mapTo(HashSet()) { it.packageName }; installed.count { it in c } }
    val tab = tabPicked ?: if (installedCount == 0 && catalogue.isNotEmpty()) Tab.SIGNED else Tab.INSTALLED
    var sort by rememberSaveable { mutableStateOf(Sort.SCORE) }
    // Filter by a tag reviewers applied: the thing the store has no way to query.
    var tagFilter by rememberSaveable { mutableStateOf<com.undersigned.protocol.ReviewMemo.Tag?>(null) }
    // The week's New: apps the weekly check listed for the first time (owner, 7 Oct: ported from the Toolkit).
    var newOnly by rememberSaveable { mutableStateOf(false) }
    val newOnes = remember(catalogue) { com.undersigned.app.data.WeeklyCatalogue.newThisWeek(catalogue).mapTo(HashSet()) { it.packageName } }
    val pendingScreen by com.undersigned.app.ui.NavIntents.pendingScreen.collectAsState()
    LaunchedEffect(pendingScreen) { if (pendingScreen == "new") { com.undersigned.app.ui.NavIntents.pendingScreen.value = null; tabPicked = Tab.ALL; newOnly = true; query = ""; tagFilter = null } }
    val pendingSearch by com.undersigned.app.ui.NavIntents.pendingSearch.collectAsState()
    LaunchedEffect(pendingSearch) { pendingSearch?.let { com.undersigned.app.ui.NavIntents.pendingSearch.value = null; tabPicked = Tab.ALL; newOnly = false; tagFilter = null; query = it } }
    val uncategorised = stringResource(R.string.category_none)
    val domainNames by vm.domainNames.collectAsState()
    val entriesForSigners by vm.entries.collectAsState()
    val signerRows = remember(query, domainNames, entriesForSigners) { vm.signerMatches(query) }
    val scope = rememberCoroutineScope()
    var signerNote by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun searchSigners() { scope.launch { val w = vm.resolveWallet(query); if (w != null) onProfile(w) else signerNote = context.getString(R.string.search_signers_none) } }

    val apps = remember(catalogue, installed, scores, query, tab, sort, tagFilter, newOnly, newOnes) {
        val q = query.trim().lowercase()
        catalogue.asSequence()
            .filter { when (tab) { Tab.INSTALLED -> it.packageName in installed; Tab.SIGNED -> scores[it.packageName] != null; Tab.ALL -> true } }
            .filter { t -> tagFilter == null || (scores[t.packageName]?.tagCounts?.get(tagFilter) ?: 0) > 0 }
            .filter { !newOnly || it.packageName in newOnes }
            // Search matches name, package, category and the tags owners applied (design: Screens E).
            .filter { q.isEmpty() || it.name.lowercase().contains(q) || it.packageName.lowercase().contains(q) || (it.category?.lowercase()?.contains(q) == true) || (scores[it.packageName]?.tagCounts?.keys?.any { t -> context.tagLabel(t.wire).lowercase().contains(q) || t.wire.contains(q) } == true) }
            .sortedWith(when (sort) {
                Sort.SCORE -> compareByDescending<CatalogueApp> { scores[it.packageName]?.takeIf { s -> s.countedReviews > 0 }?.weightedRating ?: -1.0 }.thenByDescending { it.updated }
                Sort.NEW -> compareByDescending<CatalogueApp> { it.updated }.thenBy { it.name.lowercase() }
                Sort.CATEGORY -> compareBy<CatalogueApp> { it.category ?: "￿" }.thenByDescending { scores[it.packageName]?.takeIf { s -> s.countedReviews > 0 }?.weightedRating ?: -1.0 }
                Sort.NAME -> compareBy { it.name.lowercase() }
            }).toList()
    }
    val rows = remember(apps, sort) {
        if (sort != Sort.CATEGORY) apps.map { Row.App(it) }
        else apps.groupBy { it.category ?: uncategorised }.flatMap { (c, l) -> listOf(Row.Head(c, l.size)) + l.map { Row.App(it) } }
    }

    LaunchedEffect(apps) { vm.resolveIcons(apps.take(60).map { it.packageName }); vm.fillIcons() }
    val listState = rememberLazyListState()
    // Icons for what is on screen and the next 40 rows, as the list scrolls. Most ship inside the app; only the ones that
    // don't (new apps, a changed icon) are fetched ahead, at row size, so they are on the phone when the rows arrive.
    LaunchedEffect(listState, apps) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { first ->
            val ahead = apps.drop((first - 5).coerceAtLeast(0)).take(50).map { it.packageName }
            vm.resolveIcons(ahead.filterNot { com.undersigned.app.data.BundledIcons.source(context, it) != null })
            ahead.filterNot { com.undersigned.app.data.BundledIcons.isCurrent(context, it, icons[it]) }.mapNotNull { icons[it]?.takeIf { u -> u.isNotEmpty() } }
                .forEach { u -> context.imageLoader.enqueue(coil.request.ImageRequest.Builder(context).data(u).size(160).build()) }
        }
    }
    val firstKey = rows.firstOrNull()?.let { r -> when (r) { is Row.App -> r.app.packageName; is Row.Head -> "head:" + r.category } }
    // Back to the top when the list itself changes, but not on returning to this screen (its position is restored).
    var shownKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(firstKey, tab, sort) { val k = "$firstKey|$tab|$sort"; if (shownKey != null && shownKey != k && !listState.isScrollInProgress) listState.scrollToItem(0); shownKey = k }

    // Scrolling down the list slides everything under the title away, so the list runs up to the Undersigned bar;
    // scrolling back up (or reaching the top) brings it back (owner, 7 Oct 2026).
    var headerShown by rememberSaveable { mutableStateOf(true) }
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 } }
    LaunchedEffect(atTop) { if (atTop) headerShown = true }
    val collapse = remember { object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
        override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
            if (available.y < -8f) headerShown = false else if (available.y > 8f) headerShown = true
            return androidx.compose.ui.geometry.Offset.Zero
        } } }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding().nestedScroll(collapse)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // The name is a wordmark: it grows at most 15 % with the system font size and never breaks mid-word
            // ("Undersigne / d" at 1.3× beside four icons, UI pass 6 Oct 2026).
            val fs = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
            Text(stringResource(R.string.browse_title), color = RbColors.textPrimary, fontSize = (28f * minOf(fs, 1.15f) / fs).sp, lineHeight = (30f * minOf(fs, 1.15f) / fs).sp, maxLines = 1, softWrap = false, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
            val entriesAll by vm.entries.collectAsState()
            val inboxCount = remember(entriesAll) { vm.unanswered().size + vm.repliesToMe().size }
            IconButton(onClick = onMyReviews) { Icon(Icons.Default.RateReview, contentDescription = stringResource(R.string.my_reviews_title), tint = RbColors.textSecondary) }
            IconButton(onClick = onInbox) {
                BadgedBox(badge = { if (inboxCount > 0) Badge(containerColor = RbColors.purple) { Text("$inboxCount") } }) { Icon(Icons.Default.Inbox, contentDescription = if (inboxCount > 0) pluralStringResource(R.plurals.inbox_a11y, inboxCount, inboxCount) else stringResource(R.string.inbox), tint = RbColors.textSecondary) }
            }
            IconButton(onClick = onChanges) {
                BadgedBox(badge = { if (changes.any { !it.firstSeen }) Badge(containerColor = RbColors.warning) }) { Icon(Icons.Default.Update, contentDescription = stringResource(if (changes.any { !it.firstSeen }) R.string.changes_new_a11y else R.string.changes_title), tint = RbColors.textSecondary) }
            }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings), tint = RbColors.textSecondary) }
        }
        androidx.compose.animation.AnimatedVisibility(visible = headerShown || query.isNotEmpty(), enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()) { Column {
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
        // The dApp list: an Update banner once it is older than this week's Monday morning, progress while it runs, and
        // what it found when done (owner, 7 Oct 2026). Pro refreshes in the background instead (Settings).
        val catRefreshedAt by vm.catalogueRefreshedAt.collectAsState()
        val catProgress by vm.catalogueProgress.collectAsState()
        val catResult by vm.catalogueResult.collectAsState()
        val catFailed by vm.catalogueFailed.collectAsState()
        val lastWeeks = remember(catRefreshedAt) { com.undersigned.app.data.WeeklyCatalogue.isLastWeeks(catRefreshedAt) }
        val catNote by vm.catalogueNote.collectAsState()
        val proActive = vm.pro.collectAsState().value?.active == true
        if (refreshing || catFailed || catResult != null || catNote != null || (lastWeeks && !proActive)) CatalogueBar(refreshing, catProgress, catResult, catFailed, catNote, catRefreshedAt, catalogue.size) { vm.refreshCatalogue() }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.browse_search_hint), color = RbColors.textTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RbColors.textTertiary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight),
        )
        TabRow(selectedTabIndex = tab.ordinal, containerColor = RbColors.background, contentColor = RbColors.purpleLight) {
            Tab(selected = tab == Tab.INSTALLED, onClick = { tabPicked = Tab.INSTALLED }, text = { Text(stringResource(R.string.tab_installed) + " · $installedCount", fontSize = 13.sp) })
            Tab(selected = tab == Tab.SIGNED, onClick = { tabPicked = Tab.SIGNED }, text = { Text(stringResource(R.string.tab_signed) + " · ${scores.size}", fontSize = 13.sp) })
            Tab(selected = tab == Tab.ALL, onClick = { tabPicked = Tab.ALL }, text = { Text(stringResource(R.string.tab_all), fontSize = 13.sp) })
        }
        ChipRow(stringResource(R.string.sort), listOf(Sort.SCORE to stringResource(R.string.sort_score), Sort.NEW to stringResource(R.string.sort_new), Sort.CATEGORY to stringResource(R.string.sort_category), Sort.NAME to stringResource(R.string.sort_name)), sort, { sort = it }, Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
        // Tags in use on the ledger, most used first; tapping one shows only apps reviewers gave that tag (with its count).
        val tagsInUse = remember(scores) { scores.values.flatMap { it.tagCounts.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }.map { it.key to it.value } }
        if (tagsInUse.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.browse_tag_filter).uppercase(), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)   // the same label style as SORT
            TagChip(stringResource(R.string.browse_tag_any), selected = tagFilter == null) { tagFilter = null }
            tagsInUse.forEach { (t, n) -> TagChip(context.tagLabel(t.wire), count = n, selected = tagFilter == t) { tagFilter = if (tagFilter == t) null else t } }
        }
        } }
        when {
            rows.isEmpty() && query.isNotBlank() && signerRows.isEmpty() -> Column(Modifier.fillMaxSize()) {
                EmptyState(R.drawable.photo_empty_search, stringResource(R.string.empty_search_title), signerNote ?: stringResource(R.string.empty_search_body, query.trim()),
                    action = stringResource(R.string.search_signers), onAction = { searchSigners() }, modifier = Modifier.weight(1f))
                if (!refreshing) TextButton(onClick = { vm.refreshCatalogue() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.reread_catalogue), color = RbColors.purpleLight, fontSize = 13.sp) }
            }
            rows.isEmpty() && tab == Tab.SIGNED -> EmptyState(R.drawable.photo_empty_signed, stringResource(R.string.empty_signed))
            rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text(stringResource(if (catalogue.isEmpty()) R.string.loading else R.string.empty_installed), color = RbColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center) }
            else -> { val standings by vm.standings.collectAsState()
              // The A–Z rail on long lists (Toolkit port); the list leaves room for it on the right.
              val railKeys = remember(rows) { rows.map { r -> when (r) { is Row.App -> r.app.packageName; is Row.Head -> "head:" + r.category } } }
              val railNames = remember(rows) { rows.filterIsInstance<Row.App>().associate { it.app.packageName to it.app.name } }
              val showRail = railNames.size > 30
              Box(Modifier.fillMaxSize()) {
              LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp, end = if (showRail) 26.dp else 0.dp)) {
                // Signers whose name or address matches the query, above the dApps (design: Screens E "Search signers").
                if (signerRows.isNotEmpty()) {
                    item(key = "signers-head") { CategoryHead(stringResource(R.string.signers_heading), signerRows.size) }
                    items(signerRows, key = { "signer:" + it.first }) { (w, name) ->
                        val st = standings[w]
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().clickable { onProfile(w) }.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.undersigned.app.ui.components.LetterBadge(name, Modifier.size(36.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(stringResource(R.string.signer_row_sub, st?.countedReviews ?: 0), color = RbColors.textTertiary, fontSize = 11.sp) }
                        }
                        HorizontalDivider(color = RbColors.surfaceVariant, thickness = 1.dp)
                    }
                }
                if (tab == Tab.ALL && !refreshing) item(key = "catalogue-line") {
                    val catAt by vm.catalogueRefreshedAt.collectAsState()
                    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp)) {
                        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                            CatalogueFreshness(catAt, catalogue.size, Modifier.weight(1f))
                            TextButton(onClick = { vm.refreshCatalogue() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.catalogue_update), color = RbColors.purpleLight, fontSize = 12.sp) }
                        }
                        // "2 new · added 3 days ago": when, not just how many (owner, 7 Oct 2026).
                        if (newOnes.isNotEmpty()) {
                            val addedMs = remember(catAt) { com.undersigned.app.data.WeeklyCatalogue.newAddedMs(context) }
                            val days = if (addedMs > 0) java.time.temporal.ChronoUnit.DAYS.between(java.time.Instant.ofEpochMilli(addedMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate(), java.time.LocalDate.now()).toInt() else -1
                            val ago = when { days < 0 -> null; days == 0 -> stringResource(R.string.catalogue_ago_today); days == 1 -> stringResource(R.string.catalogue_ago_yesterday); else -> pluralStringResource(R.plurals.catalogue_ago_days, days, days) }
                            TagChip(if (ago != null) pluralStringResource(R.plurals.catalogue_new_added, newOnes.size, newOnes.size, ago) else pluralStringResource(R.plurals.catalogue_new_chip, newOnes.size, newOnes.size), selected = newOnly) { newOnly = !newOnly }
                        }
                    }
                }
                items(rows, key = { r -> when (r) { is Row.App -> r.app.packageName; is Row.Head -> "head:" + r.category } }) { r ->
                    when (r) {
                        is Row.Head -> CategoryHead(r.category, r.count)
                        is Row.App -> {
                            val app = r.app
                            val iconUrl = icons[app.packageName]?.takeIf { it.isNotEmpty() }
                            if (dense) DenseRow(app, scores[app.packageName]?.takeIf { it.countedReviews > 0 }, scores[app.packageName]?.countedReviews == 0, app.packageName in installed && tab != Tab.INSTALLED, iconUrl) { onOpen(app.packageName) }
                            else AppRow(app, scores[app.packageName]?.takeIf { it.countedReviews > 0 }, scores[app.packageName]?.countedReviews == 0, app.packageName in installed && tab != Tab.INSTALLED, sort != Sort.CATEGORY, iconUrl) { onOpen(app.packageName) }
                        }
                    }
                }
            }
              if (showRail) AlphaRail(railKeys, railNames, listState, isAlphaOrder = sort == Sort.NAME, onRequestAlphaOrder = { sort = Sort.NAME },
                  modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, bottom = 72.dp))   // high on the screen: Z was near the bottom edge
              } }
        }
    }
}

/**
 * "1,539 dApps · updated Mon 6 Oct, 10:13 · 3 days old" (the Toolkit's CatalogueFreshness): the age turns amber once a
 * Monday has been missed.
 */
@Composable
private fun CatalogueFreshness(refreshedAt: Long, total: Int, modifier: Modifier = Modifier) {
    val n = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(total)
    if (refreshedAt <= 0) { Text(stringResource(R.string.catalogue_line_bundled, n), color = RbColors.textTertiary, fontSize = 12.sp, modifier = modifier); return }
    val zone = java.time.ZoneId.systemDefault()
    val at = java.time.Instant.ofEpochMilli(refreshedAt).atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val days = java.time.temporal.ChronoUnit.DAYS.between(at.toLocalDate(), today)
    val age = when { days <= 0L -> stringResource(R.string.catalogue_age_today); days == 1L -> stringResource(R.string.catalogue_age_yesterday)
        days < 14L -> pluralStringResource(R.plurals.catalogue_age_days, days.toInt(), days.toInt()) else -> pluralStringResource(R.plurals.catalogue_age_weeks, (days / 7).toInt(), (days / 7).toInt()) }
    val stamp = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", java.util.Locale.getDefault()).format(at)
    val stale = com.undersigned.app.data.WeeklyCatalogue.isStale(at.toLocalDate(), today)
    Text(androidx.compose.ui.text.buildAnnotatedString {
        append(stringResource(R.string.catalogue_line, n, stamp)); append("  ·  ")
        pushStyle(androidx.compose.ui.text.SpanStyle(color = if (stale) RbColors.warning else RbColors.green, fontWeight = FontWeight.SemiBold)); append(age); pop()
    }, color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = modifier)
}

/** The dApp list's state with its Update button: last week's list, updating, done (+N new) or failed. */
@Composable
private fun CatalogueBar(running: Boolean, progress: String?, added: Int?, failed: Boolean, note: String?, refreshedAt: Long, total: Int, onUpdate: () -> Unit) {
    val count = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(total)
    val since = if (refreshedAt > 0) java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(refreshedAt)) else null
    GlassCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), accent = if (failed) RbColors.negative else RbColors.glassBorder) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                when {
                    running -> Text(stringResource(R.string.catalogue_updating, progress ?: ""), color = RbColors.purpleLight, fontSize = 13.sp)
                    failed -> Text(stringResource(R.string.catalogue_failed) + (note?.let { "\n$it" } ?: ""), color = RbColors.negative, fontSize = 13.sp, lineHeight = 18.sp)
                    note != null -> Text(note, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    added != null -> Text(stringResource(R.string.catalogue_done, count) + " · " + pluralStringResource(R.plurals.catalogue_new, added, added), color = RbColors.green, fontSize = 13.sp)
                    else -> {
                        Text(stringResource(R.string.catalogue_week_title), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (since != null) stringResource(R.string.catalogue_week_body, count, since) else stringResource(R.string.catalogue_week_bundled, count), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
            if (!running && added == null) { Spacer(Modifier.width(8.dp)); TextButton(onClick = onUpdate, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.catalogue_update), color = RbColors.purpleLight, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
            if (running) CircularProgressIndicator(Modifier.size(18.dp), color = RbColors.purpleLight, strokeWidth = 2.dp)
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppRow(app: CatalogueApp, score: AppScore?, uncounted: Boolean, showInstalled: Boolean, showCategory: Boolean, iconUrl: String?, onClick: () -> Unit) {
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
                if (showCategory) app.category?.let { Pill(it, modifier = Modifier.widthIn(max = 128.dp)) }   // the app's name keeps the room
                if (showInstalled) { if (showCategory && app.category != null) Spacer(Modifier.height(4.dp)); Pill(stringResource(R.string.tab_installed), RbColors.green) }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (score != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RatingStars(score.weightedMean)
                    Spacer(Modifier.width(6.dp))
                    Text(String.format(java.util.Locale.US, "%.1f", score.weightedMean), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.row_signed_count, score.countedReviews), color = RbColors.textSecondary, fontSize = 12.sp)
                }
                score.tagCounts.entries.sortedByDescending { it.value }.take(2).forEach { (t, n) -> TagChip(t.label(), n) }
            }
        } else Text(stringResource(if (uncounted) R.string.reviews_group_uncounted else R.string.row_no_reviews), color = RbColors.textTertiary, fontSize = 12.sp)
    }
    HorizontalDivider(color = RbColors.surfaceVariant, thickness = 1.dp)
}

/** Dense: one baseline, 32 dp icon, tags as dot-separated text, installed as a green dot, score in a fixed column. */
@Composable
private fun DenseRow(app: CatalogueApp, score: AppScore?, uncounted: Boolean, showInstalled: Boolean, iconUrl: String?, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { onClick() }.heightIn(min = 48.dp).padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        DAppIcon(app.packageName, app.name, iconUrl, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showInstalled) { val inst = stringResource(R.string.tab_installed); Box(Modifier.size(6.dp).clip(CircleShape).background(RbColors.green).semantics { contentDescription = inst }); Spacer(Modifier.width(6.dp)) }
                Text(app.name, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(score?.tagCounts?.entries?.sortedByDescending { it.value }?.take(3)?.joinToString(" · ") { context.tagLabel(it.key.wire) }?.ifEmpty { null } ?: stringResource(if (uncounted) R.string.reviews_group_uncounted else R.string.row_no_reviews),
                color = RbColors.textTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 96.dp)) {
            if (score != null) {
                RatingStars(score.weightedMean, size = 10)
                Text(String.format(java.util.Locale.US, "%.1f · %d", score.weightedMean, score.countedReviews), color = RbColors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            } else Text("—", color = RbColors.textDim, fontSize = 11.sp)
        }
    }
    HorizontalDivider(color = RbColors.surfaceVariant, thickness = 1.dp)
}
