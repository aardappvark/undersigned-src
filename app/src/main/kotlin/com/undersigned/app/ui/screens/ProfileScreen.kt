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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.*
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.ReviewMemo
import java.text.DateFormat
import java.util.Date

/** A wallet's public standing: counts, the two published-formula scores, apps it verifiably publishes, its reviews. */
@Composable
fun ProfileScreen(vm: MainViewModel, wallet: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val standings by vm.standings.collectAsState()
    val domainNames by vm.domainNames.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val entries by vm.entries.collectAsState()
    val verified by vm.verifiedPublishers.collectAsState()
    LaunchedEffect(wallet) { vm.resolveDomains(listOf(wallet)) }
    val st = standings[wallet]
    val name = domainNames[wallet]?.takeIf { it.isNotEmpty() } ?: shortKey(wallet, 6, 6)
    val published = remember(verified) { vm.publishedBy(wallet) }
    val reviews = remember(entries) { vm.entriesBy(wallet).sortedByDescending { it.blockTime } }
    fun appName(pkg: String) = catalogue.firstOrNull { it.packageName == pkg }?.name ?: pkg

    Column(Modifier.fillMaxSize().background(RbColors.background)) {
        PhotoBackdrop(R.drawable.photo_frame_4_seal, Modifier.fillMaxWidth().heightIn(min = 200.dp), fadeFrom = 0.30f) {
            Row(Modifier.align(Alignment.TopStart).systemBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
                BackButton(onBack, tint = RbColors.textPrimary)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(name, color = RbColors.textPrimary, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
                Text(wallet, color = RbColors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            // Counts, not a score (6 Oct 2026): a single 0–100 number read as a verdict on a person and sat beside
            // "Counts only. No score" in the same app. The facts it was built from are shown as they are.
            SectionTitle(stringResource(R.string.standing_title))
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        if (st == null) Text(stringResource(R.string.loading), color = RbColors.textSecondary, fontSize = 12.sp)
                        st?.let { Text(stringResource(R.string.standing_line, it.countedReviews, it.withdrawn, it.repliesReceived, it.repliesGiven), color = RbColors.textSecondary, fontSize = 12.sp)
                            Text(stringResource(R.string.standing_record, it.appsReviewed, it.followUps, it.followUpsAfterFix, it.fixesConfirmed, it.flagsUpheld, it.flagsGiven), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp) }
                        st?.let { Text(stringResource(R.string.profile_flags, it.flagsGiven, it.flagsUpheld, it.flagsReceived, it.recordsHidden), color = RbColors.textSecondary, fontSize = 12.sp) }
                        st?.agreement?.let { Text(stringResource(R.string.standing_agreement, String.format(java.util.Locale.US, "%.0f%%", it * 100)), color = RbColors.textSecondary, fontSize = 12.sp) }
                        st?.repliedToReplies?.let { Text(stringResource(R.string.profile_replied, String.format(java.util.Locale.US, "%.0f%%", it * 100)), color = RbColors.textSecondary, fontSize = 12.sp) }
                        if ((st?.firstSeen ?: 0L) > 0) Text(stringResource(R.string.standing_since, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(st!!.firstSeen * 1000))), color = RbColors.textTertiary, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(6.dp)); Text(stringResource(R.string.standing_note2), color = RbColors.textTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            }
            if (published.isNotEmpty()) {
                SectionTitle(stringResource(R.string.profile_publishes))
                published.forEach { pkg ->
                    val r = vm.responsiveness(pkg)
                    GlassCard(modifier = Modifier.clickable { onOpen(pkg) }, accent = RbColors.green) {
                        Text(appName(pkg), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(if (r.answered == 0) stringResource(R.string.responsiveness_never) else stringResource(R.string.responsiveness_line, bucketLabel(r.bucket), r.answered, r.total), color = RbColors.textSecondary, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            SectionTitle(stringResource(R.string.profile_reviews))
            if (reviews.isEmpty()) Text(stringResource(R.string.row_no_reviews), color = RbColors.textSecondary, fontSize = 13.sp)
            reviews.forEach { e ->
                val r = e.memo as ReviewMemo.Review
                GlassCard(modifier = Modifier.clickable { onOpen(r.packageName) }) {
                    Text(appName(r.packageName), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) { RatingStars(r.rating.toDouble(), size = 12); Spacer(Modifier.width(6.dp)); Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(e.blockTime * 1000)) + " · v${r.versionCode}", color = RbColors.textTertiary, fontSize = 11.sp) }
                    vm.modHidden.collectAsState().value[e.signature]?.let { com.undersigned.app.ui.components.ModHiddenText(it) } ?: r.text?.let { Text(it, color = RbColors.textSecondary, fontSize = 13.sp) }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
