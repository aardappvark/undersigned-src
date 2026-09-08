package com.undersigned.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.undersigned.app.notify.LedgerWatchWorker
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.AppConfig
import com.undersigned.app.BuildConfig
import com.undersigned.app.R
import com.undersigned.app.data.TermsAcceptanceService
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.GlassCard
import com.undersigned.app.ui.components.SecondaryButton
import com.undersigned.app.ui.components.SectionTitle
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.app.util.WalletScreening
import com.undersigned.app.wallet.WalletSession
import com.undersigned.protocol.Ranking
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, sender: ActivityResultSender?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wallet by vm.wallet.collectAsState()
    val selfFacts by vm.selfFacts.collectAsState()
    val selfLoading by vm.selfFactsLoading.collectAsState()
    val hidden by vm.hidden.collectAsState()
    val follows by vm.follows.collectAsState()
    val alerts by UserPreferences.alertsEnabled.collectAsState()
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { UserPreferences.setAlerts(true); LedgerWatchWorker.schedule(context) } }
    val termsService = remember { TermsAcceptanceService(context) }
    val myStore by vm.myStoreReviews.collectAsState()
    val scan by vm.importScan.collectAsState()
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importStage by remember { mutableStateOf<String?>(null) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    val catalogue by vm.catalogue.collectAsState()
    var licenceText by remember { mutableStateOf<String?>(null) }
    var signing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun open(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }
    fun signIn() {
        val s = sender ?: return; signing = true; error = null
        scope.launch {
            WalletSession.signIn(s, termsService.generateConsentMessage("(pending)")).fold(
                onSuccess = { if (WalletScreening.isSanctioned(it.address)) error = context.getString(R.string.wallet_sanctioned) else { termsService.recordConsentWithSignature(it.address, it.signature, it.signedMessage); UserPreferences.setWallet(it.address) } },
                onFailure = { error = it.message },
            ); signing = false
        }
    }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            Text(stringResource(R.string.settings), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionTitle(stringResource(R.string.settings_wallet))
            GlassCard {
                Text(wallet?.let { shortKey(it, 8, 8) } ?: stringResource(R.string.settings_no_wallet), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (wallet != null) SecondaryButton(stringResource(R.string.disconnect_wallet), onClick = { UserPreferences.setWallet(null) })
                else SecondaryButton(if (signing) stringResource(R.string.terms_signing) else stringResource(R.string.settings_signin), enabled = !signing && sender != null, onClick = { signIn() })
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RbColors.negative, fontSize = 12.sp) }
            }

            if (wallet != null) {
                SectionTitle(stringResource(R.string.settings_standing))
                GlassCard {
                    when {
                        selfLoading || selfFacts == null -> Text(stringResource(R.string.review_checking_sgt), color = RbColors.textSecondary, fontSize = 13.sp)
                        else -> {
                            val f = selfFacts!!
                            Text(if (f.hasGenesisToken) stringResource(R.string.standing_sgt_yes) else stringResource(R.string.standing_sgt_no), color = if (f.hasGenesisToken) RbColors.green else RbColors.warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (f.hasGenesisToken) { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.standing_stake, String.format(java.util.Locale.US, "%,.0f", f.stakedSkr), String.format(java.util.Locale.US, "%.2f", Ranking.weight(f))), color = RbColors.textSecondary, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.standing_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }

            if (wallet != null) {
                SectionTitle(stringResource(R.string.import_title))
                GlassCard {
                    Text(stringResource(R.string.import_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(scan?.let { stringResource(R.string.import_scanning, it) } ?: stringResource(R.string.import_scan), enabled = scan == null && importStage == null, onClick = { vm.scanMyStoreReviews() })
                    val importable = myStore.filter { (pkg, _) -> vm.myReview(pkg) == null }
                    if (scan == null && myStore.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.import_found, myStore.size), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        importable.forEach { (pkg, r) ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = importStage == null) { chosen = if (pkg in chosen) chosen - pkg else chosen + pkg }, verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = pkg in chosen, onCheckedChange = { chosen = if (it) chosen + pkg else chosen - pkg }, enabled = importStage == null, colors = CheckboxDefaults.colors(checkedColor = RbColors.green))
                                Column { Text(catalogue.firstOrNull { it.packageName == pkg }?.name ?: pkg, color = RbColors.textPrimary, fontSize = 13.sp); Text("${r.rating}★ · ${r.createdAt.take(10)}" + (r.text?.let { " · ${it.take(60)}" } ?: ""), color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 2) }
                            }
                        }
                        if (importable.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            SecondaryButton(importStage ?: stringResource(R.string.import_publish, chosen.size), enabled = chosen.isNotEmpty() && importStage == null && sender != null, onClick = {
                                val s = sender ?: return@SecondaryButton
                                vm.importStoreReviews(s, importable.filter { it.key in chosen }.map { it.key to it.value }, onStage = { st -> importStage = when (st) { com.undersigned.app.solana.ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); com.undersigned.app.solana.ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); com.undersigned.app.solana.ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { r -> importStage = null; chosen = emptySet(); importMsg = r.fold({ context.getString(R.string.import_done, it) }, { it.message }) }
                            })
                            Text(stringResource(R.string.import_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    } else if (scan == null && importMsg == null) Text(stringResource(R.string.import_none), color = RbColors.textTertiary, fontSize = 11.sp)
                    importMsg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RbColors.green, fontSize = 12.sp) }
                }
            }

            SectionTitle(stringResource(R.string.alerts_title))
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.alerts_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        Text(stringResource(R.string.alerts_follows, follows.size), color = RbColors.textTertiary, fontSize = 11.sp)
                    }
                    Switch(checked = alerts, onCheckedChange = { on ->
                        if (!on) { UserPreferences.setAlerts(false); LedgerWatchWorker.cancel(context) }
                        else if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else { UserPreferences.setAlerts(true); LedgerWatchWorker.schedule(context) }
                    }, colors = SwitchDefaults.colors(checkedThumbColor = RbColors.green, checkedTrackColor = RbColors.green.copy(alpha = 0.4f)))
                }
            }

            SectionTitle(stringResource(R.string.settings_ledger))
            GlassCard {
                Text(stringResource(R.string.settings_ledger_body, shortKey(AppConfig.Ledger.REGISTRY, 6, 6)), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(stringResource(R.string.settings_refresh_ledger), onClick = { vm.syncLedger() })
            }

            SectionTitle(stringResource(R.string.settings_hidden))
            GlassCard {
                Text(stringResource(R.string.settings_hidden_body, hidden.size), color = RbColors.textSecondary, fontSize = 12.sp)
                if (hidden.isNotEmpty()) { Spacer(Modifier.height(10.dp)); SecondaryButton(stringResource(R.string.settings_unhide_all), onClick = { UserPreferences.unhideAll() }) }
            }

            SectionTitle(stringResource(R.string.settings_legal))
            GlassCard {
                Text(stringResource(R.string.settings_affiliation), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(6.dp))
                LinkRow(stringResource(R.string.terms_link_terms)) { open(AppConfig.Identity.URI + "terms.html") }
                LinkRow(stringResource(R.string.terms_link_privacy)) { open(AppConfig.Identity.URI + "privacy.html") }
                LinkRow(stringResource(R.string.terms_link_disclaimer)) { open(AppConfig.Identity.URI + "disclaimer.html") }
                LinkRow(stringResource(R.string.settings_sanctions)) { open(AppConfig.Identity.URI + "sanctions.html") }
                LinkRow(stringResource(R.string.settings_licence)) { open(AppConfig.Identity.URI + "license.html") }
                LinkRow(stringResource(R.string.settings_font_licence)) { licenceText = try { context.assets.open("licenses/OFL-1.1.txt").bufferedReader().use { it.readText() } } catch (_: Exception) { "OFL-1.1" } }
            }

            SectionTitle(stringResource(R.string.settings_data))
            GlassCard {
                Text(stringResource(R.string.settings_data_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(stringResource(R.string.settings_export_consent), onClick = {
                    try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, termsService.exportConsentRecordAsJson()) }, null)) } catch (_: Exception) { }
                })
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.settings_delete_data), onClick = {
                    termsService.deleteAllConsentData(); vm.clearLocalCaches(); UserPreferences.clearAll()
                    try { (context as? android.app.Activity)?.recreate() } catch (_: Exception) { }
                })
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(stringResource(R.string.intro_replay), onClick = { UserPreferences.setOnboardingSeen(false) })
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.settings_about, BuildConfig.VERSION_NAME), color = RbColors.textDim, fontSize = 11.sp)
            Text(stringResource(R.string.settings_contact), color = RbColors.textDim, fontSize = 11.sp)
            Spacer(Modifier.height(24.dp))
        }
    }

    licenceText?.let { text ->
        AlertDialog(onDismissRequest = { licenceText = null }, containerColor = RbColors.surface,
            title = { Text("Manrope — SIL Open Font License 1.1", color = RbColors.textPrimary, fontSize = 15.sp) },
            text = { Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) { Text(text, color = RbColors.textSecondary, fontSize = 11.sp) } },
            confirmButton = { TextButton(onClick = { licenceText = null }) { Text(stringResource(android.R.string.ok), color = RbColors.purpleLight) } })
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = RbColors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = RbColors.textTertiary, modifier = Modifier.size(16.dp))
    }
}
