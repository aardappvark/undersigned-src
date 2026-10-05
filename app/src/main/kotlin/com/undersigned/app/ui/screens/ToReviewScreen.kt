package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.undersigned.app.R
import com.undersigned.app.solana.ReviewTxService
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.ReviewMemo

/**
 * "Not reviewed on this phone": installed store apps this wallet has not reviewed and has had a week or used an hour.
 * Review one in the full composer, or tick several and sign them together in one approval (stars and tags, no text).
 */
@Composable
fun ToReviewScreen(vm: MainViewModel, sender: ActivityResultSender?, onBack: () -> Unit, onReview: (String) -> Unit) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsState()
    val installed by vm.installed.collectAsState()
    val wallet by vm.wallet.collectAsState()
    val selfFacts by vm.selfFacts.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    val list = remember(entries, installed, wallet) { vm.toReview() }
    // Saveable: signing hops to the wallet app and Android may stop this one meanwhile.
    var picked by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var batch by rememberSaveable { mutableStateOf(false) }
    var ratings by rememberSaveable { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var tagsOf by rememberSaveable(stateSaver = TagMapSaver) { mutableStateOf<Map<String, List<ReviewMemo.Tag>>>(emptyMap()) }
    var stage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    LaunchedEffect(list) { vm.resolveIcons(list.take(40).map { it.pkg }) }
    val gate: String? = when {
        wallet == null -> stringResource(R.string.review_needs_wallet)
        selfFacts?.hasGenesisToken == false -> stringResource(R.string.review_needs_sgt)
        else -> null
    }
    // Always handled: system Back during signing used to pop the screen and lose the result.
    androidx.activity.compose.BackHandler { if (stage == null) { if (batch) batch = false else onBack() } }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton({ if (batch) batch = false else onBack() }, enabled = stage == null)
            Text(stringResource(if (batch) R.string.to_review_batch_title else R.string.to_review_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (!batch) {
            Text(stringResource(R.string.to_review_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 20.dp))
            gate?.let { Text(it, color = RbColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
            message?.let { (ok, m) -> Text(m, color = if (ok) RbColors.green else RbColors.negative, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
            if (picked.size >= MAX_BATCH) Text(stringResource(R.string.to_review_max, MAX_BATCH), color = RbColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            if (list.isEmpty()) Text(stringResource(R.string.to_review_empty), color = RbColors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(20.dp))
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.pkg }) { a ->
                    val on = a.pkg in picked
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = on, enabled = gate == null && (on || picked.size < MAX_BATCH), onCheckedChange = { picked = if (on) picked - a.pkg else picked + a.pkg })
                            DAppIcon(a.pkg, a.name, icons[a.pkg]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(36.dp)); Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(a.age?.let { stringResource(R.string.use_pill, useLabel(it)) }, a.time?.let { stringResource(R.string.time_pill, timeLabel(it)) }).joinToString(" · "),
                                    color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 2)
                            }
                            TextButton(onClick = { onReview(a.pkg) }, enabled = gate == null) { Text(stringResource(R.string.to_review_one), color = RbColors.purpleLight, fontSize = 13.sp) }
                        }
                    }
                }
            }
            if (picked.isNotEmpty()) Column(Modifier.padding(16.dp)) {
                PrimaryButton(pluralStringResource(R.plurals.to_review_together, picked.size, picked.size), enabled = gate == null, onClick = { message = null; batch = true })
            }
        } else {
            val chosen = list.filter { it.pkg in picked }
            val ready = chosen.isNotEmpty() && chosen.all { (ratings[it.pkg] ?: 0) in 1..5 && !tagsOf[it.pkg].isNullOrEmpty() }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(stringResource(R.string.to_review_batch_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp) }
                items(chosen, key = { "b:" + it.pkg }) { a ->
                    GlassCard {
                        Text(a.name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        RatingInput(ratings[a.pkg] ?: 0, enabled = stage == null, size = 30) { ratings = ratings + (a.pkg to it) }
                        TagPicker(tagsOf[a.pkg] ?: emptyList(), enabled = stage == null) { tagsOf = tagsOf + (a.pkg to it) }
                    }
                }
                item { Text(stringResource(R.string.review_fee_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp) }
            }
            Column(Modifier.padding(16.dp)) {
                message?.let { (ok, m) -> Text(m, color = if (ok) RbColors.green else RbColors.negative, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }
                PrimaryButton(stage ?: pluralStringResource(R.plurals.to_review_sign, chosen.size, chosen.size), enabled = ready && stage == null && sender != null && gate == null, loading = stage != null, onClick = {
                    val s = sender ?: return@PrimaryButton
                    message = null
                    vm.publishMany(s, chosen.map { Triple(it.pkg, ratings[it.pkg]!!, tagsOf[it.pkg]!!) }, onStage = { st ->
                        stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) }
                    }) { r -> stage = null
                        r.fold({ n -> message = true to context.resources.getQuantityString(R.plurals.to_review_done, n, n); picked = emptyList(); batch = false }, { message = false to context.errorText(it) }) }
                })
            }
        }
    }
}

private const val MAX_BATCH = 8

/** Per-app tags across process death, as "pkg" -> "wire,wire". */
private val TagMapSaver = androidx.compose.runtime.saveable.Saver<Map<String, List<ReviewMemo.Tag>>, HashMap<String, String>>(
    save = { m -> HashMap(m.mapValues { (_, v) -> v.joinToString(",") { it.wire } }) },
    restore = { m -> m.mapValues { (_, v) -> v.split(',').mapNotNull { ReviewMemo.Tag.fromWire(it) } } })
