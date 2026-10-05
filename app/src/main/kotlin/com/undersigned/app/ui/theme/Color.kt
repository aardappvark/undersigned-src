package com.undersigned.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Undersigned palette: AMOLED black field, Solana purple for actions, Solana green for money coming back. */
object RbColors {
    val background = Color(0xFF000000)
    val surface = Color(0xFF0E0B18)
    val surfaceVariant = Color(0xFF17122A)
    val card = Color(0xFF120E22)
    val glass = Color(0x1F9945FF)
    val glassBorder = Color(0x339945FF)

    val purple = Color(0xFF9945FF)
    val purpleDim = Color(0xFF6B2FC7)
    val purpleLight = Color(0xFFB98CFF)
    val green = Color(0xFF14F195)
    val greenDim = Color(0xFF0FA86A)

    val textPrimary = Color(0xFFF2EEFF)
    val textSecondary = Color(0xFFB7AED1)
    // Raised 6 Oct 2026 for WCAG AA: tertiary was 4.1–4.3:1 on cards (now 5.1–5.5:1); dim was 2.2:1 (now 3.8–4.4:1,
    // for decoration and disabled text only, never for something the user must read).
    val textTertiary = Color(0xFF8C83A8)
    val textDim = Color(0xFF756D90)

    val positive = green
    val negative = Color(0xFFFF6B6B)
    val warning = Color(0xFFFFC46B)
    val info = Color(0xFF6BB9FF)

    val navBackground = Color(0xFF07050F)
}
