package com.undersigned.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.RatingSnapshots
import com.undersigned.protocol.ReviewBursts
import com.undersigned.protocol.ReviewMemo
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * The leaderboard (design H1–H3, 28 Sep 2026): dApps ranked by a chosen metric, overall or per store category, and a
 * drill-down per dApp. Revenue and paying users rank CONFIRMED treasuries only; unconfirmed amounts sit in a dashed box,
 * never ranked. Signed score ranks by the rank score (shrunk toward 3.0), minimum two signed reviews, so one 5★ cannot
 * top it. Downloads are not ranked: the store publishes no install counts.
 */
private enum class Metric(val label: Int) {
    REVENUE(R.string.lb_m_revenue), PAYERS(R.string.lb_m_payers), SIGNED_SCORE(R.string.lb_m_signed_score), SIGNED_REVIEWS(R.string.lb_m_signed_reviews),
    STORE_RATING(R.string.lb_m_store_rating), STORE_REVIEWS(R.string.lb_m_store_reviews), GROWTH(R.string.lb_m_growth)
}

private data class LbRow(val pkg: String, val name: String, val category: String, val value: String, val sub: String, val growth: Double? = null)

private fun usd(v: Double): String = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = 0 }.format(v)
private fun pct(v: Double): String = (if (v >= 0) "+" else "−") + String.format(Locale.getDefault(), "%.0f%%", kotlin.math.abs(v * 100))
private fun storeAvg(c: List<Int>): Pair<Double, Int>? { val n = c.sum(); return if (n == 0) null else c.mapIndexed { i, k -> (i + 1) * k }.sum().toDouble() / n to n }

