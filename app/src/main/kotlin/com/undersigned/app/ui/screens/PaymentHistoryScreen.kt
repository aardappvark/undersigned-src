package com.undersigned.app.ui.screens

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ledger.PaymentAnalytics
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import java.util.Locale

/**
 * One app's payments, dug into from the Payments tab (owner, 10 Oct 2026): where it is paid, USD per week over a chosen window,
 * the window's totals, a monthly table and, for a wallet found in the app's code, how it is paid and who comes back. Pushed over
 * the tabs (no bar); back returns to the board. Every figure is gross receipts in USD on the day paid, never revenue.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentHistoryScreen(vm: MainViewModel, pkg: String, onBack: () -> Unit, onOpenApp: () -> Unit) {
    val cache by vm.payHistories.collectAsState()
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(pkg, attempt) { vm.paymentHistory(pkg).fold({ failed = false }, { failed = true }) }
    val h = cache[pkg]
    val catalogue by vm.catalogue.collectAsState()
    val name = h?.week?.name ?: h?.month?.name ?: catalogue.firstOrNull { it.packageName == pkg }?.name ?: pkg
    var window by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("90d") }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Column(Modifier.weight(1f)) {
                Text(name, color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.ph_title), color = RbColors.textTertiary, fontSize = 12.sp)
            }
        }
        com.undersigned.app.ui.components.OfflineBanner(vm.offline.collectAsState().value)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.ph_open_app), color = RbColors.purpleLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onOpenApp).padding(vertical = 14.dp))
            if (h == null) {
                if (failed) {
                    Text(stringResource(R.string.pa_offline), color = RbColors.textSecondary, fontSize = 13.sp)
                    TextButton(onClick = { failed = false; attempt++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry), color = RbColors.purpleLight, fontSize = 13.sp) }
                } else Text(stringResource(R.string.pa_loading), color = RbColors.purpleLight, fontSize = 13.sp)
                return@Column
            }
            val week = h.week
            if (week == null || (week.series.isEmpty() && week.sources.isEmpty())) {
                Text(stringResource(R.string.ph_none), color = RbColors.textSecondary, fontSize = 13.sp)
                return@Column
            }
            val anyFound = week.sources.any { !it.confirmed }

            // Where the payments go.
            GlassCard {
                Text(stringResource(R.string.ph_sources), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                week.sources.forEachIndexed { i, src ->
                    if (i > 0) HorizontalDivider(color = RbColors.surfaceVariant)
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(shortKey(src.wallet, 4, 4), color = RbColors.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            val c = if (src.confirmed) RbColors.green else RbColors.warning
                            Text(stringResource(if (src.confirmed) R.string.pa_legend_confirmed else R.string.ph_pill_found), color = c, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                        Text(nPayments(src.payments) + " · " + payUsd(src.usd), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                if (anyFound) Text(stringResource(R.string.ph_found_caveat), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(12.dp))

            // The window, then USD per week inside it and the window's totals.
            val labels = mapOf("7d" to R.string.pa_window_7d, "30d" to R.string.pa_window_30d, "90d" to R.string.pa_window_90d, "1y" to R.string.pa_window_1y, "all" to R.string.pa_window_all)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PaymentAnalytics.WINDOWS.forEach { w -> FilterChip(selected = window == w, onClick = { window = w }, label = { Text(stringResource(labels.getValue(w)), fontSize = 12.sp) }) }
            }
            val weeks = remember(week, window) { PaymentAnalytics.weeksIn(week.series, window, java.time.LocalDate.now(java.time.ZoneOffset.UTC)) }
            GlassCard {
                Text(stringResource(R.string.pa_app_weekly), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                if (weeks.isEmpty() || weeks.all { it.usd <= 0.0 }) Text(stringResource(R.string.pa_none), color = RbColors.textTertiary, fontSize = 12.sp)
                else WeeklyArea(weeks, if (anyFound) RbColors.warning else RbColors.green)
                Spacer(Modifier.height(10.dp))
                val all = window == "all"
                // The server's own totals for the window (1.1.6): DISTINCT payers, counted from the payments, not from the weeks.
                // Only a server older than 11 Oct 2026 lacks them; then the weekly counts are added and labelled so.
                val t = week.windows[window]
                val items = if (t != null) listOf(
                    stringResource(R.string.pa_received) to payUsd(t.usd),
                    stringResource(R.string.pa_payments) to payCount(t.payments),
                    stringResource(R.string.pay_label_payers) to payCount(t.payers),
                ) else listOf(
                    stringResource(R.string.pa_received) to payUsd(if (all) week.totalUsd else weeks.sumOf { it.usd }),
                    stringResource(R.string.pa_payments) to payCount(if (all) week.payments else weeks.sumOf { it.payments }),
                    stringResource(if (all) R.string.pay_label_payers else R.string.ph_payers_summed) to payCount(if (all) week.payers else weeks.sumOf { it.payers }),
                )
                KeyValueGrid(items, columns = 3, description = items.joinToString(", ") { "${it.first} ${it.second}" }, valueSize = 15)
                Text(stringResource(R.string.ph_usd_label), color = RbColors.textTertiary, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(12.dp))

            // Month by month, newest first.
            h.month?.series?.takeIf { it.isNotEmpty() }?.let { months ->
                GlassCard {
                    Text(stringResource(R.string.ph_monthly), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    TableRow(listOf(stringResource(R.string.ph_month), "USD", stringResource(R.string.pa_payments), stringResource(R.string.pay_label_payers)), header = true)
                    months.asReversed().forEach { m ->
                        HorizontalDivider(color = RbColors.surfaceVariant)
                        TableRow(listOf(payBucketLabel(m.bucket), payUsd(m.usd), payCount(m.payments), payCount(m.payers)))
                    }
                    Text(stringResource(R.string.ph_usd_label), color = RbColors.textTertiary, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
            }

            // A found wallet's own reading: the rails it is paid on, and who pays again.
            h.found?.forEach { f ->
                GlassCard(accent = RbColors.warning.copy(alpha = 0.5f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pa_how_title), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(shortKey(f.wallet, 4, 4), color = RbColors.textTertiary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Text(stringResource(R.string.pay_totals, amount(f.sol), amount(f.skr), amount(f.usdc)), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    val stats = listOf(
                        stringResource(R.string.pa_repeat_share) to stringResource(R.string.ph_of, payCount(f.repeatPayers), payCount(f.payers)),
                        stringResource(R.string.ph_top_payer) to pct(f.topPayerShare),
                        stringResource(R.string.ph_top10_payers) to pct(f.top10Share),
                        stringResource(R.string.pa_median) to payUsd(f.medianUsd),
                    )
                    KeyValueGrid(stats, columns = 2, description = stats.joinToString(", ") { "${it.first} ${it.second}" }, valueSize = 14)
                    if (!f.complete && f.txListed > 0) Text(stringResource(R.string.pa_coverage, payCount(f.txRead), payCount(f.txListed)), color = RbColors.warning, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
                    if (f.monthly.isNotEmpty()) {
                        Text(stringResource(R.string.ph_new_returning), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                        TableRow(listOf(stringResource(R.string.ph_month), stringResource(R.string.ph_new), stringResource(R.string.ph_returning), stringResource(R.string.pay_label_payers)), header = true)
                        f.monthly.asReversed().forEach { m ->
                            HorizontalDivider(color = RbColors.surfaceVariant)
                            TableRow(listOf(payBucketLabel(m.month), payCount(m.newPayers), payCount(m.returningPayers), payCount(m.payers)), strong = -1)
                        }
                    }
                    Text(stringResource(R.string.ph_found_caveat), color = RbColors.textTertiary, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            Text(stringResource(R.string.pa_label), color = RbColors.textTertiary, fontSize = 10.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(28.dp))
        }
    }
}

private fun pct(fraction: Double): String = String.format(Locale.US, if (fraction * 100 >= 10) "%.0f%%" else "%.1f%%", fraction * 100)
private fun amount(v: Double): String = when {
    v == 0.0 -> "0"
    v >= 1000 -> String.format(Locale.US, "%,.0f", v)
    v >= 1 -> String.format(Locale.US, "%,.2f", v)
    else -> java.math.BigDecimal(v).setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

/** Four columns: the first takes the rest, the three numbers sit right-aligned at a fixed width. */
@Composable
private fun TableRow(cells: List<String>, header: Boolean = false, strong: Int = 1) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, c ->
            Text(c, color = if (header) RbColors.textTertiary else if (i == strong) RbColors.textPrimary else RbColors.textSecondary, fontSize = if (header) 10.sp else 12.sp,
                fontWeight = if (header || i == strong) FontWeight.SemiBold else FontWeight.Normal, maxLines = if (header) 2 else 1, overflow = TextOverflow.Ellipsis,
                textAlign = if (i == 0) TextAlign.Start else TextAlign.End,
                modifier = if (i == 0) Modifier.weight(1f) else Modifier.width(if (i == 1) 92.dp else 70.dp))
        }
    }
}

