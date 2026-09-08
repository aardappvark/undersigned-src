package com.undersigned.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.components.PrimaryButton
import com.undersigned.app.ui.theme.RbColors
import kotlinx.coroutines.launch

private data class Slide(val icon: ImageVector, val title: Int, val body: Int)

/** Five replayable intro slides (fleet pattern). Also reachable from Settings > Replay the intro. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val slides = listOf(
        Slide(Icons.Default.Draw, R.string.intro1_title, R.string.intro1_body),
        Slide(Icons.Default.Fingerprint, R.string.intro2_title, R.string.intro2_body),
        Slide(Icons.Default.Storefront, R.string.intro3_title, R.string.intro3_body),
        Slide(Icons.Default.PhoneAndroid, R.string.intro4_title, R.string.intro4_body),
        Slide(Icons.Default.Notifications, R.string.intro5_title, R.string.intro5_body),
    )
    val pager = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == slides.lastIndex
    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onDone) { Text(stringResource(R.string.intro_skip), color = RbColors.textTertiary, fontSize = 14.sp) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i ->
            val s = slides[i]
            Column(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(RbColors.purple.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(s.icon, contentDescription = null, tint = RbColors.green, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(stringResource(s.title), color = RbColors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, lineHeight = 32.sp)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(s.body), color = RbColors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), horizontalArrangement = Arrangement.Center) {
            slides.indices.forEach { i ->
                Box(Modifier.padding(4.dp).size(if (i == pager.currentPage) 10.dp else 7.dp).clip(CircleShape).background(if (i == pager.currentPage) RbColors.purpleLight else RbColors.surfaceVariant))
            }
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            PrimaryButton(if (last) stringResource(R.string.onboarding_cta) else stringResource(R.string.intro_next), onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
        }
        Spacer(Modifier.height(12.dp))
    }
}
