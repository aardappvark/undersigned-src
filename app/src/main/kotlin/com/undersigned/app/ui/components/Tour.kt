package com.undersigned.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.undersigned.app.R
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.ui.Routes
import com.undersigned.app.ui.theme.RbColors
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The first-use tour (owner, 9 Oct 2026: "takes the user through each screen with bubbles explaining each point").
 * Each step names a screen and a target; screens tag their parts with [tourTarget], and [TourOverlay] dims everything
 * else, cuts out the target and puts a bubble beside it, moving between screens as the steps do. It runs once after the
 * opening slides, can be skipped at any step, and is replayed from Settings.
 */
object Tour {
    data class Step(val route: String, val target: String?, val title: Int, val body: Int)

    val STEPS = listOf(
        Step(Routes.BROWSE, "b_search", R.string.tour_s1_t, R.string.tour_s1_b),
        Step(Routes.BROWSE, "b_tabs", R.string.tour_s2_t, R.string.tour_s2_b),
        Step(Routes.BROWSE, "b_list", R.string.tour_s3_t, R.string.tour_s3_b),
        // The tab bar along the bottom (owner, 10 Oct 2026); My reviews, Inbox and Settings moved from the Browse header to Me.
        Step(Routes.BROWSE, "nav_bar", R.string.tour_tabs_t, R.string.tour_tabs_b),
        Step(Routes.DETAIL, "d_review", R.string.tour_s4_t, R.string.tour_s4_b),
        Step(Routes.DETAIL, "d_score", R.string.tour_s5_t, R.string.tour_s5_b),
        Step(Routes.DETAIL, "d_help", R.string.tour_s6_t, R.string.tour_s6_b),
        Step(Routes.DETAIL, null, R.string.tour_s7_t, R.string.tour_s7_b),
        Step(Routes.CHANGES, "c_digest", R.string.tour_s8_t, R.string.tour_s8_b),
        Step(Routes.PRO, "nav_" + Routes.PRO, R.string.tour_pro_t, R.string.tour_pro_b),
        Step(Routes.ME, "m_reviews", R.string.tour_s9_t, R.string.tour_s9_b),
        Step(Routes.ME, "m_inbox", R.string.tour_s10_t, R.string.tour_s10_b),
        Step(Routes.ME, "m_settings", R.string.settings, R.string.tour_settings_b),
    )

    /** The step showing, or null when no tour runs. */
    val step = MutableStateFlow<Int?>(null)
    internal val bounds = mutableStateMapOf<String, Rect>()

    fun start() { step.value = 0 }
    fun finish() { step.value = null; UserPreferences.tourSeen = true }
}

/** Marks this element as the tour's [key] target: its place on screen is kept up to date for the overlay. */
fun Modifier.tourTarget(key: String): Modifier = onGloballyPositioned { Tour.bounds[key] = it.boundsInRoot() }

