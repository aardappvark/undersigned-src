package com.undersigned.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/**
 * Review analytics (owner, 12 Oct 2026): "filter between reviews on our dApp, organic, pro users etc and the actual
 * dAppstore itself … by category, by dApp, change over time". One screen, every source told apart and never mixed:
 * the dApp Store's own reviews come from the API's paced crawl (counts, means and shares only); the signed sources are
 * computed on this phone from the ledger it already holds, with the classes the protocol computes (organic = a counted
 * Seeker wallet, Pro at signing, promoted, quick, other wallet, ours). Offline, the store source falls back to the star
 * tallies read on this phone. Nothing here weighs a review.
 */
private enum class Source(val wire: String, val label: Int, val store: Boolean = false) {
    STORE("store", R.string.ra_src_store, true), ALL("all", R.string.ra_src_all), ORGANIC("organic", R.string.ra_src_organic), PRO("pro", R.string.ra_src_pro),
    PROMOTED("promoted", R.string.ra_src_promoted), QUICK("quick", R.string.ra_src_quick), OTHER("other", R.string.ra_src_other), OURS("ours", R.string.ra_src_ours)
}
private enum class Win(val wire: String, val days: Int?, val label: Int) { D30("30d", 30, R.string.tr_30d), D90("90d", 90, R.string.tr_90d), Y1("1y", 365, R.string.tr_1y), ALL("all", null, R.string.tr_all) }

private data class Cat(val name: String, val reviews: Int, val mean: Double, val change: Double?)
private data class AppRow(val pkg: String, val name: String, val category: String, val reviews: Int, val mean: Double)
private data class Pt(val bucket: String, val reviews: Int, val mean: Double)
private data class Agg(val reviews: Int, val apps: Int, val mean: Double, val stars: List<Int>, val replied: Double?, val short: Double?, val change: Double?,
                       val cats: List<Cat>, val top: List<AppRow>, val series: List<Pt>, val partial: Boolean = false)

private fun r2(v: Double) = Math.round(v * 100.0) / 100.0
private fun bucketOf(t: Long, monthly: Boolean): String {
    val d = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochSecond(t), java.time.ZoneOffset.UTC)
    return if (monthly) d.toString().take(7) else d.minusDays((d.dayOfWeek.value - 1).toLong()).toString()
}

/** Aggregates computed on the phone from rows of one source (signed, or the store tallies offline). */
private fun aggregate(rows: List<MainViewModel.RaRow>, win: Win, category: String?, catOf: Map<String, String>, nameOf: Map<String, String>, now: Long): Agg {
    val all = if (category == null) rows else rows.filter { (catOf[it.pkg] ?: "") == category }
    val from = win.days?.let { now - it * 86_400L } ?: 0L
    val l = all.filter { it.time >= from }
    fun mean(g: List<MainViewModel.RaRow>) = if (g.isEmpty()) 0.0 else r2(g.sumOf { it.rating }.toDouble() / g.size)
    fun change(g: List<MainViewModel.RaRow>): Double? { val cur = g.count { it.time >= now - 30 * 86_400L }; val prev = g.count { it.time >= now - 60 * 86_400L && it.time < now - 30 * 86_400L }; return if (prev == 0) null else r2(100.0 * (cur - prev) / prev) }
    val cats = l.groupBy { catOf[it.pkg] ?: "" }.entries.sortedByDescending { it.value.size }.map { (c, g) -> Cat(c, g.size, mean(g), change(all.filter { (catOf[it.pkg] ?: "") == c })) }
    val top = l.groupBy { it.pkg }.entries.sortedByDescending { it.value.size }.take(12).map { (p, g) -> AppRow(p, nameOf[p] ?: p, catOf[p] ?: "", g.size, mean(g)) }
    val monthly = win.days == null || win.days > 120
    val series = if (l.isEmpty()) emptyList() else {
        val first = maxOf(from, l.minOf { it.time }); val keys = LinkedHashSet<String>(); var t = first
        while (t <= now && keys.size < 400) { keys += bucketOf(t, monthly); t += if (monthly) 28 * 86_400L else 7 * 86_400L }; keys += bucketOf(now, monthly)
        val g = l.groupBy { bucketOf(it.time, monthly) }
        keys.map { k -> val x = g[k].orEmpty(); Pt(k, x.size, mean(x)) }
    }
    val timed = l.any { it.time > 0 }
    return Agg(l.size, l.map { it.pkg }.toSet().size, mean(l), (1..5).map { s -> l.count { it.rating == s } },
        if (timed) r2(100.0 * l.count { it.replied } / l.size.coerceAtLeast(1)) else null, if (timed) r2(100.0 * l.count { it.words <= 3 } / l.size.coerceAtLeast(1)) else null,
        if (timed) change(all) else null, cats, top, if (timed) series else emptyList(), partial = !timed)
}

