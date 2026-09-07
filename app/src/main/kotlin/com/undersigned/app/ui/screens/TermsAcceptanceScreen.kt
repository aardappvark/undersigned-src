package com.undersigned.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.AppConfig
import com.undersigned.app.R
import com.undersigned.app.data.TermsAcceptanceService
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.ui.components.GlassCard
import com.undersigned.app.ui.components.PrimaryButton
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.app.util.WalletScreening
import com.undersigned.app.wallet.WalletSession
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch

/** First-launch legal gate: versioned consent, ideally backed by a Sign-In-With-Solana signature, with a read-only no-wallet path. */
@Composable
fun TermsAcceptanceScreen(activityResultSender: ActivityResultSender?, onAccepted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val termsService = remember { TermsAcceptanceService(context) }
    var isConnecting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNoWallet by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }

    val consentStatement = "By signing in you accept the Undersigned Terms of Service, Privacy Policy and EULA (all v${TermsAcceptanceService.CURRENT_EULA_VERSION}); " +
        "confirm you are 18+ and not in a sanctioned jurisdiction; and understand that a review you publish is written to a public blockchain permanently and that this app never holds your keys or funds. Signing transfers no funds."

    fun signAndAccept() {
        val sender = activityResultSender ?: run { error = context.getString(R.string.terms_no_wallet_hint); return }
        isConnecting = true; error = null
        scope.launch {
            WalletSession.signIn(sender, consentStatement).fold(
                onSuccess = {
                    if (WalletScreening.isSanctioned(it.address)) error = context.getString(R.string.wallet_sanctioned)
                    else { termsService.recordConsentWithSignature(it.address, it.signature, it.signedMessage); UserPreferences.setWallet(it.address); onAccepted() }
                },
                onFailure = { error = it.message },
            )
            isConnecting = false
        }
    }
    fun open(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Text(stringResource(R.string.terms_welcome), color = RbColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.terms_intro), color = RbColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        GlassCard {
            Text(stringResource(R.string.terms_confirm_heading), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            listOf(stringResource(R.string.terms_point_age), stringResource(R.string.terms_point_region), stringResource(R.string.terms_point_docs, TermsAcceptanceService.CURRENT_EULA_VERSION), stringResource(R.string.terms_point_permanent), stringResource(R.string.terms_point_nofunds)).forEach {
                Row(Modifier.padding(vertical = 2.dp)) { Text("•  ", color = RbColors.green, fontSize = 13.sp); Text(it, color = RbColors.textSecondary, fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LinkText(stringResource(R.string.terms_link_terms)) { open(AppConfig.Identity.URI + "terms.html") }
            LinkText(stringResource(R.string.terms_link_privacy)) { open(AppConfig.Identity.URI + "privacy.html") }
            LinkText(stringResource(R.string.terms_link_disclaimer)) { open(AppConfig.Identity.URI + "disclaimer.html") }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { signAndAccept() }, enabled = !isConnecting, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = RbColors.purple, contentColor = RbColors.textPrimary)) {
            if (isConnecting) { CircularProgressIndicator(Modifier.size(18.dp), color = RbColors.textPrimary, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.terms_signing), fontWeight = FontWeight.Bold) }
            else { Icon(Icons.Default.Fingerprint, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.terms_sign_accept), fontWeight = FontWeight.Bold) }
        }
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = RbColors.negative, fontSize = 12.sp, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(12.dp))
        if (!showNoWallet) TextButton(onClick = { showNoWallet = true }) { Text(stringResource(R.string.terms_continue_without), color = RbColors.textSecondary, fontSize = 14.sp) }
        else GlassCard {
            Row(Modifier.fillMaxWidth().clickable { accepted = !accepted }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = { accepted = it }, colors = CheckboxDefaults.colors(checkedColor = RbColors.green))
                Text(stringResource(R.string.terms_checkbox), color = RbColors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton(stringResource(R.string.terms_accept_continue), enabled = accepted, onClick = { termsService.recordConsentWithoutSignature(); onAccepted() })
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.terms_preview_only_note), color = RbColors.textTertiary, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(text, color = RbColors.purpleLight, fontSize = 13.sp, textDecoration = TextDecoration.Underline) }
}
