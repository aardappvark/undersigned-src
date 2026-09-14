package com.undersigned.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.components.PhotoBackdrop
import com.undersigned.app.ui.theme.RbColors

/** The sanctions gate: a dead end the user reads once, so a photograph is permitted. The check runs on the device only. */
@Composable
fun BlockedRegionScreen(detectedRegion: String) {
    PhotoBackdrop(R.drawable.photo_blocked_photo, Modifier.fillMaxSize(), fadeFrom = 0.40f) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 36.dp)) {
            Text(stringResource(R.string.blocked_title), color = RbColors.textPrimary, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.blocked_body, detectedRegion), color = RbColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.blocked_region_line, detectedRegion), color = RbColors.textTertiary, fontSize = 12.sp)
        }
    }
}