/** The API's answer for the store source, in the same shape. */
private fun fromApi(o: JSONObject): Agg {
    fun d(x: Any?) = (x as? Number)?.toDouble()
    val cats = o.optJSONArray("categories")?.let { a -> (0 until a.length()).map { i -> val c = a.getJSONObject(i); Cat(c.optString("category"), c.optInt("reviews"), c.optDouble("mean", 0.0), d(c.opt("change"))) } }.orEmpty()
    val top = o.optJSONArray("top_apps")?.let { a -> (0 until a.length()).map { i -> val c = a.getJSONObject(i); AppRow(c.optString("package"), c.optString("name"), c.optString("category"), c.optInt("reviews"), c.optDouble("mean", 0.0)) } }.orEmpty()
    val series = o.optJSONArray("series")?.let { a -> (0 until a.length()).map { i -> val p = a.getJSONObject(i); Pt(p.optString("bucket"), p.optInt("reviews"), p.optDouble("mean", 0.0)) } }.orEmpty()
    val stars = o.optJSONArray("stars")?.let { a -> (0 until 5).map { a.optInt(it) } } ?: List(5) { 0 }
    return Agg(o.optInt("reviews"), o.optInt("apps"), o.optDouble("mean", 0.0), stars, d(o.opt("replied_share")), d(o.opt("short_share")), d(o.opt("change")), cats, top, series)
}