/** USD per week as a line over a filled area, with the largest week and $0 on the left and the first and last week below. */
@Composable
private fun WeeklyArea(weeks: List<PaymentAnalytics.Bucket>, color: Color) {
    val max = weeks.maxOf { it.usd }.takeIf { it > 0 } ?: 1.0
    val a11y = weeks.joinToString(", ") { payBucketLabel(it.bucket) + " " + payUsd(it.usd) }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.height(150.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(payUsd(max), color = RbColors.textTertiary, fontSize = 9.sp); Text("$0", color = RbColors.textTertiary, fontSize = 9.sp)
        }
        Spacer(Modifier.width(6.dp))
        val grid = RbColors.surfaceVariant
        androidx.compose.foundation.Canvas(Modifier.weight(1f).height(150.dp).semantics { contentDescription = a11y }) {
            for (g in 0..3) { val gy = size.height * g / 3; drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx()) }
            val vals = if (weeks.size > 1) weeks.map { it.usd } else List(2) { weeks.first().usd }
            val xs = vals.indices.map { it * size.width / (vals.size - 1) }
            fun y(v: Double) = (size.height * (1 - v / max)).toFloat()
            val area = Path().apply {
                moveTo(xs.first(), size.height)
                vals.indices.forEach { lineTo(xs[it], y(vals[it])) }
                lineTo(xs.last(), size.height); close()
            }
            drawPath(area, color.copy(alpha = 0.25f))
            for (i in 1 until vals.size) drawLine(color, Offset(xs[i - 1], y(vals[i - 1])), Offset(xs[i], y(vals[i])), 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 30.dp, top = 2.dp)) {
        Text(payBucketLabel(weeks.first().bucket), color = RbColors.textTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f))
        if (weeks.size > 1) Text(payBucketLabel(weeks.last().bucket), color = RbColors.textTertiary, fontSize = 9.sp)
    }
}
