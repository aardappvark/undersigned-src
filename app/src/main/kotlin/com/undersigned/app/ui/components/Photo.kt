package com.undersigned.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.ui.theme.RbColors

/**
 * A full-bleed photograph with the two scrims every photo screen in the design carries: a top scrim
 * (0.92 → 0 over the top 22 %) and a bottom scrim that is substantially opaque before any type sits
 * on it. Type over a photograph goes on the faded zone only (design §6). Photographs appear only
 * where the user reads one thing — the gate, the slides, empty states, the profile band — never
 * under a list.
 */
@Composable
fun PhotoBackdrop(@DrawableRes photo: Int, modifier: Modifier = Modifier, fadeFrom: Float = 0.40f, content: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.background(RbColors.background)) {
        Image(painterResource(photo), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.92f), 0.22f to Color.Transparent,
            fadeFrom to Color.Transparent, (fadeFrom + 0.30f).coerceAtMost(0.98f) to Color.Black.copy(alpha = 0.90f), 1f to Color.Black.copy(alpha = 0.98f),
        )))
        content()
    }
}

/** An empty state that is allowed a photograph: one line to read, one move to make. */
@Composable
fun EmptyState(@DrawableRes photo: Int, title: String, body: String? = null, action: String? = null, onAction: (() -> Unit)? = null, modifier: Modifier = Modifier.fillMaxSize()) {
    PhotoBackdrop(photo, modifier) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp)) {
            Text(title, color = RbColors.textPrimary, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp)
            body?.let { Spacer(Modifier.height(8.dp)); Text(it, color = RbColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp) }
            if (action != null && onAction != null) { Spacer(Modifier.height(16.dp)); SecondaryButton(action, onClick = onAction) }
        }
    }
}

/** A labelled row of single-choice chips ("SORT  Signed score · New · …"), scrolling sideways when a locale runs long. */
@Composable
fun <K> ChipRow(label: String, options: List<Pair<K, String>>, selected: K, onSelect: (K) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label.uppercase(), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(end = 10.dp))
        options.forEach { (k, text) -> TagChip(text, selected = k == selected) { onSelect(k) }; Spacer(Modifier.width(6.dp)) }
    }
}
