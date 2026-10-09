package com.undersigned.app.ui.screens

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.ReviewMemo
import kotlinx.coroutines.launch

/** Pro: the unanswered queue and responsiveness across every dApp the wallet has verified, in one list. */
@Composable
fun PublisherDeskScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onPro: () -> Unit, onPayments: () -> Unit = {}) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsState()
    val verified by vm.verifiedPublishers.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val domainNames by vm.domainNames.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    val pro by vm.pro.collectAsState()
    val wallet by vm.wallet.collectAsState()
    val desk = remember(entries, verified, wallet) { vm.desk() }
    val board by vm.boardRows.collectAsState()
    LaunchedEffect(Unit) { vm.loadBoard() }
    // Wallets found in the code of the publisher's own apps: the person who can confirm or report them (owner, 9 Oct 2026).
    val found by produceState<Map<String, org.json.JSONObject>>(emptyMap(), desk.apps.size) {
        value = vm.foundTreasuries().getOrNull()?.optJSONArray("rows")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            ?.filter { r -> desk.apps.any { it.packageName == r.optString("package") } }?.associateBy { it.getString("package") } ?: emptyMap()
    }
    val scope = rememberCoroutineScope()
    val translations = remember { mutableStateMapOf<String, String>() }
    var translating by remember { mutableStateOf<String?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    fun name(pkg: String) = catalogue.firstOrNull { it.packageName == pkg }?.name ?: pkg

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(stringResource(R.string.desk_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Pill(stringResource(R.string.pro_badge), RbColors.purpleLight); Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Locked for real (feature review, 9 Oct 2026): the desk rendered everything for free users behind a banner.
            if (pro?.active != true) { Spacer(Modifier.height(8.dp)); ProLock(onPro = onPro); Spacer(Modifier.height(8.dp)); return@Column }
            Text(pluralStringResource(R.plurals.desk_across, desk.apps.size, desk.apps.size), color = RbColors.textTertiary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            // Three tiles only when each has room: at phone width a tile was ~69 dp inside, and "12 Stunden" at 22 sp broke
            // mid-value (UI review 6 Oct 2026). Narrower screens get one card of label/value rows.
            BoxWithConstraints {
                val stats = listOf(
                    desk.unanswered.toString() to stringResource(R.string.desk_unanswered),
                    (desk.medianReplyHours?.let { h -> if (h < 48) stringResource(R.string.desk_hours, h.toInt()) else pluralStringResource(R.plurals.desk_days, (h / 24).toInt(), (h / 24).toInt()) } ?: "—") to stringResource(R.string.desk_median),
                    (desk.answeredPercent?.let { "$it%" } ?: "—") to stringResource(R.string.desk_answered),
                )
                if (maxWidth >= 420.dp) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.forEach { (v, l) -> GlassCard(Modifier.weight(1f)) { Text(v, color = RbColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold); Text(l, color = RbColors.textTertiary, fontSize = 11.sp) } }
                } else GlassCard { stats.forEach { (v, l) -> StatRow(l, v) } }
            }
            SectionTitle(stringResource(R.string.desk_your_dapps))
            if (desk.apps.isEmpty()) Text(stringResource(R.string.desk_none), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            desk.apps.forEach { a ->
                GlassCard(Modifier.clickable { onOpen(a.packageName) }, accent = if (a.waiting > 0) RbColors.warning else RbColors.glassBorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DAppIcon(a.packageName, name(a.packageName), icons[a.packageName]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name(a.packageName), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            val r = a.responsiveness
                            Text((a.score?.let { stringResource(R.string.desk_signed_mean, String.format(java.util.Locale.US, "%.1f", it.weightedMean)) } ?: stringResource(R.string.row_no_reviews)) + " · " + (if (r.answered == 0) stringResource(R.string.responsiveness_never) else stringResource(R.string.responsiveness_line, bucketLabel(r.bucket), r.answered, r.total)), color = RbColors.textSecondary, fontSize = 11.sp)
                        }
                        Pill(if (a.waiting > 0) stringResource(R.string.desk_waiting, a.waiting) else stringResource(R.string.desk_clear), if (a.waiting > 0) RbColors.warning else RbColors.green)
                    }
                    val (now30, prev30) = remember(entries, a.packageName) { vm.deskTrend(a.packageName) }
                    Text(stringResource(R.string.desk_trend, now30, prev30), color = RbColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    val row = board.firstOrNull { it.claim.packageName == a.packageName && it.confirmed }
                    Text(row?.let { r -> stringResource(R.string.desk_treasury_confirmed, r.usd?.let { java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(it) } ?: "—") }
                        ?: stringResource(R.string.desk_treasury_none), color = if (row != null) RbColors.green else RbColors.textTertiary, fontSize = 12.sp)
                    found[a.packageName]?.let { f ->
                        Text(stringResource(R.string.desk_found, com.undersigned.app.solana.shortKey(f.getString("wallet"), 4, 4), f.optInt("payers")), color = RbColors.warning, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
                        Row {
                            TextButton(onClick = onPayments, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.desk_payments), color = RbColors.purpleLight, fontSize = 12.sp) }
                            Spacer(Modifier.width(16.dp))
                            TextButton(onClick = { runCatching { uri.openUri(com.undersigned.app.AppConfig.Site.URL + "/report") } }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.desk_report), color = RbColors.purpleLight, fontSize = 12.sp) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            SectionTitle(stringResource(R.string.desk_oldest))
            if (desk.oldest.isEmpty()) Text(stringResource(R.string.desk_none_waiting), color = RbColors.textSecondary, fontSize = 13.sp)
            desk.oldest.take(20).forEach { e ->
                val r = e.memo as ReviewMemo.Review
                val ageDays = ((System.currentTimeMillis() / 1000 - e.blockTime) / 86400L).toInt()
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) { RatingStars(r.rating.toDouble(), size = 12); Spacer(Modifier.width(6.dp)); Text((domainNames[e.signer]?.takeIf { it.isNotEmpty() } ?: com.undersigned.app.solana.shortKey(e.signer, 4, 4)) + " · " + pluralStringResource(R.plurals.desk_days, ageDays, ageDays) + " · " + name(r.packageName), color = RbColors.textTertiary, fontSize = 11.sp) }
                    vm.modHidden.collectAsState().value[e.signature]?.let { Spacer(Modifier.height(4.dp)); com.undersigned.app.ui.components.ModHiddenText(it) } ?: r.text?.let { Spacer(Modifier.height(4.dp)); Text(it, color = RbColors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp) }
                    translations[e.signature]?.let { tr ->
                        Column(Modifier.padding(top = 6.dp).fillMaxWidth().background(RbColors.surfaceVariant, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(8.dp)) {
                            Text(stringResource(R.string.translate_label), color = RbColors.textTertiary, fontSize = 10.sp)
                            Text(tr, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                    Row {
                        TextButton(onClick = { com.undersigned.app.ui.NavIntents.pendingReply.value = e.signature to ""; onOpen(r.packageName) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.reply), color = RbColors.purpleLight, fontSize = 12.sp) }
                        Spacer(Modifier.width(12.dp))
                        if (!r.text.isNullOrBlank() && translations[e.signature] == null) TextButton(enabled = translating == null, onClick = {
                            translating = e.signature
                            scope.launch { vm.suggestedTranslation(e.signature).fold({ translations[e.signature] = it }, { translations[e.signature] = context.getString(R.string.translate_failed) }); translating = null }
                        }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.translate_button), color = RbColors.purpleLight, fontSize = 12.sp) }
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { runCatching { context.packageManager.getLaunchIntentForPackage(r.packageName)?.let { context.startActivity(it) } } }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.detail_open_app), color = RbColors.textSecondary, fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            // Developer kit (desk review, 10 Oct 2026): the badge, the MCP command and the API, where publishers already are.
            SectionTitle(stringResource(R.string.desk_kit_title))
            GlassCard {
                Text(stringResource(R.string.desk_kit_note), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 16.sp)
                var copied by remember { mutableStateOf<String?>(null) }
                fun copy(what: String, v: String) { clipboard.setText(androidx.compose.ui.text.AnnotatedString(v)); copied = what }
                val site = com.undersigned.app.AppConfig.Site.URL
                desk.apps.forEach { a ->
                    val e = java.net.URLEncoder.encode(a.packageName, "UTF-8")
                    TextButton(onClick = { copy(a.packageName, "<a href=\"$site/app/$e\"><img src=\"$site/v1/badge/$e.svg\" alt=\"Undersigned signed reviews\"></a>") }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.desk_kit_badge, name(a.packageName)) + if (copied == a.packageName) " ✓" else "", color = RbColors.purpleLight, fontSize = 13.sp) }
                }
                TextButton(onClick = { copy("mcp", "claude mcp add --transport http undersigned $site/mcp") }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.desk_kit_mcp) + if (copied == "mcp") " ✓" else "", color = RbColors.purpleLight, fontSize = 13.sp) }
                TextButton(onClick = { runCatching { uri.openUri("$site/api") } }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.desk_kit_api), color = RbColors.purpleLight, fontSize = 13.sp) }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.desk_note), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(32.dp))
        }
    }
}
