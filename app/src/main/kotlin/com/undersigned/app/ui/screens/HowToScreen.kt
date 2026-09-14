package com.undersigned.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.components.GlassCard
import com.undersigned.app.ui.components.PhotoBackdrop
import com.undersigned.app.ui.components.PrimaryButton
import com.undersigned.app.ui.theme.RbColors
import kotlinx.coroutines.launch

private data class Step(@DrawableRes val photo: Int, val title: Int, val sub: Int, val body: Int, val noteTitle: Int, val note: Int)

/**
 * Four how-to steps after the intro: the intro says what Undersigned is, these say how to use it.
 * The photo is a band at the top and the step occupies the black. Reachable any time from Settings.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HowToScreen(onDone: () -> Unit) {
    val steps = listOf(
        Step(R.drawable.photo_frame_1_fingertip, R.string.howto1_title, R.string.howto1_sub, R.string.howto1_body, R.string.howto1_note_title, R.string.howto1_note),
        Step(R.drawable.photo_frame_3_ledgers, R.string.howto2_title, R.string.howto2_sub, R.string.howto2_body, R.string.howto2_note_title, R.string.howto2_note),
        Step(R.drawable.photo_frame_2_nib, R.string.howto3_title, R.string.howto3_sub, R.string.howto3_body, R.string.howto3_note_title, R.string.howto3_note),
        Step(R.drawable.photo_frame_5_margin, R.string.howto4_title, R.string.howto4_sub, R.string.howto4_body, R.string.howto4_note_title, R.string.howto4_note),
    )
    val pager = rememberPagerState(pageCount = { steps.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == steps.lastIndex
    Column(Modifier.fillMaxSize().background(RbColors.background)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            val s = steps[i]
            Column(Modifier.fillMaxSize()) {
                PhotoBackdrop(s.photo, Modifier.fillMaxWidth().height(300.dp), fadeFrom = 0.30f) {
                    Row(Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.intro_skip), color = RbColors.textSecondary, fontSize = 14.sp) }
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 16.dp)) {
                        Text("${i + 1}", color = RbColors.purpleLight, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp)
                        Text(stringResource(s.title), color = RbColors.textPrimary, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                        Text(stringResource(s.sub), color = RbColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Text(stringResource(s.body), color = RbColors.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(14.dp))
                    GlassCard(accent = if (i == 2) RbColors.warning else RbColors.glassBorder) {
                        Text(stringResource(s.noteTitle), color = if (i == 2) RbColors.warning else RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(s.note), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center) {
            steps.indices.forEach { i -> Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape).background(if (i == pager.currentPage) RbColors.purpleLight else RbColors.textDim)) }
        }
        Box(Modifier.padding(horizontal = 24.dp).navigationBarsPadding()) {
            PrimaryButton(if (last) stringResource(R.string.onboarding_cta) else stringResource(R.string.intro_next), onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
        }
        Spacer(Modifier.height(22.dp))
    }
}
