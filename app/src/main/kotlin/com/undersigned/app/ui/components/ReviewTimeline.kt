package com.undersigned.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.Timeline
import java.text.DateFormat
import java.util.Date

/** The spans the chart reads over. Days, or null for everything this phone has read. */
enum class TimelineRange(val days: Int?, val label: Int) {
    D30(30, R.string.timeline_range_30), D90(90, R.string.timeline_range_90), Y1(365, R.string.timeline_range_1y), ALL(null, R.string.timeline_range_all),
}

/**
 * Reviews over time for one app (owner, 8 Oct 2026), in Reckoner's pattern: the bars and lines are built once per series
 * and size; the moving bar is its own layer and the ONLY thing a drag redraws. Tap or slide along the chart to read a
 * period; the header follows the finger.
 *
 * Bars, stacked per period: store reviews from wallets holding a Genesis Token (green), store reviews from other or
 * not-yet-checked wallets (dim), and signed reviews (purple). Lines on a fixed 1–5 star scale, so no move looks bigger than
 * it was: the signed score (purple), the Pro members' score (dashed lavender, when there is one) and the running
 * average of the store reviews read (dashed grey).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewTimeline(buckets: List<Timeline.Bucket>, range: TimelineRange, onRange: (TimelineRange) -> Unit, storeReadFrom: Long?, modifier: Modifier = Modifier) {
    val sel: MutableState<Int?> = remember(buckets) { mutableStateOf(null) }
    val n = buckets.size
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(modifier.fillMaxWidth()) {
        TimelineHeader(buckets, sel, df)
        Spacer(Modifier.height(10.dp))
        if (n < 2) {
            Text(stringResource(R.string.timeline_empty), color = RbColors.textTertiary, fontSize = 12.sp)
        } else {
            val maxCount = remember(buckets) { (buckets.maxOf { it.storeTotal + it.signed }).coerceAtLeast(1) }
            val showMember = remember(buckets) { buckets.any { it.proMean != null } }
            fun indexAt(x: Float, width: Int) = if (width <= 0) 0 else (x / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)
            val chartDesc = stringResource(R.string.timeline_title)
            Box(
                Modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = chartDesc }
                    .pointerInput(n, sel) { detectTapGestures { o -> sel.value = indexAt(o.x, size.width) } }
                    .pointerInput(n, sel) {
                        // Horizontal only, so the page still scrolls vertically over the chart.
                        detectHorizontalDragGestures(onDragStart = { o -> sel.value = indexAt(o.x, size.width) }) { change, _ ->
                            change.consume(); sel.value = indexAt(change.position.x, size.width)
                        }
                    },
            ) {
                // Bars, grid and lines: built once per series and size, never redrawn by the moving bar.
                Spacer(Modifier.matchParentSize().drawWithCache {
                    val w = size.width; val h = size.height
                    val lineTop = 6f; val lineBottom = h * 0.58f            // the star scale lives in the top part
                    val barTop = h * 0.64f; val barBottom = h - 2f            // the counts in the bottom part
                    val slot = w / n; val barW = (slot * 0.62f).coerceAtLeast(2f)
                    fun yStar(v: Double): Float = (lineBottom - ((v - 1.0) / 4.0) * (lineBottom - lineTop)).toFloat()
                    fun xMid(i: Int) = i * slot + slot / 2f
                    fun line(values: List<Double?>): Path = Path().apply {
                        var started = false
                        values.forEachIndexed { i, v -> if (v == null) started = false else { val p = Offset(xMid(i), yStar(v)); if (!started) moveTo(p.x, p.y) else lineTo(p.x, p.y); started = true } }
                    }
                    val signed = line(buckets.map { it.signedMean })
                    val member = if (showMember) line(buckets.map { it.proMean }) else null
                    val store = line(buckets.map { it.storeMean })
                    // The area under the signed line, faint: the eye finds the signed score first.
                    val signedFill = Path().apply {
                        var open = false; var lastX = 0f
                        buckets.forEachIndexed { i, b ->
                            val v = b.signedMean
                            if (v != null) { val x = xMid(i); if (!open) { moveTo(x, lineBottom); lineTo(x, yStar(v)); open = true } else lineTo(x, yStar(v)); lastX = x }
                            else if (open) { lineTo(lastX, lineBottom); close(); open = false }
                        }
                        if (open) { lineTo(lastX, lineBottom); close() }
                    }
                    val fillBrush = Brush.verticalGradient(listOf(RbColors.purple.copy(alpha = 0.28f), RbColors.purple.copy(alpha = 0f)), startY = lineTop, endY = lineBottom)
                    val dashed = PathEffect.dashPathEffect(floatArrayOf(7f, 6f))
                    onDrawBehind {
                        // Star grid: 1, 3 and 5, so the scale is always the whole scale.
                        listOf(1.0, 3.0, 5.0).forEach { s -> drawLine(RbColors.surfaceVariant, Offset(0f, yStar(s)), Offset(w, yStar(s)), strokeWidth = 1f) }
                        drawLine(RbColors.surfaceVariant, Offset(0f, barBottom), Offset(w, barBottom), strokeWidth = 1f)
                        buckets.forEachIndexed { i, b ->
                            val x = xMid(i) - barW / 2f
                            var top = barBottom
                            fun seg(count: Int, color: Color) {
                                if (count <= 0) return
                                val hh = (count.toFloat() / maxCount) * (barBottom - barTop)
                                drawRoundRect(color, Offset(x, top - hh), Size(barW, hh), CornerRadius(2f, 2f)); top -= hh
                            }
                            seg(b.storeOther, RbColors.textDim.copy(alpha = 0.55f))
                            seg(b.storeSeeker, RbColors.green.copy(alpha = 0.85f))
                            seg(b.signed, RbColors.purple)
                        }
                        drawPath(signedFill, fillBrush)
                        drawPath(store, RbColors.textTertiary, style = Stroke(width = 2f, pathEffect = dashed, cap = StrokeCap.Round))
                        member?.let { drawPath(it, RbColors.purpleLight, style = Stroke(width = 2f, pathEffect = dashed, cap = StrokeCap.Round)) }
                        drawPath(signed, RbColors.purple, style = Stroke(width = 3f, cap = StrokeCap.Round))
                    }
                })
                // The moving bar: a highlighted column under the finger, its line, and a dot on each line. The only layer a
                // drag redraws.
                Canvas(Modifier.matchParentSize()) {
                    val s = sel.value ?: return@Canvas
                    val w = size.width; val h = size.height
                    val lineTop = 6f; val lineBottom = h * 0.58f
                    val slot = w / n
                    fun yStar(v: Double): Float = (lineBottom - ((v - 1.0) / 4.0) * (lineBottom - lineTop)).toFloat()
                    val x = s * slot + slot / 2f
                    drawRoundRect(RbColors.purple.copy(alpha = 0.14f), Offset(s * slot, 0f), Size(slot, h), CornerRadius(6f, 6f))
                    drawLine(RbColors.purpleLight.copy(alpha = 0.8f), Offset(x, 0f), Offset(x, h), strokeWidth = 1.5f)
                    val b = buckets[s]
                    b.storeMean?.let { drawCircle(RbColors.textTertiary, radius = 4.5f, center = Offset(x, yStar(it))) }
                    if (showMember) b.proMean?.let { drawCircle(RbColors.purpleLight, radius = 4.5f, center = Offset(x, yStar(it))) }
                    b.signedMean?.let { v -> val c = Offset(x, yStar(v)); drawCircle(RbColors.textPrimary, radius = 7f, center = c); drawCircle(RbColors.purple, radius = 5f, center = c) }
                }
            }
            Spacer(Modifier.height(4.dp))
            // The time axis: where the range starts, its middle, and where it ends.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(buckets.first().start, buckets[n / 2].start, buckets.last().end).forEach {
                    Text(df.format(Date(it * 1000)), color = RbColors.textDim, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Legend(showMember)
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TimelineRange.entries.forEach { r -> TagChip(stringResource(r.label), selected = r == range, onClick = { onRange(r) }) }
            val at = sel.value
            if (at != null && n > 1) {
                TagChip(stringResource(R.string.timeline_prev), onClick = { sel.value = (at - 1).coerceAtLeast(0) })
                TagChip(stringResource(R.string.timeline_next), onClick = { sel.value = (at + 1).coerceAtMost(n - 1) })
                TagChip(stringResource(R.string.timeline_clear), onClick = { sel.value = null })
            }
        }
        if (sel.value == null && n > 1) { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.timeline_hint), color = RbColors.textTertiary, fontSize = 10.sp) }
        val read = buckets.lastOrNull()?.storeRead ?: 0
        if (read > 0 && storeReadFrom != null) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.timeline_note, read, df.format(Date(storeReadFrom * 1000))), color = RbColors.textTertiary, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

/** The header reads the period under the finger, or the latest standing when nothing is selected. */
@Composable
private fun TimelineHeader(buckets: List<Timeline.Bucket>, sel: MutableState<Int?>, df: DateFormat) {
    val at = sel.value
    val b = at?.let { buckets.getOrNull(it) } ?: buckets.lastOrNull()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (at == null) stringResource(R.string.timeline_title) else stringResource(R.string.timeline_period, df.format(Date(b!!.start * 1000)), df.format(Date((b.end - 1) * 1000))),
                color = if (at == null) RbColors.textSecondary else RbColors.textPrimary, fontSize = 12.sp, fontWeight = if (at == null) FontWeight.Normal else FontWeight.Bold)
            if (b != null && at != null) Text(stringResource(R.string.timeline_counts, b.storeTotal, b.storeSeeker, b.signed), color = RbColors.textTertiary, fontSize = 11.sp)
        }
        b?.let {
            Column(horizontalAlignment = Alignment.End) {
                Text(it.signedMean?.let { v -> String.format(java.util.Locale.US, "%.1f", v) } ?: "–", color = RbColors.purpleLight, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.timeline_store_at, it.storeMean?.let { v -> String.format(java.util.Locale.US, "%.1f", v) } ?: "–"), color = RbColors.textTertiary, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(showMember: Boolean) {
    @Composable fun key(color: Color, text: String, line: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = if (line) 14.dp else 10.dp, height = if (line) 3.dp else 10.dp).clip(if (line) RoundedCornerShape(2.dp) else CircleShape).background(color))
            Spacer(Modifier.width(6.dp)); Text(text, color = RbColors.textSecondary, fontSize = 11.sp)
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        key(RbColors.purple, stringResource(R.string.timeline_legend_signed), true)
        if (showMember) key(RbColors.purpleLight, stringResource(R.string.member_score_title), true)
        key(RbColors.textTertiary, stringResource(R.string.timeline_legend_store), true)
        key(RbColors.green, stringResource(R.string.timeline_bars_seeker), false)
        key(RbColors.textDim, stringResource(R.string.timeline_bars_other), false)
        key(RbColors.purple, stringResource(R.string.timeline_bars_signed), false)
    }
}
