package com.undersigned.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import com.undersigned.app.ui.components.PhotoBackdrop
import com.undersigned.app.ui.components.PrimaryButton
import com.undersigned.app.ui.theme.RbColors
import kotlinx.coroutines.launch

private data class Slide(@DrawableRes val photo: Int, val title: Int, val body: Int)

/**
 * Five replayable intro slides, Direction A: one full-page photograph each, the title and body on
 * the faded lower zone, copy verbatim from strings.xml. Also reachable from Settings > Replay the intro.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val slides = listOf(
        Slide(R.drawable.photo_frame_1_fingertip, R.string.intro1_title, R.string.intro1_body),
        Slide(R.drawable.photo_frame_2_nib, R.string.intro2_title, R.string.intro2_body),
        Slide(R.drawable.photo_frame_3_ledgers, R.string.intro3_title, R.string.intro3_body),
        Slide(R.drawable.photo_frame_4_seal, R.string.intro4_title, R.string.intro4_body),
        Slide(R.drawable.photo_frame_5_margin, R.string.intro5_title, R.string.intro5_body),
    )
    val pager = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    Box(Modifier.fillMaxSize().background(RbColors.background)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { i ->
            val s = slides[i]
            PhotoBackdrop(s.photo, Modifier.fillMaxSize(), fadeFrom = 0.38f) {
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 168.dp)) {
                    Text(stringResource(s.title), color = RbColors.textPrimary, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(s.body), color = RbColors.textSecondary, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
            if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.intro_skip), color = RbColors.textSecondary, fontSize = 14.sp) }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
                slides.indices.forEach { i ->
                    Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape).background(if (i == pager.currentPage) RbColors.purpleLight else RbColors.textDim))
                }
            }
            Box(Modifier.padding(horizontal = 24.dp)) {
                PrimaryButton(if (last) stringResource(R.string.onboarding_cta) else stringResource(R.string.intro_next), onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}
