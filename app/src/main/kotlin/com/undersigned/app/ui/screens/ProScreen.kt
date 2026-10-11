package com.undersigned.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun ProScreen(vm: MainViewModel, sender: ActivityResultSender?, onBack: (() -> Unit)?) {
    val context = LocalContext.current
    val wallet by vm.wallet.collectAsState()
    val pro by vm.pro.collectAsState()
    var rail by remember { mutableStateOf(ProPaymentService.Rail.SKR) }
    var consent by remember { mutableStateOf(false) }
    // Express consent to immediate supply and the withdrawal acknowledgement (CRD 16(m), UK CCR reg 37; legal review 9 Oct 2026).
    var withdrawalAck by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    fun open(url: String) { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { } }
    val price = if (rail == ProPaymentService.Rail.SKR) AppConfig.Payment.skrLabel() else AppConfig.Payment.solLabel()
    LaunchedEffect(Unit) { com.undersigned.app.service.GeoAnalyticsService.track(com.undersigned.app.service.GeoAnalyticsService.Events.UPGRADE_VIEWED) }

    Column(Modifier.fillMaxSize().background(RbColors.background)) {
        PhotoBackdrop(R.drawable.photo_pro_hero, Modifier.fillMaxWidth().heightIn(min = 240.dp), fadeFrom = 0.25f) {
            Row(Modifier.align(Alignment.TopStart).systemBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                // The Pro tab has no back arrow; Pro opened from a lock elsewhere does, and returns there.
                onBack?.let { BackButton(it, enabled = stage == null, tint = RbColors.textPrimary) }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Pill(stringResource(R.string.pro_badge), RbColors.purpleLight); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.pro_term), color = RbColors.textSecondary, fontSize = 12.sp) }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.pro_hero_title), color = RbColors.textPrimary, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Where this wallet stands, first: Pro with its dates, or Free (owner, 10 Oct 2026: the Pro tab reads clearly).
            if (pro == null) {
                Spacer(Modifier.height(12.dp))
                GlassCard { Text(stringResource(if (wallet != null) R.string.pro_status_free else R.string.settings_no_wallet), color = RbColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
            pro?.let { p ->
                Spacer(Modifier.height(12.dp))
                GlassCard(accent = if (p.active) RbColors.green else RbColors.warning) {
                    Text(if (p.active) pluralStringResource(R.plurals.pro_active, p.daysLeft().toInt(), p.daysLeft()) else stringResource(R.string.pro_expired, df.format(Date(p.expiresAt * 1000))), color = if (p.active) RbColors.green else RbColors.warning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.pro_paid_line, df.format(Date(p.paidAt * 1000)), p.amountLabel, df.format(Date(p.expiresAt * 1000))), color = RbColors.textSecondary, fontSize = 12.sp)
                    TextButton(onClick = { open(AppConfig.Api.EXPLORER_TX + p.signature) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.pro_view_licence), color = RbColors.purpleLight, fontSize = 12.sp) }
                }
            }
            listOf(R.string.pro_tool_weight_title to R.string.pro_tool_weight_body, R.string.pro_tool_import_title to R.string.pro_tool_import_body, R.string.pro_tool_desk_title to R.string.pro_tool_desk_body,
                R.string.pro_tool_export_title to R.string.pro_tool_export_body, R.string.pro_tool_watch_title to R.string.pro_tool_watch_body, R.string.pro_tool_catalogue_title to R.string.pro_tool_catalogue_body, R.string.pro_tool_translate_title to R.string.pro_tool_translate_body, R.string.pro_tool_digest_title to R.string.pro_tool_digest_body).forEach { (t, b) ->
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
                // The feature text takes the rest of the width and stops 18 dp short of two fixed columns at the right, each
                // header centred over its ticks (owner, 10 Oct 2026: the ticks sat too close to the writing).
                val col = 76.dp
                @Composable fun row(label: Int, free: String, proV: String) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        // Long words (German at 130 % text) broke mid-word in the narrowed column; hyphenate them properly instead.
                        Text(stringResource(label), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f).padding(end = 18.dp),
                            style = LocalTextStyle.current.copy(hyphens = androidx.compose.ui.text.style.Hyphens.Auto, lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph))
                        Text(free, color = RbColors.textTertiary, fontSize = if (free.length <= 2) 13.sp else 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(col))
                        Text(proV, color = RbColors.textPrimary, fontSize = if (proV.length <= 2) 13.sp else 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(col))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.fp_free), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(col))
                    Text(stringResource(R.string.fp_pro), color = RbColors.purpleLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(col))
                }
                row(R.string.fp_read, "✓", "✓"); row(R.string.fp_sign, "✓", "✓"); row(R.string.fp_facts, "✓", "✓"); row(R.string.fp_own_tags, "—", "✓"); row(R.string.fp_claim, "✓", "✓")
                // One review at a time is free (feature review, 9 Oct 2026); Pro copies them all in one pass.
                row(R.string.fp_copy, stringResource(R.string.fp_import_free), "✓"); row(R.string.fp_import, stringResource(R.string.fp_import_free), stringResource(R.string.fp_import_pro))
                row(R.string.fp_desk, "—", "✓"); row(R.string.fp_export, "—", "✓"); row(R.string.fp_watch, stringResource(R.string.fp_watch_free), stringResource(R.string.fp_watch_pro))
                row(R.string.fp_catalogue, "✓", "✓"); row(R.string.fp_catalogue_weekly, "—", "✓")
                HorizontalDivider(color = RbColors.surfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.fp_weight), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f).padding(end = 18.dp))
                    Text(stringResource(R.string.fp_weight_both), color = RbColors.green, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(col * 2))
                }
            }

            SectionTitle(stringResource(R.string.pro_pay_in))
            Row(Modifier.selectableGroup(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(ProPaymentService.Rail.SKR to AppConfig.Payment.skrLabel(), ProPaymentService.Rail.SOL to AppConfig.Payment.solLabel()).forEach { (r, label) ->
                    val sel = rail == r
                    Column(Modifier.weight(1f).selectable(selected = sel, enabled = stage == null, role = androidx.compose.ui.semantics.Role.RadioButton) { rail = r }.background(if (sel) RbColors.purple.copy(alpha = 0.22f) else RbColors.card, androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).padding(14.dp)) {
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
                StatRow(stringResource(R.string.pro_line_total), stringResource(R.string.pro_total_value, price, stringResource(R.string.pro_line_fee_short)))
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
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = stage == null) { withdrawalAck = !withdrawalAck }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = withdrawalAck, onCheckedChange = { withdrawalAck = it }, enabled = stage == null, colors = CheckboxDefaults.colors(checkedColor = RbColors.warning))
                    Text(stringResource(R.string.withdrawal_consent), color = RbColors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (wallet == null) GlassCard { Text(stringResource(R.string.pro_needs_wallet), color = RbColors.textSecondary, fontSize = 13.sp) }
            PrimaryButton(stage ?: (if (pro?.active == true) stringResource(R.string.pro_renew) else stringResource(R.string.pro_sign_pay)), enabled = consent && withdrawalAck && wallet != null && sender != null && stage == null, loading = stage != null, onClick = {
                val s = sender ?: return@PrimaryButton
                message = null
                vm.payPro(s, rail, onStage = { st -> stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { r ->
                    stage = null
                    message = r.fold(onSuccess = { sig -> true to (context.getString(R.string.pro_success, vm.pro.value?.let { df.format(Date(it.expiresAt * 1000)) } ?: "", shortKey(sig, 6, 6)) + "\n" + context.getString(R.string.withdrawal_confirmed, sig)) }, onFailure = { e -> false to (e.message ?: context.getString(R.string.action_failed)) })
                }
            })
            message?.let { (ok, m) -> Spacer(Modifier.height(8.dp)); Text(m, color = if (ok) RbColors.green else RbColors.negative, fontSize = 13.sp, lineHeight = 18.sp) }
            onBack?.let { Spacer(Modifier.height(8.dp)); SecondaryButton(stringResource(R.string.pro_stay_free), onClick = it, enabled = stage == null) }

            // The publisher plan is a separate purchase, on the website: per app, by the month, for the app's proven publisher.
            SectionTitle(stringResource(R.string.pro_publisher_title))
            GlassCard {
                Text(stringResource(R.string.pro_publisher_body), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                TextButton(onClick = { open(AppConfig.Site.URL + "/plans") }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.pro_publisher_open), color = RbColors.purpleLight, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { open(AppConfig.Identity.URI + "terms.html#pro") }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.pro_refunds), color = RbColors.purpleLight, fontSize = 12.sp) }
            Spacer(Modifier.height(32.dp))
        }
    }
}