@Composable
fun ReviewAnalyticsScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val catalogue by vm.catalogue.collectAsState()
    val entries by vm.entries.collectAsState()
    val counts by vm.storeCounts.collectAsState()
    val api by vm.reviewAnalytics.collectAsState()
    val failed by vm.reviewAnalyticsFailed.collectAsState()
    var source by remember { mutableStateOf(Source.STORE) }
    var win by remember { mutableStateOf(Win.ALL) }
    var category by remember { mutableStateOf<String?>(null) }
    val catOf = remember(catalogue) { catalogue.associate { it.packageName to (it.category ?: "") } }
    val nameOf = remember(catalogue) { catalogue.associate { it.packageName to it.name } }
    val now = remember { System.currentTimeMillis() / 1000 }
    val signedRows = remember(entries) { vm.signedAnalyticsRows() }
    val key = "${source.wire}:${win.wire}"
    LaunchedEffect(key) { if (source.store) vm.loadReviewAnalytics(source.wire, win.wire) }
    // The store source comes from the API (the whole crawl); offline it falls back to the tallies this phone has read.
    val agg: Agg? = remember(source, win, category, signedRows, api, counts, catalogue) {
        if (source.store) {
            api[key]?.let { o -> val a = fromApi(o); if (category == null) a else {
                val rows = ArrayList<MainViewModel.RaRow>(); counts.forEach { (p, c) -> c.forEachIndexed { i, k -> repeat(k) { rows += MainViewModel.RaRow(p, i + 1, 0L, 0, false, setOf("store")) } } }
                aggregate(rows, Win.ALL, category, catOf, nameOf, now).copy(replied = a.replied, short = a.short) } }
                ?: if (key in failed || counts.isNotEmpty()) { val rows = ArrayList<MainViewModel.RaRow>(); counts.forEach { (p, c) -> c.forEachIndexed { i, k -> repeat(k) { rows += MainViewModel.RaRow(p, i + 1, 0L, 0, false, setOf("store")) } } }
                    if (rows.isEmpty()) null else aggregate(rows, Win.ALL, category, catOf, nameOf, now) } else null
        } else aggregate(signedRows.filter { source == Source.ALL || source.wire in it.cls }, win, category, catOf, nameOf, now)
    }
    val ctx = LocalContext.current
    val site = com.undersigned.app.AppConfig.Site.URL
    val qs = "source=${source.wire}&window=${win.wire}" + (category?.let { "&category=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
    fun open(url: String) = runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
    val accent = if (source.store) Color(0xFFFFB547) else RbColors.green
    val nf = NumberFormat.getIntegerInstance()

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Column {
                Text(stringResource(R.string.ra_title), color = RbColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(source.label) + " · " + stringResource(win.label) + (category?.let { " · $it" } ?: ""), color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Source.entries.forEach { s -> FilterChip(selected = source == s, onClick = { source = s }, label = { Text(stringResource(s.label), fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = if (s.store) Color(0xFF8A5A14) else RbColors.purple, selectedLabelColor = RbColors.textPrimary, labelColor = RbColors.textSecondary, containerColor = RbColors.surfaceVariant),
                border = null, modifier = Modifier.heightIn(min = 40.dp)) }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Win.entries.forEach { w -> FilterChip(selected = win == w, onClick = { win = w }, label = { Text(stringResource(w.label), fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RbColors.surfaceVariant, selectedLabelColor = RbColors.textPrimary, labelColor = RbColors.textTertiary, containerColor = RbColors.background),
                modifier = Modifier.heightIn(min = 36.dp)) }
            category?.let { c -> FilterChip(selected = true, onClick = { category = null }, label = { Text("✕ $c", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = RbColors.surfaceVariant, selectedLabelColor = RbColors.textPrimary), modifier = Modifier.heightIn(min = 36.dp)) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(if (source.store) R.string.ra_store_note else R.string.ra_signed_note), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(vertical = 6.dp))
            val a = agg
            if (a == null) {
                GlassCard { Text(stringResource(if (key in failed) R.string.ra_offline else R.string.ra_loading), color = RbColors.textSecondary, fontSize = 13.sp) }
            } else {
                if (a.partial) Text(stringResource(R.string.ra_partial), color = Color(0xFFFFB547), fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                if (a.reviews == 0) GlassCard { Text(stringResource(R.string.ra_empty), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) }
                // Totals
                GlassCard(accent = accent.copy(alpha = 0.35f)) {
                    Row(Modifier.fillMaxWidth()) {
                        Stat(stringResource(R.string.ra_reviews), nf.format(a.reviews), Modifier.weight(1f))
                        Stat(stringResource(R.string.ra_dapps), nf.format(a.apps), Modifier.weight(1f))
                        Stat(stringResource(R.string.ra_mean), String.format(Locale.US, "%.2f★", a.mean), Modifier.weight(1f), accent)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        val tot = a.stars.sum().coerceAtLeast(1)
                        Stat(stringResource(R.string.ra_five), "${100 * a.stars[4] / tot}%", Modifier.weight(1f))
                        Stat(stringResource(R.string.ra_short), a.short?.let { "${it.toInt()}%" } ?: "–", Modifier.weight(1f))
                        Stat(stringResource(R.string.ra_answered), a.replied?.let { String.format(Locale.US, "%.1f%%", it) } ?: "–", Modifier.weight(1f))
                    }
                    a.change?.let { ch -> Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.ra_change, (if (ch >= 0) "+" else "") + String.format(Locale.US, "%.0f%%", ch)), color = if (ch >= 0) RbColors.green else Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                }
                // Stars
                if (a.reviews > 0) GlassCard {
                    SectionTitle(stringResource(R.string.ra_stars))
                    StarColumns(a.stars, accent)
                }
                // By category
                if (category == null && a.cats.isNotEmpty()) GlassCard {
                    SectionTitle(stringResource(R.string.ra_by_category))
                    Text(stringResource(R.string.ra_tap_category), color = RbColors.textTertiary, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    val max = a.cats.maxOf { it.reviews }.coerceAtLeast(1)
                    a.cats.take(12).forEach { c -> BarRow(c.name.ifEmpty { stringResource(R.string.ra_uncategorised) }, c.reviews, max, c.mean, c.change, accent, nf) { category = c.name } }
                }
                // By dApp
                if (a.top.isNotEmpty()) GlassCard {
                    SectionTitle(stringResource(R.string.ra_by_dapp))
                    val max = a.top.maxOf { it.reviews }.coerceAtLeast(1)
                    a.top.forEach { r -> BarRow(r.name, r.reviews, max, r.mean, null, accent, nf) { onOpen(r.pkg) } }
                }
                // Over time
                if (a.series.size >= 2) GlassCard {
                    SectionTitle(stringResource(R.string.ra_over_time))
                    Text(stringResource(R.string.ra_over_time_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    ColumnsAndLine(a.series, accent)
                }
                // Data
                GlassCard {
                    SectionTitle(stringResource(R.string.ra_data))
                    Text(stringResource(R.string.ra_data_note), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(stringResource(R.string.ra_open_web), onClick = { open("$site/reviews?$qs") })
                        SecondaryButton("JSON", onClick = { open("$site/v1/reviews/analytics?$qs") })
                        SecondaryButton("CSV", onClick = { open("$site/v1/reviews/analytics.csv?$qs") })
                        if (source.store) SecondaryButton(stringResource(R.string.ra_raw), onClick = { open("$site/v1/reviews/store.csv") })
                        else SecondaryButton(stringResource(R.string.ra_raw), onClick = { open("$site/v1/records?type=rev") })
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.ra_mcp_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, color: Color = RbColors.textPrimary) {
    Column(modifier) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(label, color = RbColors.textTertiary, fontSize = 11.sp)
    }
}

private fun starColour(mean: Double) = when { mean >= 4.5 -> Color(0xFF14F195); mean >= 4.0 -> Color(0xFF9BE37C); mean >= 3.5 -> Color(0xFFFFC46B); else -> Color(0xFFFF6B6B) }

@Composable
private fun BarRow(label: String, value: Int, max: Int, mean: Double, change: Double?, accent: Color, nf: NumberFormat, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = RbColors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(nf.format(value), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.background(starColour(mean).copy(alpha = 0.18f), RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 1.dp)) {
                Text(String.format(Locale.US, "%.1f★", mean), color = starColour(mean), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            change?.let { ch -> Spacer(Modifier.width(6.dp)); Text((if (ch >= 0) "▲" else "▼") + String.format(Locale.US, "%.0f%%", kotlin.math.abs(ch)), color = if (ch >= 0) RbColors.green else Color(0xFFFF6B6B), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(RbColors.surfaceVariant)) {
            Box(Modifier.fillMaxWidth((value.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight().background(accent, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun StarColumns(stars: List<Int>, accent: Color) {
    val tot = stars.sum().coerceAtLeast(1); val max = stars.max().coerceAtLeast(1)
    val a11y = stars.mapIndexed { i, c -> "${i + 1} stars: $c" }.joinToString(", ")
    // Every column has the same three rows (share, a fixed-height bar area, the label) so the labels stay on one line.
    Row(Modifier.fillMaxWidth().semantics { contentDescription = a11y }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        stars.forEachIndexed { i, c ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${100 * c / tot}%", color = RbColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(84.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height((84 * c.toFloat() / max).coerceAtLeast(3f).dp).background(accent.copy(alpha = 0.35f + 0.13f * i), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                }
                Spacer(Modifier.height(3.dp))
                Text("${i + 1}★", color = RbColors.textTertiary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ColumnsAndLine(pts: List<Pt>, accent: Color) {
    val max = pts.maxOf { it.reviews }.coerceAtLeast(1)
    val nf = NumberFormat.getIntegerInstance()
    val a11y = pts.joinToString(", ") { "${it.bucket}: ${it.reviews} reviews, mean ${it.mean}" }
    val grid = RbColors.surfaceVariant; val line = RbColors.textPrimary
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.height(150.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(nf.format(max), color = RbColors.textTertiary, fontSize = 9.sp)
            Text("0", color = RbColors.textTertiary, fontSize = 9.sp)
        }
        Spacer(Modifier.width(6.dp))
        Canvas(Modifier.weight(1f).height(150.dp).semantics { contentDescription = a11y }) {
            val n = pts.size; val slot = size.width / n; val bw = (slot * 0.62f).coerceAtLeast(2f)
            for (g in 0..3) { val gy = size.height * g / 3; drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx()) }
            pts.forEachIndexed { i, p -> if (p.reviews > 0) { val h = size.height * p.reviews / max; drawRoundRect(accent.copy(alpha = 0.55f), Offset(slot * i + (slot - bw) / 2, size.height - h), androidx.compose.ui.geometry.Size(bw, h), androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())) } }
            val ys = { m: Double -> (size.height * (1 - (m - 1.0) / 4.0)).toFloat() }
            val xs = pts.withIndex().filter { it.value.reviews > 0 }.map { (i, p) -> Offset(slot * i + slot / 2, ys(p.mean)) }
            for (i in 1 until xs.size) drawLine(line, xs[i - 1], xs[i], 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            xs.forEach { drawCircle(line, 2.5.dp.toPx(), it) }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.height(150.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("5★", color = RbColors.textTertiary, fontSize = 9.sp)
            Text("1★", color = RbColors.textTertiary, fontSize = 9.sp)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 30.dp, top = 2.dp, end = 24.dp)) {
        Text(pts.first().bucket, color = RbColors.textTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Text(pts.last().bucket, color = RbColors.textTertiary, fontSize = 9.sp)
    }
}
