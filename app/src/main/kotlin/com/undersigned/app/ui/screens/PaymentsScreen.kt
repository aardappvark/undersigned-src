package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.undersigned.app.AppConfig
import com.undersigned.app.R
import com.undersigned.app.ledger.PaymentAnalytics
import com.undersigned.app.ledger.PaymentBoard
import com.undersigned.app.ledger.Prices
import com.undersigned.app.ledger.PublisherVerifier
import com.undersigned.app.solana.ReviewTxService
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.Payments
import com.undersigned.protocol.ReviewMemo
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/**
 * The payments board: dApps whose treasury is confirmed by BOTH the treasury wallet (an on-chain `trs` record it signed)
 * and the publisher's store-listed website, ordered by what they were paid on-chain, at today's prices. Built as a
 * dogfood of the idea (27 Sep 2026): nothing is charged for a listing, and the order comes from measured payments only.
 */
private enum class PayPage { BOARD, LIST, LISTINGS, PREVIEW }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PaymentsScreen(vm: MainViewModel, sender: ActivityResultSender?, onBack: (() -> Unit)?, onOpen: (String) -> Unit, onHistory: (String) -> Unit = onOpen) {
    val context = LocalContext.current
    val rows by vm.boardRows.collectAsState()
    val loading by vm.boardLoading.collectAsState()
    val preview by vm.boardPreview.collectAsState()
    val quotes by vm.quotes.collectAsState()
    val wallet by vm.wallet.collectAsState()
    val entries by vm.entries.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    val details by vm.storeDetails.collectAsState()
    val lineCheck by vm.lineCheck.collectAsState()
    val boardError by vm.boardError.collectAsState()
    var withdrawFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entries) { vm.loadBoard() }
    LaunchedEffect(rows) { vm.resolveIcons(rows.map { it.claim.packageName }) }
    var page by remember { mutableStateOf(PayPage.BOARD) }
    var step by remember { mutableStateOf(1) }
    var treasury by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var listCode by remember { mutableStateOf("") }
    // Who signs the listing (owner, 7 Oct 2026: some devs won't sign with their treasury wallet): the treasury itself,
    // or the app's proven publisher naming the treasury (`trd`). Both pay the same fee; the website line names the treasury either way.
    var byPublisher by remember { mutableStateOf(false) }
    var treasuryAddr by remember { mutableStateOf("") }
    fun listMemo(): ReviewMemo? = runCatching { (if (byPublisher) ReviewMemo.TreasuryFor(pkg, listCode, treasuryAddr) else ReviewMemo.Treasury(pkg, listCode)).also { ReviewMemo.encode(it) } }.getOrNull()
    var stage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val mine = remember(entries, wallet) { vm.myTreasuryClaims() }
    fun sign(memo: ReviewMemo) {
        val s = sender ?: return
        message = null
        vm.publish(s, memo, onStage = { st -> stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { r ->
            stage = null
            message = r.fold({ true to context.getString(R.string.action_published, shortKey(it, 6, 6)) }, { false to context.errorText(it) })
            if (r.isSuccess) vm.loadBoard(force = true)
        }
    }
    fun payList(rail: com.undersigned.app.solana.ProPaymentService.Rail, memo: ReviewMemo) {
        val s = sender ?: return
        message = null
        vm.payListing(s, rail, memo, onStage = { st -> stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { r ->
            stage = null
            message = r.fold({ true to context.getString(R.string.action_published, shortKey(it, 6, 6)) }, { false to context.errorText(it) })
            if (r.isSuccess) vm.loadBoard(force = true)
        }
    }
    val back = { if (page == PayPage.BOARD) onBack?.invoke() else if (page == PayPage.LIST && step == 2) step = 1 else page = PayPage.BOARD }
    androidx.activity.compose.BackHandler(enabled = page != PayPage.BOARD) { back() }
    val title = stringResource(when (page) { PayPage.BOARD -> R.string.pay_title; PayPage.LIST -> R.string.pay_list_title; PayPage.LISTINGS -> R.string.pay_my_claims; PayPage.PREVIEW -> R.string.pay_preview_title })

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        // As a tab the board has no back arrow; its own pages (list, my listings, preview) still do.
        val arrow = onBack != null || page != PayPage.BOARD
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (arrow) 8.dp else 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (arrow) BackButton({ back() })
            Text(title, color = RbColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        when (page) {
            // G1 — the board, then the publisher tools.
            PayPage.BOARD -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                // Store-wide analytics by window (owner, 10 Oct 2026), above the board of confirmed listings.
                PaymentAnalyticsBlock(vm, onOpen, onHistory)
                Text(stringResource(R.string.pay_title), color = RbColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 4.dp))
                Text(stringResource(R.string.pay_intro), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 18.sp)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    Text(stringResource(R.string.pay_usd_note), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.pay_order), color = RbColors.textTertiary, fontSize = 11.sp)
                }
                if (loading) Text(stringResource(R.string.pay_loading), color = RbColors.purpleLight, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                val listed = rows.filter { it.confirmed && it.tally != null }
                Spacer(Modifier.height(10.dp))
                if (listed.isEmpty() && !loading) {
                    if (boardError) { Text(stringResource(R.string.load_failed), color = RbColors.negative, fontSize = 13.sp); SecondaryButton(stringResource(R.string.retry), onClick = { vm.loadBoard(force = true) }, modifier = Modifier.padding(top = 8.dp)) }
                    else Text(stringResource(R.string.pay_empty), color = RbColors.textSecondary, fontSize = 13.sp)
                }
                // Payments over time across the board (owner, 9 Oct 2026): each listing's months, top five apart, the rest together.
                if (listed.any { it.monthUsd.isNotEmpty() }) { PaymentsOverTime(listed); Spacer(Modifier.height(12.dp)) }
                if (listed.isNotEmpty()) GlassCard {
                    listed.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(color = RbColors.surfaceVariant)
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onHistory(row.claim.packageName) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${i + 1}", color = RbColors.textTertiary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.widthIn(min = 26.dp))
                            DAppIcon(row.claim.packageName, row.name, icons[row.claim.packageName]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.name, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                row.tally?.let { t -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    LabelNumber(stringResource(R.string.pay_label_payers), "${t.payers}", RbColors.textSecondary)
                                    LabelNumber(stringResource(R.string.pay_label_attested_short), "${t.attestedPayers}", RbColors.green)
                                } }
                                if (!row.complete) Text(stringResource(R.string.pay_incomplete), color = RbColors.warning, fontSize = 11.sp)
                            }
                            row.usd?.let { Text(usd(it), color = RbColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }
                val waiting = rows.count { !it.confirmed }
                if (waiting > 0) Text(stringResource(R.string.pay_waiting_list, waiting), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                // Treasuries found in app code (owner, 9 Oct 2026): their own card under the board, by name, never ranked.
                Spacer(Modifier.height(14.dp))
                FoundInCode(vm, onHistory)
                Text(stringResource(R.string.pay_tools).uppercase(), color = RbColors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RbColors.surface)) {
                    if (wallet != null) {
                        ToolRow(stringResource(R.string.pay_list_title), pluralStringResource(R.plurals.pay_steps, 2, 2), strong = true) { page = PayPage.LIST; step = 1; pkg = ""; listCode = "" }
                        HorizontalDivider(color = RbColors.surfaceVariant)
                        ToolRow(stringResource(R.string.pay_my_claims), if (mine.isNotEmpty()) "${mine.size}" else null, strong = true) { page = PayPage.LISTINGS }
                        HorizontalDivider(color = RbColors.surfaceVariant)
                    }
                    ToolRow(stringResource(R.string.pay_preview_title), null, strong = false) { page = PayPage.PREVIEW }
                }
                Spacer(Modifier.height(32.dp))
            }
            // G2 — preview: public data for any treasury and code, attributed to nothing.
            PayPage.PREVIEW -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.pay_preview_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Field(treasury, { treasury = it.trim() }, stringResource(R.string.pay_field_treasury))
                Field(code, { code = it.trim() }, stringResource(R.string.pay_field_code))
                Spacer(Modifier.height(12.dp))
                val ok = Payments.validCode(code) && runCatching { com.undersigned.app.util.Base58.decode(treasury).size == 32 }.getOrDefault(false)
                SecondaryButton(if (loading) stringResource(R.string.pay_loading) else stringResource(R.string.pay_preview), enabled = ok && !loading, onClick = { vm.previewTreasury(treasury, code) })
                preview?.takeIf { it.claim.treasury == treasury && it.claim.code == code }?.let {
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).dashedOutline(RbColors.textDim, 16.dp).padding(16.dp)) {
                        OutlinePill(stringResource(R.string.pay_preview_badge)); Spacer(Modifier.height(10.dp))
                        TallyView(it, quotes)
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
            // G3–G4 — list your dApp: add the line, then sign.
            PayPage.LIST -> Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StepDot(1, step); Text(stringResource(R.string.pay_step_line), color = if (step == 1) RbColors.textPrimary else RbColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        StepDot(2, step); Text(stringResource(R.string.pay_step_sign), color = if (step == 2) RbColors.textPrimary else RbColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    }
                    val memo = listMemo()
                    val listedTreasury = if (byPublisher) treasuryAddr else (wallet ?: "")
                    val isPub = wallet != null && pkg.isNotEmpty() && vm.isVerifiedPublisher(pkg, wallet!!)
                    if (step == 1) {
                        // Two ways to list, said plainly up front.
                        Text(stringResource(R.string.pay_who_title), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        listOf(false to (R.string.pay_who_treasury to R.string.pay_who_treasury_body), true to (R.string.pay_who_publisher to R.string.pay_who_publisher_body)).forEach { (pub, labels) ->
                            val sel = byPublisher == pub
                            Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel) RbColors.purple.copy(alpha = 0.18f) else RbColors.surface)
                                .selectable(selected = sel, role = androidx.compose.ui.semantics.Role.RadioButton) { byPublisher = pub }.padding(12.dp)) {
                                Text(stringResource(labels.first), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(labels.second), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                        Field(pkg, { pkg = it.trim() }, stringResource(R.string.pay_field_package))
                        if (byPublisher) {
                            Field(treasuryAddr, { treasuryAddr = it.trim() }, stringResource(R.string.pay_field_treasury_addr))
                            if (pkg.isNotEmpty()) Text(stringResource(if (isPub) R.string.pay_publisher_ok else R.string.pay_publisher_needed), color = if (isPub) RbColors.green else RbColors.warning, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                        Field(listCode, { listCode = it.trim() }, stringResource(R.string.pay_field_code))
                        if (memo == null && (pkg.isNotEmpty() || listCode.isNotEmpty())) Text(stringResource(R.string.pay_bad_input), color = RbColors.negative, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        Text(stringResource(R.string.pay_list_step1), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 16.dp))
                        val line = "treasury: $listedTreasury"
                        Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RbColors.surface).height(IntrinsicSize.Min)) {
                            SelectionContainer(Modifier.weight(1f)) { Text(line, color = RbColors.textPrimary, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(14.dp)) }
                            Column(Modifier.width(56.dp).fillMaxHeight().clickable {
                                    (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("treasury", line))
                                    android.widget.Toast.makeText(context, context.getString(R.string.pay_copied), android.widget.Toast.LENGTH_SHORT).show() },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = RbColors.purpleLight, modifier = Modifier.size(20.dp))
                                Text(stringResource(R.string.pay_copy), color = RbColors.purpleLight, fontSize = 10.sp)
                            }
                        }
                        if (memo != null && wallet != null) {
                            // Debounced: this read the website once per keystroke.
                            LaunchedEffect(pkg, listedTreasury) { kotlinx.coroutines.delay(700); vm.checkTreasuryLine(pkg, listedTreasury) }
                            LineStatus(lineCheck) { vm.checkTreasuryLine(pkg, listedTreasury) }
                        }
                    } else {
                        if (lineCheck?.first == true) LineStatus(lineCheck) {}
                        Text(stringResource(R.string.pay_list_step2), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 14.dp))
                        Spacer(Modifier.height(12.dp))
                        GlassCard {
                            val w = wallet ?: ""
                            KeyValueRow(stringResource(R.string.pay_field_package), pkg)
                            KeyValueRow(stringResource(R.string.pay_field_code), listCode)
                            KeyValueRow(stringResource(R.string.pay_field_treasury), shortKey(listedTreasury, 4, 4))
                            if (byPublisher) KeyValueRow(stringResource(R.string.pay_label_signing), shortKey(w, 4, 4) + " · " + stringResource(if (isPub) R.string.pay_signing_publisher else R.string.pay_signing_not_publisher), if (isPub) RbColors.green else RbColors.warning)
                            else KeyValueRow(stringResource(R.string.pay_label_signing), shortKey(w, 4, 4) + " · " + stringResource(R.string.pay_wallet_matches), RbColors.green)
                        }
                        Text(stringResource(R.string.pay_listing_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp))
                        if (!vm.listingIsFree()) Text(stringResource(R.string.pay_list_fee, "${AppConfig.Payment.LIST_SOL} SOL", String.format(java.util.Locale.US, "%,.0f SKR", AppConfig.Payment.LIST_SKR), String.format(java.util.Locale.US, "%,.0f SKR", AppConfig.Payment.LIST_SKR_SGT)),
                            color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
                        message?.let { (ok, m) -> Text(m, color = if (ok) RbColors.green else RbColors.negative, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
                Column(Modifier.padding(20.dp)) {
                    val memo = listMemo()
                    val canList = memo != null && (!byPublisher || (wallet != null && vm.isVerifiedPublisher(pkg, wallet!!)))
                    if (step == 1) {
                        if (lineCheck?.first != true) Text(stringResource(R.string.pay_sign_early), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
                        PrimaryButton(stringResource(R.string.pay_continue), enabled = canList, onClick = { step = 2 })
                    } else if (vm.listingIsFree()) PrimaryButton(stage ?: stringResource(R.string.pay_sign), enabled = canList && stage == null && sender != null, loading = stage != null, onClick = { memo?.let { sign(it) } })
                    else {
                        // Fee and record in one transaction: the wallet sheet shows the payment and signs the listing with it.
                        val t = memo.takeIf { canList }
                        PrimaryButton(stage ?: stringResource(R.string.pay_list_pay, "${AppConfig.Payment.LIST_SOL} SOL"), enabled = t != null && stage == null && sender != null, loading = stage != null,
                            onClick = { t?.let { payList(com.undersigned.app.solana.ProPaymentService.Rail.SOL, it) } })
                        Spacer(Modifier.height(8.dp))
                        SecondaryButton(stringResource(R.string.pay_list_pay, String.format(java.util.Locale.US, "%,.0f SKR", vm.listSkrPrice())), enabled = t != null && stage == null && sender != null,
                            onClick = { t?.let { payList(com.undersigned.app.solana.ProPaymentService.Rail.SKR, it) } })
                    }
                }
            }
            // G5 — this wallet's listings.
            PayPage.LISTINGS -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                if (mine.isEmpty()) Text(stringResource(R.string.pay_empty), color = RbColors.textSecondary, fontSize = 13.sp)
                mine.forEach { c ->
                    val row = rows.firstOrNull { it.claim.packageName == c.packageName && it.claim.treasury == c.treasury }
                    val confirmed = row?.confirmed == true
                    val name = row?.name ?: c.packageName
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DAppIcon(c.packageName, name, icons[c.packageName]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(40.dp)); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.pay_listing_line, c.code, shortKey(c.treasury, 4, 4)), color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (confirmed) TrustPill(stringResource(R.string.pay_claim_confirmed), RbColors.green) else OutlinePill(stringResource(R.string.pay_claim_waiting))
                            if (confirmed) row?.usd?.let { Text("  " + usd(it), color = RbColors.textTertiary, fontSize = 12.sp) }
                        }
                        if (!confirmed) details[c.packageName]?.website?.let { w -> PublisherVerifier.wellKnownUrl(w)?.let { u -> Text(u, color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp)) } }
                        Row(Modifier.fillMaxWidth()) {
                            TextButton(onClick = { if (confirmed) onOpen(c.packageName) else { pkg = c.packageName; listCode = c.code; step = 1; page = PayPage.LIST } }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(if (confirmed) R.string.pay_open_board else R.string.pay_show_line), color = RbColors.purpleLight, fontSize = 13.sp) }
                            Spacer(Modifier.weight(1f))
                            TextButton(enabled = stage == null && sender != null, onClick = { withdrawFor = c.packageName }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.pay_withdraw), color = RbColors.negative, fontSize = 13.sp) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                message?.let { (ok, m) -> Text(m, color = if (ok) RbColors.green else RbColors.negative, fontSize = 12.sp) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
    // Withdrawing removes the listing from the board, and listing again costs the fee: ask first (it was one tap beside "Open board").
    withdrawFor?.let { p ->
        AlertDialog(onDismissRequest = { withdrawFor = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.pay_withdraw), color = RbColors.textPrimary) },
            text = { Text(stringResource(if (vm.listingIsFree()) R.string.confirm_withdraw_free else R.string.confirm_withdraw_body), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) },
            confirmButton = { TextButton(onClick = { withdrawFor = null; sign(ReviewMemo.Untreasury(p)) }) { Text(stringResource(R.string.pay_withdraw), color = RbColors.negative) } },
            dismissButton = { TextButton(onClick = { withdrawFor = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }
}

@Composable
private fun LabelNumber(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Row { Text(label + " ", color = RbColors.textTertiary, fontSize = 12.sp); Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ToolRow(label: String, trailing: String?, strong: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (strong) RbColors.textPrimary else RbColors.textSecondary, fontSize = 15.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, color = RbColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp)) }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = RbColors.textTertiary)
    }
}

@Composable
private fun StepDot(n: Int, current: Int) {
    val done = n < current; val now = n == current
    Box(Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape)
        .background(when { done -> RbColors.green.copy(alpha = 0.14f); now -> RbColors.purple; else -> androidx.compose.ui.graphics.Color.Transparent })
        .then(if (!done && !now) Modifier.border(1.dp, RbColors.textDim, androidx.compose.foundation.shape.CircleShape) else Modifier), contentAlignment = Alignment.Center) {
        if (done) Icon(Icons.Default.Check, contentDescription = null, tint = RbColors.green, modifier = Modifier.size(16.dp))
        else Text("$n", color = if (now) RbColors.textPrimary else RbColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/** Waiting (outlined dot) or found (green check) for the website line, when it was checked, and a "Check again". */
@Composable
private fun LineStatus(check: Triple<Boolean, Long, String?>?, onCheck: () -> Unit) {
    val found = check?.first == true
    Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RbColors.surface)
        .then(if (found) Modifier.border(1.dp, RbColors.green.copy(alpha = 0.4f), RoundedCornerShape(12.dp)) else Modifier).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (found) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RbColors.green, modifier = Modifier.size(22.dp))
        else Box(Modifier.size(14.dp).border(1.5.dp, RbColors.textSecondary, androidx.compose.foundation.shape.CircleShape))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(stringResource(if (found) R.string.pay_line_found else R.string.pay_claim_waiting), color = if (found) RbColors.green else RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            check?.let { (_, at, site) -> Text(stringResource(R.string.pay_checked, android.text.format.DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()) + (site?.let { " · $it" } ?: ""), color = RbColors.textTertiary, fontSize = 11.sp) }
        }
        if (!found) TextButton(onClick = onCheck, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pay_check_again), color = RbColors.purpleLight, fontSize = 13.sp) }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = RbColors.textPrimary) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = RbColors.textTertiary, fontSize = 13.sp, modifier = Modifier.width(120.dp))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

private fun Modifier.dashedOutline(color: androidx.compose.ui.graphics.Color, radius: androidx.compose.ui.unit.Dp) = this.drawBehind {
    drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
}

/** Filled field, label above (design G2–G3): 52 dp, surfaceVariant, 12 dp radius. */
@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String) {
    Text(label, color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    // Addresses, package names and codes: no autocorrect or capitals (the keyboard mangled base58), and the label is the field's name.
    TextField(value = value, onValueChange = onChange, singleLine = true, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics { contentDescription = label }, shape = RoundedCornerShape(12.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrect = false, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None, keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii),
        colors = TextFieldDefaults.colors(focusedContainerColor = RbColors.surfaceVariant, unfocusedContainerColor = RbColors.surfaceVariant, focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, cursorColor = RbColors.purpleLight))
}

private fun usd(v: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(v)
private fun amount(raw: Long, decimals: Int): String = java.math.BigDecimal(raw).movePointLeft(decimals).setScale(minOf(decimals, 4), java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/**
 * Design review 28 Sep 2026 (handoff 2, F3): the USD headline with its basis right under it; counts, token totals and first
 * and latest as key-value grids (label over value, so no string breaks mid-pair); six months of bars with a value on each,
 * months valued at today's prices hatched and outlined rather than paler.
 */
@Composable
fun TallyView(row: MainViewModel.BoardRow, quotes: Map<String, Prices.Quote>) {
    val t = row.tally ?: return
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
    row.usd?.let { Text(usd(it), color = RbColors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold) }
    Text(stringResource(if (row.usdWhenPaid) R.string.pay_usd_paid else R.string.pay_usd_note), color = RbColors.textTertiary, fontSize = 11.sp)
    Spacer(Modifier.height(12.dp))
    KeyValueGrid(listOf(stringResource(R.string.pay_label_payments) to "${t.payments}", stringResource(R.string.pay_label_payers) to "${t.payers}", stringResource(R.string.pay_label_attested) to "${t.attestedPayers}"),
        columns = 3, description = stringResource(R.string.pay_row, t.payments, t.payers, t.attestedPayers), valueColors = mapOf(2 to RbColors.green))
    Spacer(Modifier.height(10.dp))
    val sol = amount(t.lamports, 9); val skr = amount(t.tokens[AppConfig.Solana.SKR_MINT] ?: 0L, 6); val usdc = amount(t.tokens[PaymentBoard.USDC_MINT] ?: 0L, 6)
    KeyValueGrid(listOf(stringResource(R.string.pay_label_sol) to sol, stringResource(R.string.pay_label_skr) to skr, stringResource(R.string.pay_label_usdc) to usdc),
        columns = 3, description = stringResource(R.string.pay_totals, sol, skr, usdc), valueSize = 14)
    if (t.ownPayments > 0) Text(stringResource(R.string.pay_own, t.ownPayments), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 8.dp))
    if (!row.complete) Text(stringResource(R.string.pay_incomplete), color = RbColors.warning, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    val months = t.months.takeLast(6).map { m -> m to (row.monthUsd[m.key] ?: Prices.usd(m.lamports, m.tokens, quotes) ?: 0.0) }
    val max = months.maxOfOrNull { it.second }?.takeIf { it > 0 }
    if (max != null) {
        Spacer(Modifier.height(14.dp))
        val today = !row.usdWhenPaid
        val chartA11y = months.joinToString(", ") { (m, v) -> java.time.Month.of(m.key.takeLast(2).toInt()).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " " + usd(v) }
        Row(Modifier.fillMaxWidth().height(120.dp).semantics(mergeDescendants = true) { contentDescription = chartA11y }, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            months.forEach { (m, v) ->
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (v >= 100) "$" + v.toLong() else String.format(Locale.US, "$%.0f", v), color = RbColors.textTertiary, fontSize = 9.sp, maxLines = 1)
                    val frac = (v / max).toFloat().coerceIn(0.03f, 1f)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().fillMaxHeight(frac * 0.78f)) {
                        if (today) {
                            drawRect(RbColors.purple, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                            var x = -size.height; while (x < size.width) { drawLine(RbColors.purpleLight.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(x, size.height), androidx.compose.ui.geometry.Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx()); x += 5.dp.toPx() }
                        } else drawRect(RbColors.purple)
                    }
                    Text(m.key.takeLast(2).let { mm -> java.time.Month.of(mm.toInt()).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }, color = RbColors.textTertiary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    if (t.first != null && t.last != null) {
        Spacer(Modifier.height(12.dp))
        val f = df.format(Date(t.first!! * 1000)); val l = df.format(Date(t.last!! * 1000))
        KeyValueGrid(listOf(stringResource(R.string.pay_label_first) to f, stringResource(R.string.pay_label_latest) to l), columns = 2, description = stringResource(R.string.pay_range, f, l), valueSize = 13)
    }
}


private val TREND_COLORS = listOf(RbColors.purple, RbColors.green, RbColors.warning, androidx.compose.ui.graphics.Color(0xFF4FC3F7), androidx.compose.ui.graphics.Color(0xFFFF8AB3))

/**
 * USD received each month by every confirmed listing, as the board values it: the five largest as their own lines, the
 * rest as one "Others" line, over the last twelve months that saw a payment. Monthly or as a running total.
 */
@Composable
private fun PaymentsOverTime(listed: List<MainViewModel.BoardRow>) {
    var running by remember { mutableStateOf(false) }
    val months = remember(listed) { listed.flatMap { it.monthUsd.keys }.distinct().sorted().takeLast(12) }
    val ranked = remember(listed) { listed.sortedByDescending { it.usd ?: 0.0 } }
    val top = ranked.take(5)
    val rest = ranked.drop(5)
    val others = stringResource(R.string.pay_trend_others)
    val series: List<Triple<String, androidx.compose.ui.graphics.Color, List<Double>>> = remember(listed, running, months) {
        fun line(rows: List<MainViewModel.BoardRow>): List<Double> {
            val m = months.map { k -> rows.sumOf { it.monthUsd[k] ?: 0.0 } }
            return if (running) m.runningFold(0.0) { a, b -> a + b }.drop(1) else m
        }
        top.mapIndexed { i, r -> Triple(r.name, TREND_COLORS[i], line(listOf(r))) } +
            (if (rest.isNotEmpty()) listOf(Triple(others, RbColors.textTertiary, line(rest))) else emptyList())
    }
    val max = series.maxOfOrNull { it.third.maxOrNull() ?: 0.0 }?.takeIf { it > 0 } ?: return
    fun label(k: String) = java.time.YearMonth.parse(k).month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) + " " + k.take(4)
    GlassCard {
        Text(stringResource(R.string.pay_trend_title), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.pay_trend_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !running, onClick = { running = false }, label = { Text(stringResource(R.string.pay_trend_monthly), fontSize = 12.sp) })
            FilterChip(selected = running, onClick = { running = true }, label = { Text(stringResource(R.string.pay_trend_running), fontSize = 12.sp) })
        }
        val a11y = series.joinToString("; ") { (n, _, v) -> n + ": " + months.indices.joinToString(", ") { i -> label(months[i]) + " " + usd(v[i]) } }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.height(150.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Text(usd(max).substringBefore('.'), color = RbColors.textTertiary, fontSize = 9.sp)
                Text("$0", color = RbColors.textTertiary, fontSize = 9.sp)
            }
            Spacer(Modifier.width(6.dp))
            val grid = RbColors.surfaceVariant
            androidx.compose.foundation.Canvas(Modifier.weight(1f).height(150.dp).semantics { contentDescription = a11y }) {
                val n = months.size
                val dx = if (n > 1) size.width / (n - 1) else 0f
                fun y(v: Double) = (size.height * (1 - v / max)).toFloat()
                for (g in 0..3) { val gy = size.height * g / 3; drawLine(grid, androidx.compose.ui.geometry.Offset(0f, gy), androidx.compose.ui.geometry.Offset(size.width, gy), 1.dp.toPx()) }
                series.asReversed().forEach { (_, c, v) ->
                    val pts = v.mapIndexed { i, x -> androidx.compose.ui.geometry.Offset(if (n > 1) i * dx else size.width / 2, y(x)) }
                    for (i in 1 until pts.size) drawLine(c, pts[i - 1], pts[i], 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    pts.forEach { drawCircle(c, 3.dp.toPx(), it) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 30.dp, top = 2.dp)) {
            Text(label(months.first()), color = RbColors.textTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f))
            if (months.size > 1) Text(label(months.last()), color = RbColors.textTertiary, fontSize = 9.sp)
        }
        Spacer(Modifier.height(8.dp))
        series.forEach { (n, c, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(c, androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(n, color = RbColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(usd(if (running) v.lastOrNull() ?: 0.0 else v.sum()), color = RbColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}


/**
 * Wallets written into an app's store build that received payments from several wallets, NOT confirmed by the publisher.
 * Listed apart from the board, by app name, each labelled; read from the Undersigned API (the phone cannot read every build).
 */
@Composable
private fun FoundInCode(vm: MainViewModel, onOpen: (String) -> Unit) {
    val found by produceState<Result<org.json.JSONObject>?>(null) { value = vm.foundTreasuries() }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    GlassCard(accent = RbColors.warning.copy(alpha = 0.5f)) {
        Text(stringResource(R.string.pay_found_title), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.pay_found_note, 5), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        when (val f = found) {
            null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = RbColors.purpleLight)
            else -> f.fold({ j ->
                val rows = j.optJSONArray("rows") ?: org.json.JSONArray()
                if (rows.length() == 0) Text(stringResource(R.string.pay_found_empty, j.optJSONObject("progress")?.optInt("builds_read") ?: 0), color = RbColors.textSecondary, fontSize = 12.sp)
                for (i in 0 until rows.length()) {
                    val r = rows.getJSONObject(i)
                    if (i > 0) HorizontalDivider(color = RbColors.surfaceVariant)
                    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onOpen(r.getString("package")) }.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.optString("name"), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(shortKey(r.getString("wallet"), 4, 4), color = RbColors.textTertiary, fontSize = 11.sp)
                        }
                        Text(stringResource(R.string.pay_found_row, r.optInt("payers"), r.optInt("payments"), r.optInt("transactions_read")), color = RbColors.textSecondary, fontSize = 12.sp)
                    }
                }
                Text(stringResource(R.string.pay_found_dispute), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.pay_found_report), color = RbColors.purpleLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.heightIn(min = 48.dp).clickable { runCatching { uri.openUri(com.undersigned.app.AppConfig.Site.URL + "/report") } }.padding(vertical = 14.dp))
            }, { Text(stringResource(R.string.load_failed), color = RbColors.textSecondary, fontSize = 12.sp) })
        }
    }
}


// ---- Analytics (owner, 10 Oct 2026): store-wide payments by window, from the Undersigned API ------------------------------

private val FOUND_COLOR = RbColors.warning
private val CONFIRMED_COLOR = RbColors.green

private fun usd0(v: Double): String = NumberFormat.getCurrencyInstance(Locale.US).apply { maximumFractionDigits = if (v >= 100) 0 else 2 }.format(v)
private fun share(v: Double): String = String.format(Locale.US, if (v >= 10) "%.0f%%" else "%.1f%%", v)
private fun count(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/** "2026-09-07" or "2026-09" as the phone's short date; anything else as given. */
/** Shared with the payment history screen (same formats as the board). */
internal fun payUsd(v: Double): String = usd0(v)
internal fun payCount(n: Int): String = count(n)
internal fun payBucketLabel(b: String): String = bucketLabel(b)

private fun bucketLabel(b: String): String = runCatching {
    if (b.length == 7) java.time.YearMonth.parse(b).let { it.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) + " " + it.year }
    else java.time.LocalDate.parse(b.take(10)).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
}.getOrDefault(b)

/** The label every analytics section carries: found wallets are unconfirmed; figures are gross receipts on the day paid. */
@Composable
private fun AnalyticsFootnote() {
    Text(stringResource(R.string.pa_label), color = RbColors.textTertiary, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SourcePill(confirmed: Boolean) {
    val c = if (confirmed) CONFIRMED_COLOR else FOUND_COLOR
    Text(stringResource(if (confirmed) R.string.pa_pill_confirmed else R.string.pa_pill_found), color = c, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 7.dp, vertical = 2.dp))
}

@Composable
private fun CardTitle(text: String) { Text(text, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp)) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentAnalyticsBlock(vm: MainViewModel, onOpen: (String) -> Unit, onHistory: (String) -> Unit) {
    var window by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("30d") }
    val cache by vm.paymentSummaries.collectAsState()
    var failed by remember { mutableStateOf(setOf<String>()) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(window, attempt) { vm.paymentSummary(window).fold({ failed = failed - window }, { failed = failed + window }) }
    val labels = mapOf("7d" to R.string.pa_window_7d, "30d" to R.string.pa_window_30d, "90d" to R.string.pa_window_90d, "1y" to R.string.pa_window_1y, "all" to R.string.pa_window_all)
    Text(stringResource(R.string.pa_title), color = RbColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.undersigned.app.ledger.PaymentAnalytics.WINDOWS.forEach { w ->
            FilterChip(selected = window == w, onClick = { window = w }, label = { Text(stringResource(labels.getValue(w)), fontSize = 12.sp) })
        }
    }
    val s = cache[window]
    if (s == null) {
        if (window in failed) {
            Text(stringResource(R.string.pa_offline), color = RbColors.textSecondary, fontSize = 12.sp)
            TextButton(onClick = { failed = failed - window; attempt++ }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry), color = RbColors.purpleLight, fontSize = 13.sp) }
        } else Text(stringResource(R.string.pa_loading), color = RbColors.purpleLight, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        return
    }
    s.coverage?.takeIf { it.read < it.listed }?.let { c -> Text(stringResource(R.string.pa_coverage, count(c.read), count(c.listed)), color = RbColors.warning, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 6.dp)) }

    // (1) Totals.
    GlassCard {
        val items = listOf(stringResource(R.string.pa_received) to usd0(s.totalUsd), stringResource(R.string.pa_payments) to count(s.payments), stringResource(R.string.pa_paying_wallets) to count(s.payers),
            stringResource(R.string.pa_apps_paid) to count(s.apps), stringResource(R.string.pa_repeat_share) to share(s.repeatShare), stringResource(R.string.pa_top10_share) to share(s.top10Share))
        KeyValueGrid(items, columns = 3, description = items.joinToString(", ") { "${it.first} ${it.second}" }, valueSize = 15)
        AnalyticsFootnote()
    }
    Spacer(Modifier.height(10.dp))

    // (2) Stacked area: confirmed treasuries below, found-in-code above.
    if (s.confirmedSeries.isNotEmpty() || s.foundSeries.isNotEmpty()) {
        GlassCard {
            CardTitle(stringResource(R.string.pa_chart_title))
            StackedArea(s.confirmedSeries, s.foundSeries)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(CONFIRMED_COLOR to R.string.pa_legend_confirmed, FOUND_COLOR to R.string.pa_legend_found).forEach { (c, l) ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(10.dp).background(c, androidx.compose.foundation.shape.CircleShape)); Text(" " + stringResource(l), color = RbColors.textSecondary, fontSize = 11.sp) }
                }
            }
            AnalyticsFootnote()
        }
        Spacer(Modifier.height(10.dp))
    }

    // (3) Leaderboard.
    if (s.leaders.isNotEmpty()) GlassCard {
        CardTitle(stringResource(R.string.pa_board_title))
        s.leaders.take(10).forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = RbColors.surfaceVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onHistory(r.pkg) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${r.rank}", color = RbColors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.widthIn(min = 24.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(r.name, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp)); SourcePill(r.confirmed)
                    }
                    Text(share(r.share) + " · " + stringResource(R.string.pa_row_counts, count(r.payments), count(r.payers), count(r.newPayers)), color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 2)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(usd0(r.usd), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Sparkline(r.spark, if (r.confirmed) CONFIRMED_COLOR else FOUND_COLOR)
                }
            }
        }
        AnalyticsFootnote()
    }
    Spacer(Modifier.height(10.dp))

    // (4) Categories.
    if (s.categories.isNotEmpty()) GlassCard {
        CardTitle(stringResource(R.string.pa_cat_title))
        Text(stringResource(R.string.pa_vs_before), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(bottom = 2.dp))
        s.categories.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = RbColors.surfaceVariant)
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    val ch = if (c.isNew) stringResource(R.string.pa_new) else com.undersigned.app.ledger.PaymentAnalytics.pct(c.change)
                    ch?.let { Text(it, color = if (c.isNew || (c.change ?: 0.0) >= 0) RbColors.green else RbColors.negative, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp)) }
                    Text(usd0(c.usd), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(stringResource(R.string.pa_cat_apps, count(c.appsPaid)) + " · " + share(c.share), color = RbColors.textTertiary, fontSize = 11.sp)
                if (c.leaders.isNotEmpty()) FlowRow(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    c.leaders.take(3).forEachIndexed { k, l -> Text("${k + 1}. ${l.name} ${usd0(l.usd)}", color = RbColors.purpleLight, fontSize = 12.sp, modifier = Modifier.heightIn(min = 32.dp).clickable { onOpen(l.pkg) }.padding(vertical = 6.dp)) }
                }
            }
        }
        AnalyticsFootnote()
    }
    Spacer(Modifier.height(10.dp))

    // (5) Rising, falling, newcomers.
    GlassCard {
        listOf(R.string.pa_rising to s.rising, R.string.pa_falling to s.falling, R.string.pa_newcomers to s.newcomers).forEachIndexed { i, (title, list) ->
            Text(stringResource(title), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = if (i > 0) 12.dp else 0.dp, bottom = 4.dp))
            if (list.isEmpty()) Text(stringResource(R.string.pa_none), color = RbColors.textTertiary, fontSize = 12.sp)
            list.take(5).forEach { m ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpen(m.pkg) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name, color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(6.dp)); SourcePill(m.confirmed)
                        }
                        Text(if (m.isNew) stringResource(R.string.pa_new_line, count(m.payments), count(m.newPayers))
                            else stringResource(R.string.pa_was, usd0(m.previousUsd)), color = RbColors.textTertiary, fontSize = 11.sp)
                    }
                    val ch = if (m.isNew) stringResource(R.string.pa_new) else com.undersigned.app.ledger.PaymentAnalytics.pct(m.change)
                    ch?.let { Text(it, color = if (m.isNew || (m.change ?: 0.0) >= 0) RbColors.green else RbColors.negative, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp)) }
                    Text(usd0(m.usd), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(stringResource(R.string.pa_trend_note, usd0(s.minBaseUsd)), color = RbColors.textTertiary, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 8.dp))
        AnalyticsFootnote()
    }
    Spacer(Modifier.height(10.dp))

    // (6) How it was paid.
    GlassCard {
        CardTitle(stringResource(R.string.pa_how_title))
        s.rails.forEach { (k, r) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k.uppercase(Locale.US), color = RbColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(RbColors.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth((r.share / 100).toFloat().coerceIn(0f, 1f)).fillMaxHeight().background(RbColors.purple))
                }
                Text(share(r.share), color = RbColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 48.dp))
            }
            Text(stringResource(R.string.pa_rail_line, count(r.payments), usd0(r.usd)), color = RbColors.textTertiary, fontSize = 10.sp, modifier = Modifier.padding(start = 48.dp))
        }
        Spacer(Modifier.height(10.dp))
        val sizes = listOf(stringResource(R.string.pa_median) to usd0(s.median), stringResource(R.string.pa_p90) to usd0(s.p90), stringResource(R.string.pa_max) to usd0(s.max))
        KeyValueGrid(sizes, columns = 3, description = sizes.joinToString(", ") { "${it.first} ${it.second}" }, valueSize = 14)
        if (s.byWeekday.size == 7 && s.byWeekday.any { it > 0 }) {
            Text(stringResource(R.string.pa_weekday_title), color = RbColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            WeekdayBars(s.byWeekday)
        }
        AnalyticsFootnote()
    }
    Spacer(Modifier.height(10.dp))

    // (7) Wallets.
    if (s.wallets.isNotEmpty()) GlassCard {
        CardTitle(stringResource(R.string.pa_wallets_title))
        s.wallets.take(10).forEachIndexed { i, w ->
            if (i > 0) HorizontalDivider(color = RbColors.surfaceVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = w.apps.isNotEmpty()) { w.apps.firstOrNull()?.let { onOpen(it.pkg) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${w.rank}", color = RbColors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.widthIn(min = 24.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(shortKey(w.wallet, 4, 4), color = RbColors.textPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.width(6.dp)); SourcePill(w.confirmed)
                    }
                    if (w.apps.isNotEmpty()) Text(stringResource(R.string.pa_wallet_apps, w.apps.joinToString(", ") { it.name }), color = RbColors.textSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.pa_payments_payers, count(w.payments), count(w.payers)), color = RbColors.textTertiary, fontSize = 11.sp)
                }
                Text(usd0(w.usd), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        AnalyticsFootnote()
    }
    Spacer(Modifier.height(18.dp))
}

