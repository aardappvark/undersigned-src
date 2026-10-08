package com.undersigned.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Share as ShareIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.runtime.saveable.rememberSaveable
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
    val proScores by vm.proScores.collectAsState()
    val promotedScores by vm.promotedScores.collectAsState()
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
    var importOk by remember { mutableStateOf(true) }
    val details by vm.storeDetails.collectAsState()
    val verified by vm.verifiedPublishers.collectAsState()
    val txMeta by vm.txMeta.collectAsState()
    val standings by vm.standings.collectAsState()
    val factsMap by vm.facts.collectAsState()
    var standingFor by remember { mutableStateOf<String?>(null) }
    var claimDialog by remember { mutableStateOf(false) }
    var proofSheet by remember { mutableStateOf(false) }
    var claimStage by remember { mutableStateOf<String?>(null) }
    val versionScores = remember(entries, factsMap) { vm.versionScores(pkg) }
    LaunchedEffect(storeReviews[pkg]) { storeReviews[pkg]?.mapNotNull { it.walletAddress }?.let { vm.resolveFacts(it) } }
    val ledgerSigners = remember(entries) { vm.ledgerSigners() }
    val starred by vm.starred.collectAsState()
    val flagWeights by vm.flagWeights.collectAsState()
    val consensusHidden by vm.hiddenByConsensus.collectAsState()
    val modHidden by vm.modHidden.collectAsState()
    var modTarget by remember { mutableStateOf<String?>(null) }
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
    val storeNotes by vm.storeNotes.collectAsState()
    var noteFor by remember { mutableStateOf<String?>(null) }
    var noteText by remember { mutableStateOf("") }
    val useTimes by vm.useTimes.collectAsState()
    val storeHistory by vm.storeHistory.collectAsState()
    val storeBursts = remember(storeHistory[pkg]) { vm.storeBursts(pkg) }
    val ledgerBursts = remember(entries) { vm.ledgerBursts(pkg) }
    // Snapshots come from files (up to 60 days of the store-wide archive): read them off the main thread.
    val snaps by produceState(initialValue = emptyList<com.undersigned.protocol.RatingSnapshots.Snapshot>(), pkg, storeRatings[pkg]) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.ratingSnapshots(pkg) } }
    val ratingBursts = remember(snaps) { com.undersigned.protocol.ReviewBursts.find(com.undersigned.protocol.RatingSnapshots.toPoints(snaps), com.undersigned.protocol.ReviewBursts.MIN_SNAPSHOT_DAYS) }
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
    fun stageText(st: ReviewTxService.Stage) = when (st) { ReviewTxService.Stage.Simulating -> context.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> context.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> context.getString(R.string.review_confirming) }
    fun sign(memo: ReviewMemo, onDone: (Boolean) -> Unit = {}) {
        val s = sender ?: return
        actionMessage = null
        vm.publish(s, memo, onStage = { st -> simpleStage = stageText(st) }) { r ->
            simpleStage = null
            actionMessage = r.fold(
                onSuccess = { sig -> true to context.getString(R.string.action_published, shortKey(sig, 6, 6)) },
                onFailure = { e -> false to context.errorText(e) },
            )
            onDone(r.isSuccess)
        }
    }
    // The dialogs below used to close on any result, so a declined or failed signature looked like it went through
    // (UI review 6 Oct 2026). Now they close only on success and say what went wrong otherwise.
    var dialogError by remember { mutableStateOf<String?>(null) }
    // A link may ask to withdraw a vote, a flag or a "looks genuine": never signed straight from the link.
    var pendingSimple by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(pendingAction, entries) {
        pendingAction?.let { a -> if (vm.packageOf(a.target) == pkg) {
            com.undersigned.app.ui.NavIntents.pendingAction.value = null
            when (a.kind) {
                "flag" -> { dialogError = null; flagTarget = a.target }
                "dispute" -> { dialogError = null; disputeTarget = a.target; disputeText = a.arg }
                "vote" -> { dialogError = null; pendingVote = a.target }
                "unvote", "unflag", "clear" -> pendingSimple = a.kind to a.target
            }
        } }
    }
    val hiddenCount = remember(entries, hidden) { entries.count { it.signature in hidden && (it.memo as? ReviewMemo.Review)?.packageName == pkg } }
    val pro by vm.pro.collectAsState()
    var boostFor by remember { mutableStateOf<String?>(null) }
    var boostStage by remember { mutableStateOf<String?>(null) }
    var boostMsg by remember { mutableStateOf<Pair<String, String>?>(null) }
    val boostContext = androidx.compose.ui.platform.LocalContext.current
    fun payBoost(rail: com.undersigned.app.solana.ProPaymentService.Rail, reviewId: String) {
        val s = sender ?: return
        vm.payBoost(s, rail, reviewId, onStage = { st -> boostStage = when (st) { ReviewTxService.Stage.Simulating -> boostContext.getString(R.string.review_simulating); ReviewTxService.Stage.Signing -> boostContext.getString(R.string.review_signing); ReviewTxService.Stage.Confirming -> boostContext.getString(R.string.review_confirming) } }) { r ->
            boostStage = null
            boostMsg = r.fold({ boostContext.getString(R.string.boost_done, shortKey(it, 6, 6)) to reviewId }, { boostContext.errorText(it) to reviewId })
        }
    }
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
            BackButton(onBack)
            DAppIcon(pkg, name, icons[pkg]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(app?.updated?.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.detail_pkg_updated, pkg, storeDate(it)) } ?: pkg, color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            val ctxShare = LocalContext.current
            IconButton(onClick = { val sr = storeRatings[pkg]; com.undersigned.app.ui.components.Share.app(ctxShare, pkg, name, score, sr?.average, sr?.total) }) {
                Icon(Icons.Default.ShareIcon, contentDescription = stringResource(R.string.share), tint = RbColors.textSecondary)
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
            if (mine != null) { Spacer(Modifier.height(6.dp)); SecondaryButton(stringResource(R.string.detail_revoke_review), onClick = { dialogError = null; revokeDialog = true }) }

            SectionTitle(stringResource(R.string.detail_score_title))
            GlassCard {
                // A score with nothing counted is not a 0.0: until a review counts there is no signed score to show.
                if (score != null && score.countedReviews > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(String.format(java.util.Locale.US, "%.1f", score.weightedMean), color = RbColors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(12.dp))
                        Column { RatingStars(score.weightedMean, size = 18); Text(stringResource(R.string.row_signed_count, score.countedReviews), color = RbColors.textSecondary, fontSize = 12.sp) }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.detail_rank_score, String.format(java.util.Locale.US, "%.2f", score.weightedRating)), color = RbColors.textTertiary, fontSize = 11.sp)
                    // Pro members' and promoted reviews as their OWN scores, each with its count, never blended into the signed score
                    // (compliance check, 9 Oct 2026). A row shows only when the segment has a counted review.
                    @Composable fun segment(title: String, note: String, sc: com.undersigned.protocol.AppScore?, color: androidx.compose.ui.graphics.Color) {
                        if (sc == null) return
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RbColors.surfaceVariant).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(note, color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(String.format(java.util.Locale.US, "%.1f", sc.weightedMean), color = color, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                Text(stringResource(R.string.row_signed_count, sc.countedReviews), color = RbColors.textTertiary, fontSize = 11.sp)
                            }
                        }
                    }
                    segment(stringResource(R.string.member_score_title), stringResource(R.string.member_score_note), proScores[pkg], RbColors.purpleLight)
                    segment(stringResource(R.string.promoted_score_title), stringResource(R.string.promoted_score_note), promotedScores[pkg], RbColors.warning)
                } else Text(stringResource(R.string.row_no_reviews), color = RbColors.textSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.detail_score_sub), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            }

            // Reviews over time (owner, 8 Oct 2026): store reviews by Seeker owners and other wallets, signed reviews, and both
            // scores as they stood, with Reckoner's moving bar. Built off the main thread; redrawn only when its inputs change.
            var timelineRange by remember { mutableStateOf(TimelineRange.ALL) }
            val timeline by androidx.compose.runtime.produceState<Pair<List<com.undersigned.protocol.Timeline.Bucket>, Long?>>(emptyList<com.undersigned.protocol.Timeline.Bucket>() to null, pkg, timelineRange, entries, storeHistory[pkg], factsMap.size, txMeta.size) {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { vm.timeline(pkg, timelineRange.days) }
            }
            SectionTitle(stringResource(R.string.timeline_title))
            GlassCard { ReviewTimeline(timeline.first, timelineRange, { timelineRange = it }, timeline.second) }

            // Signed reviews are what this app is for, so they come straight after the score; the store's own rating,
            // bursts and device facts follow (UI review 6 Oct 2026: they sat about ten swipes down, under store reviews).
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
            var limit by rememberSaveable(pkg) { mutableStateOf(REVIEW_PAGE) }
            shown.take(limit).forEach { (e, w) -> key(e.signature) {
                val r = e.memo as ReviewMemo.Review
                if (e.signature in consensusHidden && e.signature !in revealed) {
                    GlassCard(accent = RbColors.surfaceVariant) {
                        Text(stringResource(R.string.flagged_hidden, String.format(java.util.Locale.US, "%.1f", flagWeights[e.signature] ?: 0.0)), color = RbColors.textTertiary, fontSize = 12.sp)
                        disputesMap[e.signature]?.let { d -> Text(stringResource(R.string.disputed_note, (d.memo as ReviewMemo.Dispute).text), color = RbColors.textSecondary, fontSize = 12.sp) }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TextButton(onClick = { revealed = revealed + e.signature }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.flagged_show), color = RbColors.purpleLight, fontSize = 12.sp) }
                            if (e.signer == wallet && disputesMap[e.signature] == null) { TextButton(onClick = { dialogError = null; disputeTarget = e.signature; disputeText = "" }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.dispute), color = RbColors.textSecondary, fontSize = 12.sp) } }
                            if (wallet != null && e.signer != wallet) {
                                if (vm.myFlag(e.signature)) TextButton(enabled = simpleStage == null, onClick = { sign(ReviewMemo.Unflag(e.signature)) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.unflag), color = RbColors.textSecondary, fontSize = 12.sp) }
                                else if (!vm.myClear(e.signature)) TextButton(enabled = simpleStage == null, onClick = { sign(ReviewMemo.Clear(e.signature)) }, contentPadding = PaddingValues(0.dp)) { Text(simpleStage ?: stringResource(R.string.clear), color = RbColors.info, fontSize = 12.sp) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp)); return@forEach
                }
                val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v -> if (v == SwipeToDismissBoxValue.StartToEnd && wallet != null) { replyTo = e.signature; replyText = ""; replyConsent = false; replyError = null }; false })
                SwipeToDismissBox(state = swipe, enableDismissFromEndToStart = false, backgroundContent = { Box(Modifier.fillMaxSize().padding(start = 16.dp), contentAlignment = Alignment.CenterStart) { Text(stringResource(R.string.reply), color = RbColors.purpleLight, fontWeight = FontWeight.Bold) } }) {
                GlassCard(accent = if (w.device > 0) RbColors.glassBorder else RbColors.surfaceVariant) {
                    // Design review 28 Sep 2026 (handoff 2, F4): author row, then three tiers — trust pills, the phone's report as
                    // one line, context as quiet text — instead of up to ten equal pills.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RatingStars(r.rating.toDouble())
                        Spacer(Modifier.width(8.dp))
                        Text(if (e.signer == wallet) stringResource(R.string.review_by_you) else (domainNames[e.signer]?.takeIf { it.isNotEmpty() } ?: shortKey(e.signer, 4, 4)), color = RbColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { onProfile(e.signer) })
                        standings[e.signer]?.let { st -> Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable { onProfile(e.signer) }.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.row_signed_count, st.countedReviews), color = RbColors.textTertiary, fontSize = 11.sp) } }
                    }
                    val paidReviewer = boardRow?.payers?.contains(e.signer) == true
                    val isPublisher = vm.isVerifiedPublisher(pkg, e.signer)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (w.device > 0) TrustPill(stringResource(R.string.review_weight_verified) + if (w.device > 1.0) String.format(java.util.Locale.US, " %.1f×", w.device) else "", RbColors.green)
                        else OutlinePill(stringResource(R.string.review_weight_unverified))
                        // Membership and paid promotion are always shown on the review itself.
                        if (w.device > 0 && w.pro) TrustPill(stringResource(R.string.weight_pro_pill), RbColors.purpleLight)
                        if (w.device > 0 && w.promoted) TrustPill(stringResource(R.string.weight_boosted_pill), RbColors.warning)
                        if (paidReviewer) TrustPill(stringResource(R.string.pay_paid_pill), RbColors.purpleLight)
                        if (isPublisher) TrustPill(stringResource(R.string.publisher_badge), RbColors.green)
                    }
                    if (w.device > 0) Text(stringResource(R.string.weight_counts, String.format(java.util.Locale.US, "%.2f", w.weight), String.format(java.util.Locale.US, "%.2f", w.quality)), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    val seekerSigned = txMeta[e.signature.substringBefore(':')]?.seekerWallet == true
                    val useAge = usage[e.signature]; val useSpent = useTimes[e.signature]
                    if (seekerSigned || useAge != null || useSpent != null) {
                        Spacer(Modifier.height(8.dp))
                        DeviceLine {
                            if (seekerSigned) DeviceUnit(Icons.Default.Key, stringResource(R.string.seeker_signed), stringResource(R.string.seeker_signed), tint = RbColors.info)
                            useAge?.let { DeviceUnit(Icons.Default.PhoneAndroid, useLabel(it), stringResource(R.string.use_pill, useLabel(it))) }
                            useSpent?.let { DeviceUnit(Icons.Default.Schedule, timeLabel(it), stringResource(R.string.time_pill, timeLabel(it))) }
                        }
                    }
                    disputesMap[e.signature]?.let { d -> Text(stringResource(R.string.disputed_note, (d.memo as ReviewMemo.Dispute).text), color = RbColors.textSecondary, fontSize = 11.sp) }
                    if (vm.isSelfPublished(e)) Text(stringResource(R.string.review_self_published), color = RbColors.textTertiary, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { r.tags.forEach { TagChip(it.label()) } }
                    // A note from a wallet that is not counted stays folded until asked for: anyone can sign text for one network
                    // fee, and an uncounted wallet's words should not sit on an app's page by default.
                    modHidden[e.signature]?.let { reason -> Spacer(Modifier.height(6.dp)); ModHiddenText(reason, 13) }
                    r.text?.takeIf { e.signature !in modHidden }?.let { note ->
                        // Keyed on whether the wallet counts too: the Genesis check often lands after the first draw, and a fold decided then stuck.
                        var shown by remember(e.signature, w.device > 0) { mutableStateOf(w.device > 0) }
                        Spacer(Modifier.height(6.dp))
                        if (shown) Text(note, color = RbColors.textPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                        else Text(stringResource(R.string.review_show_note), color = RbColors.purpleLight, fontSize = 12.sp, modifier = Modifier.heightIn(min = 48.dp).clickable { shown = true }.padding(vertical = 14.dp))
                    }
                    // Tier 3, context: season, then similar wording (amber: that one is unusual). Text, not pills.
                    val seasonText = vm.seasonAt(e.blockTime)?.let { stringResource(R.string.season_pill, it.label) }
                    val similarText = similar[e.signature]?.let { stringResource(if (it.copiedAcrossApps) R.string.similar_copied else R.string.similar_badge) }
                    if (seasonText != null || similarText != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(androidx.compose.ui.text.buildAnnotatedString {
                            seasonText?.let { append(it) }
                            if (seasonText != null && similarText != null) append(" · ")
                            similarText?.let { pushStyle(androidx.compose.ui.text.SpanStyle(color = RbColors.warning)); append(it); pop() }
                        }, color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 16.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    // Locked in place (design: Screens D): the copy-to-store tool where the user meets it, on their own review.
                    if (e.signer == wallet && myStore[pkg] == null) {
                        Spacer(Modifier.height(8.dp))
                        if (pro?.active == true) { SecondaryButton(stringResource(R.string.pro_copy_review), onClick = { copyToStore(r.text) }); copyMsg?.let { Text(it, color = RbColors.green, fontSize = 11.sp) } }
                        else ProLock(hint = stringResource(R.string.pro_copy_review), onPro = onPro)
                    }
                    // A boost on my own counted review: Pro members only, once per review, always labelled (see protocol Weighting).
                    if (e.signer == wallet && w.device > 0 && !w.promoted) {
                        Spacer(Modifier.height(8.dp))
                        if (pro?.active == true) SecondaryButton(boostStage ?: stringResource(R.string.boost_button), enabled = boostStage == null && sender != null, onClick = { boostMsg = null; boostFor = e.signature })
                        else ProLock(hint = stringResource(R.string.boost_button), onPro = onPro)
                    }
                    if (e.signer == wallet) boostMsg?.takeIf { boostFor == null && it.second == e.signature }?.let { Text(it.first, color = RbColors.green, fontSize = 11.sp) }
                    if (boostFor == e.signature) androidx.compose.material3.AlertDialog(
                        onDismissRequest = { boostFor = null },
                        containerColor = RbColors.surface,
                        title = { Text(stringResource(R.string.boost_title), color = RbColors.textPrimary, fontWeight = FontWeight.Bold) },
                        text = { Text(stringResource(R.string.boost_body), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { boostFor = null; payBoost(com.undersigned.app.solana.ProPaymentService.Rail.SKR, e.signature) }) { Text(stringResource(R.string.boost_pay, String.format(java.util.Locale.US, "%,.0f SKR", com.undersigned.app.AppConfig.Payment.BOOST_SKR)), color = RbColors.purpleLight) } },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { boostFor = null; payBoost(com.undersigned.app.solana.ProPaymentService.Rail.SOL, e.signature) }) { Text(stringResource(R.string.boost_pay, "${com.undersigned.app.AppConfig.Payment.BOOST_SOL} SOL"), color = RbColors.textSecondary) } },
                    )
                    Spacer(Modifier.height(6.dp))
                    val replies = remember(entries, hidden, e.signature) { vm.repliesFor(e.signature) }
                    replies.forEach { rp ->
                        Spacer(Modifier.height(6.dp))
                        if (rp.signature in consensusHidden && rp.signature !in revealed) {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RbColors.surfaceVariant).padding(10.dp)) {
                                Text(stringResource(R.string.hidden_reply) + " · " + stringResource(R.string.flag_net, String.format(java.util.Locale.US, "%.1f", flagWeights[rp.signature] ?: 0.0)), color = RbColors.textTertiary, fontSize = 11.sp)
                                disputesMap[rp.signature]?.let { d -> Text(stringResource(R.string.disputed_note, (d.memo as ReviewMemo.Dispute).text), color = RbColors.textSecondary, fontSize = 11.sp) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    TextButton(onClick = { revealed = revealed + rp.signature }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.flagged_show), color = RbColors.purpleLight, fontSize = 11.sp) }
                                    if (rp.signer == wallet && disputesMap[rp.signature] == null) { TextButton(onClick = { dialogError = null; disputeTarget = rp.signature; disputeText = "" }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.dispute), color = RbColors.textSecondary, fontSize = 11.sp) } }
                                    if (wallet != null && rp.signer != wallet && !vm.myClear(rp.signature)) { TextButton(enabled = simpleStage == null, onClick = { sign(ReviewMemo.Clear(rp.signature)) }, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.clear), color = RbColors.info, fontSize = 11.sp) } }
                                }
                            }
                            return@forEach
                        }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RbColors.surfaceVariant).padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text((if (rp.signer == wallet) stringResource(R.string.review_by_you) else (domainNames[rp.signer]?.takeIf { it.isNotEmpty() } ?: shortKey(rp.signer, 4, 4))) + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(rp.blockTime * 1000)), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                if (vm.isVerifiedPublisher(pkg, rp.signer)) Pill(stringResource(R.string.publisher_badge), RbColors.green)
                            }
                            modHidden[rp.signature]?.let { ModHiddenText(it) } ?: Text((rp.memo as ReviewMemo.Reply).text, color = RbColors.textPrimary, fontSize = 12.sp, lineHeight = 17.sp)
                            if (vm.isModerator(wallet) && rp.signer != wallet) CardAction(stringResource(if (vm.myModeration(rp.signature)) R.string.mod_unhide else R.string.mod_hide), RbColors.textSecondary, enabled = simpleStage == null) {
                                if (vm.myModeration(rp.signature)) sign(ReviewMemo.Unmoderate(rp.signature)) else { dialogError = null; modTarget = rp.signature } }
                            if (wallet != null && rp.signer != wallet) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp))  {
                                flagWeights[rp.signature]?.let { fw -> OutlinePill(stringResource(R.string.flag_net, String.format(java.util.Locale.US, "%.1f", fw))) }
                                val rpFlagged = vm.myFlag(rp.signature)
                                CardAction(if (rpFlagged) stringResource(R.string.flag_on) + "\u00A0✓" else stringResource(R.string.flag), RbColors.textSecondary, enabled = !rpFlagged || simpleStage == null,
                                    a11y = stringResource(if (rpFlagged) R.string.unflag else R.string.flag)) { if (rpFlagged) sign(ReviewMemo.Unflag(rp.signature)) else { dialogError = null; flagTarget = rp.signature } }
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
                    // Reply · Helpful + weight · Flag + weight, the date on the right; the rest in an overflow menu so the row
                    // stays short on a Seeker (design F4). A FlowRow still, never a Row (20 Sep: a Row squeezes its last child).
                    val voted = wallet != null && vm.myUpvote(e.signature); val flagged = wallet != null && vm.myFlag(e.signature)
                    FlowRow {
                        if (wallet != null) CardAction(stringResource(R.string.reply), RbColors.purpleLight) { replyTo = e.signature; replyText = ""; replyConsent = false; replyError = null }
                        if (e.signer != wallet && wallet != null) {
                            val helpfulA11y = stringResource(if (voted) R.string.unvote else R.string.helpful)
                            TextButton(enabled = simpleStage == null, onClick = { if (voted) sign(ReviewMemo.Unvote(e.signature)) else sign(ReviewMemo.Upvote(e.signature)) }, contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.semantics { contentDescription = helpfulA11y }) {
                                Text(simpleStage ?: (stringResource(R.string.helpful) + if (voted) tick else ""), color = RbColors.green, fontSize = 12.sp)
                                helpful[e.signature]?.let { hw -> Text(" " + String.format(java.util.Locale.US, "%.1f", hw), color = RbColors.textTertiary, fontSize = 12.sp) }
                            }
                            val flagA11y = stringResource(if (flagged) R.string.unflag else R.string.flag)
                            TextButton(enabled = !flagged || simpleStage == null, onClick = { if (flagged) sign(ReviewMemo.Unflag(e.signature)) else { dialogError = null; flagTarget = e.signature } }, contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.semantics { contentDescription = flagA11y }) {
                                Text(if (flagged) stringResource(R.string.flag_on) + tick else stringResource(R.string.flag), color = RbColors.textSecondary, fontSize = 12.sp)
                                flagWeights[e.signature]?.let { fw -> Spacer(Modifier.width(6.dp)); OutlinePill(String.format(java.util.Locale.US, "%.1f", fw)) }
                            }
                        } else {
                            helpful[e.signature]?.let { hw -> Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) { Text(stringResource(R.string.helpful_count, String.format(java.util.Locale.US, "%.1f", hw)), color = RbColors.textTertiary, fontSize = 12.sp) } }
                            flagWeights[e.signature]?.let { fw -> Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) { OutlinePill(stringResource(R.string.flagged_count, String.format(java.util.Locale.US, "%.1f", fw))) } }
                        }
                        var menu by remember(e.signature) { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.more_actions), tint = RbColors.textTertiary) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.background(RbColors.surface)) {
                                DropdownMenuItem(text = { Text(stringResource(if (e.signature in starred) R.string.unstar else R.string.star)) }, onClick = { menu = false; vm.toggleStar(e.signature) })
                                DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = { menu = false; com.undersigned.app.ui.components.Share.review(context, name, e, hideText = e.signature in modHidden) })
                                if (e.signer != wallet) {
                                    // "Looks genuine" is the opposite of a flag, so it is never offered to someone currently flagging it.
                                    if (wallet != null && !flagged && (flagWeights[e.signature] ?: 0.0) > 0 && !vm.myClear(e.signature))
                                        DropdownMenuItem(text = { Text(stringResource(R.string.clear)) }, onClick = { menu = false; sign(ReviewMemo.Clear(e.signature)) })
                                    DropdownMenuItem(text = { Text(stringResource(R.string.review_hide)) }, onClick = { menu = false; vm.hide(e.signature) })
                                    // Moderators only: hide this review's text for everyone (rating and tags still count), or undo it.
                                    if (vm.isModerator(wallet) && r.text != null) {
                                        if (vm.myModeration(e.signature)) DropdownMenuItem(text = { Text(stringResource(R.string.mod_unhide)) }, onClick = { menu = false; sign(ReviewMemo.Unmoderate(e.signature)) })
                                        else DropdownMenuItem(text = { Text(stringResource(R.string.mod_hide)) }, onClick = { menu = false; dialogError = null; modTarget = e.signature })
                                    }
                                    DropdownMenuItem(text = { Text(stringResource(R.string.review_report)) }, onClick = { menu = false; report(e.signature) })
                                }
                            }
                        }
                        Box(Modifier.heightIn(min = 48.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
                            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.blockTime * 1000)) + " · v${r.versionCode}", color = RbColors.textTertiary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
                }
                Spacer(Modifier.height(8.dp))
            } }
            if (shown.size > limit) SecondaryButton(pluralStringResource(R.plurals.reviews_more, shown.size - limit, shown.size - limit), onClick = { limit += REVIEW_PAGE })
            if (hiddenCount > 0) Text(stringResource(R.string.review_hidden_note, hiddenCount), color = RbColors.textTertiary, fontSize = 11.sp)
            SectionTitle(stringResource(R.string.publisher_title))
            GlassCard(accent = if (verified[pkg].isNullOrEmpty()) RbColors.glassBorder else RbColors.green) {
                val d = details[pkg]
                d?.publisherName?.let { Text(stringResource(R.string.publisher_listed, it + (d.website?.let { w -> " · " + w.removePrefix("https://").removePrefix("http://").trimEnd('/') } ?: "")), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp) }
                val vp = verified[pkg].orEmpty()
                if (vp.isNotEmpty()) {
                    val how by vm.verifiedHow.collectAsState()
                    vp.forEach { w ->
                        val who = domainNames[w]?.takeIf { it.isNotEmpty() } ?: shortKey(w, 4, 4)
                        val site = (d?.website ?: "").removePrefix("https://").removePrefix("http://").trimEnd('/')
                        // Say how it was proven, and since when this phone has seen the proof (hardening, 28 Sep 2026).
                        val line = when (how[pkg to w]) {
                            "apk" -> stringResource(R.string.publisher_verified_apk)
                            "store" -> stringResource(R.string.publisher_verified_store)
                            else -> vm.proofSince(pkg, w)?.let { stringResource(R.string.publisher_verified_since, site, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))) } ?: stringResource(R.string.publisher_verified, site)
                        }
                        Text("$line · $who", color = RbColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.heightIn(min = 48.dp).clickable { onProfile(w) }.padding(vertical = 6.dp))
                    }
                    // The proven publisher, signed in here, can take the badge for their website or README (feature list, 8 Oct 2026).
                    if (wallet != null && wallet in vp) {
                        val copied = stringResource(R.string.badge_copied)
                        TextButton(onClick = {
                            (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("badge", com.undersigned.app.AppConfig.Site.badgeEmbed(pkg)))
                            android.widget.Toast.makeText(context, copied, android.widget.Toast.LENGTH_SHORT).show()
                        }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.badge_copy), color = RbColors.purpleLight, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                    val b = bucketLabel(responsiveness.bucket)
                    Text(if (responsiveness.answered == 0) stringResource(R.string.responsiveness_never) else stringResource(R.string.responsiveness_line, b, responsiveness.answered, responsiveness.total), color = RbColors.textSecondary, fontSize = 12.sp)
                }
                else Text(stringResource(R.string.publisher_none), color = RbColors.textTertiary, fontSize = 12.sp)
                // A claim nobody has proven yet can be checked against the build the store itself serves (the strongest
                // proof, and it needs no install): offered once per version, with the download size on the button.
                val unproven = remember(entries, vp) { vm.claimers(pkg) - vp }
                val checking by vm.buildCheck.collectAsState()
                if (unproven.isNotEmpty() && d?.apkUri != null) {
                    val read = remember(d.versionCode, checking[pkg]) { com.undersigned.app.data.UserPreferences.storeBuild(pkg, d.versionCode) }
                    Spacer(Modifier.height(8.dp))
                    when {
                        checking[pkg] == "reading" -> Text(stringResource(R.string.publisher_checking_build), color = RbColors.textSecondary, fontSize = 12.sp)
                        read == null -> {
                            SecondaryButton(stringResource(R.string.publisher_check_build, String.format(java.util.Locale.US, "%.0f MB", d.apkSize / 1e6)), onClick = { vm.checkStoreBuild(pkg) })
                            if (checking[pkg] == "failed") Text(stringResource(R.string.publisher_build_failed), color = RbColors.negative, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        read.declared.isEmpty() -> Text(stringResource(R.string.publisher_build_nothing), color = RbColors.textTertiary, fontSize = 12.sp)
                        else -> Text(stringResource(R.string.publisher_build_other), color = RbColors.textTertiary, fontSize = 12.sp)
                    }
                }
                // How a claim becomes proven: the signing key names the wallet; the store's publishing wallet alone is not proof.
                TextButton(onClick = { proofSheet = true }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.proof_how_link), color = RbColors.purpleLight, fontSize = 12.sp) }
                // A claim needs no website: the strongest proof is the app's own signed build.
                if (wallet != null) {
                    Spacer(Modifier.height(8.dp))
                    val mine = vm.myClaim(pkg)
                    if (mine == null) SecondaryButton(claimStage ?: stringResource(R.string.publisher_claim), enabled = claimStage == null && sender != null, onClick = { dialogError = null; claimDialog = true })
                    else if (!vp.contains(wallet)) Text(stringResource(R.string.publisher_claimed_pending), color = RbColors.textSecondary, fontSize = 12.sp)
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
                            Text("$star★", color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.widthIn(min = 26.dp))
                            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(RbColors.surfaceVariant)) { Box(Modifier.fillMaxWidth(n.toFloat() / max).height(6.dp).background(RbColors.purple)) }
                            Text("$n", color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.widthIn(min = 36.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.store_sub), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
                vm.seasonShare(pkg)?.takeIf { it.first > 0 }?.let { (n, of) ->
                    // A 6 dp bar with the share hatched (reads without colour), then the line (design F2).
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(RbColors.surfaceVariant)) {
                        val wv = size.width * n / of.toFloat()
                        drawRect(RbColors.textDim, size = androidx.compose.ui.geometry.Size(wv, size.height))
                        var x = -size.height
                        while (x < wv) { drawLine(RbColors.textSecondary, androidx.compose.ui.geometry.Offset(x, size.height), androidx.compose.ui.geometry.Offset(x + size.height, 0f), strokeWidth = 1.5f); x += 4.dp.toPx() }
                    }
                    Text(stringResource(R.string.season_share, n, of), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp)) }
                myStore[pkg]?.let { mine ->
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.store_mine_found, mine.rating, storeDate(mine.createdAt)), color = RbColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (vm.myReview(pkg) == null) {
                        Spacer(Modifier.height(6.dp))
                        SecondaryButton(importStage ?: stringResource(R.string.store_import_one), enabled = importStage == null && sender != null, onClick = {
                            val s = sender ?: return@SecondaryButton
                            vm.requestImport(s, listOf(pkg to mine), onStage = { st -> importStage = stageText(st) }) { r -> importStage = null; importOk = r.isSuccess; importMsg = r.fold({ context.resources.getQuantityString(R.plurals.import_done, it, it) }, { context.errorText(it) }) }
                        })
                    }
                    importMsg?.let { Text(it, color = if (importOk) RbColors.green else RbColors.negative, fontSize = 12.sp) }
                }
                // Store rows sit flat in the card (design F2): trust pills only, season as quiet text, then the store's own
                // reply and, after it, the publisher's SIGNED note — each marked for where it comes from.
                storeReviews[pkg]?.take(3)?.forEach { r ->
                    HorizontalDivider(color = RbColors.surfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RatingStars(r.rating.toDouble(), size = 12); Spacer(Modifier.width(8.dp))
                            Text(r.domain ?: r.walletAddress?.let { shortKey(it, 4, 4) } ?: stringResource(R.string.anonymous), color = RbColors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(storeDate(r.createdAt), color = RbColors.textTertiary, fontSize = 11.sp)
                        }
                        r.walletAddress?.let { wa ->
                            val f = factsMap[wa]; val paidHere = boardRow?.payers?.contains(wa) == true
                            if (f != null || wa in ledgerSigners || paidHere) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Outline, never the green pill: a store review is not a signed one (UI review 6 Oct 2026).
                                f?.let { OutlinePill(stringResource(if (it.hasGenesisToken) R.string.store_reviewer_genesis else R.string.store_reviewer_nogenesis)) }
                                if (paidHere) TrustPill(stringResource(R.string.pay_paid_pill), RbColors.purpleLight)
                                if (wa in ledgerSigners) OutlinePill(stringResource(R.string.store_reviewer_signed_here))
                            }
                        }
                        vm.seasonAtIso(r.createdAt)?.let { se -> Text(stringResource(R.string.season_pill, se.label), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
                        r.text?.let { t ->
                            var full by remember(r.id) { mutableStateOf(false) }
                            Text(t, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = if (full) Int.MAX_VALUE else 4, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                            if (t.length > 220) ShowMore(full) { full = !full }
                        }
                        // The store's own reply: flat, blue, "from the dApp Store".
                        r.publisherReply?.takeIf { it.isNotBlank() }?.let { reply ->
                            var open by remember(r.id) { mutableStateOf(false) }
                            Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RbColors.surfaceVariant).padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = RbColors.info, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.store_publisher_reply), color = RbColors.info, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(" · " + stringResource(R.string.store_reply_source), color = RbColors.textTertiary, fontSize = 10.sp)
                                }
                                Text(reply, color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, maxLines = if (open) Int.MAX_VALUE else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                if (reply.length > 160) ShowMore(open) { open = !open }
                            }
                        }
                        // The verified publisher's SIGNED note (protocol StoreNotes): green outline, the mark, "signed on-chain".
                        storeNotes[pkg to r.id]?.let { ne -> val n = ne.memo as ReviewMemo.StoreNote
                            Column(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RbColors.surface).border(1.dp, RbColors.green.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UndersignedMark(RbColors.green, Modifier.size(14.dp)); Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.note_title), color = RbColors.green, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(stringResource(R.string.note_signed_label), color = RbColors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text(n.text, color = RbColors.textPrimary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                                Text((domainNames[ne.signer]?.takeIf { it.isNotEmpty() } ?: shortKey(ne.signer, 4, 4)) + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ne.blockTime * 1000)), color = RbColors.textTertiary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        if (wallet != null && vm.isVerifiedPublisher(pkg, wallet!!) && com.undersigned.protocol.ReviewMemo.STORE_REVIEW_ID.matches(r.id))
                            TextButton(onClick = { actionMessage = null; noteFor = r.id; noteText = (storeNotes[pkg to r.id]?.memo as? ReviewMemo.StoreNote)?.text ?: "" }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(if (storeNotes[pkg to r.id] == null) R.string.note_add else R.string.note_update), color = RbColors.purpleLight, fontSize = 13.sp) }
                    }
                }
            }

            SectionTitle(stringResource(R.string.burst_title))
            // Design review 28 Sep 2026 (handoff 2, F1): per source, the strongest burst as a headline (count, ×usual,
            // range, pace, a 30-day chart with the window hatched), the next two as compact rows, the rest behind "More".
            GlassCard(accent = if (storeBursts?.bursts?.isNotEmpty() == true || ledgerBursts.bursts.isNotEmpty() || ratingBursts.bursts.isNotEmpty()) RbColors.warning else RbColors.glassBorder) {
                val sb = storeBursts
                if (sb == null) Text(stringResource(R.string.burst_reading), color = RbColors.textTertiary, fontSize = 12.sp)
                else if (sb.bursts.isNotEmpty()) BurstSection(stringResource(R.string.burst_source_store), sb.bursts, vm.storePoints(pkg).orEmpty(), ratings = false, attested = { vm.burstWallets(pkg, it).let { w -> if (w.isNotEmpty() && w.all { x -> x in factsMap }) w.count { x -> factsMap[x]?.hasGenesisToken == true } to w.size else null } }, season = { vm.seasonAt(it.start)?.label })
                else {
                    val newest = storeHistory[pkg]?.items?.firstOrNull()?.createdAt?.let { runCatching { java.time.Instant.parse(it).epochSecond }.getOrNull() }
                    val from = sb.from
                    Text(stringResource(R.string.burst_source_store).uppercase(), color = RbColors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    if (from == null || newest == null || newest - from < com.undersigned.protocol.ReviewBursts.MIN_HISTORY_DAYS * com.undersigned.protocol.ReviewBursts.DAY)
                        Text(stringResource(R.string.burst_short), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                    else Text(stringResource(R.string.burst_none, sb.checked, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(from * 1000))), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (snaps.size >= 2 || ratingBursts.bursts.isNotEmpty()) {
                    HorizontalDivider(color = RbColors.surfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    if (ratingBursts.bursts.isNotEmpty()) BurstSection(stringResource(R.string.burst_source_ratings), ratingBursts.bursts, com.undersigned.protocol.RatingSnapshots.toPoints(snaps), ratings = true, attested = { null }, season = { vm.seasonAt(it.start)?.label })
                    else {
                        Text(stringResource(R.string.burst_source_ratings).uppercase(), color = RbColors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(stringResource(R.string.burst_ratings_none) + " " + stringResource(R.string.ratings_tracked, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(snaps.first().time * 1000)), snaps.size), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (ledgerBursts.bursts.isNotEmpty()) {
                    HorizontalDivider(color = RbColors.surfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    BurstSection(stringResource(R.string.burst_source_ledger), ledgerBursts.bursts, vm.ledgerPoints(pkg), ratings = false, attested = { null }, season = { vm.seasonAt(it.start)?.label })
                }
                val deep by vm.historyLoading.collectAsState()
                val read = storeHistory[pkg]
                if (read != null && read.complete && !read.exhausted && read.items.size < DEEP_HISTORY_PAGES * 10)
                    TextButton(enabled = pkg !in deep, onClick = { vm.loadOlderStoreHistory(pkg) }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (pkg in deep) stringResource(R.string.burst_reading) else stringResource(R.string.burst_read_older), color = RbColors.purpleLight, fontSize = 13.sp) }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.burst_causes), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 16.sp)
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

            Spacer(Modifier.height(32.dp))
        }
    }

    // Design review 28 Sep 2026 (handoff 2, F7): a full dialog that quotes the store review being answered, and says
    // permanence in purple with the mark instead of amber.
    noteFor?.let { rid ->
        var consent by remember(rid) { mutableStateOf(false) }
        val ok = noteText.trim().isNotEmpty() && noteText.trim().length <= ReviewMemo.MAX_TEXT_CHARS && noteText.trim().toByteArray().size <= ReviewMemo.MAX_TEXT_BYTES && noteText.none { it == '|' || it == '\n' }
        val quoted = storeReviews[pkg]?.firstOrNull { it.id == rid }
        androidx.compose.ui.window.Dialog(onDismissRequest = { if (simpleStage == null) noteFor = null }) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(RbColors.surface).padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.note_dialog_title), color = RbColors.textPrimary, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                quoted?.let { q ->
                    Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RbColors.surfaceVariant).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RatingStars(q.rating.toDouble(), size = 12); Spacer(Modifier.width(8.dp))
                            Text(q.domain ?: q.walletAddress?.let { shortKey(it, 4, 4) } ?: stringResource(R.string.anonymous), color = RbColors.textSecondary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            Text(storeDate(q.createdAt), color = RbColors.textTertiary, fontSize = 11.sp)
                        }
                        q.text?.let { Text(it, color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
                OutlinedTextField(value = noteText, onValueChange = { noteText = it.take(ReviewMemo.MAX_TEXT_CHARS + 20) }, minLines = 3, maxLines = 5, isError = !ok && noteText.isNotEmpty(),
                    placeholder = { Text(stringResource(R.string.note_hint), color = RbColors.textTertiary) }, modifier = Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(top = 14.dp), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = RbColors.textPrimary, unfocusedTextColor = RbColors.textPrimary, focusedBorderColor = RbColors.purple, unfocusedBorderColor = RbColors.glassBorder, cursorColor = RbColors.purpleLight))
                Text("${ReviewMemo.MAX_TEXT_CHARS - noteText.trim().length}", color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
                Spacer(Modifier.height(10.dp))
                PermanenceNote(stringResource(R.string.permanent_title), stringResource(R.string.note_dialog_body)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { consent = !consent }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it }, colors = CheckboxDefaults.colors(checkedColor = RbColors.purple))
                        Text(stringResource(R.string.review_consent_check), color = RbColors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                actionMessage?.takeIf { !it.first }?.let { Text(it.second, color = RbColors.negative, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = simpleStage == null, onClick = { noteFor = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(android.R.string.cancel), color = RbColors.textSecondary) }
                    Spacer(Modifier.width(8.dp))
                    PrimaryButton(simpleStage ?: stringResource(R.string.note_sign), enabled = ok && consent && simpleStage == null && sender != null, onClick = { sign(ReviewMemo.StoreNote(pkg, rid, noteText.trim())) { ok -> if (ok) noteFor = null } }, modifier = Modifier.weight(1f))
                }
            }
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
                target?.let { t -> Spacer(Modifier.height(8.dp)); Text((domainNames[t.signer]?.takeIf { it.isNotEmpty() } ?: shortKey(t.signer, 4, 4)) + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.blockTime * 1000)), color = RbColors.textTertiary, fontSize = 11.sp)
                    ((t.memo as? ReviewMemo.Review)?.text ?: (t.memo as? ReviewMemo.Reply)?.text)?.takeIf { t.signature !in modHidden }?.let { Text("\u201C$it\u201D", color = RbColors.textSecondary, fontSize = 12.sp, maxLines = 3) } }
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
                TextButton(onClick = { vm.hide(sig); reportTarget = null }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.report_hide_instead), color = RbColors.purpleLight, fontSize = 13.sp) }
            } },
            confirmButton = { TextButton(onClick = { sendReport(sig, context.getString(reason), note); reportTarget = null }) { Text(stringResource(R.string.report_write), color = RbColors.purpleLight) } },
            dismissButton = { TextButton(onClick = { reportTarget = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
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
                    if (st.firstSeen > 0) Text(stringResource(R.string.standing_since, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(st.firstSeen * 1000))), color = RbColors.textTertiary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.standing_note2), color = RbColors.textTertiary, fontSize = 11.sp)
            } },
            confirmButton = { TextButton(onClick = { standingFor = null }) { Text(stringResource(android.R.string.ok), color = RbColors.purpleLight) } })
    }

    if (claimDialog) {
        val d = details[pkg]; val site = d?.website ?: ""
        val wk = com.undersigned.app.ledger.PublisherVerifier.wellKnownUrl(site)
        AlertDialog(onDismissRequest = { if (claimStage == null) claimDialog = false }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.publisher_claim_title), color = RbColors.textPrimary) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                // Route 1, strongest: name the wallet inside the app's own signed build.
                Text(stringResource(R.string.claim_route_build_title), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.claim_route_build_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                androidx.compose.foundation.text.selection.SelectionContainer { Text("<meta-data android:name=\"undersigned.publisher\"\n    android:value=\"${wallet ?: ""}\" />", color = RbColors.textPrimary, fontSize = 11.sp, lineHeight = 15.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(vertical = 6.dp)) }
                // Route 2: the website the store lists, when it is not a shared host.
                Text(stringResource(R.string.claim_route_site_title), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text(if (wk != null) stringResource(R.string.claim_route_site_body, site, wk, wallet ?: "") else stringResource(R.string.claim_route_site_none), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Text(stringResource(R.string.claim_then_sign), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 8.dp)); claimStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }; dialogError?.let { Text(it, color = RbColors.negative, fontSize = 12.sp) } } },
            confirmButton = { TextButton(enabled = claimStage == null && sender != null, onClick = {
                val s = sender ?: return@TextButton
                dialogError = null
                vm.publish(s, ReviewMemo.Claim(pkg), onStage = { st -> claimStage = stageText(st) }) { r -> claimStage = null; r.fold({ claimDialog = false }, { dialogError = context.errorText(it) }) }
            }) { Text(stringResource(R.string.publisher_claim_sign), color = RbColors.purpleLight) } },
            dismissButton = { TextButton(enabled = claimStage == null, onClick = { claimDialog = false }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    if (proofSheet) ProofSheet { proofSheet = false }

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
                    PermanenceNote(stringResource(R.string.permanent_title), stringResource(R.string.review_consent_body)) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = replyStage == null) { replyConsent = !replyConsent }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = replyConsent, onCheckedChange = { replyConsent = it }, enabled = replyStage == null, colors = CheckboxDefaults.colors(checkedColor = RbColors.purple))
                            Text(stringResource(R.string.review_consent_check), color = RbColors.textPrimary, fontSize = 12.sp, lineHeight = 16.sp)
                        }
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
                        replyStage = stageText(st)
                    }) { r -> replyStage = null; r.fold(onSuccess = { replyTo = null }, onFailure = { replyError = context.errorText(it) }) }
                }) { Text(stringResource(R.string.reply_publish), color = RbColors.purpleLight) }
            },
            dismissButton = { TextButton(enabled = replyStage == null, onClick = { replyTo = null; fromLink = false }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } },
        )
    }

    pendingVote?.let { target ->
        AlertDialog(onDismissRequest = { if (voteStage == null) pendingVote = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.helpful_sign), color = RbColors.textPrimary) },
            text = { Column { Text(stringResource(R.string.helpful_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp); voteStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }; dialogError?.let { Text(it, color = RbColors.negative, fontSize = 12.sp) } } },
            confirmButton = { TextButton(enabled = voteStage == null && sender != null && !vm.myUpvote(target), onClick = {
                val s = sender ?: return@TextButton
                dialogError = null
                vm.publish(s, ReviewMemo.Upvote(target), onStage = { st -> voteStage = stageText(st) }) { r -> voteStage = null; r.fold({ pendingVote = null }, { dialogError = context.errorText(it) }) }
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
                dialogError?.let { Text(it, color = RbColors.negative, fontSize = 12.sp) }
            } },
            confirmButton = { TextButton(enabled = ok && disputeStage == null && sender != null, onClick = {
                val s = sender ?: return@TextButton
                dialogError = null
                vm.publish(s, ReviewMemo.Dispute(target, disputeText.trim()), onStage = { st -> disputeStage = stageText(st) }) { r -> disputeStage = null; r.fold({ disputeTarget = null }, { dialogError = context.errorText(it) }) }
            }) { Text(stringResource(R.string.dispute_sign), color = RbColors.purpleLight) } },
            dismissButton = { TextButton(enabled = disputeStage == null, onClick = { disputeTarget = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    flagTarget?.let { target ->
        var reason by remember { mutableStateOf(ReviewMemo.FlagReason.SPAM) }
        AlertDialog(onDismissRequest = { if (flagStage == null) flagTarget = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.flag_title), color = RbColors.textPrimary) },
            text = { Column {
                Text(stringResource(R.string.flag_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                if (wallet != null && vm.isVerifiedPublisher(pkg, wallet!!)) Text(stringResource(R.string.publisher_flags_ignored), color = RbColors.textSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(ReviewMemo.FlagReason.SPAM to R.string.flag_spam, ReviewMemo.FlagReason.ABUSE to R.string.flag_abuse, ReviewMemo.FlagReason.OFF_TOPIC to R.string.flag_offtopic, ReviewMemo.FlagReason.MISLEADING to R.string.flag_misleading)
                        .forEach { (fr, label) -> TagChip(stringResource(label), selected = reason == fr) { if (flagStage == null) reason = fr } }
                }
                flagStage?.let { Spacer(Modifier.height(6.dp)); Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }
                dialogError?.let { Text(it, color = RbColors.negative, fontSize = 12.sp) }
            } },
            confirmButton = { TextButton(enabled = flagStage == null && sender != null, onClick = {
                val s = sender ?: return@TextButton
                dialogError = null
                vm.publish(s, ReviewMemo.Flag(target, reason), onStage = { st -> flagStage = stageText(st) }) { r -> flagStage = null; r.fold({ flagTarget = null }, { dialogError = context.errorText(it) }) }
            }) { Text(stringResource(R.string.flag_sign), color = RbColors.purpleLight) } },
            dismissButton = { TextButton(enabled = flagStage == null, onClick = { flagTarget = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    modTarget?.let { target ->
        var reason by remember(target) { mutableStateOf(ReviewMemo.ModReason.ILLEGAL) }
        AlertDialog(onDismissRequest = { if (simpleStage == null) modTarget = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.mod_title), color = RbColors.textPrimary) },
            text = { Column {
                Text(stringResource(R.string.mod_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReviewMemo.ModReason.entries.forEach { mr -> TagChip(modReasonLabel(mr), selected = reason == mr) { if (simpleStage == null) reason = mr } }
                }
                simpleStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }
                actionMessage?.takeIf { !it.first }?.let { Text(it.second, color = RbColors.negative, fontSize = 12.sp) }
            } },
            confirmButton = { TextButton(enabled = simpleStage == null && sender != null, onClick = { sign(ReviewMemo.Moderate(target, reason)) { ok -> if (ok) modTarget = null } }) { Text(stringResource(R.string.mod_sign), color = RbColors.negative) } },
            dismissButton = { TextButton(enabled = simpleStage == null, onClick = { modTarget = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    pendingSimple?.let { (kind, target) ->
        val memo = when (kind) { "unvote" -> ReviewMemo.Unvote(target); "unflag" -> ReviewMemo.Unflag(target); else -> ReviewMemo.Clear(target) }
        val quoted = remember(target) { vm.entryOf(target) }
        AlertDialog(onDismissRequest = { if (simpleStage == null) pendingSimple = null }, containerColor = RbColors.surface,
            title = { Text(stringResource(when (kind) { "unvote" -> R.string.unvote; "unflag" -> R.string.unflag; else -> R.string.clear }), color = RbColors.textPrimary) },
            text = { Column {
                Text(stringResource(R.string.link_confirm_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                quoted?.let { t -> Text((domainNames[t.signer]?.takeIf { it.isNotEmpty() } ?: shortKey(t.signer, 4, 4)) + " · " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.blockTime * 1000)), color = RbColors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
                simpleStage?.let { Text(it, color = RbColors.purpleLight, fontSize = 12.sp) }
                actionMessage?.takeIf { !it.first }?.let { Text(it.second, color = RbColors.negative, fontSize = 12.sp) }
            } },
            confirmButton = { TextButton(enabled = simpleStage == null && sender != null, onClick = { sign(memo) { ok -> if (ok) pendingSimple = null } }) { Text(stringResource(R.string.link_confirm_sign), color = RbColors.purpleLight) } },
            dismissButton = { TextButton(enabled = simpleStage == null, onClick = { pendingSimple = null }) { Text(stringResource(R.string.cancel), color = RbColors.textSecondary) } })
    }

    if (revokeDialog && mine != null) {
        AlertDialog(
            onDismissRequest = { if (revokeStage == null) revokeDialog = false }, containerColor = RbColors.surface,
            title = { Text(stringResource(R.string.revoke_title), color = RbColors.textPrimary) },
            text = { Column { Text(revokeStage ?: stringResource(R.string.revoke_body), color = RbColors.textSecondary, fontSize = 13.sp); dialogError?.let { Text(it, color = RbColors.negative, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) } } },
            confirmButton = {
                TextButton(enabled = revokeStage == null, onClick = {
                    val s = sender ?: return@TextButton
                    dialogError = null
                    vm.publish(s, ReviewMemo.Revocation(mine.signature), onStage = { st -> revokeStage = stageText(st) }) { r -> revokeStage = null; r.fold({ revokeDialog = false }, { dialogError = context.errorText(it) }) }
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

/** A burst's multiple of the usual pace, as shown: whole numbers from ×2 up, one decimal below. */
private fun timesText(b: com.undersigned.protocol.ReviewBursts.Burst): String {
    val m = com.undersigned.protocol.BurstSpark.multiple(b)
    return if (m >= 2) String.format(java.util.Locale.US, "%.0f", m) else String.format(java.util.Locale.US, "%.1f", m)
}

@Composable
private fun burstRange(b: com.undersigned.protocol.ReviewBursts.Burst): String = android.text.format.DateUtils.formatDateRange(LocalContext.current, b.start * 1000, b.end * 1000 + 1,
    android.text.format.DateUtils.FORMAT_ABBREV_MONTH or android.text.format.DateUtils.FORMAT_SHOW_YEAR or android.text.format.DateUtils.FORMAT_SHOW_DATE)

/**
 * One source's bursts (design F1). [attested] gives (with an attested device, of) for a burst when known; [season] its
 * reward-period label. Strongest first; the first as a headline with its chart, two compact rows, the rest behind More.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BurstSection(source: String, bursts: List<com.undersigned.protocol.ReviewBursts.Burst>, points: List<com.undersigned.protocol.ReviewBursts.Point>, ratings: Boolean,
                         attested: (com.undersigned.protocol.ReviewBursts.Burst) -> Pair<Int, Int>?, season: (com.undersigned.protocol.ReviewBursts.Burst) -> String?) {
    val sorted = bursts.sortedByDescending { it.intensity }
    val lead = sorted.first()
    var all by remember(bursts) { mutableStateOf(false) }
    val high = lead.kind == com.undersigned.protocol.ReviewBursts.Kind.HIGH
    val kind = stringResource(when { ratings && high -> R.string.burst_kind_high_ratings; ratings -> R.string.burst_kind_low_ratings; high -> R.string.burst_kind_high; else -> R.string.burst_kind_low })
    Text("$source · $kind".uppercase(), color = RbColors.textTertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    val pace = String.format(java.util.Locale.US, "%.1f", lead.expected)
    val full = stringResource(if (ratings) (if (high) R.string.burst_high_ratings else R.string.burst_low_ratings) else (if (high) R.string.burst_high else R.string.burst_low),
        lead.count, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(lead.start * 1000)), DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(lead.end * 1000)), pace)
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            // FlowRow: in German the pill is twice as wide and must wrap under the count, not be cut off (seen on device).
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${lead.count}", color = RbColors.textPrimary, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 44.sp)
                Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) { TrustPill(stringResource(R.string.burst_times, timesText(lead)), RbColors.warning) }
            }
            Text(burstRange(lead), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            Text(stringResource(R.string.burst_pace, pace), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        }
        val sp = remember(points, lead) { com.undersigned.protocol.BurstSpark.of(points, lead) }
        Sparkline(sp.counts, sp.from, sp.to, lead.expected / 3.0, full, Modifier.size(132.dp, 48.dp))
    }
    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attested(lead)?.let { (n, of) -> TrustPill(stringResource(R.string.burst_attested, n, of), RbColors.green) }
        season(lead)?.let { OutlinePill(stringResource(R.string.season_pill, it)) }
    }
    if (lead.sameText > 0) Text(stringResource(R.string.burst_same_text, lead.sameText), color = RbColors.warning, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    val rest = sorted.drop(1).let { if (all) it else it.take(2) }
    if (rest.isNotEmpty()) HorizontalDivider(color = RbColors.surfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
    rest.forEach { b ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Text("${b.count}", color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.widthIn(min = 52.dp))
            Text("×" + timesText(b), color = RbColors.warning, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 48.dp).padding(top = 3.dp))
            Column(Modifier.weight(1f)) {
                Text(burstRange(b), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                val extra = listOfNotNull(attested(b)?.let { (n, of) -> stringResource(R.string.burst_attested, n, of) }, season(b)?.let { stringResource(R.string.season_pill, it) })
                if (extra.isNotEmpty()) Text(extra.joinToString(" · "), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
    if (!all && sorted.size > 3) Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { all = true }, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.burst_more, sorted.size - 3), color = RbColors.purpleLight, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = RbColors.purpleLight)
    }
}


/**
 * "How is this proven?": why a publisher claim is proven by the app's signing key (or the store-listed website), and
 * why neither a wallet's own signature nor the wallet that published on the dApp Store proves it by itself.
 */
@Composable
private fun ProofSheet(onClose: () -> Unit) {
    AlertDialog(onDismissRequest = onClose, containerColor = RbColors.surface,
        title = { Text(stringResource(R.string.proof_how_title), color = RbColors.textPrimary, fontWeight = FontWeight.ExtraBold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.proof_how_intro), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            listOf(R.string.proof_key_title to R.string.proof_key_body, R.string.proof_site_title to R.string.proof_site_body, R.string.proof_not_title to R.string.proof_not_body).forEach { (t, b) ->
                Column {
                    Text(stringResource(t), color = RbColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(b), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
            Text(stringResource(R.string.proof_how_after), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
        } },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(android.R.string.ok), color = RbColors.purpleLight) } })
}

private const val REVIEW_PAGE = 20

/** "Show more" / "Show less" under folded text, with a full touch target. */
@Composable
private fun ShowMore(open: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(if (open) R.string.show_less else R.string.show_more), color = RbColors.purpleLight, fontSize = 12.sp) }
}
