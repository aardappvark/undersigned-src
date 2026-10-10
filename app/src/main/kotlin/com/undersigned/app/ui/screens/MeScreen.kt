package com.undersigned.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.Pill
import com.undersigned.app.ui.components.SecondaryButton
import com.undersigned.app.ui.components.tourTarget
import com.undersigned.app.ui.theme.RbColors

/**
 * The Me tab: everything about this wallet in one place (owner, 10 Oct 2026). My reviews, Inbox and Settings used to be
 * icons on the Browse header; they and the screens that were only reachable from Settings now sit here.
 */
@Composable
fun MeScreen(
    vm: MainViewModel, onMyReviews: () -> Unit, onInbox: () -> Unit, onToReview: () -> Unit, onDesk: () -> Unit,
    onWatch: () -> Unit, onLeaderboard: () -> Unit, onStats: () -> Unit, onSettings: () -> Unit,
) {
    val wallet by vm.wallet.collectAsState()
    val pro by vm.pro.collectAsState()
    val entries by vm.entries.collectAsState()
    val inboxCount = remember(entries) { vm.unanswered().size + vm.repliesToMe().size }
    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding().verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.nav_me), color = RbColors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(RbColors.card).padding(16.dp)) {
            Text(stringResource(R.string.settings_wallet), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(wallet?.let { shortKey(it, 6, 6) } ?: stringResource(R.string.settings_no_wallet), color = RbColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                if (pro?.active == true) { Spacer(Modifier.width(8.dp)); Pill(stringResource(R.string.pro_badge), RbColors.purpleLight) }
            }
            if (wallet == null) { Spacer(Modifier.height(10.dp)); SecondaryButton(stringResource(R.string.settings_signin), onClick = onSettings) }
        }
        Spacer(Modifier.height(8.dp))
        HubRow(Icons.Outlined.RateReview, stringResource(R.string.my_reviews_title), onClick = onMyReviews, modifier = Modifier.tourTarget("m_reviews"))
        HubRow(Icons.Outlined.Inbox, stringResource(R.string.inbox), count = inboxCount, a11y = if (inboxCount > 0) pluralStringResource(R.plurals.inbox_a11y, inboxCount, inboxCount) else null, onClick = onInbox, modifier = Modifier.tourTarget("m_inbox"))
        HubRow(Icons.Outlined.PendingActions, stringResource(R.string.to_review_title), onClick = onToReview)
        HubRow(Icons.Outlined.Storefront, stringResource(R.string.desk_title), onClick = onDesk)
        HubRow(Icons.Outlined.Visibility, stringResource(R.string.watch_title), onClick = onWatch)
        HubRow(Icons.Outlined.Leaderboard, stringResource(R.string.lb_open), onClick = onLeaderboard)
        HubRow(Icons.Outlined.BarChart, stringResource(R.string.stats_open), onClick = onStats)
        HubRow(Icons.Outlined.Settings, stringResource(R.string.settings), onClick = onSettings, modifier = Modifier.tourTarget("m_settings"))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun HubRow(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier, count: Int = 0, a11y: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp)
        .let { m -> if (a11y != null) m.then(Modifier.semanticsLabel(a11y)) else m }, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = RbColors.textSecondary)
        Spacer(Modifier.width(16.dp))
        Text(text, color = RbColors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (count > 0) Badge(containerColor = RbColors.purple) { Text(if (count > 99) "99+" else "$count") }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = RbColors.textTertiary)
    }
}

private fun Modifier.semanticsLabel(label: String): Modifier = this.then(androidx.compose.ui.Modifier.semantics(mergeDescendants = true) { contentDescription = label })