/** Draws the running tour over the whole app. [samplePackage] is the app page the tour opens to explain an app. */
@Composable
fun TourOverlay(nav: NavHostController, samplePackage: () -> String?) {
    val index by Tour.step.collectAsState()
    val i = index ?: return
    val step = Tour.STEPS.getOrNull(i) ?: run { Tour.finish(); return }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    // Take the user to the step's screen. Its target is forgotten first, so a place measured on an earlier visit is never
    // used before the screen has laid itself out again.
    LaunchedEffect(i, route) {
        if (route == step.route) return@LaunchedEffect
        step.target?.let { Tour.bounds.remove(it) }
        runCatching {
            when (step.route) {
                Routes.BROWSE -> if (!nav.popBackStack(Routes.BROWSE, inclusive = false)) nav.navigate(Routes.BROWSE)
                Routes.DETAIL -> samplePackage()?.let { nav.navigate(Routes.detail(it)) { popUpTo(Routes.BROWSE); launchSingleTop = true } }
                else -> nav.navigate(step.route) { popUpTo(Routes.BROWSE); launchSingleTop = true }
            }
        }
    }
    val density = LocalDensity.current
    val pad = with(density) { 8.dp.toPx() }
    val bottomInset = WindowInsets.navigationBars.getBottom(density)
    val topInset = WindowInsets.statusBars.getTop(density)
    // Not called `size`: inside the Canvas blocks below that name must mean the DrawScope's own size.
    var screen by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    // Only a target on this screen and on screen counts; otherwise the bubble sits in the middle with nothing cut out.
    val rect = step.target?.let { Tour.bounds[it] }?.takeIf { route == step.route && screen.height > 0 && it.top >= 0f && it.bottom <= screen.height && it.height > 0 }
        ?.let { Rect(it.left - pad, it.top - pad, it.right + pad, it.bottom + pad) }
    val purple = RbColors.purpleLight
    Box(Modifier.fillMaxSize().onGloballyPositioned { screen = androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat()) }
        .pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.74f))
            rect?.let { r ->
                val cr = CornerRadius(14.dp.toPx())
                drawRoundRect(Color.Transparent, Offset(r.left, r.top), r.size, cr, blendMode = BlendMode.Clear)
                drawRoundRect(purple, Offset(r.left, r.top), r.size, cr, style = Stroke(2.dp.toPx()))
            }
        }
        val gap = with(density) { 14.dp.roundToPx() }
        val margin = with(density) { 16.dp.roundToPx() }
        val below = rect != null && rect.center.y < screen.height / 2
        Layout(content = {
            TourBubble(i, step)
            Canvas(Modifier.size(20.dp, 10.dp)) {
                val p = Path().apply {
                    if (below) { moveTo(0f, size.height); lineTo(size.width / 2, 0f); lineTo(size.width, size.height) }
                    else { moveTo(0f, 0f); lineTo(size.width / 2, size.height); lineTo(size.width, 0f) }
                    close()
                }
                drawPath(p, RbColors.surface)
            }
        }, modifier = Modifier.fillMaxSize()) { m, c ->
            val bubble = m[0].measure(Constraints(maxWidth = c.maxWidth - 2 * margin))
            val tip = m[1].measure(Constraints())
            layout(c.maxWidth, c.maxHeight) {
                val x = (c.maxWidth - bubble.width) / 2
                val minY = topInset + margin
                val maxY = c.maxHeight - bottomInset - margin - bubble.height
                val want = when {
                    rect == null -> (c.maxHeight - bubble.height) / 2
                    below -> rect.bottom.toInt() + gap
                    else -> rect.top.toInt() - gap - bubble.height
                }
                val y = if (rect == null) want else want.coerceIn(minY, maxOf(minY, maxY))
                bubble.place(x, y)
                // The pointer only when the bubble sits where it wanted to, right beside its target.
                if (rect != null && y == want) {
                    val tx = (rect.center.x.toInt() - tip.width / 2).coerceIn(x + margin, maxOf(x + margin, x + bubble.width - margin - tip.width))
                    tip.place(tx, if (below) y - tip.height + 1 else y + bubble.height - 1)
                }
            }
        }
    }
}

@Composable
private fun TourBubble(i: Int, step: Tour.Step) {
    val last = i == Tour.STEPS.lastIndex
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().background(RbColors.surface, RoundedCornerShape(16.dp)).padding(start = 18.dp, end = 10.dp, top = 14.dp, bottom = 6.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(stringResource(R.string.tour_count, i + 1, Tour.STEPS.size), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(step.title), color = RbColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 8.dp))
        Spacer(Modifier.height(6.dp))
        Text(stringResource(step.body), color = RbColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(end = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            if (!last) TextButton(onClick = { Tour.finish() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.tour_skip), color = RbColors.textTertiary) }
            Spacer(Modifier.weight(1f))
            if (i > 0) TextButton(onClick = { Tour.step.value = i - 1 }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.tour_back), color = RbColors.textSecondary) }
            TextButton(onClick = { if (last) Tour.finish() else Tour.step.value = i + 1 }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (last) R.string.tour_done else R.string.tour_next), color = RbColors.purpleLight, fontWeight = FontWeight.Bold)
            }
        }
    }
}
