package com.undersigned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.theme.RbColors
import kotlinx.coroutines.launch

/**
 * "Ask how scoring works": a question answered by AI from the published review integrity policy only, labelled a
 * suggestion, with the policy one tap away. Free for everyone: it explains the rules, it never changes a score.
 */
@Composable
fun HelpAsk(vm: MainViewModel, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Text(stringResource(R.string.help_button), color = RbColors.purpleLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.heightIn(min = 48.dp).clickable { open = true }.padding(vertical = 14.dp))
    if (!open) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    AlertDialog(onDismissRequest = { open = false }, containerColor = RbColors.surface,
        title = { Text(stringResource(R.string.help_title), color = RbColors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(question, { question = it.take(400); error = null }, placeholder = { Text(stringResource(R.string.help_hint), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                error?.let { Text(it, color = RbColors.negative, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp), color = RbColors.purpleLight)
                answer?.let { a ->
                    Column(Modifier.padding(top = 10.dp).fillMaxWidth().background(RbColors.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp)) {
                        Text(stringResource(R.string.help_label), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(a, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                    Text(stringResource(R.string.integrity_link), color = RbColors.purpleLight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.heightIn(min = 48.dp).clickable { runCatching { uri.openUri(com.undersigned.app.AppConfig.Identity.URI + "integrity.html") } }.padding(vertical = 14.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && question.trim().length >= 3, onClick = {
                busy = true; error = null
                scope.launch { vm.askHelp(question).fold({ answer = it }, { error = context.getString(R.string.help_failed) }); busy = false }
            }) { Text(stringResource(R.string.help_ask), color = RbColors.purpleLight) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.help_close), color = RbColors.textSecondary) } })
}

/** This week on the store: the AI digest of store-wide bursts and updates, for Pro; others meet the lock where the card would be. */
@Composable
fun DigestCard(vm: MainViewModel, onPro: () -> Unit, modifier: Modifier = Modifier) {
    val pro by vm.pro.collectAsState()
    // Without Pro the card is the one Pro prompt on Changes (owner, 10 Oct 2026).
    if (pro?.active != true) { ProPrompt(stringResource(R.string.pro_tool_digest_title), stringResource(R.string.pro_tool_digest_body), onPro, modifier); return }
    GlassCard(modifier = modifier) {
        Text(stringResource(R.string.digest_title), color = RbColors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        val digest by produceState<Result<org.json.JSONObject>?>(null, pro) { value = vm.weeklyDigest() }
        when (val d = digest) {
            null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = RbColors.purpleLight)
            else -> d.fold({ j ->
                Text(stringResource(R.string.digest_label), color = RbColors.textTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                // A busy week lists twenty-odd updates; the card opens at its first lines (seen on a Seeker, 9 Oct 2026).
                var more by remember { mutableStateOf(false) }
                val text = j.optString("text")
                Text(text, color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = if (more) Int.MAX_VALUE else 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (text.count { it == '\n' } >= 8) TextButton(onClick = { more = !more }, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(if (more) R.string.show_less else R.string.show_more), color = RbColors.purpleLight, fontSize = 12.sp)
                }
            }, { Text(stringResource(R.string.digest_unavailable), color = RbColors.textSecondary, fontSize = 12.sp) })
        }
    }
}