/** Confirmed (green, bottom) and found (amber, stacked on top) USD per bucket, as filled areas. */
@Composable
private fun StackedArea(confirmed: List<PaymentAnalytics.Bucket>, found: List<PaymentAnalytics.Bucket>) {
    val keys = remember(confirmed, found) { (confirmed.map { it.bucket } + found.map { it.bucket }).distinct().sorted() }
    val c = remember(confirmed, keys) { val m = confirmed.associate { it.bucket to it.usd }; keys.map { m[it] ?: 0.0 } }
    val f = remember(found, keys) { val m = found.associate { it.bucket to it.usd }; keys.map { m[it] ?: 0.0 } }
    val max = keys.indices.maxOfOrNull { c[it] + f[it] }?.takeIf { it > 0 } ?: 1.0
    val a11y = keys.indices.joinToString(", ") { bucketLabel(keys[it]) + " " + usd0(c[it] + f[it]) }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.height(140.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(usd0(max), color = RbColors.textTertiary, fontSize = 9.sp)
            Text("$0", color = RbColors.textTertiary, fontSize = 9.sp)
        }
        Spacer(Modifier.width(6.dp))
        val grid = RbColors.surfaceVariant
        androidx.compose.foundation.Canvas(Modifier.weight(1f).height(140.dp).semantics { contentDescription = a11y }) {
            for (g in 0..3) { val gy = size.height * g / 3; drawLine(grid, androidx.compose.ui.geometry.Offset(0f, gy), androidx.compose.ui.geometry.Offset(size.width, gy), 1.dp.toPx()) }
            // One bucket: a flat band across the width.
            val xs = if (keys.size > 1) keys.indices.map { it * size.width / (keys.size - 1) } else listOf(0f, size.width)
            val lo = if (keys.size > 1) c else listOf(c.firstOrNull() ?: 0.0, c.firstOrNull() ?: 0.0)
            val hi = if (keys.size > 1) keys.indices.map { c[it] + f[it] } else List(2) { (c.firstOrNull() ?: 0.0) + (f.firstOrNull() ?: 0.0) }
            fun y(v: Double) = (size.height * (1 - v / max)).toFloat()
            fun band(top: List<Double>, bottom: List<Double>?, color: androidx.compose.ui.graphics.Color) {
                val p = androidx.compose.ui.graphics.Path()
                xs.forEachIndexed { i, x -> if (i == 0) p.moveTo(x, y(top[i])) else p.lineTo(x, y(top[i])) }
                for (i in xs.indices.reversed()) p.lineTo(xs[i], bottom?.let { y(it[i]) } ?: size.height)
                p.close()
                drawPath(p, color.copy(alpha = 0.45f))
                for (i in 1 until xs.size) drawLine(color, androidx.compose.ui.geometry.Offset(xs[i - 1], y(top[i - 1])), androidx.compose.ui.geometry.Offset(xs[i], y(top[i])), 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            band(hi, lo, FOUND_COLOR)
            band(lo, null, CONFIRMED_COLOR)
        }
    }
    if (keys.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 30.dp, top = 2.dp)) {
        Text(bucketLabel(keys.first()), color = RbColors.textTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f))
        if (keys.size > 1) Text(bucketLabel(keys.last()), color = RbColors.textTertiary, fontSize = 9.sp)
    }
}

