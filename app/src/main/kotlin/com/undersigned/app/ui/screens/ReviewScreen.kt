package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.undersigned.app.R
import com.undersigned.app.solana.ReviewTxService
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.components.label
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.ReviewMemo
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(vm: MainViewModel, pkg: String, sender: ActivityResultSender?, onBack: () -> Unit, onPublished: () -> Unit, onPro: () -> Unit = {}) {
    val context = LocalContext.current
    val catalogue by vm.catalogue.collectAsState()
    val wallet by vm.wallet.collectAsState()
    val selfFacts by vm.selfFacts.collectAsState()
    val selfLoading by vm.selfFactsLoading.collectAsState()
    val name = catalogue.firstOrNull { it.packageName == pkg }?.name ?: vm.labelFor(pkg, pkg)
    val versionCode = remember(pkg) { vm.deviceFacts(pkg)?.versionCode ?: 1L }
    val existing = remember(pkg) { vm.myReview(pkg)?.memo as? ReviewMemo.Review }

    var rating by remember { mutableStateOf(existing?.rating ?: 0) }
    var tags by remember { mutableStateOf(existing?.tags ?: emptyList()) }
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var consent by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }

    val bytes = text.toByteArray(Charsets.UTF_8).size
    val textOk = text.isEmpty() || (text.length <= ReviewMemo.MAX_TEXT_CHARS && bytes <= ReviewMemo.MAX_TEXT_BYTES && text.none { it == '|' || it == '\n' || it == '\r' })
    val gate: String? = when {
        wallet == null -> stringResource(R.string.review_needs_wallet)
        selfLoading || selfFacts == null -> stringResource(R.string.review_checking_sgt)
        selfFacts?.hasGenesisToken != true -> stringResource(R.string.review_needs_sgt)
        else -> null
    }
    val canSubmit = gate == null && rating in 1..5 && tags.isNotEmpty() && textOk && (text.isEmpty() || consent) && stage == null && done == null && sender != null

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = stage == null) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            Column { Text(stringResource(R.string.review_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text(stringResource(R.string.review_for, name), color = RbColors.textTertiary, fontSize = 12.sp, maxLines = 1) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionTitle(stringResource(R.string.review_rating))
            Row {
                (1..5).forEach { i -> Text(if (i <= rating) "★" else "☆", color = if (i <= rating) RbColors.warning else RbColors.textTertiary, fontSize = 34.sp, modifier = Modifier.clickable(enabled = stage == null) { rating = i }.padding(end = 6.dp)) }
            }
            SectionTitle(stringResource(R.string.review_tags))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ReviewMemo.Tag.entries.forEach { t ->
                    val sel = t in tags
                    TagChip(t.label(), selected = sel) { if (stage != null) return@TagChip; tags = if (sel) tags - t else if (tags.size < ReviewMemo.MAX_TAGS) tags + t else tags }
                }
            }
            SectionTitle(stringResource(R.string.review_text_label, ReviewMemo.MAX_TEXT_CHARS - text.length, ReviewMemo.MAX_TEXT_BYTES - bytes))
            OutlinedTextField(
                value = text, onValueChange = { if (stage == null) text = it.take(ReviewMemo.MAX_TEXT_CHARS + 20) }, minLines = 2, maxLines = 4, isError = !textOk,
                placeholder = { Text(stringResource(R.string.review_text_hint), color = RbColors.textTertiary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight, errorBorderColor = RbColors.negative),
            )
            val looksCopied = remember(text) { vm.draftLooksCopied(text, pkg) }
            if (looksCopied) { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.compose_similar_warning), color = RbColors.warning, fontSize = 12.sp, lineHeight = 16.sp) }
            if (text.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                GlassCard(accent = RbColors.warning) {
                    Text(stringResource(R.string.review_consent_title), color = RbColors.warning, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.review_consent_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().clickable(enabled = stage == null) { consent = !consent }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = stage == null, colors = CheckboxDefaults.colors(checkedColor = RbColors.warning))
                        Text(stringResource(R.string.review_consent_check), color = RbColors.textPrimary, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            gate?.let { GlassCard { Text(it, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) }; Spacer(Modifier.height(10.dp)) }
            Text(stringResource(R.string.review_fee_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(12.dp))
            PrimaryButton(stage ?: stringResource(R.string.review_submit), enabled = canSubmit, loading = stage != null, onClick = {
                val s = sender ?: return@PrimaryButton
                error = null
                val memo = ReviewMemo.Review(pkg, rating, tags, versionCode, text.trim().takeIf { it.isNotEmpty() })
                vm.publish(s, memo, onStage = { st ->
                    stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) }
                }) { r -> stage = null; r.fold(onSuccess = { done = it }, onFailure = { error = it.message }) }
            })
            error?.let { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.review_failed, it), color = RbColors.negative, fontSize = 12.sp) }
            done?.let {
                Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.review_done, shortKey(it, 8, 8)), color = RbColors.green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val pro by vm.pro.collectAsState()
                if (pro?.active != true) ProLock(hint = stringResource(R.string.review_also_store), onPro = onPro)
                else SecondaryButton(stringResource(R.string.review_also_store), onClick = { com.undersigned.app.util.StoreHandoff.copyAndOpen(context, pkg, text) })
                if (pro?.active == true) Text(stringResource(R.string.review_also_store_hint), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(8.dp)); SecondaryButton(stringResource(android.R.string.ok), onClick = onPublished)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
