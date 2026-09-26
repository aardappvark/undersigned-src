package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.solana.ReviewTxService
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.app.util.StoreHandoff
import com.undersigned.protocol.ReviewMemo
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.text.DateFormat
import java.util.Date

/**
 * Every review the signed-in wallet has written, one card per dApp: the signed review on the ledger beside the review
 * in the dApp Store, searchable by app or text. Built because the owner could not find their own reviews (26 Sep 2026):
 * signed ones lived only on each app's page and store ones only behind a four-minute scan in Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyReviewsScreen(vm: MainViewModel, sender: ActivityResultSender?, onBack: () -> Unit, onOpen: (String) -> Unit, onReview: (String) -> Unit, onPro: () -> Unit) {
    val context = LocalContext.current
    val wallet by vm.wallet.collectAsState()
    val entries by vm.entries.collectAsState()
    val store by vm.myStoreReviews.collectAsState()
    val scannedAt by vm.myStoreScannedAt.collectAsState()
    val scan by vm.importScan.collectAsState()
    val scanApp by vm.importScanApp.collectAsState()
    val scanResult by vm.importScanResult.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    val pro by vm.pro.collectAsState()
    var query by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var handoff by remember { mutableStateOf<Pair<String, String?>?>(null) }
    val all = remember(entries, store, catalogue, wallet) { vm.myDapps() }
    LaunchedEffect(all) { vm.resolveIcons(all.map { it.pkg }) }
    val shown = remember(all, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) all else all.filter { d ->
            d.name.lowercase().contains(q) || d.pkg.lowercase().contains(q) ||
                ((d.signed?.memo as? ReviewMemo.Review)?.text ?: "").lowercase().contains(q) || (d.store?.text ?: "").lowercase().contains(q)
        }
    }
    fun date(sec: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(sec * 1000))
    fun importOne(pkg: String, r: com.undersigned.app.data.StoreReview) {
        val s = sender ?: return
        message = null
        vm.importStoreReviews(s, listOf(pkg to r), onStage = { st -> stage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { res ->
            stage = null
            message = res.fold({ context.resources.getQuantityString(R.plurals.import_done, it, it) }, { it.message })
        }
    }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            Text(stringResource(R.string.my_reviews_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (wallet == null) {
            Text(stringResource(R.string.my_reviews_sign_in), color = RbColors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            return@Column
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(stringResource(R.string.my_reviews_search), color = RbColors.textTertiary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = RbColors.textTertiary) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight),
        )
        // Where the store side comes from, and a way to refresh it. The scan is paced (the store's CDN blocks fast
        // readers), so progress is shown here rather than hidden in Settings.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        scan != null -> stringResource(R.string.import_scanning, scan!!)
                        scannedAt != null -> stringResource(R.string.my_reviews_scanned, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(scannedAt!!)))
                        else -> stringResource(R.string.my_reviews_never_scanned)
                    }, color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                if (scan == null) TextButton(onClick = { vm.scanMyStoreReviews() }) { Text(stringResource(R.string.import_scan), color = RbColors.purpleLight, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            scanApp?.let { Text(it, color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            scanResult?.let { (failed, total) -> if (scan == null && failed > 0) Text(stringResource(R.string.import_incomplete, failed, total), color = RbColors.warning, fontSize = 11.sp) }
            (stage ?: message)?.let { Text(it, color = if (stage != null) RbColors.purpleLight else RbColors.green, fontSize = 12.sp) }
        }
        if (shown.isEmpty()) {
            Text(stringResource(if (all.isEmpty()) R.string.my_reviews_empty else R.string.my_reviews_no_match), color = RbColors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(20.dp))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(shown, key = { it.pkg }) { d ->
                GlassCard(accent = if (d.differs) RbColors.warning else RbColors.glassBorder) {
                    Row(Modifier.fillMaxWidth().clickable { onOpen(d.pkg) }, verticalAlignment = Alignment.CenterVertically) {
                        DAppIcon(d.pkg, d.name, icons[d.pkg]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(d.name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                    if (d.differs) Text(stringResource(R.string.my_reviews_differs), color = RbColors.warning, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))

                    // Ledger side
                    Side(stringResource(R.string.my_reviews_signed))
                    val r = d.signed?.memo as? ReviewMemo.Review
                    if (r != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) { RatingStars(r.rating.toDouble(), size = 12); Spacer(Modifier.width(6.dp)); Text(date(d.signed.blockTime) + " · v${r.versionCode}", color = RbColors.textTertiary, fontSize = 11.sp) }
                        if (vm.isSelfPublished(d.signed)) Text(stringResource(R.string.review_self_published), color = RbColors.textTertiary, fontSize = 11.sp)
                        r.text?.let { Text(it, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        TextButton(onClick = { onReview(d.pkg) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.detail_update_review), color = RbColors.purpleLight, fontSize = 12.sp) }
                    } else {
                        Text(stringResource(R.string.my_reviews_not_signed), color = RbColors.textTertiary, fontSize = 12.sp)
                        d.store?.let { s -> TextButton(enabled = stage == null && sender != null, onClick = { importOne(d.pkg, s) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.import_publish, 1), color = RbColors.green, fontSize = 12.sp) } }
                    }

                    // Store side — hidden until a scan has run; the header already says how to get it.
                    val s = d.store
                    if (s != null || scannedAt != null || scan != null) Side(stringResource(R.string.my_reviews_store))
                    if (s != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) { RatingStars(s.rating.toDouble(), size = 12); Spacer(Modifier.width(6.dp)); Text(s.createdAt.take(10), color = RbColors.textTertiary, fontSize = 11.sp) }
                        s.text?.let { Text(it, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        TextButton(onClick = { StoreHandoff.copyAndOpen(context, d.pkg, null) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.detail_open_store), color = RbColors.purpleLight, fontSize = 12.sp) }
                    } else if (scannedAt != null || scan != null) {
                        Text(stringResource(R.string.my_reviews_not_in_store), color = RbColors.textTertiary, fontSize = 12.sp)
                        if (r != null) {
                            if (pro?.active == true) TextButton(onClick = { handoff = d.pkg to r.text }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(stringResource(R.string.pro_copy_review), color = RbColors.purpleLight, fontSize = 12.sp) }
                            else ProLock(hint = stringResource(R.string.pro_copy_review), onPro = onPro)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    handoff?.let { (pkg, text) -> StoreHandoffDialog(pkg, text) { handoff = null } }
}

@Composable
private fun Side(label: String) {
    Text(label.uppercase(), color = RbColors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}
