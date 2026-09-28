package com.undersigned.app.ui.components

// Undersigned — components for the 28 Sep 2026 review designs (Screens F, G).
// Drop into ui/components/. Uses only RbColors and existing Material3 APIs.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.ui.theme.RbColors

/** Tier 1 on review cards: Attested, Paid for this app, Publisher ✓. 28dp, 12sp bold. */
@Composable
fun TrustPill(text: String, color: Color) {
    Box(
        Modifier.height(28.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
}

/** Neutral outline pill: season, flag weight, "Preview · not listed", waiting states. */
@Composable
fun OutlinePill(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, RbColors.textDim, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp)
    ) { Text(text, color = RbColors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
}

/** One device fact. Draws glyph + value; TalkBack hears [spoken] (the full use_pill / time_pill string). */
@Composable
fun DeviceUnit(icon: ImageVector?, value: String, spoken: String, tint: Color = RbColors.textTertiary) {
    Row(
        Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(value, color = RbColors.textSecondary, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 1, softWrap = false)
    }
}

/** Tier 2: wraps between units only, never inside one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceLine(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
}

/** Permanence, out of amber: purpleLight, dashed, the mark. */
@Composable
fun PermanenceNote(title: String?, body: String, modifier: Modifier = Modifier, trailing: (@Composable ColumnScope.() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).drawBehind {
            drawRoundRect(
                color = RbColors.purpleLight, cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            )
        }.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UndersignedMark(RbColors.purpleLight, Modifier.size(20.dp).padding(top = 1.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            title?.let { Text(it, color = RbColors.purpleLight, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            Text(body, color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            trailing?.invoke(this)
        }
    }
}

/** The ring-and-stroke mark, drawn. Used by PermanenceNote and the signed-note header. */
@Composable
fun UndersignedMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 512f
        val w = 40f * s
        drawCircle(color, radius = 150f * s, center = center, style = Stroke(width = w))
        drawLine(color, Offset(80f * s, 80f * s), Offset(432f * s, 432f * s), strokeWidth = w, cap = StrokeCap.Round)
    }
}

/** "Label: value · Label: value" as a grid. Keep the old combined string as [description] for TalkBack. */
@Composable
fun KeyValueGrid(items: List<Pair<String, String>>, columns: Int = 3, description: String? = null, valueColors: Map<Int, Color> = emptyMap(), valueSize: Int = 17) {
    Column(
        Modifier.fillMaxWidth().then(if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.chunked(columns).forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEachIndexed { c, (label, value) ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 14.sp)
                        Text(value, color = valueColors[r * columns + c] ?: RbColors.textPrimary, fontSize = valueSize.sp, fontWeight = FontWeight.Bold)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Daily bars with the burst window hatched and the usual pace dashed. Reads without colour:
 * the window is marked by hatch, not by hue. [counts] oldest first; window = [from]..[to] inclusive.
 */
@Composable
fun Sparkline(counts: List<Int>, from: Int, to: Int, pacePerDay: Double, description: String, modifier: Modifier = Modifier.size(132.dp, 44.dp)) {
    Canvas(modifier.semantics { contentDescription = description }) {
        if (counts.isEmpty()) return@Canvas
        val max = maxOf(counts.max().toDouble(), pacePerDay * 2, 1.0)
        val slot = size.width / counts.size
        val bw = slot * 0.66f
        val h = size.height
        // hatch the window
        val x0 = from * slot; val x1 = (to + 1) * slot
        clipRect(x0, 0f, x1, h) {
            var x = x0 - h
            while (x < x1) { drawLine(RbColors.warning.copy(alpha = 0.35f), Offset(x, h), Offset(x + h, 0f), strokeWidth = 1.5.dp.toPx()); x += 4.dp.toPx() }
        }
        // bars
        counts.forEachIndexed { i, v ->
            val bh = (v / max * (h - 2)).toFloat().coerceAtLeast(1f)
            val inWindow = i in from..to
            drawRect(if (inWindow) RbColors.warning else RbColors.textDim, Offset(i * slot + (slot - bw) / 2, h - bh), Size(bw, bh))
        }
        // usual pace
        val py = (h - pacePerDay / max * (h - 2)).toFloat()
        drawLine(RbColors.textTertiary, Offset(0f, py), Offset(size.width, py), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))
    }
}

/** Burst multiplier normalised to the window, not window-total ÷ 3-day pace (see addendum §1). */
fun burstMultiple(count: Int, windowDays: Int, pacePer3Days: Double): Double =
    if (pacePer3Days <= 0 || windowDays <= 0) 0.0 else count / (pacePer3Days * windowDays / 3.0)
