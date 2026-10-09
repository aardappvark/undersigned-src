package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
fun PaymentsScreen(vm: MainViewModel, sender: ActivityResultSender?, onBack: () -> Unit, onOpen: (String) -> Unit) {
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
    val back = { if (page == PayPage.BOARD) onBack() else if (page == PayPage.LIST && step == 2) step = 1 else page = PayPage.BOARD }
    androidx.activity.compose.BackHandler(enabled = page != PayPage.BOARD) { back() }
    val title = stringResource(when (page) { PayPage.BOARD -> R.string.pay_title; PayPage.LIST -> R.string.pay_list_title; PayPage.LISTINGS -> R.string.pay_my_claims; PayPage.PREVIEW -> R.string.pay_preview_title })

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton({ back() })
            Text(title, color = RbColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        when (page) {
            // G1 — the board, then the publisher tools.
            PayPage.BOARD -> Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
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
                        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onOpen(row.claim.packageName) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                FoundInCode(vm, onOpen)
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
