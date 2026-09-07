package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.theme.RbColors

@Composable
fun BlockedRegionScreen(detectedRegion: String) {
    Column(
        Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Block, contentDescription = null, tint = RbColors.negative, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.blocked_title), color = RbColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.blocked_body), color = RbColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(detectedRegion, color = RbColors.textTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}
