package com.undersigned.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.undersigned.app.ui.theme.RbColors

@Composable
fun GlassCard(modifier: Modifier = Modifier, accent: Color = RbColors.glassBorder, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RbColors.card)
            .border(1.dp, accent, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RbColors.purple, contentColor = RbColors.textPrimary,
            disabledContainerColor = RbColors.surfaceVariant, disabledContentColor = RbColors.textTertiary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = RbColors.textPrimary, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = RbColors.purpleLight, disabledContentColor = RbColors.textTertiary),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color = RbColors.textPrimary, sub: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = RbColors.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, color = RbColors.textTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
fun Pill(text: String, color: Color = RbColors.purpleLight) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = RbColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun RatingStars(rating: Double, modifier: Modifier = Modifier, size: Int = 14) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val full = kotlin.math.floor(rating).toInt()
        repeat(5) { i ->
            Text(if (i < full) "★" else "☆", color = if (i < full) RbColors.warning else RbColors.textTertiary, fontSize = size.sp)
        }
    }
}

@Composable
fun TagChip(text: String, count: Int? = null, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val bg = if (selected) RbColors.purple.copy(alpha = 0.35f) else RbColors.surfaceVariant
    val fg = if (selected) RbColors.textPrimary else RbColors.textSecondary
    val m = Modifier.clip(RoundedCornerShape(50)).background(bg).let { if (onClick != null) it.clickable { onClick() } else it }.padding(horizontal = 10.dp, vertical = 5.dp)
    Text(if (count != null) "$text · $count" else text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = m)
}

/** Display label for a protocol tag: "wallet-fails" → "Wallet fails". The wire value never changes. */
fun com.undersigned.protocol.ReviewMemo.Tag.label(): String = wire.replace('-', ' ').replaceFirstChar { it.uppercase() }