@Composable
private fun Sparkline(values: List<Double>, color: androidx.compose.ui.graphics.Color) {
    if (values.size < 2) return
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: return
    androidx.compose.foundation.Canvas(Modifier.padding(top = 4.dp).size(width = 56.dp, height = 18.dp)) {
        val dx = size.width / (values.size - 1)
        for (i in 1 until values.size) drawLine(color, androidx.compose.ui.geometry.Offset((i - 1) * dx, (size.height * (1 - values[i - 1] / max)).toFloat()),
            androidx.compose.ui.geometry.Offset(i * dx, (size.height * (1 - values[i] / max)).toFloat()), 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/** Payment counts Monday first (UTC), seven bars with the weekday under each. */
@Composable
private fun WeekdayBars(counts: List<Int>) {
    val max = counts.maxOrNull()?.takeIf { it > 0 } ?: return
    val days = java.time.DayOfWeek.entries.map { it.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }
    val a11y = days.indices.joinToString(", ") { days[it] + " " + counts[it] }
    Row(Modifier.fillMaxWidth().height(110.dp).semantics(mergeDescendants = true) { contentDescription = a11y }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { i, n ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(count(n), color = RbColors.textTertiary, fontSize = 8.sp, maxLines = 1)
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().fillMaxHeight((n.toFloat() / max).coerceIn(0.03f, 1f) * 0.55f)) { drawRect(RbColors.purple) }
                Text(days[i], color = RbColors.textTertiary, fontSize = 9.sp, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

/** One app's weekly USD received (from the API), bars over the last 26 weeks with a value; shown on the app page. */
@Composable
fun AppWeeklyPayments(series: PaymentAnalytics.AppSeries) {
    val weeks = series.series.takeLast(26)
    val max = weeks.maxOfOrNull { it.usd }?.takeIf { it > 0 } ?: return
    Text(stringResource(R.string.pa_app_weekly), color = RbColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
    Text(usd0(series.totalUsd) + " · " + stringResource(R.string.pa_payments_payers, count(series.payments), count(series.payers)), color = RbColors.textTertiary, fontSize = 11.sp)
    val a11y = weeks.joinToString(", ") { bucketLabel(it.bucket) + " " + usd0(it.usd) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Column(Modifier.height(80.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(usd0(max), color = RbColors.textTertiary, fontSize = 9.sp); Text("$0", color = RbColors.textTertiary, fontSize = 9.sp)
        }
        Spacer(Modifier.width(6.dp))
        val color = if (series.anyFound) FOUND_COLOR else CONFIRMED_COLOR
        androidx.compose.foundation.Canvas(Modifier.weight(1f).height(80.dp).semantics { contentDescription = a11y }) {
            val slot = size.width / weeks.size; val gap = (slot * 0.2f).coerceAtMost(4.dp.toPx())
            weeks.forEachIndexed { i, b ->
                val h = (size.height * (b.usd / max)).toFloat().coerceAtLeast(if (b.usd > 0) 1.dp.toPx() else 0f)
                drawRect(color, androidx.compose.ui.geometry.Offset(i * slot + gap / 2, size.height - h), androidx.compose.ui.geometry.Size(slot - gap, h))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 30.dp, top = 2.dp)) {
        Text(bucketLabel(weeks.first().bucket), color = RbColors.textTertiary, fontSize = 9.sp, modifier = Modifier.weight(1f))
        if (weeks.size > 1) Text(bucketLabel(weeks.last().bucket), color = RbColors.textTertiary, fontSize = 9.sp)
    }
    Text(stringResource(R.string.pa_label), color = RbColors.textTertiary, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 6.dp))
}
