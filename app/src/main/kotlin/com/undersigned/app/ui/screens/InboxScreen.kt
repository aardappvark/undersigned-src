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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.solana.shortKey
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.ChipRow
import com.undersigned.app.ui.components.EmptyState
import com.undersigned.app.ui.components.GlassCard
import com.undersigned.app.ui.components.RatingStars
import com.undersigned.app.ui.components.tagLabel
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.LedgerEntry
import com.undersigned.protocol.ReviewMemo
import java.text.DateFormat
import java.util.Date

/** The publisher's and reviewer's queues: unanswered reviews on my verified apps, replies to my records, starred records. */
@Composable
fun InboxScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onDesk: () -> Unit = {}) {
    val entries by vm.entries.collectAsState()
    val starred by vm.starred.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val domainNames by vm.domainNames.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var sort by remember { mutableStateOf(0) }   // 0 oldest first · 1 dApp · 2 signer · 3 rating
    val unanswered = remember(entries, sort, domainNames) {
        val list = vm.unanswered()
        when (sort) {
            1 -> list.sortedBy { (it.memo as ReviewMemo.Review).packageName }
            2 -> list.sortedBy { domainNames[it.signer]?.takeIf { n -> n.isNotEmpty() } ?: it.signer }
            3 -> list.sortedBy { (it.memo as ReviewMemo.Review).rating }
            else -> list.sortedBy { it.blockTime }
        }
    }
    val replies = remember(entries) { vm.repliesToMe().sortedByDescending { it.first.blockTime } }
    val starredEntries = remember(entries, starred) { entries.filter { it.signature in starred } }
    fun name(pkg: String) = catalogue.firstOrNull { it.packageName == pkg }?.name ?: pkg
    fun who(w: String) = domainNames[w]?.takeIf { it.isNotEmpty() } ?: shortKey(w, 4, 4)

    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = RbColors.textSecondary) }
            Text(stringResource(R.string.inbox), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        TabRow(selectedTabIndex = tab, containerColor = RbColors.background, contentColor = RbColors.purpleLight) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.inbox_unanswered) + " · ${unanswered.size}", fontSize = 13.sp) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.inbox_replies) + " · ${replies.size}", fontSize = 13.sp) })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.inbox_starred) + " · ${starredEntries.size}", fontSize = 13.sp) })
        }
        when (tab) {
            0 -> {
                Text(stringResource(R.string.inbox_unanswered_sub), color = RbColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Row(Modifier.padding(horizontal = 20.dp)) { TextButton(onClick = onDesk, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.desk_title) + " · " + stringResource(R.string.pro_badge), color = RbColors.purpleLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) } }
                if (unanswered.isEmpty()) Empty(stringResource(R.string.inbox_unanswered_empty))
                else { ChipRow(stringResource(R.string.sort), listOf(0 to stringResource(R.string.sort_oldest), 1 to stringResource(R.string.sort_dapp), 2 to stringResource(R.string.sort_signer), 3 to stringResource(R.string.sort_rating)), sort, { sort = it }, Modifier.padding(horizontal = 20.dp))
                LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) { items(unanswered, key = { it.signature }) { e -> ReviewRow(e, name((e.memo as ReviewMemo.Review).packageName), who(e.signer)) { onOpen((e.memo as ReviewMemo.Review).packageName) } } } }
            }
            1 -> if (replies.isEmpty()) EmptyState(R.drawable.photo_empty_replies, stringResource(R.string.inbox_replies_empty))
                 else LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                     items(replies, key = { it.first.signature }) { (rp, mine) ->
                         val pkg = (mine.memo as? ReviewMemo.Review)?.packageName ?: vm.packageOf(mine.signature) ?: ""
                         GlassCard(modifier = Modifier.clickable { if (pkg.isNotEmpty()) onOpen(pkg) }) {
                             Text(name(pkg), color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                             Text(who(rp.signer) + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(rp.blockTime * 1000)), color = RbColors.textTertiary, fontSize = 11.sp)
                             Spacer(Modifier.height(4.dp)); Text((rp.memo as ReviewMemo.Reply).text, color = RbColors.textSecondary, fontSize = 13.sp)
                         }
                         Spacer(Modifier.height(8.dp))
                     }
                 }
            else -> if (starredEntries.isEmpty()) Empty(stringResource(R.string.inbox_starred_empty))
                 else LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                     items(starredEntries, key = { it.signature }) { e ->
                         val pkg = (e.memo as? ReviewMemo.Review)?.packageName ?: vm.packageOf((e.memo as? ReviewMemo.Reply)?.reviewSignature ?: "") ?: ""
                         ReviewRow(e, name(pkg), who(e.signer)) { if (pkg.isNotEmpty()) onOpen(pkg) }
                     }
                 }
        }
    }
}

@Composable
private fun Empty(text: String) { Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) { Text(text, color = RbColors.textSecondary, fontSize = 14.sp) } }

@Composable
private fun ReviewRow(e: LedgerEntry, app: String, who: String, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.clickable { onClick() }) {
        Text(app, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            (e.memo as? ReviewMemo.Review)?.let { RatingStars(it.rating.toDouble(), size = 12); Spacer(Modifier.width(6.dp)) }
            Text(who + " · " + DateFormat.getDateInstance(DateFormat.SHORT).format(Date(e.blockTime * 1000)), color = RbColors.textTertiary, fontSize = 11.sp)
        }
        when (val m = e.memo) {
            is ReviewMemo.Review -> { Text(m.tags.joinToString(" · ") { tagLabel(it.wire) }, color = RbColors.textSecondary, fontSize = 12.sp); m.text?.let { Text(it, color = RbColors.textSecondary, fontSize = 13.sp) } }
            is ReviewMemo.Reply -> Text(m.text, color = RbColors.textSecondary, fontSize = 13.sp)
            else -> {}
        }
    }
    Spacer(Modifier.height(8.dp))
}
