package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    LaunchedEffect(entries) { vm.loadBoard() }
    LaunchedEffect(rows) { vm.resolveIcons(rows.map { it.claim.packageName }) }
    var treasury by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var listCode by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    fun sign(memo: ReviewMemo) {
        val s = sender ?: return
        message = null
        vm.publish(s, memo, onStage = { st -> stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { r ->
            stage = null
            message = r.fold({ true to context.getString(R.string.action_published, shortKey(it, 6, 6)) }, { false to (it.message ?: context.getString(R.string.action_failed)) })
            if (r.isSuccess) vm.loadBoard(force = true)
        }
    }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            Text(stringResource(R.string.pay_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.pay_intro), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            Text(stringResource(R.string.pay_usd_both), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            if (loading) { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.pay_loading), color = RbColors.purpleLight, fontSize = 12.sp) }

            val listed = rows.filter { it.confirmed && it.tally != null }
            Spacer(Modifier.height(10.dp))
            if (listed.isEmpty() && !loading) Text(stringResource(R.string.pay_empty), color = RbColors.textSecondary, fontSize = 13.sp)
            listed.forEachIndexed { i, row ->
                GlassCard(modifier = Modifier.clickable { onOpen(row.claim.packageName) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}", color = RbColors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                        DAppIcon(row.claim.packageName, row.name, icons[row.claim.packageName]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(row.name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        row.usd?.let { Text(usd(it), color = RbColors.green, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                    row.tally?.let { t -> Text(stringResource(R.string.pay_row, t.payments, t.payers, t.attestedPayers), color = RbColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                    if (!row.complete) Text(stringResource(R.string.pay_incomplete), color = RbColors.warning, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
            val waiting = rows.count { !it.confirmed }
            if (waiting > 0) Text(stringResource(R.string.pay_waiting_list, waiting), color = RbColors.textTertiary, fontSize = 11.sp)

            // Preview: public data for any treasury and code, attributed to nothing.
            SectionTitle(stringResource(R.string.pay_preview_title))
            GlassCard {
                Text(stringResource(R.string.pay_preview_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Field(treasury, { treasury = it.trim() }, stringResource(R.string.pay_field_treasury))
                Field(code, { code = it.trim() }, stringResource(R.string.pay_field_code))
                Spacer(Modifier.height(8.dp))
                val ok = Payments.validCode(code) && runCatching { com.undersigned.app.util.Base58.decode(treasury).size == 32 }.getOrDefault(false)
                SecondaryButton(stringResource(R.string.pay_preview), enabled = ok && !loading, onClick = { vm.previewTreasury(treasury, code) })
                preview?.takeIf { it.claim.treasury == treasury && it.claim.code == code }?.let { Spacer(Modifier.height(10.dp)); TallyView(it, quotes) }
            }

            // Listing: the two confirmations, and this wallet's own listings.
            if (wallet != null) {
                SectionTitle(stringResource(R.string.pay_list_title))
                GlassCard {
                    Text(stringResource(R.string.pay_list_step1), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    SelectionContainer { Text("treasury: $wallet", color = RbColors.textPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(RbColors.surfaceVariant).padding(8.dp)) }
                    Text(stringResource(R.string.pay_list_step2), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    Field(pkg, { pkg = it.trim() }, stringResource(R.string.pay_field_package))
                    Field(listCode, { listCode = it.trim() }, stringResource(R.string.pay_field_code))
                    Spacer(Modifier.height(8.dp))
                    val memo = runCatching { ReviewMemo.Treasury(pkg, listCode).also { ReviewMemo.encode(it) } }.getOrNull()
                    if (memo == null && (pkg.isNotEmpty() || listCode.isNotEmpty())) Text(stringResource(R.string.pay_bad_input), color = RbColors.warning, fontSize = 11.sp)
                    PrimaryButton(stage ?: stringResource(R.string.pay_sign), enabled = memo != null && stage == null && sender != null, loading = stage != null, onClick = { memo?.let { sign(it) } })
                    message?.let { (ok, m) -> Text(m, color = if (ok) RbColors.green else RbColors.negative, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                    val mine = remember(entries, wallet) { vm.myTreasuryClaims() }
                    if (mine.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.pay_my_claims), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        mine.forEach { c ->
                            val confirmed = rows.firstOrNull { it.claim.packageName == c.packageName && it.claim.treasury == c.treasury }?.confirmed == true
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${c.packageName} · ${c.code}", color = RbColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(stringResource(if (confirmed) R.string.pay_claim_confirmed else R.string.pay_claim_waiting), color = if (confirmed) RbColors.green else RbColors.warning, fontSize = 11.sp)
                                    details[c.packageName]?.website?.let { w -> PublisherVerifier.wellKnownUrl(w)?.let { u -> Text(u, color = RbColors.textTertiary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
                                }
                                TextButton(enabled = stage == null && sender != null, onClick = { sign(ReviewMemo.Untreasury(c.packageName)) }) { Text(stringResource(R.string.pay_withdraw), color = RbColors.textTertiary, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight, focusedLabelColor = RbColors.purpleLight, unfocusedLabelColor = RbColors.textTertiary))
}

private fun usd(v: Double): String = NumberFormat.getCurrencyInstance(Locale.US).format(v)
private fun amount(raw: Long, decimals: Int): String = java.math.BigDecimal(raw).movePointLeft(decimals).setScale(minOf(decimals, 4), java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** Totals by rail, payers, first and latest payment, and one bar per month (USD at today's prices). */
@Composable
fun TallyView(row: MainViewModel.BoardRow, quotes: Map<String, Prices.Quote>) {
    val t = row.tally ?: return
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
    Row(verticalAlignment = Alignment.CenterVertically) {
        row.usd?.let { Text(usd(it), color = RbColors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.width(8.dp)) }
        Text(stringResource(if (row.usdWhenPaid) R.string.pay_usd_paid else R.string.pay_usd_note), color = RbColors.textTertiary, fontSize = 11.sp)
    }
    Text(stringResource(R.string.pay_totals, amount(t.lamports, 9), amount(t.tokens[AppConfig.Solana.SKR_MINT] ?: 0L, 6), amount(t.tokens[PaymentBoard.USDC_MINT] ?: 0L, 6)), color = RbColors.textSecondary, fontSize = 12.sp)
    Text(stringResource(R.string.pay_row, t.payments, t.payers, t.attestedPayers), color = RbColors.textSecondary, fontSize = 12.sp)
    if (t.first != null && t.last != null) Text(stringResource(R.string.pay_range, df.format(Date(t.first!! * 1000)), df.format(Date(t.last!! * 1000))), color = RbColors.textTertiary, fontSize = 11.sp)
    if (t.ownPayments > 0) Text(stringResource(R.string.pay_own, t.ownPayments), color = RbColors.textTertiary, fontSize = 11.sp)
    if (!row.complete) Text(stringResource(R.string.pay_incomplete), color = RbColors.warning, fontSize = 11.sp)
    val months = t.months.takeLast(12).map { m -> m to (row.monthUsd[m.key] ?: Prices.usd(m.lamports, m.tokens, quotes) ?: 0.0) }
    val max = months.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: return
    Spacer(Modifier.height(6.dp))
    months.forEach { (m, v) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
            Text(m.key, color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.width(58.dp))
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(RbColors.surfaceVariant)) { Box(Modifier.fillMaxWidth((v / max).toFloat().coerceIn(0.02f, 1f)).height(8.dp).background(RbColors.green)) }
            Text(usd(v), color = RbColors.textTertiary, fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
        }
    }
}
