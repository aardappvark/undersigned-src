package com.undersigned.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.undersigned.app.R
import com.undersigned.app.ui.theme.RbColors
import com.undersigned.app.util.StoreHandoff

/**
 * Says where to paste BEFORE the store opens. A toast shown over the store was half-covered by Android's own clipboard
 * preview (bottom-left, appears on every copy), and a text toast cannot be moved (seen on vitalik, 26 Sep 2026). Reading
 * the one instruction first, then tapping "Open in dApp Store", survives the switch. Every string here was already
 * translated, including the store's own button names in its own languages.
 */
@Composable
fun StoreHandoffDialog(pkg: String, text: String?, onOpened: () -> Unit = {}, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, containerColor = RbColors.surface,
        title = { Text(stringResource(R.string.pro_copy_review), color = RbColors.textPrimary) },
        text = { Text(stringResource(R.string.review_also_store_hint), color = RbColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) },
        confirmButton = { TextButton(onClick = { onDismiss(); if (StoreHandoff.copyAndOpen(context, pkg, text)) onOpened() }) { Text(stringResource(R.string.detail_open_store), color = RbColors.purpleLight) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel), color = RbColors.textTertiary) } })
}