/** This calendar month's USD against last month's, from a listing's monthly totals; null without both months. */
private fun growthOf(row: MainViewModel.BoardRow): Double? {
    val m = row.monthUsd; if (m.size < 2) return null
    val now = java.time.YearMonth.now(java.time.ZoneOffset.UTC)
    val cur = m[now.toString()] ?: 0.0; val prev = m[now.minusMonths(1).toString()] ?: return null
    return if (prev <= 0) null else (cur - prev) / prev
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LeaderboardScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val catalogue by vm.catalogue.collectAsState()
    val boardRows by vm.boardRows.collectAsState()
    val scores by vm.scores.collectAsState()
    val counts by vm.storeCounts.collectAsState()
    val countsRead by vm.storeCountsRead.collectAsState()
    val countsProgress by vm.storeCountsProgress.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    LaunchedEffect(Unit) { vm.loadBoard() }
    // Revenue first when any treasury is confirmed; otherwise a metric that has data today (store rating).
    var metric by remember { mutableStateOf<Metric?>(null) }
    LaunchedEffect(boardRows) { if (metric == null && boardRows.isNotEmpty()) metric = if (boardRows.any { it.confirmed && it.tally != null }) Metric.REVENUE else Metric.STORE_RATING }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(4000); if (metric == null) metric = Metric.STORE_RATING }
    var tab by remember { mutableStateOf<String?>(null) }   // null = Overall
    var drill by remember { mutableStateOf<Pair<String, String>?>(null) }   // package, "#1 Overall · Store rating"
    val cats: List<String> = remember(catalogue) { catalogue.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
    val catOf = remember(catalogue) { catalogue.associate { it.packageName to (it.category ?: "") } }
    val nameOf = remember(catalogue) { catalogue.associate { it.packageName to it.name } }
    val inTab = remember(catalogue, tab) { catalogue.filter { tab == null || it.category == tab }.map { it.packageName }.toSet() }
    val m = metric ?: Metric.STORE_RATING
    val needsStore = m == Metric.STORE_RATING || m == Metric.STORE_REVIEWS
    LaunchedEffect(needsStore, inTab) { if (needsStore) vm.loadStoreCounts(inTab) }

    drill?.let { (pkg, rank) -> DrillDown(vm, pkg, nameOf[pkg] ?: pkg, rank = rank, onBack = { drill = null }, onOpen = onOpen); androidx.activity.compose.BackHandler { drill = null }; return }

    val confirmed = boardRows.filter { it.confirmed && it.tally != null && it.claim.packageName in inTab }
    // Store rating ranks by a shrunk average (toward the store-wide mean, strength 20) so seven 5★ cannot top 7,000
    // reviews at 4.8; the average itself is what is shown.
    val storeMean = remember(counts) { val all = counts.values.fold(IntArray(5)) { a, c -> c.forEachIndexed { i, k -> a[i] += k }; a }.toList(); storeAvg(all)?.first ?: 4.0 }
    val rows: List<LbRow> = remember(m, tab, boardRows, scores, counts) {
        when (m) {
            Metric.REVENUE -> confirmed.sortedByDescending { it.usd ?: 0.0 }.map { LbRow(it.claim.packageName, it.name, catOf[it.claim.packageName] ?: "", it.usd?.let(::usd) ?: "—", "payers:${it.tally!!.payers}", growthOf(it)) }
            Metric.PAYERS -> confirmed.sortedByDescending { it.tally!!.payers }.map { LbRow(it.claim.packageName, it.name, catOf[it.claim.packageName] ?: "", "${it.tally!!.payers}", "usd:" + (it.usd?.let(::usd) ?: "—")) }
            Metric.GROWTH -> confirmed.mapNotNull { r -> growthOf(r)?.let { g -> r to g } }.sortedByDescending { it.second }.map { (r, g) -> LbRow(r.claim.packageName, r.name, catOf[r.claim.packageName] ?: "", pct(g), "usd:" + (r.usd?.let(::usd) ?: "—"), g) }
            Metric.SIGNED_SCORE -> scores.values.filter { it.packageName in inTab && it.countedReviews >= 2 }.sortedByDescending { it.weightedRating }
                .map { LbRow(it.packageName, nameOf[it.packageName] ?: it.packageName, catOf[it.packageName] ?: "", String.format(Locale.getDefault(), "%.2f", it.weightedRating), "signed:${it.countedReviews}:${it.weightedMean}") }
            Metric.SIGNED_REVIEWS -> scores.values.filter { it.packageName in inTab && it.countedReviews > 0 }.sortedByDescending { it.countedReviews }
                .map { LbRow(it.packageName, nameOf[it.packageName] ?: it.packageName, catOf[it.packageName] ?: "", "${it.countedReviews}", "mean:${it.weightedMean}") }
            Metric.STORE_RATING -> counts.filterKeys { it in inTab }.mapNotNull { (p, c) -> storeAvg(c)?.takeIf { it.second >= 5 }?.let { p to it } }
                .sortedWith(compareByDescending<Pair<String, Pair<Double, Int>>> { (storeMean * 20 + it.second.first * it.second.second) / (20 + it.second.second) }.thenByDescending { it.second.second })
                .map { (p, a) -> LbRow(p, nameOf[p] ?: p, catOf[p] ?: "", String.format(Locale.getDefault(), "%.1f", a.first), "reviews:${a.second}") }
            Metric.STORE_REVIEWS -> counts.filterKeys { it in inTab }.mapNotNull { (p, c) -> storeAvg(c)?.let { p to it } }.sortedByDescending { it.second.second }
                .map { (p, a) -> LbRow(p, nameOf[p] ?: p, catOf[p] ?: "", NumberFormat.getIntegerInstance().format(a.second), "avg:${a.first}") }
        }.take(100)
    }
    LaunchedEffect(rows) { vm.resolveIcons(rows.take(30).map { it.pkg }) }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            Column {
                Text(stringResource(R.string.lb_title), color = RbColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text((tab ?: stringResource(R.string.lb_overall)) + " · " + pluralStringResource(R.plurals.lb_dapps, inTab.size, inTab.size), color = RbColors.textTertiary, fontSize = 11.sp)
            }
        }
        // Metric chips, then category tabs — both scroll sideways.
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Metric.entries.forEach { mm -> FilterChip(selected = m == mm, onClick = { metric = mm }, label = { Text(stringResource(mm.label), fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RbColors.purple, selectedLabelColor = RbColors.textPrimary, labelColor = RbColors.textSecondary, containerColor = RbColors.surfaceVariant),
                border = null, modifier = Modifier.heightIn(min = 40.dp)) }
        }
        ScrollableTabRow(selectedTabIndex = if (tab == null) 0 else cats.indexOf(tab) + 1, edgePadding = 16.dp, containerColor = RbColors.background, contentColor = RbColors.textPrimary, divider = {}) {
            Tab(selected = tab == null, onClick = { tab = null }, text = { Text(stringResource(R.string.lb_overall), fontSize = 13.sp) }, selectedContentColor = RbColors.textPrimary, unselectedContentColor = RbColors.textTertiary)
            cats.forEach { c -> Tab(selected = tab == c, onClick = { tab = c }, text = { Text(c, fontSize = 13.sp, maxLines = 1) }, selectedContentColor = RbColors.textPrimary, unselectedContentColor = RbColors.textTertiary) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            val note = when (m) {
                Metric.REVENUE, Metric.PAYERS -> stringResource(R.string.lb_note_revenue)
                Metric.GROWTH -> stringResource(R.string.lb_note_growth)
                Metric.SIGNED_SCORE -> stringResource(R.string.lb_note_score)
                Metric.SIGNED_REVIEWS -> stringResource(R.string.lb_note_signed)
                Metric.STORE_RATING, Metric.STORE_REVIEWS -> countsRead?.let { stringResource(R.string.lb_note_store, android.text.format.DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()) } ?: stringResource(R.string.lb_note_store_unread)
            }
            Text(note, color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
            if (needsStore) countsProgress?.let { Text(stringResource(R.string.lb_store_reading, it), color = RbColors.purpleLight, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            if (rows.isEmpty()) Text(stringResource(R.string.lb_empty), color = RbColors.textSecondary, fontSize = 13.sp)
            else GlassCard {
                rows.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(color = RbColors.surfaceVariant)
                    val rankLine = "#${i + 1} " + (tab ?: stringResource(R.string.lb_overall)) + " · " + stringResource(m.label)
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { drill = r.pkg to rankLine }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = RbColors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(30.dp))
                        DAppIcon(r.pkg, r.name, icons[r.pkg]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(40.dp)); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subLine(r), color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(r.value, color = if (m == Metric.GROWTH) growthColor(r.growth) else RbColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            if (m == Metric.REVENUE) r.growth?.let { Text(pct(it), color = growthColor(it), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            if (m == Metric.SIGNED_SCORE) RatingStars(r.sub.split(":").getOrNull(2)?.toDoubleOrNull() ?: 0.0, size = 10)
                        }
                    }
                }
            }
            // Beneath, never ranked: unconfirmed revenue; apps too thin to rank; downloads.
            if (m == Metric.REVENUE || m == Metric.PAYERS) {
                val waiting = boardRows.filter { !it.confirmed && it.claim.packageName in inTab }
                if (waiting.isNotEmpty()) Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).dashed().padding(14.dp)) {
                    Text(stringResource(R.string.lb_unconfirmed_title), color = RbColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    waiting.forEach { w -> Text(w.name + (w.usd?.let { " · " + usd(it) } ?: ""), color = RbColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                    Text(stringResource(R.string.lb_unconfirmed_body), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (m == Metric.SIGNED_SCORE) {
                val thin = inTab.count { p -> (scores[p]?.countedReviews ?: 0) < 2 }
                if (thin > 0) Text(pluralStringResource(R.plurals.lb_thin, thin, thin), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp))
            }
            Column(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RbColors.surface).padding(14.dp)) {
                Text(stringResource(R.string.lb_downloads_title), color = RbColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.lb_downloads_body), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun subLine(r: LbRow): String {
    val p = r.sub.split(":")
    return when (p[0]) {
        "payers" -> r.category + " · " + stringResource(R.string.pay_label_payers) + " " + p[1]
        "usd" -> r.category + " · " + p.drop(1).joinToString(":")
        "signed" -> stringResource(R.string.lb_signed_n, p[1].toInt())
        "mean" -> r.category + " · " + String.format(Locale.getDefault(), "%.1f", p[1].toDouble()) + "★"
        "reviews" -> pluralStringResource(R.plurals.store_total, p[1].toInt(), p[1].toInt())
        "avg" -> r.category + " · " + String.format(Locale.getDefault(), "%.1f", p[1].toDouble()) + "★"
        else -> r.category
    }
}

private fun growthColor(g: Double?) = when { g == null -> RbColors.textSecondary; g >= 0 -> RbColors.green; else -> RbColors.negative }

private fun Modifier.dashed() = this.drawBehind {
    drawRoundRect(RbColors.textDim, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

// ---- Drill-down (H3) and trends ---------------------------------------------------------------------------------

private enum class Range(val days: Int?, val label: Int) { D30(30, R.string.tr_30d), D90(90, R.string.tr_90d), Y1(365, R.string.tr_1y), ALL(null, R.string.tr_all) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrillDown(vm: MainViewModel, pkg: String, name: String, rank: String?, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val icons by vm.iconCache.state.collectAsState()
    val scores by vm.scores.collectAsState()
    val entries by vm.entries.collectAsState()
    val counts by vm.storeCounts.collectAsState()
    val boardRows by vm.boardRows.collectAsState()
    val verified by vm.verifiedPublishers.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val row = boardRows.firstOrNull { it.claim.packageName == pkg && it.confirmed && it.tally != null }
    val score = scores[pkg]
    val store = counts[pkg]?.let(::storeAvg)
    val snaps by produceState(emptyList<RatingSnapshots.Snapshot>(), pkg) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.ratingSnapshots(pkg) } }
    val changes = remember(pkg) { vm.changes.value.filter { it.packageName == pkg && !it.firstSeen }.sortedByDescending { it.at } }
    val updated = catalogue.firstOrNull { it.packageName == pkg }?.updated?.let { u -> runCatching { df.format(Date(java.time.LocalDate.parse(u).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli())) }.getOrDefault(u) }
    val ledgerReviews = remember(entries, pkg) { vm.reviewsFor(pkg).map { it.first } }
    var range by remember { mutableStateOf(Range.D90) }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            DAppIcon(pkg, name, icons[pkg]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(40.dp)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(rank ?: pkg, color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            GlassCard {
                KeyValueGrid(listOf(stringResource(R.string.dd_revenue) to (row?.usd?.let(::usd) ?: "—"),
                    stringResource(R.string.dd_signed) to (score?.takeIf { it.countedReviews > 0 }?.let { String.format(Locale.getDefault(), "%.1f", it.weightedMean) } ?: "—"),
                    stringResource(R.string.dd_store) to (store?.let { String.format(Locale.getDefault(), "%.1f", it.first) } ?: "—")), columns = 3, valueSize = 20)
            }
            // Updates: what THIS phone saw of each release (the store publishes only its latest).
            SectionTitle(stringResource(R.string.dd_updates))
            GlassCard {
                if (changes.isEmpty()) Text(stringResource(R.string.dd_updates_none, updated ?: "—"), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                changes.take(6).forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = RbColors.surfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    Row { Text("v${c.versionCode}", color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(df.format(Date(c.at)), color = RbColors.textTertiary, fontSize = 12.sp) }
                    Text(stringResource(if (c.signerChanged) R.string.dd_cert_changed else R.string.dd_same_cert), color = if (c.signerChanged) RbColors.negative else RbColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val since = ledgerReviews.count { it.blockTime * 1000 >= c.at }
                    val perm = when { c.permsAdded.isNotEmpty() -> stringResource(R.string.dd_perm_added, c.permsAdded.joinToString(", ")); c.permsRemoved.isNotEmpty() -> stringResource(R.string.dd_perm_removed, c.permsRemoved.joinToString(", ")); else -> stringResource(R.string.dd_perms_same) }
                    Text(perm + " · " + stringResource(R.string.dd_signed_since, since), color = if (c.permsAdded.isNotEmpty()) RbColors.warning else RbColors.textTertiary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            // Worth knowing.
            SectionTitle(stringResource(R.string.dd_worth))
            GlassCard {
                val resp = vm.responsiveness(pkg)
                if (!verified[pkg].isNullOrEmpty()) Text(stringResource(R.string.publisher_badge) + " · " + (if (resp.answered == 0) stringResource(R.string.responsiveness_never) else stringResource(R.string.responsiveness_line, resp.bucket, resp.answered, resp.total)), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                val bursts = vm.ledgerBursts(pkg).bursts + ReviewBursts.find(RatingSnapshots.toPoints(snaps), ReviewBursts.MIN_SNAPSHOT_DAYS).bursts
                val firstSeen = listOfNotNull(ledgerReviews.minOfOrNull { it.blockTime * 1000 }, snaps.firstOrNull()?.time?.times(1000)).minOrNull()
                    ?.takeIf { System.currentTimeMillis() - it >= ReviewBursts.MIN_HISTORY_DAYS * ReviewBursts.DAY * 1000 }   // "since today" says nothing
                Text(if (bursts.isEmpty()) firstSeen?.let { stringResource(R.string.dd_no_bursts, df.format(Date(it))) } ?: stringResource(R.string.tr_no_data)
                     else pluralStringResource(R.plurals.dd_bursts, bursts.size, bursts.size), color = if (bursts.isEmpty()) RbColors.textSecondary else RbColors.warning, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Text(stringResource(if (row != null) R.string.dd_treasury_confirmed else R.string.dd_treasury_none), color = RbColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                updated?.let { Text(stringResource(R.string.dd_last_update, it), color = RbColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
            // Trends.
            SectionTitle(stringResource(R.string.tr_title))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(stringResource(r.label)) }, border = null,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RbColors.purple, selectedLabelColor = RbColors.textPrimary, labelColor = RbColors.textSecondary, containerColor = RbColors.surfaceVariant)) }
            }
            val now = System.currentTimeMillis() / 1000
            val from = range.days?.let { now - it * 86_400L } ?: Long.MIN_VALUE
            Spacer(Modifier.height(10.dp))
            RevenueTrend(row, from)
            Spacer(Modifier.height(10.dp))
            ScoreTrend(vm, pkg, ledgerReviews, snaps, from)
            Spacer(Modifier.height(10.dp))
            WeeklyReviews(vm, pkg, ledgerReviews, from)
            Spacer(Modifier.height(10.dp))
            PayersTrend(row, from)
            Spacer(Modifier.height(24.dp))
        }
        Box(Modifier.padding(20.dp)) { PrimaryButton(stringResource(R.string.dd_open), onClick = { onOpen(pkg) }) }
    }
}

@Composable
private fun TrendCard(title: String, summary: String, trailing: (@Composable () -> Unit)? = null, chart: @Composable () -> Unit) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(title, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); trailing?.invoke() }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.semantics(mergeDescendants = true) { contentDescription = summary }) { chart() }
        Text(summary, color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

private fun gridlines(scope: androidx.compose.ui.graphics.drawscope.DrawScope, rows: Int = 3) = with(scope) {
    for (i in 0..rows) { val y = size.height * i / rows; drawLine(RbColors.surfaceVariant, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx()) }
}

/** Revenue: cumulative USD over the range as an area — purpleLight line over a purple fade; current total large; change vs the start. */
@Composable
private fun RevenueTrend(row: MainViewModel.BoardRow?, from: Long) {
    val pts = row?.paid?.filter { it.first.time >= from }?.sortedBy { it.first.time }.orEmpty()
    if (pts.isEmpty()) { TrendCard(stringResource(R.string.tr_revenue), stringResource(R.string.tr_no_data)) {}; return }
    val total = pts.sumOf { it.second }
    val before = row!!.paid.filter { it.first.time < from }.sumOf { it.second }
    val change = if (before > 0) total / before else null
    TrendCard(stringResource(R.string.tr_revenue), stringResource(R.string.tr_sum_revenue, usd(total), pts.size),
        trailing = { change?.let { Text(pct(it), color = growthColor(it), fontSize = 12.sp, fontWeight = FontWeight.Bold) } }) {
        Column {
            Text(usd(total), color = RbColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                gridlines(this)
                val t0 = pts.first().first.time.toFloat(); val t1 = maxOf(pts.last().first.time.toFloat(), t0 + 1)
                var acc = 0.0
                val xy = pts.map { (p, u) -> acc += u; Offset((p.time - t0) / (t1 - t0) * size.width, (size.height - (acc / total * (size.height - 4)).toFloat())) }
                val line = Path().apply { moveTo(0f, size.height); xy.forEach { lineTo(it.x, it.y) }; lineTo(size.width, xy.last().y) }
                val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(area, Brush.verticalGradient(listOf(RbColors.purple.copy(alpha = 0.45f), Color.Transparent)))
                drawPath(line, RbColors.purpleLight, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
    }
}

/** Score over time on a 3.0–5.0 axis: signed (green, solid, recomputed at each review) and store (dashed, from snapshots). */
@Composable
private fun ScoreTrend(vm: MainViewModel, pkg: String, reviews: List<com.undersigned.protocol.LedgerEntry>, snaps: List<RatingSnapshots.Snapshot>, from: Long) {
    val signed = remember(reviews) {
        val sorted = reviews.sortedBy { it.blockTime }; val acc = ArrayList<Pair<Long, Double>>(); var sum = 0.0
        sorted.forEachIndexed { i, e -> sum += (e.memo as ReviewMemo.Review).rating; acc.add(e.blockTime to sum / (i + 1)) }; acc
    }.filter { it.first >= from }
    val store = snaps.mapNotNull { s -> storeAvg(s.byStar)?.let { s.time to it.first } }.filter { it.first >= from }
    if (signed.size < 2 && store.size < 2) { TrendCard(stringResource(R.string.tr_score), stringResource(R.string.tr_no_data)) {}; return }
    val sNow = signed.lastOrNull()?.second; val stNow = store.lastOrNull()?.second
    val gapStart = if (signed.isNotEmpty() && store.isNotEmpty()) kotlin.math.abs(signed.first().second - store.first().second) else null
    val gapNow = if (sNow != null && stNow != null) kotlin.math.abs(sNow - stNow) else null
    val gapWord = if (gapStart != null && gapNow != null) stringResource(if (gapNow > gapStart + 0.05) R.string.tr_gap_wider else if (gapNow < gapStart - 0.05) R.string.tr_gap_narrower else R.string.tr_gap_same) else ""
    val fmt = { v: Double? -> v?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—" }
    TrendCard(stringResource(R.string.tr_score), stringResource(R.string.tr_sum_score, fmt(sNow), fmt(stNow)) + (if (gapWord.isNotEmpty()) " · $gapWord" else ""),
        trailing = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(stringResource(R.string.tr_signed), color = RbColors.green, fontSize = 11.sp); Text(stringResource(R.string.tr_store), color = RbColors.textTertiary, fontSize = 11.sp) } }) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            gridlines(this, 2)
            val all = signed.map { it.first } + store.map { it.first }
            val t0 = all.min().toFloat(); val t1 = maxOf(all.max().toFloat(), t0 + 1)
            fun y(v: Double) = size.height - ((v.coerceIn(3.0, 5.0) - 3.0) / 2.0 * size.height).toFloat()
            fun path(p: List<Pair<Long, Double>>) = Path().apply { p.forEachIndexed { i, (t, v) -> val x = (t - t0) / (t1 - t0) * size.width; if (i == 0) moveTo(x, y(v)) else lineTo(x, y(v)) }; p.lastOrNull()?.let { lineTo(size.width, y(it.second)) } }
            if (store.isNotEmpty()) drawPath(path(store), RbColors.textTertiary, style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
            if (signed.isNotEmpty()) drawPath(path(signed), RbColors.green, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

/** Signed reviews per week: bars in purpleDim; weeks inside a burst hatched amber with its multiple. */
@Composable
private fun WeeklyReviews(vm: MainViewModel, pkg: String, reviews: List<com.undersigned.protocol.LedgerEntry>, from: Long) {
    val inRange = reviews.filter { it.blockTime >= from }
    if (inRange.isEmpty()) { TrendCard(stringResource(R.string.tr_reviews_week), stringResource(R.string.tr_no_data)) {}; return }
    val week = 7 * 86_400L
    val w0 = inRange.minOf { it.blockTime } / week; val w1 = System.currentTimeMillis() / 1000 / week
    val n = (w1 - w0 + 1).toInt().coerceIn(1, 60)
    val bars = IntArray(n); inRange.forEach { e -> val i = (e.blockTime / week - w0).toInt(); if (i in 0 until n) bars[i]++ }
    val bursts = vm.ledgerBursts(pkg).bursts.filter { it.end >= from }
    val hot = bursts.flatMap { b -> ((b.start / week - w0).toInt()..(b.end / week - w0).toInt()).toList() }.toSet()
    TrendCard(stringResource(R.string.tr_reviews_week), pluralStringResource(R.plurals.tr_sum_reviews, inRange.size, inRange.size) + " · " + (if (bursts.isEmpty()) stringResource(R.string.tr_no_bursts) else pluralStringResource(R.plurals.dd_bursts, bursts.size, bursts.size))) {
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            gridlines(this, 2)
            val max = maxOf(bars.max(), 1); val slot = size.width / n; val bw = slot * 0.7f
            bars.forEachIndexed { i, v ->
                val h = v.toFloat() / max * (size.height - 2); val x = i * slot + (slot - bw) / 2
                drawRect(if (i in hot) RbColors.warning.copy(alpha = 0.3f) else RbColors.purpleDim, Offset(x, size.height - h), androidx.compose.ui.geometry.Size(bw, h))
                if (i in hot) clipRect(x, size.height - h, x + bw, size.height) {
                    var hx = x - h; while (hx < x + bw) { drawLine(RbColors.warning, Offset(hx, size.height), Offset(hx + h, size.height - h), strokeWidth = 1.dp.toPx()); hx += 4.dp.toPx() } }
            }
        }
    }
}

/** Paying users: a step line of distinct payers over time — confirmed treasuries only. */
@Composable
private fun PayersTrend(row: MainViewModel.BoardRow?, from: Long) {
    val pts = row?.paid?.map { it.first }?.sortedBy { it.time }.orEmpty()
    if (pts.isEmpty()) { TrendCard(stringResource(R.string.tr_payers), stringResource(R.string.tr_no_data)) {}; return }
    val seen = HashSet<String>(); val steps = pts.map { p -> seen.add(p.payer); p.time to seen.size }.filter { it.first >= from }
    val inRange = pts.filter { it.time >= from }.map { it.payer }.toSet().size
    TrendCard(stringResource(R.string.tr_payers), pluralStringResource(R.plurals.tr_sum_payers, inRange, inRange)) {
        Canvas(Modifier.fillMaxWidth().height(80.dp)) {
            gridlines(this, 2)
            if (steps.isEmpty()) return@Canvas
            val t0 = steps.first().first.toFloat(); val t1 = maxOf(steps.last().first.toFloat(), t0 + 1); val max = steps.last().second.toFloat()
            val p = Path(); var lastY = size.height
            steps.forEachIndexed { i, (t, v) -> val x = (t - t0) / (t1 - t0) * size.width; val y = size.height - v / max * (size.height - 4)
                if (i == 0) p.moveTo(x, y) else { p.lineTo(x, lastY); p.lineTo(x, y) }; lastY = y }
            p.lineTo(size.width, lastY)
            drawPath(p, RbColors.purpleLight, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
