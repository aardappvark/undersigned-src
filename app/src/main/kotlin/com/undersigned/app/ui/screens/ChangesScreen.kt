package com.undersigned.app.ui.screens

import com.undersigned.app.ui.components.tourTarget
import com.undersigned.app.ui.components.BackButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.components.DAppIcon
import com.undersigned.app.ui.components.GlassCard
import com.undersigned.app.ui.theme.RbColors
import java.text.DateFormat
import java.util.Date

@Composable
fun ChangesScreen(vm: MainViewModel, onBack: (() -> Unit)?, onOpen: (String) -> Unit, onReview: (String) -> Unit = onOpen, onPro: () -> Unit = {}) {
    val changes by vm.changes.collectAsState()
    val catalogue by vm.catalogue.collectAsState()
    val icons by vm.iconCache.state.collectAsState()
    val real = changes.filter { !it.firstSeen }
    val details by vm.storeDetails.collectAsState()
    Column(Modifier.fillMaxSize().background(RbColors.background).systemBarsPadding()) {
        // As a tab (no back arrow) the title lines up with the other tabs' titles.
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onBack == null) 20.dp else 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            onBack?.let { BackButton(it) }
            Text(stringResource(R.string.changes_title), color = RbColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(stringResource(R.string.changes_sub), color = RbColors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            // This week on the store (Pro): store-wide bursts and updates in plain words, by AI, labelled a suggestion.
            item(key = "digest") { com.undersigned.app.ui.components.DigestCard(vm, onPro, Modifier.tourTarget("c_digest")); Spacer(Modifier.height(8.dp)) }
            if (real.isEmpty()) item(key = "empty") { Text(stringResource(R.string.changes_empty), color = RbColors.textSecondary, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(32.dp)) }
            items(real, key = { it.packageName + it.at }) { c ->
                val name = catalogue.firstOrNull { it.packageName == c.packageName }?.name ?: c.packageName
                GlassCard(modifier = Modifier.clickable { onOpen(c.packageName) }, accent = if (c.signerChanged) RbColors.negative else RbColors.glassBorder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DAppIcon(c.packageName, name, icons[c.packageName]?.takeIf { it.isNotEmpty() }, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(name, color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(c.at)), color = RbColors.textTertiary, fontSize = 11.sp)
                    Spacer(Modifier.height(6.dp))
                    if (c.signerChanged) Text(stringResource(R.string.fact_signer_changed), color = RbColors.negative, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    if (c.versionChanged) Text(stringResource(R.string.change_new_version, c.versionCode), color = RbColors.textSecondary, fontSize = 12.sp)
                    if (c.permsAdded.isNotEmpty()) Text(stringResource(R.string.fact_perms_added, vm.permissionNames(c.permsAdded).joinToString(", ")), color = RbColors.warning, fontSize = 12.sp)
                    if (c.permsRemoved.isNotEmpty()) Text(stringResource(R.string.fact_perms_removed, vm.permissionNames(c.permsRemoved).joinToString(", ")), color = RbColors.textSecondary, fontSize = 12.sp)
                    // The store's "What's new" describes ITS latest release. Shown only when that is the exact version this
                    // phone updated to — the Toolkit's listing said vc 175 while phones ran 243 and 248 (24 Sep 2026), and
                    // those notes would have described the wrong build.
                    if (c.versionChanged) {
                        LaunchedEffect(c.packageName) { vm.loadStoreDetails(c.packageName) }
                        details[c.packageName]?.takeIf { it.versionCode == c.versionCode }?.newInVersion?.let { notes ->
                            var open by remember(c.packageName + c.at) { mutableStateOf(false) }
                            Column(Modifier.padding(top = 6.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(RbColors.surfaceVariant).padding(10.dp)) {
                                Text(stringResource(R.string.change_whats_new), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(notes, color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 16.sp, maxLines = if (open) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
                                if (notes.length > 180 || notes.count { it == '\n' } >= 4) TextButton(onClick = { open = !open }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(if (open) R.string.show_less else R.string.show_more), color = RbColors.purpleLight, fontSize = 12.sp) }
                            }
                        }
                    }
                    if (c.versionChanged) { Spacer(Modifier.height(6.dp)); com.undersigned.app.ui.components.SecondaryButton(stringResource(R.string.change_rate_version), onClick = { onReview(c.packageName) }) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
