package com.undersigned.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.AppConfig
import com.undersigned.app.R
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.GlassCard
import com.undersigned.app.ui.components.SecondaryButton
import com.undersigned.app.ui.components.SectionTitle
import com.undersigned.app.ui.components.StatRow
import com.undersigned.app.ui.theme.RbColors
import java.text.DateFormat
import java.util.Date

/** The curator's numbers: what the ledger holds, recomputed on this device from the public registry. */
@Composable
fun LedgerStatsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val entries by vm.entries.collectAsState()
    val facts by vm.facts.collectAsState()
    val verified by vm.verifiedPublishers.collectAsState()
    val hidden by vm.hiddenByConsensus.collectAsState()
    val s = remember(entries, facts, verified, hidden) { vm.stats() }
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    fun pct(n: Int, d: Int) = if (d == 0) "—" else "$n (${100 * n / d}%)"
    val rows = listOf(
        R.string.stats_records to s.records.toString(),
        R.string.stats_reviews_live to s.reviewsLive.toString(),
        R.string.stats_reviews_counted to s.reviewsCounted.toString(),
        R.string.stats_signers to s.signers.toString(),
        R.string.stats_attested to pct(s.attestedSigners, s.signers),
        R.string.stats_seeker_share to pct(s.seekerSigned, s.seekerTotal),
        R.string.stats_apps to s.apps.toString(),
        R.string.stats_apps_scored to s.appsScored.toString(),
        R.string.stats_replies to s.replies.toString(),
        R.string.stats_publisher_replies to s.publisherReplies.toString(),
        R.string.stats_claims to "${s.claims} · ${s.claimsVerified}",
        R.string.stats_moderation to "${s.flags} · ${s.clears} · ${s.disputes}",
        R.string.stats_hidden to s.hidden.toString(),
        R.string.stats_withdrawn to s.withdrawn.toString(),
        R.string.stats_licences to s.licences.toString(),
        R.string.stats_first to (if (s.first > 0) df.format(Date(s.first * 1000)) else "—"),
        R.string.stats_latest to (if (s.latest > 0) df.format(Date(s.latest * 1000)) else "—"),
    )
    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            Text(stringResource(R.string.stats_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.stats_sub), color = RbColors.textTertiary, fontSize = 12.sp, lineHeight = 16.sp)
            SectionTitle(stringResource(R.string.settings_ledger))
            GlassCard {
                Text(AppConfig.Ledger.REGISTRY, color = RbColors.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                rows.forEach { (label, value) -> StatRow(stringResource(label), value) }
            }
            Spacer(Modifier.height(12.dp))
            SecondaryButton(stringResource(R.string.stats_share), onClick = {
                val body = buildString {
                    appendLine("Undersigned ledger — ${df.format(Date())}"); appendLine("Registry ${AppConfig.Ledger.REGISTRY}")
                    rows.forEach { (label, value) -> appendLine("${context.getString(label)}: $value") }
                }
                try { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body) }, null)) } catch (_: Exception) { }
            })
            Spacer(Modifier.height(32.dp))
        }
    }
}
