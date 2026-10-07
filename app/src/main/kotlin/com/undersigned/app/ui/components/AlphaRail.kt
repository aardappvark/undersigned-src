package com.undersigned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.ui.theme.RbColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The A–Z fast-scroll rail, ported from the AarDappvark Toolkit's CatalogueScrollRail (owner, 7 Oct 2026). Tap or drag a
 * letter to jump; a bubble names it under the thumb. The list's rows are contiguous and [keys] is their order, so the
 * target index comes from any row on screen: its absolute index plus (target position − its position). In any other
 * sort than A–Z a letter has nowhere coherent to land, so touching the rail asks for A–Z first ([onRequestAlphaOrder])
 * and finishes the jump once the reordered rows arrive.
 *
 * [keys]: every row's LazyColumn key in display order; [names]: key → display name for the rows that are apps.
 */
@Composable
fun AlphaRail(keys: List<String>, names: Map<String, String>, listState: LazyListState, isAlphaOrder: Boolean, onRequestAlphaOrder: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val posByKey = remember(keys) { keys.withIndex().associate { (i, k) -> k to i } }
    fun bucket(name: String): Char { val c = name.trim().firstOrNull()?.uppercaseChar() ?: '#'; return if (c in 'A'..'Z') c else '#' }
    // (letter, position in keys): the first row with that letter in A–Z order; position -1 when not in A–Z order.
    val anchors = remember(keys, names, isAlphaOrder) {
        if (isAlphaOrder) { val seen = LinkedHashMap<Char, Int>(); keys.forEachIndexed { i, k -> names[k]?.let { seen.putIfAbsent(bucket(it), i) } }; seen.map { (c, i) -> c to i } }
        else keys.mapNotNull { names[it]?.let(::bucket) }.distinct().sortedBy { if (it == '#') 'Z' + 1 else it }.map { it to -1 }
    }
    if (anchors.size < 2) return
    val visibleAnchor: Pair<Int, Int>? by remember(posByKey) {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.firstNotNullOfOrNull { info -> (info.key as? String)?.let { k -> posByKey[k]?.let { pos -> info.index to pos } } } }
    }
    fun jumpTo(i: Int) {
        val targetPos = anchors[i.coerceIn(anchors.indices)].second.takeIf { it >= 0 } ?: return
        val target = visibleAnchor?.let { (abs, pos) -> abs + (targetPos - pos) } ?: (listState.layoutInfo.totalItemsCount - keys.size + targetPos)
        scope.launch { listState.scrollToItem(target.coerceAtLeast(0)) }
    }
    val railHeight = remember(anchors) { intArrayOf(1) }
    var active by remember { mutableStateOf<Int?>(null) }
    var tapToken by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<Char?>(null) }
    LaunchedEffect(isAlphaOrder, anchors) {
        val letter = pending
        if (isAlphaOrder && letter != null) {
            pending = null
            val i = anchors.indexOfFirst { it.first == letter }.takeIf { it >= 0 } ?: anchors.indexOfFirst { it.first >= letter }.takeIf { it >= 0 } ?: anchors.lastIndex
            active = i; jumpTo(i); val t = ++tapToken; delay(700); if (t == tapToken) active = null
        }
    }
    fun indexAt(y: Float) = (y / railHeight[0] * anchors.size).toInt().coerceIn(anchors.indices)
    fun engage(i: Int) { active = i; if (isAlphaOrder) jumpTo(i) else { pending = anchors[i].first; onRequestAlphaOrder() } }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        active?.let { i ->
            Text(anchors[i].first.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RbColors.purpleLight, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(RbColors.surface).border(1.dp, RbColors.glassBorder, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 10.dp))
            Spacer(Modifier.width(8.dp))
        }
        // A 48 dp wide drag track (27 letters rule out 48 dp per-letter targets); the visible pill hugs the edge.
        Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.width(40.dp).onSizeChanged { railHeight[0] = it.height.coerceAtLeast(1) }
            .semantics { contentDescription = "A to Z" }
            .pointerInput(anchors) { detectTapGestures { o -> engage(indexAt(o.y)); val t = ++tapToken; scope.launch { delay(700); if (t == tapToken) active = null } } }
            .pointerInput(anchors) { detectVerticalDragGestures(onDragStart = { o -> engage(indexAt(o.y)) }, onDragEnd = { active = null }, onDragCancel = { active = null }) { ch, _ -> val i = indexAt(ch.position.y); if (i != active) engage(i) } }) {
            Column(Modifier.padding(end = 4.dp).clip(RoundedCornerShape(12.dp)).background(RbColors.surface.copy(alpha = 0.94f)).border(1.dp, RbColors.glassBorder, RoundedCornerShape(12.dp)).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                anchors.forEachIndexed { i, (c, _) ->
                    val on = i == active
                    // A fixed cell with the letter centred in it, so the highlight sits exactly on the letter (the oval was
                    // offset when the text's line height and the cell disagreed, owner 7 Oct 2026).
                    Box(Modifier.size(18.dp).clip(CircleShape).background(if (on) RbColors.purple.copy(alpha = 0.45f) else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text(c.toString(), fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, color = if (on) RbColors.textPrimary else RbColors.purpleLight, textAlign = TextAlign.Center,
                            style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false), lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)))
                    }
                }
            }
        }
    }
}
