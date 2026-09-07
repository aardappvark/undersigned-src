package com.undersigned.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.undersigned.app.solana.ReviewTxService
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.ReviewMemo
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDetailScreen(vm: MainViewModel, pkg: String, sender: ActivityResultSender?, onBack: () -> Unit, onReview: () -> Unit) {
    val context = LocalContext.current
    val catalogue by vm.catalogue.collectAsState()
    val installed by vm.installed.collectAsState()
    val scores by vm.scores.collectAsState()
    val entries by vm.entries.collectAsState()
    val hidden by vm.hidden.collectAsState()
    val wallet by vm.wallet.collectAsState()
    val app = catalogue.firstOrNull { it.packageName == pkg }
    val name = app?.name ?: vm.labelFor(pkg, pkg)
    val score = scores[pkg]
    val isInstalled = pkg in installed
    val facts = remember(pkg, isInstalled) { if (isInstalled) vm.deviceFacts(pkg) else null }
    val reviews = remember(entries, hidden, scores) { vm.reviewsFor(pkg) }
    val mine = remember(entries, wallet) { vm.myReview(pkg) }
    var revokeDialog by remember { mutableStateOf(false) }
    var revokeStage by remember { mutableStateOf<String?>(null) }
    val icons by vm.iconCache.state.collectAsState()
    val follows by vm.follows.collectAsState()
    var replyTo by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }
    var replyConsent by remember { mutableStateOf(false) }
    var replyStage by remember { mutableStateOf<String?>(null) }
    var replyError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pkg) { vm.resolveIcons(listOf(pkg)) }
    val hiddenCount = remember(entries, hidden) { entries.count { it.signature in hidden && (it.memo as? ReviewMemo.Review)?.packageName == pkg } }

    fun openStore() { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.Store.DEEP_LINK + pkg))) } catch (_: Exception) { } }
    fun report(sig: String) { try { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + context.getString(R.string.settings_contact) + "?subject=" + Uri.encode("Undersigned report: $sig") + "&body=" + Uri.encode("Package: $pkg\nReview signature: $sig\nReason: ")))) } catch (_: Exception) { } }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            DAppIcon(pkg, name, icons[pkg]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(pkg + (app?.updated?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""), color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1)
            }
            IconButton(onClick = { vm.toggleFollow(pkg) }) {
                Icon(if (pkg in follows) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = stringResource(if (pkg in follows) R.string.following else R.string.follow), tint = if (pkg in follows) RbColors.negative else RbColors.textSecondary)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(stringResource(R.string.detail_open_store), onClick = { openStore() }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(if (mine != null) stringResource(R.string.detail_update_review) else stringResource(R.string.detail_write_review), onClick = onReview)
            if (mine != null) { Spacer(Modifier.height(6.dp)); SecondaryButton(stringResource(R.string.detail_revoke_review), onClick = { revokeDialog = true }) }

            SectionTitle(stringResource(R.string.detail_score_title))
            GlassCard {
                if (score != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format(java.util.Locale.US, "%.1f", score.weightedMean), color = RbColors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(12.dp))
                        Column { RatingStars(score.weightedMean, size = 18); Text(stringResource(R.string.row_signed_count, score.countedReviews), color = RbColors.textSecondary, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.detail_rank_score, String.format(java.util.Locale.US, "%.2f", score.weightedRating)), color = RbColors.textTertiary, fontSize = 11.sp)
                } else Text(stringResource(R.string.row_no_reviews), color = RbColors.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.detail_score_sub), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            }

            if (score != null && score.tagCounts.isNotEmpty()) {
                SectionTitle(stringResource(R.string.detail_tags_title))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    score.tagCounts.entries.sortedByDescending { it.value }.forEach { (t, n) -> TagChip(t.wire, n) }
                }
            }

            SectionTitle(stringResource(R.string.detail_facts_title))
            GlassCard(accent = if (facts?.delta?.signerChanged == true) RbColors.negative else RbColors.glassBorder) {
                Text(stringResource(R.string.detail_facts_sub), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(8.dp))
                if (facts == null) Text(stringResource(R.string.fact_not_installed), color = RbColors.textSecondary, fontSize = 13.sp)
                else {
                    StatRow(stringResource(R.string.fact_version), facts.versionCode.toString())
                    StatRow(stringResource(R.string.fact_signer), shortKey(facts.signerSha256, 8, 8))
                    StatRow(stringResource(R.string.fact_perms), facts.dangerousPermissions.size.toString())
                    if (facts.dangerousPermissions.isNotEmpty()) Text(facts.dangerousPermissions.joinToString(", "), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                    facts.delta?.let { d ->
                        if (d.signerChanged) { Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.fact_signer_changed), color = RbColors.negative, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        if (d.permissionsAdded.isNotEmpty()) Text(stringResource(R.string.fact_perms_added, d.permissionsAdded.joinToString(", ")), color = RbColors.warning, fontSize = 12.sp)
                        if (d.permissionsRemoved.isNotEmpty()) Text(stringResource(R.string.fact_perms_removed, d.permissionsRemoved.joinToString(", ")), color = RbColors.textSecondary, fontSize = 12.sp)
                    }
                }
            }

            SectionTitle(stringResource(R.string.reviews_title))
            if (reviews.isEmpty()) Text(stringResource(R.string.row_no_reviews), color = RbColors.textSecondary, fontSize = 13.sp)
            reviews.forEach { (e, w) ->
                val r = e.memo as ReviewMemo.Review
                GlassCard(accent = if (w > 0) RbColors.glassBorder else RbColors.surfaceVariant) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingStars(r.rating.toDouble())
                        Spacer(Modifier.width(8.dp))
                        Text(if (e.signer == wallet) stringResource(R.string.review_by_you) else shortKey(e.signer, 4, 4), color = RbColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        if (w > 0) Pill(stringResource(R.string.review_weight_verified) + if (w > 1.0) String.format(java.util.Locale.US, " %.1f×", w) else "", RbColors.green) else Pill(stringResource(R.string.review_weight_unverified), RbColors.textTertiary)
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { r.tags.forEach { TagChip(it.wire) } }
                    r.text?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RbColors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp) }
                    Spacer(Modifier.height(6.dp))
                    val replies = remember(entries, hidden) { vm.repliesFor(e.signature) }
                    replies.forEach { rp ->
                        Spacer(Modifier.height(6.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RbColors.surfaceVariant).padding(10.dp)) {
                            Text((if (rp.signer == wallet) stringResource(R.string.review_by_you) else shortKey(rp.signer, 4, 4)) + " · " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(rp.blockTime * 1000)), color = RbColors.textTertiary, fontSize = 11.sp)
                            Text((rp.memo as ReviewMemo.Reply).text, color = RbColors.textPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(e.blockTime * 1000)) + " · v${r.versionCode}", color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        if (wallet != null) TextButton(onClick = { replyTo = e.signature; replyText = ""; replyConsent = false; replyError = null }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(stringResource(R.string.reply), color = RbColors.purpleLight, fontSize = 12.sp) }
                        if (e.signer != wallet) {
                            TextButton(onClick = { vm.hide(e.signature) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(stringResource(R.string.review_hide), color = RbColors.textTertiary, fontSize = 12.sp) }
                            TextButton(onClick = { report(e.signature) }, contentPadding = PaddingValues(horizontal = 6.dp)) { Text(stringResource(R.string.review_report), color = RbColors.textTertiary, fontSize = 12.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (hiddenCount > 0) Text(stringResource(R.string.review_hidden_note, hiddenCount), color = RbColors.textTertiary, fontSize = 11.sp)
            Spacer(Modifier.height(32.dp))
        }
    }

    replyTo?.let { target ->
        val bytes = replyText.toByteArray(Charsets.UTF_8).size
        val ok = replyText.isNotBlank() && replyText.length <= ReviewMemo.MAX_TEXT_CHARS && bytes <= ReviewMemo.MAX_TEXT_BYTES && replyText.none { it == '|' || it == '\n' || it == '\r' }
        AlertDialog(
            onDismissRequest = { if (replyStage == null) replyTo = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.reply_title), color = RbColors.textPrimary) },
            text = {
                Column {
                    OutlinedTextField(value = replyText, onValueChange = { if (replyStage == null) replyText = it.take(ReviewMemo.MAX_TEXT_CHARS + 20) }, minLines = 2, maxLines = 4, isError = replyText.isNotEmpty() && !ok,
                        placeholder = { Text(stringResource(R.string.reply_hint), color = RbColors.textTertiary) }, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight))
                    Text(stringResource(R.string.review_text_label, ReviewMemo.MAX_TEXT_CHARS - replyText.length, ReviewMemo.MAX_TEXT_BYTES - bytes), color = RbColors.textTertiary, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.review_consent_body), color = RbColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                    Row(Modifier.fillMaxWidth().clickable(enabled = replyStage == null) { replyConsent = !replyConsent }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = replyConsent, onCheckedChange = { replyConsent = it }, enabled = replyStage == null, colors = CheckboxDefaults.colors(checkedColor = RbColors.warning))
                        Text(stringResource(R.string.review_consent_check), color = RbColors.textPrimary, fontSize = 11.sp)
                    }
                    replyStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }
                    replyError?.let { Text(it, color = RbColors.negative, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = ok && replyConsent && replyStage == null && sender != null, onClick = {
                    val s = sender ?: return@TextButton
                    replyError = null
                    vm.publish(s, ReviewMemo.Reply(target, replyText.trim()), onStage = { st ->
                        replyStage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) }
                    }) { r -> replyStage = null; r.fold(onSuccess = { replyTo = null }, onFailure = { replyError = it.message }) }
                }) { Text(stringResource(R.string.reply_publish), color = RbColors.purpleLight) }
            },
            dismissButton = { TextButton(enabled = replyStage == null, onClick = { replyTo = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } },
        )
    }

    if (revokeDialog && mine != null) {
        AlertDialog(
            onDismissRequest = { if (revokeStage == null) revokeDialog = false }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.revoke_title), color = RbColors.textPrimary) },
            text = { Text(revokeStage ?: stringResource(R.string.revoke_body), color = RbColors.textSecondary, fontSize = 13.sp) },
            confirmButton = {
                TextButton(enabled = revokeStage == null, onClick = {
                    val s = sender ?: return@TextButton
                    vm.publish(s, ReviewMemo.Revocation(mine.signature), onStage = { st ->
                        revokeStage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) }
                    }) { r -> revokeStage = null; revokeDialog = false; r.onFailure { revokeStage = null } }
                }) { Text(stringResource(R.string.revoke_confirm), color = RbColors.negative) }
            },
            dismissButton = { TextButton(enabled = revokeStage == null, onClick = { revokeDialog = false }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } },
        )
    }
}
