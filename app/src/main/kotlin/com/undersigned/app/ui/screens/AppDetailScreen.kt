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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.AppConfig
import com.undersigned.app.R
import com.undersigned.app.solana.ReviewTxService
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.DEEP_HISTORY_PAGES
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.components.label
import androidx.compose.foundation.layout.Box
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.ReviewMemo
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(vm: MainViewModel, pkg: String, sender: ActivityResultSender?, onBack: () -> Unit, onReview: () -> Unit, onProfile: (String) -> Unit = {}, onPro: () -> Unit = {}) {
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
    LaunchedEffect(pkg) { vm.resolveIcons(listOf(pkg)); vm.loadStoreMirror(pkg) }
    val storeRatings by vm.storeRatings.collectAsState()
    val storeReviews by vm.storeReviews.collectAsState()
    val myStore by vm.myStoreReviews.collectAsState()
    val domainNames by vm.domainNames.collectAsState()
    LaunchedEffect(reviews) { vm.resolveDomains(reviews.map { it.first.signer }) }
    var importStage by remember { mutableStateOf<String?>(null) }
    var importMsg by remember { mutableStateOf<String?>(null) }
    val details by vm.storeDetails.collectAsState()
    val verified by vm.verifiedPublishers.collectAsState()
    val txMeta by vm.txMeta.collectAsState()
    val standings by vm.standings.collectAsState()
    val factsMap by vm.facts.collectAsState()
    var standingFor by remember { mutableStateOf<String?>(null) }
    var claimDialog by remember { mutableStateOf(false) }
    var claimStage by remember { mutableStateOf<String?>(null) }
    val versionScores = remember(entries, factsMap) { vm.versionScores(pkg) }
    LaunchedEffect(storeReviews[pkg]) { storeReviews[pkg]?.mapNotNull { it.walletAddress }?.let { vm.resolveFacts(it) } }
    val ledgerSigners = remember(entries) { vm.ledgerSigners() }
    val starred by vm.starred.collectAsState()
    val flagWeights by vm.flagWeights.collectAsState()
    val consensusHidden by vm.hiddenByConsensus.collectAsState()
    var revealed by remember { mutableStateOf<Set<String>>(emptySet()) }
    var flagTarget by remember { mutableStateOf<String?>(null) }
    var flagStage by remember { mutableStateOf<String?>(null) }
    var fromLink by remember { mutableStateOf(false) }
    val pendingReply by com.undersigned.app.ui.NavIntents.pendingReply.collectAsState()
    LaunchedEffect(pendingReply, entries) {
        pendingReply?.let { (target, txt) -> if (vm.packageOf(target) == pkg) { com.undersigned.app.ui.NavIntents.pendingReply.value = null; replyTo = target; replyText = txt; replyConsent = false; replyError = null; fromLink = txt.isNotEmpty() } }
    }
    val responsiveness = remember(entries, verified) { vm.responsiveness(pkg) }
    val pendingAction by com.undersigned.app.ui.NavIntents.pendingAction.collectAsState()
    val helpful by vm.helpfulWeights.collectAsState()
    val disputesMap by vm.disputes.collectAsState()
    val similar by vm.similarity.collectAsState()
    val usage by vm.usage.collectAsState()
    val storeHistory by vm.storeHistory.collectAsState()
    val storeBursts = remember(storeHistory[pkg]) { vm.storeBursts(pkg) }
    val ledgerBursts = remember(entries) { vm.ledgerBursts(pkg) }
    val snaps = remember(storeRatings[pkg]) { vm.ratingSnapshots(pkg) }
    val ratingBursts = remember(snaps) { vm.ratingBursts(pkg) }
    LaunchedEffect(storeBursts) { storeBursts?.bursts?.forEach { b -> vm.resolveFacts(vm.burstWallets(pkg, b)) } }
    val boardRows by vm.boardRows.collectAsState()
    val boardRow = remember(boardRows) { vm.boardFor(pkg) }
    LaunchedEffect(pkg, entries) { if (vm.treasuryClaims().any { it.packageName == pkg }) vm.loadBoard() }
    var disputeTarget by remember { mutableStateOf<String?>(null) }
    var disputeText by remember { mutableStateOf("") }
    var disputeStage by remember { mutableStateOf<String?>(null) }
    var voteStage by remember { mutableStateOf<String?>(null) }
    var pendingVote by remember { mutableStateOf<String?>(null) }
    var simpleStage by remember { mutableStateOf<String?>(null) }
    // Quick actions (helpful, withdraw, looks genuine) used to discard their result, so a declined or
    // timed-out wallet request did nothing visible (15 Sep 2026). The outcome is now always shown.
    var actionMessage by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    fun sign(memo: ReviewMemo) {
        val s = sender ?: return
        actionMessage = null
        vm.publish(s, memo, onStage = { st -> simpleStage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { r ->
            simpleStage = null
            actionMessage = r.fold(
                onSuccess = { sig -> true to context.getString(R.string.action_published, shortKey(sig, 6, 6)) },
                onFailure = { e -> false to (e.message ?: context.getString(R.string.action_failed)) },
            )
        }
    }
    LaunchedEffect(pendingAction, entries) {
        pendingAction?.let { a -> if (vm.packageOf(a.target) == pkg) {
            com.undersigned.app.ui.NavIntents.pendingAction.value = null
            when (a.kind) {
                "flag" -> flagTarget = a.target
                "dispute" -> { disputeTarget = a.target; disputeText = a.arg }
                "vote" -> pendingVote = a.target
                "unvote" -> sign(ReviewMemo.Unvote(a.target))
                "unflag" -> sign(ReviewMemo.Unflag(a.target))
                "clear" -> sign(ReviewMemo.Clear(a.target))
            }
        } }
    }
    val hiddenCount = remember(entries, hidden) { entries.count { it.signature in hidden && (it.memo as? ReviewMemo.Review)?.packageName == pkg } }
    val pro by vm.pro.collectAsState()
    var copyMsg by remember { mutableStateOf<String?>(null) }
    var handoffText by remember { mutableStateOf<String?>(null) }
    var handoffOpen by remember { mutableStateOf(false) }
    fun copyToStore(text: String?) { handoffText = text; handoffOpen = true }

    fun openStore() { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.Store.DEEP_LINK + pkg))) } catch (_: Exception) { } }
    // The dApp launcher: the package manager's launch intent for an installed store app. Nothing is installed or changed.
    val launchIntent = remember(pkg, isInstalled) { if (isInstalled) runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }.getOrNull() else null }
    fun openApp() { launchIntent?.let { try { context.startActivity(it) } catch (_: Exception) { } } }
    // Report: off-ledger, to a human, by mail. The dialog collects a reason; nothing is sent from the app itself.
    var reportTarget by remember { mutableStateOf<String?>(null) }
    fun report(sig: String) { reportTarget = sig }
    fun sendReport(sig: String, reason: String, note: String) {
        val body = "Package: $pkg\nRecord: $sig\nReason: $reason" + (note.takeIf { it.isNotBlank() }?.let { "\nNote: $it" } ?: "")
        try { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + context.getString(R.string.settings_contact) + "?subject=" + Uri.encode("Undersigned report: $sig") + "&body=" + Uri.encode(body)))) } catch (_: Exception) { }
    }

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))  {
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
            // Stacked, not side by side: "Open in dApp Store" wrapped and clipped at half width on the Seeker (15 Sep 2026), and German runs longer.
            SecondaryButton(stringResource(R.string.detail_open_store), onClick = { openStore() })
            if (launchIntent != null) { Spacer(Modifier.height(6.dp)); SecondaryButton(stringResource(R.string.detail_open_app), onClick = { openApp() }) }
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

            SectionTitle(stringResource(R.string.publisher_title))
            GlassCard(accent = if (verified[pkg].isNullOrEmpty()) RbColors.glassBorder else RbColors.green) {
                val d = details[pkg]
                d?.publisherName?.let { Text(stringResource(R.string.publisher_listed, it + (d.website?.let { w -> " · $w" } ?: "")), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp) }
                val vp = verified[pkg].orEmpty()
                if (vp.isNotEmpty()) {
                    vp.forEach { w -> Text(stringResource(R.string.publisher_verified, (d?.website ?: "").removePrefix("https://").removePrefix("http://")) + " · " + (domainNames[w]?.takeIf { it.isNotEmpty() } ?: shortKey(w, 4, 4)), color = RbColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onProfile(w) }) }
                    val b = when (responsiveness.bucket) { "hours" -> stringResource(R.string.bucket_hours); "days" -> stringResource(R.string.bucket_days); "weeks" -> stringResource(R.string.bucket_weeks); "months" -> stringResource(R.string.bucket_months); else -> responsiveness.bucket }
                    Text(if (responsiveness.answered == 0) stringResource(R.string.responsiveness_never) else stringResource(R.string.responsiveness_line, b, responsiveness.answered, responsiveness.total), color = RbColors.textSecondary, fontSize = 12.sp)
                }
                else Text(stringResource(R.string.publisher_none), color = RbColors.textTertiary, fontSize = 12.sp)
                if (wallet != null && d?.website != null) {
                    Spacer(Modifier.height(8.dp))
                    val mine = vm.myClaim(pkg)
                    if (mine == null) SecondaryButton(claimStage ?: stringResource(R.string.publisher_claim), enabled = claimStage == null && sender != null, onClick = { claimDialog = true })
                    else if (!vp.contains(wallet)) Text(stringResource(R.string.publisher_claimed_pending), color = RbColors.warning, fontSize = 12.sp)
                }
            }

            if (versionScores.isNotEmpty()) {
                SectionTitle(stringResource(R.string.versions_title))
                GlassCard {
                    versionScores.take(5).forEach { v ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RatingStars(v.mean, size = 12); Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.versions_row, v.versionCode, String.format(java.util.Locale.US, "%.1f", v.mean), v.count), color = RbColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (facts?.versionCode == v.versionCode) Pill(stringResource(R.string.versions_installed), RbColors.green)
                        }
                    }
                }
            }

            if (score != null && score.tagCounts.isNotEmpty()) {
                SectionTitle(stringResource(R.string.detail_tags_title))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    score.tagCounts.entries.sortedByDescending { it.value }.forEach { (t, n) -> TagChip(t.label(), n) }
                }
            }

            SectionTitle(stringResource(R.string.store_title))
            GlassCard {
                val sr = storeRatings[pkg]
                if (sr == null || sr.total == 0) Text(stringResource(R.string.store_none), color = RbColors.textSecondary, fontSize = 13.sp)
                else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format(java.util.Locale.US, "%.1f", sr.average), color = RbColors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(12.dp))
                        Column { RatingStars(sr.average, size = 16); Text(pluralStringResource(R.plurals.store_total, sr.total, sr.total), color = RbColors.textSecondary, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    val max = (sr.byStar.maxOrNull() ?: 1).coerceAtLeast(1)
                    (5 downTo 1).forEach { star ->
                        val n = sr.byStar.getOrNull(star - 1) ?: 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$star★", color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.width(26.dp))
                            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(RbColors.surfaceVariant)) { Box(Modifier.fillMaxWidth(n.toFloat() / max).height(6.dp).background(RbColors.purple)) }
                            Text("$n", color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.width(36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.store_sub), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                myStore[pkg]?.let { mine ->
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.store_mine_found, mine.rating, storeDate(mine.createdAt)), color = RbColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (vm.myReview(pkg) == null) {
                        Spacer(Modifier.height(6.dp))
                        SecondaryButton(importStage ?: stringResource(R.string.store_import_one), enabled = importStage == null && sender != null, onClick = {
                            val s = sender ?: return@SecondaryButton
                            vm.importStoreReviews(s, listOf(pkg to mine), onStage = { st -> importStage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { r -> importStage = null; importMsg = r.fold({ context.resources.getQuantityString(R.plurals.import_done, it, it) }, { it.message }) }
                        })
                    }
                    importMsg?.let { Text(it, color = RbColors.textSecondary, fontSize = 12.sp) }
                }
                storeReviews[pkg]?.take(3)?.forEach { r ->
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RbColors.surfaceVariant).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RatingStars(r.rating.toDouble(), size = 12); Spacer(Modifier.width(6.dp))
                            Text((r.domain ?: r.walletAddress?.let { shortKey(it, 4, 4) } ?: "anonymous") + " · " + storeDate(r.createdAt), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            if (r.publisherReply != null) Pill(stringResource(R.string.store_publisher_replied), RbColors.info)
                        }
                        r.walletAddress?.let { wa ->
                            Row(Modifier.padding(top = 4.dp))  {
                                factsMap[wa]?.let { f -> Pill(stringResource(if (f.hasGenesisToken) R.string.store_reviewer_genesis else R.string.store_reviewer_nogenesis), if (f.hasGenesisToken) RbColors.green else RbColors.warning); Spacer(Modifier.width(6.dp)) }
                                if (wa in ledgerSigners) Pill(stringResource(R.string.store_reviewer_signed_here), RbColors.purpleLight)
                                if (boardRow?.payers?.contains(wa) == true) { Spacer(Modifier.width(6.dp)); Pill(stringResource(R.string.pay_paid_pill), RbColors.green) }
                            }
                        }
                        r.text?.let { Text(it, color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 4) }
                        // The store now shows the publisher's reply prominently (v1.59.0); the mirror showed only a pill.
                        r.publisherReply?.takeIf { it.isNotBlank() }?.let { reply ->
                            var open by remember(r.id) { mutableStateOf(false) }
                            Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(RbColors.card).padding(8.dp)) {
                                Text(stringResource(R.string.store_publisher_reply), color = RbColors.info, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(reply, color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = if (open) Int.MAX_VALUE else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (reply.length > 160) Text(stringResource(if (open) R.string.show_less else R.string.show_more), color = RbColors.purpleLight, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp).clickable { open = !open })
                            }
                        }
                    }
                }
            }

            SectionTitle(stringResource(R.string.burst_title))
            GlassCard(accent = if (storeBursts?.bursts?.isNotEmpty() == true || ledgerBursts.bursts.isNotEmpty()) RbColors.warning else RbColors.glassBorder) {
                val sb = storeBursts
                if (sb == null) Text(stringResource(R.string.burst_reading), color = RbColors.textTertiary, fontSize = 12.sp)
                else {
                    // Strongest first, three at most: a busy launch can hold many windows over a flat median.
                    sb.bursts.sortedByDescending { it.intensity }.take(3).forEachIndexed { i, b -> BurstLine(b, stringResource(R.string.burst_source_store).takeIf { i == 0 }, vm.burstWallets(pkg, b), factsMap) }
                    if (sb.bursts.size > 3) Text(stringResource(R.string.burst_more, sb.bursts.size - 3), color = RbColors.textTertiary, fontSize = 11.sp)
                    if (sb.bursts.isEmpty()) {
                        val newest = storeHistory[pkg]?.items?.firstOrNull()?.createdAt?.let { runCatching { java.time.Instant.parse(it).epochSecond }.getOrNull() }
                        val from = sb.from
                        if (from == null || newest == null || newest - from < com.undersigned.protocol.ReviewBursts.MIN_HISTORY_DAYS * com.undersigned.protocol.ReviewBursts.DAY)
                            Text(stringResource(R.string.burst_short), color = RbColors.textSecondary, fontSize = 12.sp)
                        else Text(stringResource(R.string.burst_none, sb.checked, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(from * 1000))), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                ratingBursts.bursts.sortedByDescending { it.intensity }.take(3).forEachIndexed { i, b -> BurstLine(b, stringResource(R.string.burst_source_ratings).takeIf { i == 0 }, emptyList(), factsMap, ratings = true) }
                if (snaps.size >= 2) Text(stringResource(R.string.ratings_tracked, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(snaps.first().time * 1000)), snaps.size), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
                ledgerBursts.bursts.sortedByDescending { it.intensity }.take(3).forEachIndexed { i, b -> BurstLine(b, stringResource(R.string.burst_source_ledger).takeIf { i == 0 }, emptyList(), factsMap) }
                val deep by vm.historyLoading.collectAsState()
                val read = storeHistory[pkg]
                if (read != null && read.complete && !read.exhausted && read.items.size < DEEP_HISTORY_PAGES * 10)
                    TextButton(enabled = pkg !in deep, onClick = { vm.loadOlderStoreHistory(pkg) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text(if (pkg in deep) stringResource(R.string.burst_reading) else stringResource(R.string.burst_read_older), color = RbColors.purpleLight, fontSize = 12.sp) }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.burst_causes), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            }

            boardRow?.let { row ->
                SectionTitle(stringResource(R.string.pay_detail_title))
                GlassCard {
                    TallyView(row, vm.quotes.collectAsState().value)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.pay_detail_sub, shortKey(row.claim.treasury, 4, 4), row.claim.code), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
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
                    details[pkg]?.let { d ->
                        if (d.versionCode > 0) StatRow(stringResource(R.string.fact_store_version), d.versionCode.toString())
                        d.certFingerprint?.let { cf ->
                            val match = cf.equals(facts.signerSha256, ignoreCase = true)
                            Text(stringResource(if (match) R.string.fact_store_cert_match else R.string.fact_store_cert_mismatch), color = if (match) RbColors.green else RbColors.negative, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
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
            simpleStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }
            actionMessage?.let { (ok, msg) -> Text(msg, color = if (ok) RbColors.green else RbColors.negative, fontSize = 12.sp, lineHeight = 16.sp) }
            if (reviews.isEmpty()) Text(stringResource(R.string.row_no_reviews), color = RbColors.textSecondary, fontSize = 13.sp)
            else Text(stringResource(R.string.swipe_hint), color = RbColors.textTertiary, fontSize = 11.sp)
            var show by remember { mutableStateOf(0) }   // 0 everyone · 1 mine · 2 answered by the publisher
            var sortBy by remember { mutableStateOf(0) } // 0 most helpful (default order) · 1 newest · 2 signer · 3 version
            if (reviews.size > 1) {
                ChipRow(stringResource(R.string.show), listOf(0 to stringResource(R.string.show_everyone), 1 to stringResource(R.string.show_mine), 2 to stringResource(R.string.show_answered)), show, { show = it })
                ChipRow(stringResource(R.string.sort), listOf(0 to stringResource(R.string.sort_helpful), 1 to stringResource(R.string.sort_newest), 2 to stringResource(R.string.sort_signer), 3 to stringResource(R.string.sort_version)), sortBy, { sortBy = it })
            }
            val shown = remember(reviews, show, sortBy, entries, domainNames, verified) {
                reviews.filter { (e, _) -> when (show) { 1 -> e.signer == wallet; 2 -> vm.repliesFor(e.signature).any { vm.isVerifiedPublisher(pkg, it.signer) }; else -> true } }
                    .let { l -> when (sortBy) {
                        1 -> l.sortedByDescending { it.first.blockTime }
                        2 -> l.sortedBy { domainNames[it.first.signer]?.takeIf { n -> n.isNotEmpty() } ?: it.first.signer }
                        3 -> l.sortedByDescending { (it.first.memo as ReviewMemo.Review).versionCode }
                        else -> l
                    } }
            }
            // key() by signature, not position. Without it every remember in this loop (the replies list, the
            // swipe-to-reply state) stayed bound to a SLOT: the list is newest-first until helpful weights load and
            // re-sort it, and the slot kept the old review's state. Measured 21 Sep 2026: a reply to 5XTu3S73 was
            // drawn under the publisher's review, and a swipe there would have opened a reply to the wrong record.
            shown.forEach { (e, w) -> key(e.signature) {
                val r = e.memo as ReviewMemo.Review
                if (e.signature in consensusHidden && e.signature !in revealed) {
                    GlassCard(accent = RbColors.surfaceVariant) {
                        Text(stringResource(R.string.flagged_hidden, String.format(java.util.Locale.US, "%.1f", flagWeights[e.signature] ?: 0.0)), color = RbColors.textTertiary, fontSize = 12.sp)
                        disputesMap[e.signature]?.let { d -> Text(stringResource(R.string.disputed_note, (d.memo as ReviewMemo.Dispute).text), color = RbColors.warning, fontSize = 12.sp) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { revealed = revealed + e.signature }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.flagged_show), color = RbColors.purpleLight, fontSize = 12.sp) }
                            if (e.signer == wallet && disputesMap[e.signature] == null) { TextButton(onClick = { disputeTarget = e.signature; disputeText = "" }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.dispute), color = RbColors.warning, fontSize = 12.sp) } }
                            if (wallet != null && e.signer != wallet) {
                                if (vm.myFlag(e.signature)) TextButton(enabled = simpleStage == null, onClick = { sign(ReviewMemo.Unflag(e.signature)) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.unflag), color = RbColors.warning, fontSize = 12.sp) }
                                else if (!vm.myClear(e.signature)) TextButton(enabled = simpleStage == null, onClick = { sign(ReviewMemo.Clear(e.signature)) }, contentPadding = PaddingValues(0.dp)) { Text(simpleStage ?: stringResource(R.string.clear), color = RbColors.info, fontSize = 12.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp)); return@forEach
                }
                val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.StartToEnd && wallet != null) { replyTo = e.signature; replyText = ""; replyConsent = false; replyError = null }; false })
                SwipeToDismissBox(state = swipe, enableDismissFromEndToStart = false, backgroundContent = { Box(Modifier.fillMaxSize().padding(start = 16.dp), contentAlignment = Alignment.CenterStart) { Text(stringResource(R.string.reply), color = RbColors.purpleLight, fontWeight = FontWeight.Bold) } }) {
                GlassCard(accent = if (w > 0) RbColors.glassBorder else RbColors.surfaceVariant) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingStars(r.rating.toDouble())
                        Spacer(Modifier.width(8.dp))
                        Text(if (e.signer == wallet) stringResource(R.string.review_by_you) else (domainNames[e.signer]?.takeIf { it.isNotEmpty() } ?: shortKey(e.signer, 4, 4)), color = RbColors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f).clickable { onProfile(e.signer) })
                        IconButton(onClick = { vm.toggleStar(e.signature) }, modifier = Modifier.size(28.dp)) { Icon(if (e.signature in starred) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = stringResource(if (e.signature in starred) R.string.unstar else R.string.star), tint = if (e.signature in starred) RbColors.warning else RbColors.textTertiary, modifier = Modifier.size(18.dp)) }
                        if (w > 0) Pill(stringResource(R.string.review_weight_verified) + if (w > 1.0) String.format(java.util.Locale.US, " %.1f×", w) else "", RbColors.green) else Pill(stringResource(R.string.review_weight_unverified), RbColors.textTertiary)
                    }
                    // FlowRow, not Row: with a Seeker pill, a publisher pill, a standing, a flag weight and a
                    // helpful weight all present, a Row runs out of width and squeezes the last pill towards
                    // zero, which wrapped "Helpful · 3.0" onto three clipped lines (measured on device, 20 Sep).
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (txMeta[e.signature.substringBefore(':')]?.seekerWallet == true) Pill(stringResource(R.string.seeker_signed), RbColors.info)
                        if (vm.isVerifiedPublisher(pkg, e.signer)) Pill(stringResource(R.string.publisher_badge), RbColors.green)
                        standings[e.signer]?.let { st -> Text("${st.countedReviews} signed · ${st.credibility}", color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onProfile(e.signer) }.padding(horizontal = 4.dp, vertical = 4.dp)) }
                        flagWeights[e.signature]?.let { fw -> Pill(stringResource(R.string.flagged_count, String.format(java.util.Locale.US, "%.1f", fw)), RbColors.warning) }
                        helpful[e.signature]?.let { hw -> Pill(stringResource(R.string.helpful_count, String.format(java.util.Locale.US, "%.1f", hw)), RbColors.green) }
                        similar[e.signature]?.let { sf -> Pill(stringResource(if (sf.copiedAcrossApps) R.string.similar_copied else R.string.similar_badge), RbColors.warning) }
                        usage[e.signature]?.let { u -> Pill(stringResource(R.string.use_pill, useLabel(u)), RbColors.textSecondary) }
                        if (boardRow?.payers?.contains(e.signer) == true) Pill(stringResource(R.string.pay_paid_pill), RbColors.green)
                    }
                    disputesMap[e.signature]?.let { d -> Text(stringResource(R.string.disputed_note, (d.memo as ReviewMemo.Dispute).text), color = RbColors.warning, fontSize = 11.sp) }
                    if (vm.isSelfPublished(e)) Text(stringResource(R.string.review_self_published), color = RbColors.textTertiary, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { r.tags.forEach { TagChip(it.label()) } }
                    r.text?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RbColors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp) }
                    // Locked in place (design: Screens D): the copy-to-store tool where the user meets it, on their own review.
                    if (e.signer == wallet && myStore[pkg] == null) {
                        Spacer(Modifier.height(8.dp))
                        if (pro?.active == true) { SecondaryButton(stringResource(R.string.pro_copy_review), onClick = { copyToStore(r.text) }); copyMsg?.let { Text(it, color = RbColors.green, fontSize = 11.sp) } }
                        else ProLock(hint = stringResource(R.string.pro_copy_review), onPro = onPro)
                    }
                    Spacer(Modifier.height(6.dp))
                    val replies = remember(entries, hidden, e.signature) { vm.repliesFor(e.signature) }
                    replies.forEach { rp ->
                        Spacer(Modifier.height(6.dp))
                        if (rp.signature in consensusHidden && rp.signature !in revealed) {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RbColors.surfaceVariant).padding(10.dp)) {
                                Text(stringResource(R.string.hidden_reply) + " · " + stringResource(R.string.flag_net, String.format(java.util.Locale.US, "%.1f", flagWeights[rp.signature] ?: 0.0)), color = RbColors.textTertiary, fontSize = 11.sp)
                                disputesMap[rp.signature]?.let { d -> Text(stringResource(R.string.disputed_note, (d.memo as ReviewMemo.Dispute).text), color = RbColors.warning, fontSize = 11.sp) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TextButton(onClick = { revealed = revealed + rp.signature }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.flagged_show), color = RbColors.purpleLight, fontSize = 11.sp) }
                                    if (rp.signer == wallet && disputesMap[rp.signature] == null) { TextButton(onClick = { disputeTarget = rp.signature; disputeText = "" }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.dispute), color = RbColors.warning, fontSize = 11.sp) } }
                                    if (wallet != null && rp.signer != wallet && !vm.myClear(rp.signature)) { TextButton(enabled = simpleStage == null, onClick = { sign(ReviewMemo.Clear(rp.signature)) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.clear), color = RbColors.info, fontSize = 11.sp) } }
                                }
                            }
                            return@forEach
                        }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RbColors.surfaceVariant).padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text((if (rp.signer == wallet) stringResource(R.string.review_by_you) else (domainNames[rp.signer]?.takeIf { it.isNotEmpty() } ?: shortKey(rp.signer, 4, 4))) + " · " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(rp.blockTime * 1000)), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                if (vm.isVerifiedPublisher(pkg, rp.signer)) Pill(stringResource(R.string.publisher_badge), RbColors.green)
                            }
                            Text((rp.memo as ReviewMemo.Reply).text, color = RbColors.textPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                            if (wallet != null && rp.signer != wallet) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp))  {
                                flagWeights[rp.signature]?.let { fw -> Pill(stringResource(R.string.flag_net, String.format(java.util.Locale.US, "%.1f", fw)), RbColors.warning) }
                                val rpFlagged = vm.myFlag(rp.signature)
                                CardAction(if (rpFlagged) stringResource(R.string.flag_on) + "\u00A0✓" else stringResource(R.string.flag), RbColors.warning, enabled = !rpFlagged || simpleStage == null,
                                    a11y = stringResource(if (rpFlagged) R.string.unflag else R.string.flag)) { if (rpFlagged) sign(ReviewMemo.Unflag(rp.signature)) else flagTarget = rp.signature }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    // One compact line, as designed (handoff/screens/dapp-page-reviews.png): date, then the actions.
                    // Toggles keep a SHORT visible label ("Helpful ✓", "Flag ✓"): "Withdraw my helpful vote" was five
                    // times the width of "Helpful" and pushed this bar onto three lines (measured on device, 24 Sep
                    // 2026). The full wording stays as the screen-reader label. It stays a FlowRow, never a Row: a Row
                    // that overflows squeezes its last child to zero width and inflates its own height (20 Sep).
                    val tick = "\u00A0✓"
                    FlowRow {
                        Box(Modifier.heightIn(min = 48.dp).padding(end = 4.dp), contentAlignment = Alignment.CenterStart) {
                            Text(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(e.blockTime * 1000)) + " · v${r.versionCode}", color = RbColors.textTertiary, fontSize = 11.sp)
                        }
                        if (wallet != null) CardAction(stringResource(R.string.reply), RbColors.purpleLight) { replyTo = e.signature; replyText = ""; replyConsent = false; replyError = null }
                        if (e.signer != wallet) {
                            if (wallet != null) {
                                val voted = vm.myUpvote(e.signature); val flagged = vm.myFlag(e.signature)
                                CardAction(simpleStage ?: (stringResource(R.string.helpful) + if (voted) tick else ""), RbColors.green, enabled = simpleStage == null,
                                    a11y = stringResource(if (voted) R.string.unvote else R.string.helpful)) { if (voted) sign(ReviewMemo.Unvote(e.signature)) else sign(ReviewMemo.Upvote(e.signature)) }
                                CardAction(if (flagged) stringResource(R.string.flag_on) + tick else stringResource(R.string.flag), RbColors.warning, enabled = !flagged || simpleStage == null,
                                    a11y = stringResource(if (flagged) R.string.unflag else R.string.flag)) { if (flagged) sign(ReviewMemo.Unflag(e.signature)) else flagTarget = e.signature }
                                // "Looks genuine" is the opposite of a flag, so it is never offered to someone currently flagging it.
                                if (!flagged && (flagWeights[e.signature] ?: 0.0) > 0 && !vm.myClear(e.signature))
                                    CardAction(stringResource(R.string.clear), RbColors.info, enabled = simpleStage == null) { sign(ReviewMemo.Clear(e.signature)) }
                            }
                            CardAction(stringResource(R.string.review_hide), RbColors.textTertiary) { vm.hide(e.signature) }
                            CardAction(stringResource(R.string.review_report), RbColors.textTertiary) { report(e.signature) }
                        }
                    }
                }
                }
                Spacer(Modifier.height(8.dp))
            } }
            if (hiddenCount > 0) Text(stringResource(R.string.review_hidden_note, hiddenCount), color = RbColors.textTertiary, fontSize = 11.sp)
            Spacer(Modifier.height(32.dp))
        }
    }

    reportTarget?.let { sig ->
        var reason by remember { mutableStateOf(R.string.report_illegal) }
        var note by remember { mutableStateOf("") }
        val target = remember(sig) { vm.entryOf(sig) }
        AlertDialog(onDismissRequest = { reportTarget = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.report_title), color = RbColors.textPrimary) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.report_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                target?.let { t -> Spacer(Modifier.height(8.dp)); Text((domainNames[t.signer]?.takeIf { it.isNotEmpty() } ?: shortKey(t.signer, 4, 4)) + " · " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(t.blockTime * 1000)), color = RbColors.textTertiary, fontSize = 11.sp)
                    ((t.memo as? ReviewMemo.Review)?.text ?: (t.memo as? ReviewMemo.Reply)?.text)?.let { Text("\u201C$it\u201D", color = RbColors.textSecondary, fontSize = 12.sp, maxLines = 3) } }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.report_why), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(R.string.report_illegal, R.string.report_personal, R.string.report_abuse, R.string.report_impersonation).forEach { r -> TagChip(stringResource(r), selected = reason == r) { reason = r } }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, minLines = 2, maxLines = 4, placeholder = { Text(stringResource(R.string.report_note_hint), color = RbColors.textTertiary, fontSize = 12.sp) }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.report_footer), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            } },
            confirmButton = { TextButton(onClick = { sendReport(sig, context.getString(reason), note); reportTarget = null }) { Text(stringResource(R.string.report_write), color = RbColors.purpleLight) } },
            dismissButton = { TextButton(onClick = { vm.hide(sig); reportTarget = null }) { Text(stringResource(R.string.report_hide_instead), color = RbColors.textSecondary) } })
    }

    standingFor?.let { w ->
        val st = standings[w]
        AlertDialog(onDismissRequest = { standingFor = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.standing_title), color = RbColors.textPrimary) },
            text = { Column {
                Text(domainNames[w]?.takeIf { it.isNotEmpty() } ?: shortKey(w, 6, 6), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (st != null) {
                    Text(stringResource(R.string.standing_line, st.countedReviews, st.withdrawn, st.repliesReceived, st.repliesGiven), color = RbColors.textSecondary, fontSize = 12.sp)
                    st.agreement?.let { Text(stringResource(R.string.standing_agreement, String.format(java.util.Locale.US, "%.0f%%", it * 100)), color = RbColors.textSecondary, fontSize = 12.sp) }
                    if (st.firstSeen > 0) Text(stringResource(R.string.standing_since, DateFormat.getDateInstance(DateFormat.SHORT).format(Date(st.firstSeen * 1000))), color = RbColors.textTertiary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.standing_note2), color = RbColors.textTertiary, fontSize = 11.sp)
            } },
            confirmButton = { TextButton(onClick = { standingFor = null }) { Text(stringResource(android.R.string.ok), color = RbColors.purpleLight) } })
    }

    if (claimDialog) {
        val d = details[pkg]; val site = d?.website ?: ""
        val wk = com.undersigned.app.ledger.PublisherVerifier.wellKnownUrl(site) ?: ""
        AlertDialog(onDismissRequest = { if (claimStage == null) claimDialog = false }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.publisher_claim_title), color = RbColors.textPrimary) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(stringResource(R.string.publisher_claim_body, site, wk, wallet ?: ""), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp); claimStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) } } },
            confirmButton = { TextButton(enabled = claimStage == null && sender != null, onClick = {
                val s = sender ?: return@TextButton
                vm.publish(s, ReviewMemo.Claim(pkg), onStage = { st -> claimStage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { claimStage = null; claimDialog = false }
            }) { Text(stringResource(R.string.publisher_claim_sign), color = RbColors.purpleLight) } },
            dismissButton = { TextButton(enabled = claimStage == null, onClick = { claimDialog = false }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    replyTo?.let { target ->
        val bytes = replyText.toByteArray(Charsets.UTF_8).size
        val ok = replyText.isNotBlank() && replyText.length <= ReviewMemo.MAX_TEXT_CHARS && bytes <= ReviewMemo.MAX_TEXT_BYTES && replyText.none { it == '|' || it == '\n' || it == '\r' }
        AlertDialog(
            onDismissRequest = { if (replyStage == null) replyTo = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.reply_title), color = RbColors.textPrimary) },
            text = {
                Column {
                    if (fromLink) Text(stringResource(R.string.reply_from_link), color = RbColors.info, fontSize = 12.sp)
                    OutlinedTextField(value = replyText, onValueChange = { if (replyStage == null) replyText = it.take(ReviewMemo.MAX_TEXT_CHARS + 20) }, minLines = 2, maxLines = 4, isError = replyText.isNotEmpty() && !ok,
                        placeholder = { Text(stringResource(R.string.reply_hint), color = RbColors.textTertiary) }, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight))
                    Text(stringResource(R.string.review_text_label, ReviewMemo.MAX_TEXT_CHARS - replyText.length, ReviewMemo.MAX_TEXT_BYTES - bytes), color = RbColors.textTertiary, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.review_consent_body), color = RbColors.textSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                    Row(Modifier.fillMaxWidth().clickable(enabled = replyStage == null) { replyConsent = !replyConsent })  {
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
            dismissButton = { TextButton(enabled = replyStage == null, onClick = { replyTo = null; fromLink = false }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } },
        )
    }

    pendingVote?.let { target ->
        AlertDialog(onDismissRequest = { if (voteStage == null) pendingVote = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.helpful_sign), color = RbColors.textPrimary) },
            text = { Column { Text(stringResource(R.string.helpful_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp); voteStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) } } },
            confirmButton = { TextButton(enabled = voteStage == null && sender != null && !vm.myUpvote(target), onClick = {
                val s = sender ?: return@TextButton
                vm.publish(s, ReviewMemo.Upvote(target), onStage = { st -> voteStage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { voteStage = null; pendingVote = null }
            }) { Text(stringResource(R.string.helpful), color = RbColors.green) } },
            dismissButton = { TextButton(enabled = voteStage == null, onClick = { pendingVote = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    disputeTarget?.let { target ->
        val ok = disputeText.isNotBlank() && disputeText.length <= ReviewMemo.MAX_TEXT_CHARS && disputeText.toByteArray().size <= ReviewMemo.MAX_TEXT_BYTES && disputeText.none { it == '|' || it == '\n' || it == '\r' }
        AlertDialog(onDismissRequest = { if (disputeStage == null) disputeTarget = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.dispute_title), color = RbColors.textPrimary) },
            text = { Column {
                Text(stringResource(R.string.dispute_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = disputeText, onValueChange = { if (disputeStage == null) disputeText = it.take(ReviewMemo.MAX_TEXT_CHARS + 20) }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight))
                Text(stringResource(R.string.review_consent_body), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                disputeStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }
            } },
            confirmButton = { TextButton(enabled = ok && disputeStage == null && sender != null, onClick = {
                val s = sender ?: return@TextButton
                vm.publish(s, ReviewMemo.Dispute(target, disputeText.trim()), onStage = { st -> disputeStage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { disputeStage = null; disputeTarget = null }
            }) { Text(stringResource(R.string.dispute_sign), color = RbColors.warning) } },
            dismissButton = { TextButton(enabled = disputeStage == null, onClick = { disputeTarget = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    flagTarget?.let { target ->
        var reason by remember { mutableStateOf(ReviewMemo.FlagReason.SPAM) }
        AlertDialog(onDismissRequest = { if (flagStage == null) flagTarget = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.flag_title), color = RbColors.textPrimary) },
            text = { Column {
                Text(stringResource(R.string.flag_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                if (wallet != null && vm.isVerifiedPublisher(pkg, wallet!!)) Text(stringResource(R.string.publisher_flags_ignored), color = RbColors.warning, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ReviewMemo.FlagReason.SPAM to R.string.flag_spam, ReviewMemo.FlagReason.ABUSE to R.string.flag_abuse, ReviewMemo.FlagReason.OFF_TOPIC to R.string.flag_offtopic, ReviewMemo.FlagReason.MISLEADING to R.string.flag_misleading)
                        .forEach { (fr, label) -> TagChip(stringResource(label), selected = reason == fr) { if (flagStage == null) reason = fr } }
                }
                flagStage?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }
            } },
            confirmButton = { TextButton(enabled = flagStage == null && sender != null, onClick = {
                val s = sender ?: return@TextButton
                vm.publish(s, ReviewMemo.Flag(target, reason), onStage = { st -> flagStage = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) } }) { flagStage = null; flagTarget = null }
            }) { Text(stringResource(R.string.flag_sign), color = RbColors.warning) } },
            dismissButton = { TextButton(enabled = flagStage == null, onClick = { flagTarget = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
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
    if (handoffOpen) StoreHandoffDialog(pkg, handoffText, onOpened = { copyMsg = context.getString(R.string.pro_copy_done) }) { handoffOpen = false }
}

/** A review-card action: compact text, full 48 dp touch target, optional screen-reader label for short toggles. */
@Composable
private fun CardAction(label: String, color: androidx.compose.ui.graphics.Color, enabled: Boolean = true, a11y: String? = null, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = if (a11y != null) Modifier.semantics { contentDescription = a11y } else Modifier) {
        Text(label, color = color, fontSize = 12.sp)
    }
}

/** One burst: what, when, against what pace, and — for store bursts — how many of its reviewers hold an attested device. */
@Composable
private fun BurstLine(b: com.undersigned.protocol.ReviewBursts.Burst, source: String?, wallets: List<String>, facts: Map<String, com.undersigned.protocol.SignerFacts>, ratings: Boolean = false) {
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
    val pace = String.format(java.util.Locale.getDefault(), "%.1f", b.expected)
    source?.let { Text(it.uppercase(), color = RbColors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(4.dp))
    val high = b.kind == com.undersigned.protocol.ReviewBursts.Kind.HIGH
    Text(stringResource(if (ratings) (if (high) R.string.burst_high_ratings else R.string.burst_low_ratings) else (if (high) R.string.burst_high else R.string.burst_low), b.count, df.format(Date(b.start * 1000)), df.format(Date(b.end * 1000)), pace),
        color = RbColors.warning, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
    val extra = ArrayList<String>()
    if (b.sameText > 0) extra.add(stringResource(R.string.burst_same_text, b.sameText))
    if (wallets.isNotEmpty() && wallets.all { it in facts }) extra.add(stringResource(R.string.burst_attested, wallets.count { facts[it]?.hasGenesisToken == true }, wallets.size))
    if (extra.isNotEmpty()) Text(extra.joinToString(" · "), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp)
}
