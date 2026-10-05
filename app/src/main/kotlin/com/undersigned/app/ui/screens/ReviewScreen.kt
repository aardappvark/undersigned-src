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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.selection.toggleable
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
    // The installed version; for an app not on this phone, the store's current version (a review filed as "version 1"
    // polluted the per-version scores, and the web path already records the store's version).
    val storeDetails by vm.storeDetails.collectAsState()
    val versionCode = remember(pkg, storeDetails[pkg]) { vm.deviceFacts(pkg)?.versionCode ?: storeDetails[pkg]?.versionCode?.takeIf { it > 0 } ?: 1L }
    val existing = remember(pkg) { vm.myReview(pkg)?.memo as? ReviewMemo.Review }

    // Saveable: signing hops to the wallet app, and Android may stop this one meanwhile; a typed review must survive it.
    var rating by rememberSaveable(pkg) { mutableStateOf(existing?.rating ?: 0) }
    var tags by rememberSaveable(pkg, stateSaver = TagListSaver) { mutableStateOf(existing?.tags ?: emptyList()) }
    var text by rememberSaveable(pkg) { mutableStateOf(existing?.text ?: "") }
    var consent by rememberSaveable(pkg) { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    // Install age rides in the review's own transaction; on by default because the reviewer sees exactly what is added.
    val installAge = remember(pkg) { vm.installAge(pkg) }
    val reinstalls = remember(pkg) { vm.reinstalls(pkg) }
    var addAge by remember { mutableStateOf(true) }
    // Usage access is a system-settings switch, so re-read it whenever the screen comes back from Settings.
    var resumes by remember { mutableStateOf(0) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) { val o = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumes++ }; owner.lifecycle.addObserver(o); onDispose { owner.lifecycle.removeObserver(o) } }
    val hasUsageAccess = remember(resumes) { com.undersigned.app.device.UsageTime.hasAccess(context) }
    val useTime = remember(pkg, hasUsageAccess) { if (hasUsageAccess && installAge != null) vm.useTime(pkg) else null }
    var addTime by remember { mutableStateOf(true) }

    val bytes = text.toByteArray(Charsets.UTF_8).size
    val textOk = text.isEmpty() || (text.length <= ReviewMemo.MAX_TEXT_CHARS && bytes <= ReviewMemo.MAX_TEXT_BYTES && text.none { it == '|' || it == '\n' || it == '\r' })
    val gate: String? = when {
        wallet == null -> stringResource(R.string.review_needs_wallet)
        selfLoading || selfFacts == null -> stringResource(R.string.review_checking_sgt)
        selfFacts?.hasGenesisToken != true -> stringResource(R.string.review_needs_sgt)
        else -> null
    }
    // Back while signing would lose the outcome; back with unsaved changes asks first.
    val dirty = done == null && (rating != (existing?.rating ?: 0) || tags != (existing?.tags ?: emptyList<ReviewMemo.Tag>()) || text != (existing?.text ?: ""))
    fun leave() { when { stage != null -> Unit; dirty -> confirmLeave = true; else -> onBack() } }
    androidx.activity.compose.BackHandler { leave() }
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, containerColor = RbColors.surface,
        title = { Text(stringResource(R.string.discard_title), color = RbColors.textPrimary) },
        text = { Text(stringResource(R.string.discard_body), color = RbColors.textSecondary, fontSize = 13.sp) },
        confirmButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text(stringResource(R.string.discard), color = RbColors.negative) } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.keep_editing), color = RbColors.purpleLight) } })
    val canSubmit = gate == null && rating in 1..5 && tags.isNotEmpty() && textOk && (text.isEmpty() || consent) && stage == null && done == null && sender != null

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton({ leave() }, enabled = stage == null)
            Column { Text(stringResource(R.string.review_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text(stringResource(R.string.review_for, name), color = RbColors.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // "Still true on this version?": the review on the ledger is of an older version than this phone runs. One tap
            // signs the same stars, tags and text for the version installed now, so per-version scores fill as apps update.
            val installedVersion = remember(pkg) { vm.deviceFacts(pkg)?.versionCode }
            if (existing != null && installedVersion != null && existing.versionCode < installedVersion && done == null) {
                Spacer(Modifier.height(8.dp))
                GlassCard {
                    Text(stringResource(R.string.resign_title, existing.versionCode, installedVersion), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(if (existing.text.isNullOrEmpty()) R.string.resign_body else R.string.resign_body_text), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(stage ?: stringResource(R.string.resign_button, installedVersion), enabled = gate == null && stage == null && sender != null, loading = stage != null, onClick = {
                        val s = sender ?: return@PrimaryButton
                        error = null
                        vm.publishReview(s, ReviewMemo.Review(pkg, existing.rating, existing.tags, installedVersion, existing.text), installAge?.takeIf { addAge }, useTime?.takeIf { addTime }, onStage = { st ->
                            stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) }
                        }) { r -> stage = null; r.fold(onSuccess = { done = it }, onFailure = { error = context.errorText(it) }) }
                    })
                    Text(stringResource(R.string.resign_or_edit), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            SectionTitle(stringResource(R.string.review_rating))
            RatingInput(rating, enabled = stage == null) { rating = it }
            SectionTitle(stringResource(R.string.review_tags))
            TagPicker(tags, enabled = stage == null) { tags = it }
            SectionTitle(stringResource(R.string.review_text_label, ReviewMemo.MAX_TEXT_CHARS - text.length, ReviewMemo.MAX_TEXT_BYTES - bytes))
            OutlinedTextField(
                // A line break can't be signed: Enter and pasted paragraphs become spaces instead of a silent red border.
                value = text, onValueChange = { if (stage == null) text = it.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').take(ReviewMemo.MAX_TEXT_CHARS + 20) }, minLines = 2, maxLines = 4, isError = !textOk,
                supportingText = if (!textOk) { { Text(stringResource(R.string.text_invalid, ReviewMemo.MAX_TEXT_CHARS), color = RbColors.negative, fontSize = 11.sp) } } else null,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                placeholder = { Text(stringResource(R.string.review_text_hint), color = RbColors.textTertiary) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight, errorBorderColor = RbColors.negative),
            )
            val looksCopied = remember(text) { vm.draftLooksCopied(text, pkg) }
            if (looksCopied) { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.compose_similar_warning), color = RbColors.warning, fontSize = 12.sp, lineHeight = 16.sp) }
            // Design review 28 Sep 2026 (handoff 2, F5–F6): what the phone adds, as one card of switch rows with the value as
            // a chip, ABOVE the permanence note; permanence in purple with the mark, not amber.
            Spacer(Modifier.height(14.dp))
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = RbColors.purpleLight, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.use_card_title), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
                if (installAge == null) Text(stringResource(R.string.use_not_installed), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
                else {
                    val ageDesc = stringResource(R.string.use_add, useLabel(installAge))
                    ExtraRow(stringResource(R.string.use_row_label), useLabel(installAge), addAge, enabled = stage == null, description = ageDesc) { addAge = it }
                    if (reinstalls > 0) Text(stringResource(R.string.use_reinstalled, reinstalls), color = RbColors.textTertiary, fontSize = 11.sp)
                    if (useTime != null) {
                        val timeDesc = stringResource(R.string.time_add, timeLabel(useTime))
                        ExtraRow(stringResource(R.string.time_row_label), timeLabel(useTime), addTime, enabled = stage == null, description = timeDesc) { addTime = it }
                    } else if (!hasUsageAccess) {
                        HorizontalDivider(color = RbColors.surfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
                        Text(stringResource(R.string.time_needs_access), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                        OutlinedButton(onClick = { runCatching { context.startActivity(com.undersigned.app.device.UsageTime.settingsIntent()) } }, modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                            Text(stringResource(R.string.time_allow), color = RbColors.purpleLight, fontSize = 13.sp) }
                    }
                    Text(stringResource(R.string.use_card_hint), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (text.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                PermanenceNote(stringResource(R.string.permanent_title), stringResource(R.string.review_consent_body)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = stage == null) { consent = !consent }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it }, enabled = stage == null, colors = CheckboxDefaults.colors(checkedColor = RbColors.purple))
                        Text(stringResource(R.string.review_consent_check), color = RbColors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
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
                vm.publishReview(s, memo, installAge?.takeIf { addAge }, useTime?.takeIf { addTime }, onStage = { st ->
                    stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) }
                }) { r -> stage = null; r.fold(onSuccess = { done = it }, onFailure = { error = context.errorText(it) }) }
            })
            error?.let { Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.review_failed, it), color = RbColors.negative, fontSize = 12.sp) }
            done?.let {
                Spacer(Modifier.height(8.dp)); Text(stringResource(R.string.review_done, shortKey(it, 8, 8)), color = RbColors.green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val pro by vm.pro.collectAsState()
                var storeHandoff by remember { mutableStateOf(false) }
                if (pro?.active != true) ProLock(hint = stringResource(R.string.review_also_store), onPro = onPro)
                else SecondaryButton(stringResource(R.string.review_also_store), onClick = { storeHandoff = true })
                if (storeHandoff) com.undersigned.app.ui.components.StoreHandoffDialog(pkg, text) { storeHandoff = false }
                if (pro?.active == true) Text(stringResource(R.string.review_also_store_hint), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(8.dp)); SecondaryButton(stringResource(android.R.string.ok), onClick = onPublished)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** One "Your phone adds" row: label, the value as a chip, a switch; the whole ≥ 56 dp row toggles. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraRow(label: String, value: String, on: Boolean, enabled: Boolean, description: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = on, enabled = enabled, role = androidx.compose.ui.semantics.Role.Switch) { onChange(it) }.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically) {
        // Label and chip wrap as units (the chip drops under the label), never mid-label (seen on device, 28 Sep).
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = RbColors.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
            Box(Modifier.clip(RoundedCornerShape(50)).background(RbColors.surfaceVariant).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(value, color = RbColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false) }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = on, onCheckedChange = null, enabled = enabled)
    }
}

/** Saves the chosen tags across process death by their wire names. */
internal val TagListSaver = androidx.compose.runtime.saveable.listSaver<List<ReviewMemo.Tag>, String>(
    save = { l -> l.map { it.wire } }, restore = { l -> l.mapNotNull { ReviewMemo.Tag.fromWire(it) } })
