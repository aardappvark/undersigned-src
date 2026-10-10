package com.undersigned.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.MainViewModel
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.protocol.ReviewMemo

/**
 * Before an import is signed: the store review's stars and text as they will be signed, and the tags the reviewer
 * chooses for each (1 to 3, grouped, opposites released). Nothing is chosen for them. Placed once at the app's root.
 */
@Composable
fun ImportTagsDialog(vm: MainViewModel) {
    val req by vm.importRequest.collectAsState()
    val r = req ?: return
    val catalogue by vm.catalogue.collectAsState()
    val tags = remember(r) { mutableStateMapOf<String, List<ReviewMemo.Tag>>() }
    val ready = r.chosen.all { !tags[it.first].isNullOrEmpty() }
    AlertDialog(
        onDismissRequest = { vm.importRequest.value = null },
        containerColor = RbColors.surface,
        title = { Text(stringResource(R.string.import_tags_title), color = RbColors.textPrimary, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.import_tags_body), color = RbColors.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                r.chosen.forEach { (pkg, review) ->
                    Column {
                        Text(catalogue.firstOrNull { it.packageName == pkg }?.name ?: pkg, color = RbColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("★".repeat(review.rating.coerceIn(1, 5)) + "☆".repeat(5 - review.rating.coerceIn(1, 5)) + (vm.importText(review)?.let { " · " + it.take(300) + if (it.length > 300) "…" else "" } ?: ""), color = RbColors.textTertiary, fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        TagPicker(tags[pkg] ?: emptyList(), collapsible = true) { tags[pkg] = it }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = ready, onClick = { vm.importRequest.value = null; vm.importStoreReviews(r.sender, r.chosen, tags.toMap(), r.onStage, r.onResult) }) {
            Text(stringResource(R.string.import_tags_sign), color = if (ready) RbColors.green else RbColors.textTertiary) } },
        dismissButton = { TextButton(onClick = { vm.importRequest.value = null }) { Text(stringResource(android.R.string.cancel), color = RbColors.textSecondary) } },
    )
}
