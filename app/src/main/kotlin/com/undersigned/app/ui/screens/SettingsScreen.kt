package com.undersigned.app.ui.screens

import com.undersigned.app.ui.components.BackButton
import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.undersigned.app.notify.LedgerWatchWorker
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.AppConfig
import com.undersigned.app.BuildConfig
import com.undersigned.app.R
import com.undersigned.app.ui.components.errorText
import com.undersigned.app.data.TermsAcceptanceService
import com.undersigned.app.data.UserPreferences
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.GlassCard
import com.undersigned.app.ui.components.Pill
import com.undersigned.app.ui.components.storeDate
import com.undersigned.app.ui.components.SecondaryButton
import com.undersigned.app.ui.components.SectionTitle
import com.undersigned.app.ui.components.ProPrompt
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.app.util.WalletScreening
import com.undersigned.app.wallet.WalletSession
import com.undersigned.protocol.Ranking
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: MainViewModel, sender: ActivityResultSender?, onBack: () -> Unit, onHowTo: () -> Unit = {}, onIntro: () -> Unit = {}, onTour: () -> Unit = {}, onPro: () -> Unit = {}, onDesk: () -> Unit = {}, onStats: () -> Unit = {}, onPayments: () -> Unit = {}, onWatch: () -> Unit = {}, onLeaderboard: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val wallet by vm.wallet.collectAsState()
    val selfFacts by vm.selfFacts.collectAsState()
    val selfLoading by vm.selfFactsLoading.collectAsState()
    val hidden by vm.hidden.collectAsState()
    val follows by vm.follows.collectAsState()
    val alerts by UserPreferences.alertsEnabled.collectAsState()
    val analyticsOn by UserPreferences.analyticsEnabled.collectAsState()
    LaunchedEffect(Unit) { com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.SETTINGS_OPENED) }
    val dense by UserPreferences.dense.collectAsState()
    val pro by vm.pro.collectAsState()
    val entries by vm.entries.collectAsState()
    var copyOpen by remember { mutableStateOf(false) }
    val granters by vm.granters.collectAsState()
    var shareOpen by remember { mutableStateOf(false) }
    var shareInput by remember { mutableStateOf("") }
    var shareResolved by remember { mutableStateOf<String?>(null) }
    var shareNote by remember { mutableStateOf<String?>(null) }
    var shareStage by remember { mutableStateOf<String?>(null) }
    var granterInput by remember { mutableStateOf("") }
    var granterNote by remember { mutableStateOf<String?>(null) }
    fun stageOf(st: com.undersigned.app.solana.ReviewTxService.Stage) = when (st) { com.undersigned.app.solana.ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); com.undersigned.app.solana.ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); com.undersigned.app.solana.ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) }
    fun signShare(memo: com.undersigned.protocol.ReviewMemo, then: (Result<String>) -> Unit) { val s = sender ?: return; vm.publish(s, memo, onStage = { shareStage = stageOf(it) }) { r -> shareStage = null; then(r) } }
    var copyMsg by remember { mutableStateOf<String?>(null) }
    val df = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM) }
    fun share(text: String, mime: String, title: String) { try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_SUBJECT, title); putExtra(Intent.EXTRA_TEXT, text) }, title)) } catch (_: Exception) { } }
    fun copyToStore(pkg: String, text: String?) {
        if (com.undersigned.app.util.StoreHandoff.copyAndOpen(context, pkg, text)) copyMsg = context.getString(R.string.pro_copy_done)
    }
    val domainNames by vm.domainNames.collectAsState()
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) { UserPreferences.setAlerts(true); LedgerWatchWorker.schedule(context) } }
    // The store scan reports by notification; ask first if that permission is missing (see MyReviewsScreen).
    val askNotifForScan = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.scanMyStoreReviews() }
    var scanRationale by remember { mutableStateOf(false) }
    if (scanRationale) ScanRationaleSheet(
        onAllow = { scanRationale = false; UserPreferences.scanRationaleShown = true; askNotifForScan.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onSkip = { scanRationale = false; UserPreferences.scanRationaleShown = true; vm.scanMyStoreReviews() },
        onDismiss = { scanRationale = false })
    val termsService = remember { TermsAcceptanceService(context) }
    val myStore by vm.myStoreReviews.collectAsState()
    val scan by vm.importScan.collectAsState()
    val scanResult by vm.importScanResult.collectAsState()
    val scanApp by vm.importScanApp.collectAsState()
    var chosen by remember { mutableStateOf<Set<String>>(emptySet()) }
    var importStage by remember { mutableStateOf<String?>(null) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    val catalogue by vm.catalogue.collectAsState()
    var licenceText by remember { mutableStateOf<String?>(null) }
    var signing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var importOk by remember { mutableStateOf(true) }
    // One confirmation for every action that can't be undone from here (UI review 6 Oct 2026: all were one tap).
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    confirm?.let { c ->
        AlertDialog(onDismissRequest = { confirm = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(c.title), color = RbColors.textPrimary) },
            text = { Text(stringResource(c.body), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) },
            confirmButton = { TextButton(onClick = { confirm = null; c.run() }) { Text(stringResource(c.action), color = RbColors.negative) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    fun open(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }
    fun signIn() {
        val s = sender ?: return; signing = true; error = null
        scope.launch {
            WalletSession.signIn(s, termsService.generateConsentMessage("(pending)")).fold(
                onSuccess = { if (WalletScreening.isSanctioned(it.address)) error = context.getString(R.string.wallet_sanctioned) else { termsService.recordConsentWithSignature(it.address, it.signature, it.signedMessage); UserPreferences.setWallet(it.address); com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.WALLET_CONNECTED) } },
                onFailure = { error = context.errorText(it) },
            ); signing = false
        }
    }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(stringResource(R.string.settings), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionTitle(stringResource(R.string.settings_wallet))
            GlassCard {
                Text(wallet?.let { shortKey(it, 8, 8) } ?: stringResource(R.string.settings_no_wallet), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (wallet != null) SecondaryButton(stringResource(R.string.disconnect_wallet), onClick = { confirm = Confirm(R.string.disconnect_wallet, R.string.confirm_disconnect_body, R.string.disconnect_wallet) { UserPreferences.setWallet(null); com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.WALLET_DISCONNECTED) } })
                else SecondaryButton(if (signing) stringResource(R.string.terms_signing) else stringResource(R.string.settings_signin), enabled = !signing && sender != null, onClick = { signIn() })
                error?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RbColors.negative, fontSize = 12.sp) }
            }

            // Pro, above Your standing (design: Screens D). Read from the ledger; nothing stored locally decides it.
            SectionTitle(stringResource(R.string.pro_title), trailing = { Pill(stringResource(R.string.pro_badge), RbColors.purpleLight) })
            // The one Pro prompt on Settings (owner, 10 Oct 2026), above the card; the import and weekly-list cards below only say "Pro".
            if (pro?.active != true) { ProPrompt(stringResource(R.string.pro_tools), stringResource(R.string.pro_prompt_settings_body), onPro); Spacer(Modifier.height(8.dp)) }
            GlassCard(accent = if (pro?.active == true) RbColors.green else RbColors.glassBorder) {
                val p = pro
                if (p != null && !p.owned) {
                    Text(pluralStringResource(R.plurals.pro_shared_active, p.daysLeft().toInt(), domainNames[p.sharedFrom!!]?.takeIf { it.isNotEmpty() } ?: shortKey(p.sharedFrom, 4, 4), p.daysLeft()), color = RbColors.green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pro_paid_line, df.format(java.util.Date(p.paidAt * 1000)), p.amountLabel, df.format(java.util.Date(p.expiresAt * 1000))), color = RbColors.textSecondary, fontSize = 12.sp)
                    LinkRow(stringResource(R.string.pro_view_licence)) { open(AppConfig.Api.EXPLORER_TX + p.signature) }
                    TextButton(onClick = { vm.removeGranter(p.sharedFrom) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.pro_shared_forget), color = RbColors.textTertiary, fontSize = 12.sp) }
                } else if (p != null) {
                    Text(if (p.active) pluralStringResource(R.plurals.pro_active, p.daysLeft().toInt(), p.daysLeft()) else stringResource(R.string.pro_expired, df.format(java.util.Date(p.expiresAt * 1000))), color = if (p.active) RbColors.green else RbColors.warning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pro_paid_line, df.format(java.util.Date(p.paidAt * 1000)), p.amountLabel, df.format(java.util.Date(p.expiresAt * 1000))), color = RbColors.textSecondary, fontSize = 12.sp)
                    LinkRow(stringResource(R.string.pro_view_licence)) { open(AppConfig.Api.EXPLORER_TX + p.signature) }
                    Text(stringResource(R.string.pro_tied, df.format(java.util.Date(p.expiresAt * 1000))), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    if (p.active) SecondaryButton(stringResource(R.string.pro_renew), onClick = onPro)
                    if (p.active) {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.pro_share_seated), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        val seats = remember(entries) { vm.mySeats() }
                        if (seats.isEmpty()) Text(stringResource(R.string.pro_share_none), color = RbColors.textTertiary, fontSize = 11.sp)
                        seats.forEach { (tag, label) ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label?.let { l -> domainNames[l]?.takeIf { it.isNotEmpty() } ?: shortKey(l, 6, 6) } ?: shortKey(tag, 6, 6), color = RbColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(enabled = shareStage == null && sender != null, onClick = { confirm = Confirm(R.string.confirm_unshare_title, R.string.confirm_unshare_body, R.string.pro_share_revoke) { signShare(com.undersigned.protocol.ReviewMemo.Unshare(tag)) { r -> shareNote = r.fold({ null }, { context.errorText(it) }) } } }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.pro_share_revoke), color = RbColors.warning, fontSize = 12.sp) }
                            }
                        }
                        shareStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }
                        shareNote?.let { Text(it, color = RbColors.textSecondary, fontSize = 12.sp) }
                        Spacer(Modifier.height(6.dp))
                        SecondaryButton(stringResource(R.string.pro_share_title), enabled = shareStage == null, onClick = { shareInput = ""; shareResolved = null; shareNote = null; shareOpen = true })
                    }
                } else {
                    Text(stringResource(R.string.pro_free_keeps), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    if (wallet != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.pro_shared_from_title), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.pro_shared_from_body), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                        granters.forEach { g ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.pro_shared_none, domainNames[g]?.takeIf { it.isNotEmpty() } ?: shortKey(g, 6, 6)), color = RbColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.removeGranter(g) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.pro_shared_forget), color = RbColors.textTertiary, fontSize = 11.sp) }
                            }
                        }
                        OutlinedTextField(value = granterInput, onValueChange = { granterInput = it; granterNote = null }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None, keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii), placeholder = { Text(stringResource(R.string.pro_shared_from_hint), color = RbColors.textTertiary, fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight))
                        granterNote?.let { Text(it, color = RbColors.warning, fontSize = 11.sp) }
                        Spacer(Modifier.height(6.dp))
                        SecondaryButton(stringResource(R.string.pro_shared_from_add), enabled = granterInput.isNotBlank(), onClick = {
                            scope.launch { val w = vm.resolveWallet(granterInput); when { w == null -> granterNote = context.getString(R.string.pro_share_unresolved); w == wallet -> granterNote = context.getString(R.string.pro_share_self); else -> { vm.addGranter(w); vm.resolveDomains(listOf(w)); granterInput = "" } } }
                        })
                    }
                }
                if (p?.active == true) {
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.pro_tools), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    val queue = remember(entries, myStore) { vm.copyQueue() }
                    LinkRow(stringResource(R.string.pro_copy_all) + " · " + stringResource(R.string.pro_copy_count, queue.size)) { copyOpen = true }
                    LinkRow(stringResource(R.string.desk_title)) { onDesk() }
                    Text(stringResource(R.string.pro_export_body), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(stringResource(R.string.pro_export_csv), modifier = Modifier.weight(1f), onClick = { share(vm.exportLedger(json = false), "text/csv", "undersigned-ledger.csv") })
                        SecondaryButton(stringResource(R.string.pro_export_json), modifier = Modifier.weight(1f), onClick = { share(vm.exportLedger(json = true), "application/json", "undersigned-ledger.json") })
                    }
                    Text(stringResource(R.string.pro_tool_watch_title) + " · " + stringResource(if (alerts) R.string.pro_watch_on else R.string.pro_watch_alerts_off), color = RbColors.textTertiary, fontSize = 11.sp)
                }
            }

            if (wallet != null) {
                SectionTitle(stringResource(R.string.settings_standing))
                GlassCard {
                    when {
                        // An operator wallet is never told its reviews count: shown, labelled, never counted (Operator).
                        wallet?.let { com.undersigned.protocol.Operator.isOperator(it) } == true -> Text(stringResource(R.string.standing_operator), color = RbColors.warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
                        selfLoading || selfFacts == null -> Text(stringResource(R.string.review_checking_sgt), color = RbColors.textSecondary, fontSize = 13.sp)
                        else -> {
                            val f = selfFacts!!
                            Text(if (f.hasGenesisToken) stringResource(R.string.standing_sgt_yes, com.undersigned.app.solana.AttestationList.label(context, f.attestation) ?: stringResource(R.string.review_weight_verified)) else stringResource(R.string.standing_sgt_no), color = if (f.hasGenesisToken) RbColors.green else RbColors.warning, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            if (f.hasGenesisToken) { Spacer(Modifier.height(4.dp)); Text(stringResource(R.string.standing_stake, String.format(java.util.Locale.US, "%,.0f", f.stakedSkr), f.stakedSol?.let { String.format(java.util.Locale.US, "%,.2f", it) } ?: "–"), color = RbColors.textSecondary, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(if (pro?.active == true) R.string.pro_standing_note else R.string.standing_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }

            if (wallet != null) {
                SectionTitle(stringResource(R.string.import_title))
                GlassCard {
                    Text(stringResource(R.string.import_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(scan?.let { stringResource(R.string.import_scanning, it) } ?: stringResource(R.string.import_scan), enabled = scan == null && importStage == null, onClick = {
                        val need = Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (need && !UserPreferences.scanRationaleShown) scanRationale = true else vm.scanMyStoreReviews() })
                    scanApp?.let { Text(it, color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
                    val importable = myStore.filter { (pkg, _) -> vm.myReview(pkg) == null }
                    if (scan == null && myStore.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.import_found, myStore.size), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        val isPro = pro?.active == true
                        fun importOne(pkg: String, r: com.undersigned.app.data.StoreReview) {
                            val s = sender ?: return
                            vm.requestImport(s, listOf(pkg to r), onStage = { st -> importStage = when (st) { com.undersigned.app.solana.ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); com.undersigned.app.solana.ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); com.undersigned.app.solana.ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { res -> importStage = null; importMsg = res.fold({ context.resources.getQuantityString(R.plurals.import_done, it, it) }, { it.message }) }
                        }
                        importable.forEach { (pkg, r) ->
                            Row(Modifier.fillMaxWidth().clickable(enabled = importStage == null && isPro) { chosen = if (pkg in chosen) chosen - pkg else chosen + pkg }, verticalAlignment = Alignment.CenterVertically) {
                                if (isPro) Checkbox(checked = pkg in chosen, onCheckedChange = { chosen = if (it) chosen + pkg else chosen - pkg }, enabled = importStage == null, colors = CheckboxDefaults.colors(checkedColor = RbColors.green))
                                Column(Modifier.weight(1f)) { Text(catalogue.firstOrNull { it.packageName == pkg }?.name ?: pkg, color = RbColors.textPrimary, fontSize = 13.sp); Text("${r.rating}★ · ${storeDate(r.createdAt)}" + (r.text?.let { " · ${it.take(60)}" } ?: ""), color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 2) }
                                if (!isPro) TextButton(enabled = importStage == null && sender != null, onClick = { importOne(pkg, r) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(stringResource(R.string.pro_import_one), color = RbColors.purpleLight, fontSize = 11.sp) }
                            }
                        }
                        if (importable.isNotEmpty() && !isPro) {
                            Spacer(Modifier.height(6.dp))
                            Text(importStage ?: stringResource(R.string.pro_import_locked), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                        if (importable.isNotEmpty() && isPro) {
                            Spacer(Modifier.height(6.dp))
                            SecondaryButton(importStage ?: stringResource(R.string.import_publish, chosen.size), enabled = chosen.isNotEmpty() && importStage == null && sender != null, onClick = {
                                val s = sender ?: return@SecondaryButton
                                vm.requestImport(s, importable.filter { it.key in chosen }.map { it.key to it.value }, onStage = { st -> importStage = when (st) { com.undersigned.app.solana.ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); com.undersigned.app.solana.ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); com.undersigned.app.solana.ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { r -> importStage = null; chosen = emptySet(); importOk = r.isSuccess; importMsg = r.fold({ context.resources.getQuantityString(R.plurals.import_done, it, it) }, { context.errorText(it) }) }
                            })
                            Text(stringResource(R.string.import_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    } else if (scan == null && importMsg == null) scanResult?.let { (failed, total) ->
                        if (failed > 0) Text(stringResource(R.string.import_incomplete, failed, total), color = RbColors.warning, fontSize = 11.sp)
                        else Text(stringResource(R.string.import_none), color = RbColors.textTertiary, fontSize = 11.sp)
                    }
                    importMsg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = if (importOk) RbColors.green else RbColors.negative, fontSize = 12.sp) }
                }
            }

            SectionTitle(stringResource(R.string.alerts_title))
            GlassCard {
                fun setAlerts(on: Boolean) {
                    if (!on) { UserPreferences.setAlerts(false); LedgerWatchWorker.cancel(context) }
                    else if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else { UserPreferences.setAlerts(true); LedgerWatchWorker.schedule(context) }
                }
                Row(Modifier.toggleable(value = alerts, role = androidx.compose.ui.semantics.Role.Switch) { setAlerts(it) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.alerts_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        Text(pluralStringResource(R.plurals.alerts_following, follows.size, follows.size), color = RbColors.textTertiary, fontSize = 11.sp)
                    }
                    Switch(checked = alerts, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = RbColors.green, checkedTrackColor = RbColors.green.copy(alpha = 0.4f)))
                }
                // Each kind of alert on its own switch (owner, 10 Oct 2026), as a reviewer and as a publisher.
                if (alerts) {
                    LaunchedEffect(Unit) { UserPreferences.loadNotifOff() }
                    val off by UserPreferences.notifOff.collectAsState()
                    listOf(false to R.string.notif_group_reviewer, true to R.string.notif_group_publisher).forEach { (pub, title) ->
                        Text(stringResource(title).uppercase(), color = RbColors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
                        com.undersigned.app.notify.NotifKind.entries.filter { it.publisher == pub }.forEach { k ->
                            val on = k.key !in off
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = on, role = androidx.compose.ui.semantics.Role.Switch) { UserPreferences.setNotif(k.key, it) }, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(k.label), color = RbColors.textSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Switch(checked = on, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = RbColors.green, checkedTrackColor = RbColors.green.copy(alpha = 0.4f)))
                            }
                        }
                    }
                }
            }

            // The weekly reminder: one notification a week at most, off by default, naming how many apps here are unreviewed.
            GlassCard {
                var remind by remember { mutableStateOf(UserPreferences.reviewReminder) }
                fun setRemind(on: Boolean) {
                    remind = on; UserPreferences.reviewReminder = on
                    if (on) com.undersigned.app.notify.ReviewReminderWorker.schedule(context) else com.undersigned.app.notify.ReviewReminderWorker.cancel(context)
                }
                Row(Modifier.toggleable(value = remind, role = androidx.compose.ui.semantics.Role.Switch) { setRemind(it) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.remind_setting), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.remind_setting_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Switch(checked = remind, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = RbColors.green, checkedTrackColor = RbColors.green.copy(alpha = 0.4f)))
                }
                // Auto-translate (Pro): every note and reply as it is shown, each an AI call, labelled a suggestion (owner, 11 Oct 2026).
                var autoTr by remember { mutableStateOf(UserPreferences.autoTranslate) }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.toggleable(value = autoTr, enabled = pro?.active == true, role = androidx.compose.ui.semantics.Role.Switch) { autoTr = it; UserPreferences.autoTranslate = it }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.settings_auto_translate), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(8.dp)); Pill(stringResource(R.string.pro_badge), RbColors.purpleLight) }
                        Text(stringResource(R.string.settings_auto_translate_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Switch(checked = autoTr && pro?.active == true, onCheckedChange = null, enabled = pro?.active == true, colors = SwitchDefaults.colors(checkedThumbColor = RbColors.green, checkedTrackColor = RbColors.green.copy(alpha = 0.4f)))
                }
            }

            // Review nudges: one app a day at most, each asked about once, off by default (feature list, 8 Oct 2026).
            GlassCard {
                var nudge by remember { mutableStateOf(UserPreferences.reviewNudge) }
                fun setNudge(on: Boolean) {
                    nudge = on; UserPreferences.reviewNudge = on
                    if (on) com.undersigned.app.notify.UseNudgeWorker.schedule(context) else com.undersigned.app.notify.UseNudgeWorker.cancel(context)
                }
                Row(Modifier.toggleable(value = nudge, role = androidx.compose.ui.semantics.Role.Switch) { setNudge(it) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.nudge_setting), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.nudge_setting_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Switch(checked = nudge, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = RbColors.green, checkedTrackColor = RbColors.green.copy(alpha = 0.4f)))
                }
            }

            // The dApp list: Pro refreshes it in the background every Monday morning; everyone has the Update button on Browse.
            SectionTitle(stringResource(R.string.settings_catalogue_title), trailing = { Pill(stringResource(R.string.pro_badge), RbColors.purpleLight) })
            GlassCard {
                var auto by remember { mutableStateOf(UserPreferences.catalogueAuto) }
                val isPro = pro?.active == true
                fun setAuto(on: Boolean) {
                    auto = on; UserPreferences.catalogueAuto = on
                    com.undersigned.app.notify.CatalogueWorker.ensureScheduled(context)
                }
                Row(Modifier.toggleable(value = auto && isPro, enabled = isPro, role = androidx.compose.ui.semantics.Role.Switch) { setAuto(it) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_catalogue_auto), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_catalogue_auto_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Switch(checked = auto && isPro, onCheckedChange = null, enabled = isPro, colors = SwitchDefaults.colors(checkedThumbColor = RbColors.green, checkedTrackColor = RbColors.green.copy(alpha = 0.4f)))
                }
                Spacer(Modifier.height(6.dp))
                val catAt by vm.catalogueRefreshedAt.collectAsState()
                val refreshingCat by vm.catalogueRefreshing.collectAsState()
                val catalogueNow by vm.catalogue.collectAsState()
                val n = java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(catalogueNow.size)
                Text(if (catAt > 0) stringResource(R.string.catalogue_line, n, df.format(java.util.Date(catAt))) else stringResource(R.string.catalogue_line_bundled, n), color = RbColors.textTertiary, fontSize = 11.sp)
                SecondaryButton(stringResource(R.string.catalogue_update), enabled = !refreshingCat, onClick = { vm.refreshCatalogue() }, modifier = Modifier.padding(top = 6.dp))
                // Icon status: how many of the list's icons are on the phone, and what the last lookup hit.
                val iconMap by vm.iconCache.state.collectAsState()
                val withIcon = remember(iconMap, catalogueNow) { catalogueNow.count { com.undersigned.app.data.BundledIcons.source(context, it.packageName) != null || !iconMap[it.packageName].isNullOrEmpty() } }
                Text(stringResource(R.string.icons_status, java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(withIcon), n) + (vm.iconCache.lastError?.let { " · $it" } ?: ""),
                    color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }

            SectionTitle(stringResource(R.string.density_title))
            GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.undersigned.app.ui.components.TagChip(stringResource(R.string.density_balanced), selected = !dense) { UserPreferences.setDense(false) }
                    com.undersigned.app.ui.components.TagChip(stringResource(R.string.density_dense), selected = dense) { UserPreferences.setDense(true) }
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.density_body), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            }

            SectionTitle(stringResource(R.string.attest_title))
            GlassCard {
                Text(stringResource(R.string.attest_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                val attests by vm.attestations.collectAsState()
                attests.forEach { a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.label, color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        com.undersigned.app.ui.components.Pill(stringResource(if (a.source == com.undersigned.app.solana.Attestation.REMOTE) R.string.attest_remote else R.string.attest_bundled), if (a.source == com.undersigned.app.solana.Attestation.REMOTE) RbColors.info else RbColors.green)
                    }
                    Text(shortKey(a.mintAuthority, 6, 6), color = RbColors.textTertiary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(if (vm.attestationsGoverned) R.string.attest_governed else R.string.attest_local), color = RbColors.textTertiary, fontSize = 11.sp)
            }

            SectionTitle(stringResource(R.string.settings_ledger))
            GlassCard {
                Text(stringResource(R.string.settings_ledger_body, shortKey(AppConfig.Ledger.REGISTRY, 6, 6)), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(stringResource(R.string.settings_refresh_ledger), onClick = { vm.syncLedger() })
                LinkRow(stringResource(R.string.stats_open)) { onStats() }
                LinkRow(stringResource(R.string.pay_open)) { onPayments() }
                LinkRow(stringResource(R.string.watch_title)) { onWatch() }
                LinkRow(stringResource(R.string.lb_open)) { onLeaderboard() }
            }

            SectionTitle(stringResource(R.string.settings_hidden))
            GlassCard {
                Text(pluralStringResource(R.plurals.settings_hidden_body, hidden.size, hidden.size), color = RbColors.textSecondary, fontSize = 12.sp)
                Text(stringResource(R.string.settings_hidden_sub), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                // Settings offered "Unhide all" and never showed what (design: Screens E). Each hidden record, with its own Unhide.
                hidden.forEach { sig ->
                    val e = vm.entryOf(sig)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().background(RbColors.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(10.dp)) {
                        val who = e?.let { domainNames[it.signer]?.takeIf { n -> n.isNotEmpty() } ?: shortKey(it.signer, 4, 4) } ?: shortKey(sig, 4, 4)
                        val app = (e?.memo as? com.undersigned.protocol.ReviewMemo.Review)?.packageName?.let { p -> catalogue.firstOrNull { it.packageName == p }?.name ?: p }
                        Text(listOfNotNull(who, app, e?.let { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it.blockTime * 1000)) }).joinToString(" · "), color = RbColors.textTertiary, fontSize = 11.sp)
                        ((e?.memo as? com.undersigned.protocol.ReviewMemo.Review)?.text ?: (e?.memo as? com.undersigned.protocol.ReviewMemo.Reply)?.text)?.takeIf { sig !in vm.modHidden.value }?.let { Text("\u201C$it\u201D", color = RbColors.textSecondary, fontSize = 12.sp, maxLines = 2) }
                        TextButton(onClick = { vm.unhide(sig) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.settings_unhide), color = RbColors.purpleLight, fontSize = 12.sp) }
                    }
                }
                if (hidden.isNotEmpty()) { Spacer(Modifier.height(10.dp)); SecondaryButton(stringResource(R.string.settings_unhide_all), onClick = { confirm = Confirm(R.string.settings_unhide_all, R.string.confirm_unhide_body, R.string.settings_unhide_all) { UserPreferences.unhideAll() } }) }
            }

            SectionTitle(stringResource(R.string.settings_legal))
            GlassCard {
                Text(stringResource(R.string.settings_affiliation), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                // Who runs Undersigned, beside the affiliation line (1.1.5): the operator publishes in this store too.
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.operator_disclosure), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(6.dp))
                LinkRow(stringResource(R.string.terms_link_terms)) { open(AppConfig.Identity.URI + "terms.html") }
                LinkRow(stringResource(R.string.terms_link_privacy)) { open(AppConfig.Identity.URI + "privacy.html") }
                LinkRow(stringResource(R.string.terms_link_disclaimer)) { open(AppConfig.Identity.URI + "disclaimer.html") }
                LinkRow(stringResource(R.string.settings_sanctions)) { open(AppConfig.Identity.URI + "sanctions.html") }
                LinkRow(stringResource(R.string.settings_licence)) { open(AppConfig.Identity.URI + "license.html") }
                LinkRow(stringResource(R.string.pro_refunds)) { open(AppConfig.Identity.URI + "terms.html#pro") }
                LinkRow(stringResource(R.string.settings_font_licence)) { licenceText = try { context.assets.open("licenses/OFL-1.1.txt").bufferedReader().use { it.readText() } } catch (_: Exception) { "OFL-1.1" } }
            }

            SectionTitle(stringResource(R.string.settings_data))
            GlassCard {
                // Fleet analytics opt-out (default on): one event name per action, no identifier; off means nothing is sent.
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = analyticsOn, role = androidx.compose.ui.semantics.Role.Switch) { UserPreferences.setAnalytics(it) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_analytics), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.settings_analytics_hint), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    Switch(checked = analyticsOn, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = RbColors.green, checkedTrackColor = RbColors.green.copy(alpha = 0.4f)))
                }
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.settings_data_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(stringResource(R.string.settings_export_consent), onClick = {
                    try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_TEXT, termsService.exportConsentRecordAsJson()) }, null)) } catch (_: Exception) { }
                })
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.settings_delete_data), onClick = { confirm = Confirm(R.string.settings_delete_data, R.string.confirm_delete_body, R.string.confirm_delete_action) {
                    termsService.deleteAllConsentData(); vm.clearLocalCaches(); UserPreferences.clearAll()
                    try { (context as? android.app.Activity)?.recreate() } catch (_: Exception) { }
                } })
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(stringResource(R.string.intro_replay), onClick = onIntro)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.tour_replay), onClick = onTour)
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.howto_open), onClick = onHowTo)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.settings_about, BuildConfig.VERSION_NAME), color = RbColors.textDim, fontSize = 11.sp)
            Text(stringResource(R.string.settings_contact), color = RbColors.textTertiary, fontSize = 11.sp)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (shareOpen) {
        val displace = remember(entries) { vm.wouldDisplace() }
        val tag = shareResolved?.let { com.undersigned.protocol.ProSharing.tag(wallet ?: "", it) }
        AlertDialog(onDismissRequest = { if (shareStage == null) shareOpen = false }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.pro_share_title), color = RbColors.textPrimary) },
            text = { Column {
                Text(stringResource(R.string.pro_share_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = shareInput, onValueChange = { shareInput = it; shareResolved = null; shareNote = null }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None, keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii), enabled = shareStage == null, placeholder = { Text(stringResource(R.string.pro_share_hint), color = RbColors.textTertiary, fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight))
                shareResolved?.let { Text(stringResource(R.string.pro_share_resolved, domainNames[it]?.takeIf { n -> n.isNotEmpty() } ?: shortKey(it, 6, 6)), color = RbColors.green, fontSize = 12.sp) }
                if (shareResolved != null && displace != null && tag != null && tag !in vm.mySeats().map { it.first }) Text(stringResource(R.string.pro_share_displace, vm.mySeats().firstOrNull { it.first == displace }?.second?.let { shortKey(it, 6, 6) } ?: shortKey(displace, 6, 6)), color = RbColors.warning, fontSize = 12.sp, lineHeight = 16.sp)
                shareStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }
                shareNote?.let { Text(it, color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp) }
            } },
            confirmButton = {
                if (shareResolved == null) TextButton(enabled = shareInput.isNotBlank() && shareStage == null, onClick = { scope.launch { val w = vm.resolveWallet(shareInput); shareNote = null; when { w == null -> shareNote = context.getString(R.string.pro_share_unresolved); w == wallet -> shareNote = context.getString(R.string.pro_share_self); else -> { shareResolved = w; vm.resolveDomains(listOf(w)) } } } }) { Text(stringResource(R.string.pro_share_resolve), color = RbColors.purpleLight) }
                else TextButton(enabled = shareStage == null && sender != null && tag != null, onClick = {
                    val w = shareResolved ?: return@TextButton; val t = tag ?: return@TextButton
                    signShare(com.undersigned.protocol.ReviewMemo.Share(t)) { r -> r.fold(onSuccess = { UserPreferences.labelSeat(t, w); shareNote = context.getString(R.string.pro_share_done); shareResolved = null; shareInput = "" }, onFailure = { shareNote = it.message }) }
                }) { Text(stringResource(R.string.pro_share_sign), color = RbColors.purpleLight) }
            },
            dismissButton = { TextButton(enabled = shareStage == null, onClick = { shareOpen = false }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    if (copyOpen) {
        val queue = remember(entries, myStore) { vm.copyQueue() }
        AlertDialog(onDismissRequest = { copyOpen = false }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.pro_copy_all), color = RbColors.textPrimary) },
            text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.pro_copy_review_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.review_also_store_hint), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(8.dp))
                if (queue.isEmpty()) Text(stringResource(R.string.pro_copy_none), color = RbColors.textTertiary, fontSize = 12.sp)
                queue.forEach { e -> val r = e.memo as com.undersigned.protocol.ReviewMemo.Review
                    Row(Modifier.fillMaxWidth().clickable { copyToStore(r.packageName, r.text) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(catalogue.firstOrNull { it.packageName == r.packageName }?.name ?: r.packageName, color = RbColors.textPrimary, fontSize = 13.sp); Text("${r.rating}★" + (r.text?.let { " · ${it.take(50)}" } ?: ""), color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1) }
                        Text(stringResource(R.string.pro_copy_review), color = RbColors.purpleLight, fontSize = 11.sp)
                    }
                }
                copyMsg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RbColors.green, fontSize = 12.sp) }
            } },
            confirmButton = { TextButton(onClick = { copyOpen = false }) { Text(stringResource(android.R.string.ok), color = RbColors.purpleLight) } })
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
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = RbColors.textPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = RbColors.textTertiary, modifier = Modifier.size(16.dp))
    }
}

/** An action that asks first: what it is, what it does, and the confirm button's words. */
private class Confirm(val title: Int, val body: Int, val action: Int, val run: () -> Unit)
