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
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.undersigned.app.data.BundledIcons
import com.undersigned.app.ui.theme.RbColors

/**
 * The real dApp icon, in order (Toolkit `DAppIcon` pattern): the installed app's launcher icon rasterised from
 * PackageManager (adaptive icons need toBitmap); else the icon shipped inside the app ([BundledIcons]), no network;
 * else the store's icon URI via Coil, with the shipped icon (if an older one exists) or a letter badge standing in
 * while it loads or when it fails.
 */
@Composable
fun DAppIcon(packageName: String, displayName: String, iconUrl: String?, modifier: Modifier = Modifier.size(44.dp)) {
    val context = LocalContext.current
    val installed: ImageBitmap? = remember(packageName) {
        try { context.packageManager.getApplicationIcon(packageName).toBitmap(192, 192).asImageBitmap() }
        catch (_: PackageManager.NameNotFoundException) { null } catch (_: Exception) { null }
    }
    val shipped = remember(packageName) { BundledIcons.source(context, packageName) != null }
    val shape = modifier.clip(RoundedCornerShape(11.dp))
    val standIn: @Composable () -> Unit = {
        if (shipped) AsyncImage(model = BundledIcons.model(packageName), contentDescription = null, contentScale = ContentScale.Crop, modifier = shape)
        else LetterBadge(displayName, modifier)
    }
    when {
        installed != null -> Image(bitmap = installed, contentDescription = null, contentScale = ContentScale.Crop, modifier = shape)
        shipped && BundledIcons.isCurrent(context, packageName, iconUrl) -> standIn()
        iconUrl != null -> SubcomposeAsyncImage(model = iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = shape,
            loading = { standIn() }, error = { standIn() })
        else -> standIn()
    }
}

@Composable
fun LetterBadge(displayName: String, modifier: Modifier = Modifier.size(44.dp)) {
    Box(modifier.clip(RoundedCornerShape(11.dp)).background(RbColors.purple.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        // Two-letter monogram (design §4.4): the initials of the first two words, one letter for a one-word name.
        val mono = displayName.split(' ', '-', '_').filter { it.isNotBlank() }.take(2).map { it.first().uppercaseChar() }.joinToString("").ifEmpty { "?" }
        Text(mono, fontSize = if (mono.length > 1) 14.sp else 17.sp, fontWeight = FontWeight.ExtraBold, color = RbColors.purpleLight)
    }
}
