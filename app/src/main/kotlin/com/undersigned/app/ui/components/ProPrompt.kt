package com.undersigned.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.AppConfig
import com.undersigned.app.R
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.ui.theme.RbColors

/**
 * The one way a Pro tool is offered (owner, 10 Oct 2026): a small card in place of the gated control, never a modal, at most
 * one per screen and never on a free screen (Browse, app pages, the composer, flags). What the tool does in one line, the
 * price read from AppConfig, a "Get Pro" button that opens the Pro tab (pushed, so back returns here), and the quiet note
 * that Pro never changes a score. Without a wallet it says to connect one first. Callers show it only when Pro is not active,
 * and the card also hides ITSELF the moment Pro turns active ([ProSignal], owner, 11 Oct 2026: "dynamically disappears once
 * Pro is enabled"), so no screen can keep offering what the wallet already has. The one exception to "never on the
 * composer" is [ProPromptLine] for a Pro-only control that lives there (the reviewer's own tags).
 */
@Composable
fun ProPrompt(feature: String, body: String, onGetPro: () -> Unit, modifier: Modifier = Modifier) {
    val wallet by UserPreferences.walletAddress.collectAsState()
    val active by ProSignal.active.collectAsState()
    if (active) return
    GlassCard(modifier, accent = RbColors.purple.copy(alpha = 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(stringResource(R.string.pro_badge), RbColors.purpleLight)
            Spacer(Modifier.width(8.dp))
            Text(feature, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(body, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.pro_prompt_price, AppConfig.Payment.solLabel(), AppConfig.Payment.skrLabel()), color = RbColors.purpleLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (wallet == null) Text(stringResource(R.string.pro_needs_wallet), color = RbColors.warning, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        SecondaryButton(stringResource(R.string.pro_get_short), onClick = onGetPro)
        Text(stringResource(R.string.pro_prompt_never), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Whether the signed-in wallet's Pro is active right now; set by the view model whenever the licence is recomputed. */
object ProSignal { val active = kotlinx.coroutines.flow.MutableStateFlow(false) }

/**
 * The compact form: one line in place of a Pro-only control, with the price and a Get Pro button; hides itself when Pro is
 * active, like [ProPrompt].
 */
@Composable
fun ProPromptLine(feature: String, body: String, onGetPro: () -> Unit, modifier: Modifier = Modifier) {
    val active by ProSignal.active.collectAsState()
    if (active) return
    // A Column, not a Row: SecondaryButton fills its width, and beside a weighted column that left the text zero wide, which
    // laid out one glyph per line and swallowed the rest of the composer (emulator, 11 Oct 2026).
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Pill(stringResource(R.string.pro_badge), RbColors.purpleLight); Spacer(Modifier.width(8.dp)); Text(feature, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        Text(body, color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
        Text(stringResource(R.string.pro_prompt_price, AppConfig.Payment.solLabel(), AppConfig.Payment.skrLabel()), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(6.dp))
        SecondaryButton(stringResource(R.string.pro_get_short), onClick = onGetPro)
    }
}
