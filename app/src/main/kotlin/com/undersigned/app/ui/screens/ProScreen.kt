package com.undersigned.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.undersigned.app.R
import com.undersigned.app.solana.ProPaymentService
import com.undersigned.app.solana.ReviewTxService
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.text.DateFormat
import java.util.Date

/**
 * The one screen that sells Pro (design: Screens D). A photograph is permitted: it is a single reading.
 * Pro buys tools, never standing; the price pair is the fleet's, read from AppConfig.Payment.
 */
@Composable
fun ProScreen(vm: MainViewModel, sender: ActivityResultSender?, onBack: () -> Unit) {
    val context = LocalContext.current
    val wallet by vm.wallet.collectAsState()
    val pro by vm.pro.collectAsState()
    var rail by remember { mutableStateOf(ProPaymentService.Rail.SKR) }
    var consent by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val df = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    fun open(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }
    val price = if (rail == ProPaymentService.Rail.SKR) AppConfig.Payment.skrLabel() else AppConfig.Payment.solLabel()

    Column(Modifier.fillMaxSize().background(RbColors.background)) {
        PhotoBackdrop(R.drawable.photo_pro_hero, Modifier.fillMaxWidth().height(240.dp), fadeFrom = 0.25f) {
            Row(Modifier.align(Alignment.TopStart).systemBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                IconButton(onClick = onBack, enabled = stage == null) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textPrimary) }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Pill(stringResource(R.string.pro_badge), RbColors.purpleLight); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.pro_term), color = RbColors.textSecondary, fontSize = 12.sp) }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.pro_hero_title), color = RbColors.textPrimary, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            pro?.let { p ->
                Spacer(Modifier.height(12.dp))
                GlassCard(accent = if (p.active) RbColors.green else RbColors.warning) {
                    Text(if (p.active) stringResource(R.string.pro_active, p.daysLeft()) else stringResource(R.string.pro_expired, df.format(Date(p.expiresAt * 1000))), color = if (p.active) RbColors.green else RbColors.warning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pro_paid_line, df.format(Date(p.paidAt * 1000)), p.amountLabel, df.format(Date(p.expiresAt * 1000))), color = RbColors.textSecondary, fontSize = 12.sp)
                    TextButton(onClick = { open(AppConfig.Api.EXPLORER_TX + p.signature) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.pro_view_licence), color = RbColors.purpleLight, fontSize = 12.sp) }
                }
            }
            listOf(R.string.pro_tool_copy_title to R.string.pro_tool_copy_body, R.string.pro_tool_import_title to R.string.pro_tool_import_body, R.string.pro_tool_desk_title to R.string.pro_tool_desk_body,
                R.string.pro_tool_export_title to R.string.pro_tool_export_body, R.string.pro_tool_watch_title to R.string.pro_tool_watch_body).forEach { (t, b) ->
                Column(Modifier.padding(top = 14.dp)) {
                    Text(stringResource(t), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(b), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            SectionTitle(stringResource(R.string.pro_not_title))
            GlassCard { Text(stringResource(R.string.pro_not_body), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.pro_free_keeps), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 16.sp)

            SectionTitle(stringResource(R.string.free_pro_title))
            GlassCard {
                @Composable fun row(label: Int, free: String, proV: String) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label), color = RbColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1.6f))
                        Text(free, color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                        Text(proV, color = RbColors.textPrimary, fontSize = 11.sp, modifier = Modifier.weight(0.8f))
                    }
                }
                Row { Spacer(Modifier.weight(1.6f)); Text(stringResource(R.string.fp_free), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f)); Text(stringResource(R.string.fp_pro), color = RbColors.purpleLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f)) }
                row(R.string.fp_read, "✓", "✓"); row(R.string.fp_sign, "✓", "✓"); row(R.string.fp_facts, "✓", "✓"); row(R.string.fp_claim, "✓", "✓")
                row(R.string.fp_copy, "—", "✓"); row(R.string.fp_import, stringResource(R.string.fp_import_free), stringResource(R.string.fp_import_pro))
                row(R.string.fp_desk, "—", "✓"); row(R.string.fp_export, "—", "✓"); row(R.string.fp_watch, stringResource(R.string.fp_watch_free), stringResource(R.string.fp_watch_pro))
                HorizontalDivider(color = RbColors.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.fp_weight), color = RbColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1.6f)); Text(stringResource(R.string.fp_weight_both), color = RbColors.green, fontSize = 11.sp, modifier = Modifier.weight(1.6f)) }
            }

            SectionTitle(stringResource(R.string.pro_pay_in))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(ProPaymentService.Rail.SKR to AppConfig.Payment.skrLabel(), ProPaymentService.Rail.SOL to AppConfig.Payment.solLabel()).forEach { (r, label) ->
                    val sel = rail == r
                    Column(Modifier.weight(1f).clickable(enabled = stage == null) { rail = r }.background(if (sel) RbColors.purple.copy(alpha = 0.22f) else RbColors.card, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(14.dp)) {
                        Text(label, color = RbColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(if (r == ProPaymentService.Rail.SKR) R.string.pro_rail_skr_note else R.string.pro_rail_sol_note), color = RbColors.textTertiary, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.pro_price_line), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            if (rail == ProPaymentService.Rail.SKR) Text(stringResource(R.string.pro_skr_weight_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)

            SectionTitle(stringResource(R.string.pro_paying_title))
            GlassCard {
                StatRow(stringResource(R.string.pro_line_licence), price)
                StatRow(stringResource(R.string.pro_line_fee), stringResource(R.string.pro_line_fee_value))
                HorizontalDivider(color = RbColors.surfaceVariant)
                StatRow(stringResource(R.string.pro_line_total), "$price + " + stringResource(R.string.pro_line_fee_value).removePrefix("≈"))
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.pro_paid_to), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            }

            SectionTitle(stringResource(R.string.pro_before_title))
            GlassCard(accent = RbColors.warning) {
                Text(stringResource(R.string.pro_before_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Row(Modifier.fillMaxWidth().clickable(enabled = stage == null) { consent = !consent }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = stage == null, colors = CheckboxDefaults.colors(checkedColor = RbColors.warning))
                    Text(stringResource(R.string.pro_consent), color = RbColors.textPrimary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (wallet == null) GlassCard { Text(stringResource(R.string.pro_needs_wallet), color = RbColors.textSecondary, fontSize = 13.sp) }
            PrimaryButton(stage ?: (if (pro?.active == true) stringResource(R.string.pro_renew) else stringResource(R.string.pro_sign_pay)), enabled = consent && wallet != null && sender != null && stage == null, loading = stage != null, onClick = {
                val s = sender ?: return@PrimaryButton
                message = null
                vm.payPro(s, rail, onStage = { st -> stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { r ->
                    stage = null
                    message = r.fold(onSuccess = { sig -> true to context.getString(R.string.pro_success, vm.pro.value?.let { df.format(Date(it.expiresAt * 1000)) } ?: "", shortKey(sig, 6, 6)) }, onFailure = { e -> false to (e.message ?: context.getString(R.string.action_failed)) })
                }
            })
            message?.let { (ok, m) -> Spacer(Modifier.height(8.dp)); Text(m, color = if (ok) RbColors.green else RbColors.negative, fontSize = 13.sp, lineHeight = 18.sp) }
            Spacer(Modifier.height(8.dp))
            SecondaryButton(stringResource(R.string.pro_stay_free), onClick = onBack, enabled = stage == null)
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { open(AppConfig.Identity.URI + "terms.html#pro") }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.pro_refunds), color = RbColors.purpleLight, fontSize = 12.sp) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** The lock a Pro tool shows in place, where the user meets it: what it is, then the way in. */
@Composable
fun ProLock(hint: String? = null, onPro: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onPro() }.background(RbColors.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Pill(stringResource(R.string.pro_badge), RbColors.purpleLight)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(hint ?: stringResource(R.string.pro_locked_hint), color = RbColors.textSecondary, fontSize = 12.sp)
            Text(stringResource(R.string.pro_get, AppConfig.Payment.solLabel(), AppConfig.Payment.skrLabel()), color = RbColors.purpleLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
