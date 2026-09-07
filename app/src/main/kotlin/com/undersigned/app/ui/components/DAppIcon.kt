package com.undersigned.app.ui.components

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.SubcomposeAsyncImage
import com.undersigned.app.ui.theme.RbColors

/**
 * The real dApp icon, three tiers in order (Toolkit `DAppIcon` pattern): the installed app's launcher
 * icon rasterised from PackageManager (adaptive icons need toBitmap), else the store's icon URI via
 * Coil, else a letter badge while loading / on error / when the store has no icon.
 */
@Composable
fun DAppIcon(packageName: String, displayName: String, iconUrl: String?, modifier: Modifier = Modifier.size(44.dp)) {
    val context = LocalContext.current
    val installed: ImageBitmap? = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName).toBitmap(192, 192).asImageBitmap() }
        catch (_: PackageManager.NameNotFoundException) { null } catch (_: Exception) { null }
    }
    when {
        installed != null -> Image(bitmap = installed, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(11.dp)))
        iconUrl != null -> SubcomposeAsyncImage(model = iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(11.dp)),
            loading = { LetterBadge(displayName, modifier) }, error = { LetterBadge(displayName, modifier) })
        else -> LetterBadge(displayName, modifier)
    }
}

@Composable
fun LetterBadge(displayName: String, modifier: Modifier = Modifier.size(44.dp)) {
    Box(modifier.clip(RoundedCornerShape(11.dp)).background(RbColors.purple.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        Text(displayName.firstOrNull()?.uppercase() ?: "?", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = RbColors.purpleLight)
    }
}
