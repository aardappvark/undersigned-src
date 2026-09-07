package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.components.GlassCard
import com.undersigned.app.ui.components.PrimaryButton
import com.undersigned.app.ui.theme.RbColors

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_title), color = RbColors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.onboarding_subtitle), color = RbColors.textSecondary, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        Step(Icons.Default.Draw, stringResource(R.string.onboarding_step1_title), stringResource(R.string.onboarding_step1_body))
        Spacer(Modifier.height(10.dp))
        Step(Icons.Default.Lock, stringResource(R.string.onboarding_step2_title), stringResource(R.string.onboarding_step2_body))
        Spacer(Modifier.height(10.dp))
        Step(Icons.Default.PhoneAndroid, stringResource(R.string.onboarding_step3_title), stringResource(R.string.onboarding_step3_body))
        Spacer(Modifier.height(10.dp))
        Step(Icons.Default.Storefront, stringResource(R.string.onboarding_step4_title), stringResource(R.string.onboarding_step4_body))
        Spacer(Modifier.height(18.dp))
        PrimaryButton(stringResource(R.string.onboarding_cta), onClick = onDone)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, body: String) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = RbColors.green, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, color = RbColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(body, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
