package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.solana.ReviewTxService
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.app.util.StoreHandoff
import com.undersigned.protocol.ReviewMemo
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.text.DateFormat
import java.util.Date

/**
 * Every review the signed-in wallet has written, one card per dApp: the signed review on the ledger beside the review
 * in the dApp Store, searchable by app or text. Built because the owner could not find their own reviews (26 Sep 2026):
 * signed ones lived only on each app's page and store ones only behind a four-minute scan in Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyReviewsScreen(vm: MainViewModel, sender: ActivityResultSender?, onBack: () -> Unit, onOpen: (String) -> Unit, onReview: (String) -> Unit, onPro: () -> Unit, onToReview: () -> Unit = {}, onSignIn: () -> Unit = {}) {
    val context = LocalContext.current
    val wallet by vm.wallet.collectAsState()
    val entries by vm.entries.collectAsState()
    val store by vm.myStoreReviews.collectAsState()
    val scannedAt by vm.myStoreScannedAt.collectAsState()
    val scan by vm.importScan.collectAsState()
    val scanApp by vm.importScanApp.collectAsState()
    val scanResult by vm.importScanResult.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    val pro by vm.pro.collectAsState()
    var query by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(true) }
    var handoff by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val all = remember(entries, store, catalogue, wallet) { vm.myDapps() }
    LaunchedEffect(all) { vm.resolveIcons(all.map { it.pkg }) }
    val shown = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all else all.filter { d ->
            d.name.lowercase().contains(q) || d.pkg.lowercase().contains(q) ||
                ((d.signed?.memo as? ReviewMemo.Review)?.text ?: "").lowercase().contains(q) || (d.store?.text ?: "").lowercase().contains(q)
        }
    }
    // The scan runs in the background and says it has finished by notification. Without notification permission (only
    // asked for when alerts are switched on) that notice never came: popcat's scan finished silently (28 Sep 2026).
    val askNotif = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { vm.scanMyStoreReviews() }
    // Design review 28 Sep 2026 (handoff 2, F8): the first time, explain before Android's prompt; after that go straight on.
    var rationale by remember { mutableStateOf(false) }
    fun startScan() {
        val need = android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        when {
            need && !com.undersigned.app.data.UserPreferences.scanRationaleShown -> rationale = true
            need -> vm.scanMyStoreReviews()   // asked once already; a "no" in settings is respected, the scan still runs
            else -> vm.scanMyStoreReviews()
        }
    }
    if (rationale) ScanRationaleSheet(
        onAllow = { rationale = false; com.undersigned.app.data.UserPreferences.scanRationaleShown = true; askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
        onSkip = { rationale = false; com.undersigned.app.data.UserPreferences.scanRationaleShown = true; vm.scanMyStoreReviews() },
        onDismiss = { rationale = false })
    fun date(sec: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(sec * 1000))
    fun importMany(list: List<Pair<String, com.undersigned.app.data.StoreReview>>) {
        val s = sender ?: return
        message = null
        vm.requestImport(s, list, onStage = { st -> stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { res ->
            stage = null
            messageOk = res.isSuccess
            message = res.fold({ context.resources.getQuantityString(R.plurals.import_done, it, it) }, { context.errorText(it) })
        }
    }
    fun importOne(pkg: String, r: com.undersigned.app.data.StoreReview) = importMany(listOf(pkg to r))

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(stringResource(R.string.my_reviews_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (wallet == null) {
            Text(stringResource(R.string.my_reviews_sign_in), color = RbColors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            SecondaryButton(stringResource(R.string.settings_signin), onClick = onSignIn, modifier = Modifier.padding(horizontal = 20.dp))   // not a dead end
            return@Column
        }
        // Apps on this phone not reviewed yet: the way into reviewing several at once.
        val entriesNow by vm.entries.collectAsState()
        val toReview = remember(entriesNow) { vm.toReview().size }
        if (toReview > 0) TextButton(onClick = onToReview, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(pluralStringResource(R.plurals.to_review_entry, toReview, toReview), color = RbColors.purpleLight, fontSize = 13.sp)
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.my_reviews_search), color = RbColors.textTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RbColors.textTertiary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight),
        )
        // Where the store side comes from, and a way to refresh it. The scan is paced (the store's CDN blocks fast
        // readers), so progress is shown here rather than hidden in Settings.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        scan != null -> stringResource(R.string.import_scanning, scan!!)
                        scannedAt != null -> stringResource(R.string.my_reviews_scanned, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(scannedAt!!)))
                        else -> stringResource(R.string.my_reviews_never_scanned)
                    }, color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                if (scan == null) TextButton(onClick = { startScan() }) { Text(stringResource(R.string.import_scan), color = RbColors.purpleLight, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            scanApp?.let { Text(it, color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            // Every store review not yet signed, in one wallet approval (import review, 10 Oct 2026).
            val importable = all.filter { it.signed == null && it.store != null }
            // All in one signature is the Pro tool (Settings already gated it; here it was open to everyone until 10 Oct 2026).
            // Free imports one at a time from each card below. The prompt is the one Pro prompt on this screen.
            if (importable.size >= 2 && scan == null) {
                if (pro?.active == true) com.undersigned.app.ui.components.PrimaryButton(stringResource(R.string.import_all, importable.size),
                    onClick = { importMany(importable.map { it.pkg to it.store!! }) }, enabled = stage == null && sender != null, modifier = Modifier.padding(top = 6.dp))
            }
            scanResult?.let { (failed, total) -> if (scan == null && failed > 0) Text(stringResource(R.string.import_incomplete, failed, total), color = RbColors.warning, fontSize = 11.sp) }
            (stage ?: message)?.let { Text(it, color = if (stage != null) RbColors.purpleLight else if (messageOk) RbColors.green else RbColors.negative, fontSize = 12.sp) }
        }
        if (shown.isEmpty()) {
            Text(stringResource(if (all.isEmpty()) R.string.my_reviews_empty else R.string.my_reviews_no_match), color = RbColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(20.dp))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            // Without Pro, the prompt sits first in the list (it scrolls away) rather than in the fixed header.
            if (pro?.active != true && scan == null && all.count { it.signed == null && it.store != null } >= 2) item(key = "pro-prompt") {
                com.undersigned.app.ui.components.ProPrompt(stringResource(R.string.pro_tool_import_title), stringResource(R.string.pro_tool_import_body), onPro)
                Spacer(Modifier.height(10.dp))
            }
            items(shown, key = { it.pkg }) { d ->
                GlassCard(accent = if (d.differs) RbColors.info else RbColors.glassBorder) {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(d.pkg) }, verticalAlignment = Alignment.CenterVertically) {
                        DAppIcon(d.pkg, d.name, icons[d.pkg]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(d.name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    if (d.differs) Text(stringResource(R.string.my_reviews_differs), color = RbColors.info, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

                    // Ledger side
                    Side(stringResource(R.string.my_reviews_signed))
                    val r = d.signed?.memo as? ReviewMemo.Review
                    if (r != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) { RatingStars(r.rating.toDouble(), size = 12); Spacer(Modifier.width(6.dp)); Text(date(d.signed.blockTime) + " · v${r.versionCode}", color = RbColors.textTertiary, fontSize = 11.sp) }
                        if (vm.isSelfPublished(d.signed)) Text(stringResource(R.string.review_self_published), color = RbColors.textTertiary, fontSize = 11.sp)
                        d.signed?.let { vm.modHidden.collectAsState().value[it.signature] }?.let { com.undersigned.app.ui.components.ModHiddenText(it) } ?: r.text?.let { Text(it, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        TextButton(onClick = { onReview(d.pkg) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.detail_update_review), color = RbColors.purpleLight, fontSize = 12.sp) }
                    } else {
                        Text(stringResource(R.string.my_reviews_not_signed), color = RbColors.textTertiary, fontSize = 12.sp)
                        d.store?.let { s -> TextButton(enabled = stage == null && sender != null, onClick = { importOne(d.pkg, s) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.import_publish, 1), color = RbColors.green, fontSize = 12.sp) } }
                    }

                    // Store side — hidden until a scan has run; the header already says how to get it.
                    val s = d.store
                    if (s != null || scannedAt != null || scan != null) Side(stringResource(R.string.my_reviews_store))
                    if (s != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) { RatingStars(s.rating.toDouble(), size = 12); Spacer(Modifier.width(6.dp)); Text(storeDate(s.createdAt), color = RbColors.textTertiary, fontSize = 11.sp) }
                        s.text?.let { Text(it, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        TextButton(onClick = { StoreHandoff.copyAndOpen(context, d.pkg, null) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.detail_open_store), color = RbColors.purpleLight, fontSize = 12.sp) }
                    } else if (scannedAt != null || scan != null) {
                        Text(stringResource(R.string.my_reviews_not_in_store), color = RbColors.textTertiary, fontSize = 12.sp)
                        if (r != null) {
                            TextButton(onClick = { handoff = d.pkg to r.text }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.pro_copy_review), color = RbColors.purpleLight, fontSize = 12.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    handoff?.let { (pkg, text) -> StoreHandoffDialog(pkg, text) { handoff = null } }
}

@Composable
private fun Side(label: String) {
    Text(label.uppercase(), color = RbColors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

/** "Hear when the scan is done" (design F8): shown once, before Android's notification prompt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanRationaleSheet(onAllow: () -> Unit, onSkip: () -> Unit, onDismiss: () -> Unit = {}) {
    // A swipe or a tap outside closes the sheet and starts nothing (it used to start the multi-minute scan).
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = RbColors.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(RbColors.purple.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Default.Notifications, contentDescription = null, tint = RbColors.purpleLight) }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.scan_notif_title), color = RbColors.textPrimary, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(stringResource(R.string.scan_notif_body), color = RbColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.scan_notif_note), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            PrimaryButton(stringResource(R.string.scan_notif_allow), onClick = onAllow)
            TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.scan_notif_skip), color = RbColors.purpleLight) }
        }
    }
}
