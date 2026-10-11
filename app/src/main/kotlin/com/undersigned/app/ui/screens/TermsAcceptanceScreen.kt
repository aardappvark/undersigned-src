package com.undersigned.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import com.undersigned.app.ui.components.errorText
import com.undersigned.app.data.TermsAcceptanceService
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.ui.components.GlassCard
import com.undersigned.app.ui.components.PhotoBackdrop
import com.undersigned.app.ui.components.PrimaryButton
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.app.util.WalletScreening
import com.undersigned.app.wallet.WalletSession
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch

/**
 * The sign-in slide: the last page of the one opening set. Slide five's photograph and title, then the versioned
 * consent (ideally backed by a Sign-In-With-Solana signature) and the read-only no-wallet path. No separate
 * "welcome" page: the owner asked for one set, and consent is part of it (17 Sep 2026).
 */
@OptIn(ExperimentalLayoutApi::class)
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
                    else { termsService.recordConsentWithSignature(it.address, it.signature, it.signedMessage); UserPreferences.setWallet(it.address); com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.TERMS_ACCEPTED); com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.WALLET_CONNECTED); onAccepted() }
                },
                onFailure = { error = context.errorText(it) },
            )
            isConnecting = false
        }
    }
    fun open(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }

    // Slide five's photograph; the type starts where the scrim is already opaque.
    val scroll = rememberScrollState()
    // "Browse without a wallet" opens its card below the fold: bring it into view.
    LaunchedEffect(showNoWallet) { if (showNoWallet) scroll.animateScrollTo(scroll.maxValue) }
    PhotoBackdrop(R.drawable.photo_frame_5_margin, Modifier.fillMaxSize(), fadeFrom = 0.14f) {
    // Text scrolled up over the bright part of the photograph became unreadable: darken it as the page scrolls.
    Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = (scroll.value / 300f).coerceIn(0f, 0.85f))))
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.Start) {
        Spacer(Modifier.height(150.dp))
        Text(stringResource(R.string.intro5_title), color = RbColors.textPrimary, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.intro5_body), color = RbColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.terms_intro), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 17.sp)
        // The operator, right after the "not affiliated" line (1.1.5).
        Text(stringResource(R.string.operator_disclosure), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))
        GlassCard {
            Text(stringResource(R.string.terms_confirm_heading), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            listOf(stringResource(R.string.terms_point_age), stringResource(R.string.terms_point_region), stringResource(R.string.terms_point_docs, TermsAcceptanceService.CURRENT_EULA_VERSION), stringResource(R.string.terms_point_permanent), stringResource(R.string.terms_point_nofunds)).forEach {
                Row(Modifier.padding(vertical = 1.dp)) { Text("•  ", color = RbColors.green, fontSize = 12.sp); Text(it, color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp) }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Wraps, because a fixed Row clipped the third link off the screen in German, where "Disclaimer" is
        // "Haftungsausschluss" (measured on an emulator, 20 Sep 2026). Same for Greek, Hungarian and Vietnamese.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LinkText(stringResource(R.string.terms_link_terms)) { open(AppConfig.Identity.URI + "terms.html") }
            LinkText(stringResource(R.string.terms_link_privacy)) { open(AppConfig.Identity.URI + "privacy.html") }
            LinkText(stringResource(R.string.terms_link_disclaimer)) { open(AppConfig.Identity.URI + "disclaimer.html") }
        }
        Spacer(Modifier.height(14.dp))
        Button(onClick = { signAndAccept() }, enabled = !isConnecting, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = RbColors.purple, contentColor = androidx.compose.ui.graphics.Color.White)) {
            if (isConnecting) { CircularProgressIndicator(Modifier.size(18.dp), color = RbColors.textPrimary, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.terms_signing), fontWeight = FontWeight.Bold) }
            else { Icon(Icons.Default.Fingerprint, contentDescription = null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.terms_sign_accept), fontWeight = FontWeight.Bold) }
        }
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = RbColors.negative, fontSize = 12.sp, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(12.dp))
        if (!showNoWallet) TextButton(onClick = { showNoWallet = true }) { Text(stringResource(R.string.terms_continue_without), color = RbColors.textSecondary, fontSize = 14.sp) }
        else GlassCard {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = accepted, role = androidx.compose.ui.semantics.Role.Checkbox) { accepted = it }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = accepted, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = RbColors.green))
                Text(stringResource(R.string.terms_checkbox), color = RbColors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton(stringResource(R.string.terms_accept_continue), enabled = accepted, onClick = { termsService.recordConsentWithoutSignature(); com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.TERMS_ACCEPTED); onAccepted() })
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.terms_preview_only_note), color = RbColors.textTertiary, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
    }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(text, color = RbColors.purpleLight, fontSize = 13.sp, textDecoration = TextDecoration.Underline) }
}
