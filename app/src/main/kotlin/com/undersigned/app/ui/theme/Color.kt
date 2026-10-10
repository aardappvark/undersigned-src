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
    // WCAG AA (1.4.3, 4.5:1). Measured on surface / card / surfaceVariant / black:
    // primary 17.1 / 16.6 / 16.0 / 18.5; secondary 9.3 / 9.0 / 8.6 / 10.0; tertiary 5.5 / 5.3 / 5.1 / 5.9.
    // Dim raised 11 Oct 2026 (1.1.6) from 0xFF756D90 (4.03 / 3.91 / 3.76 / 4.35, failed AA) to the same hue at
    // 4.95 / 4.81 / 4.62 / 5.34, so it passes on every background it sits on and still reads a step below tertiary.
    val textTertiary = Color(0xFF8C83A8)
    val textDim = Color(0xFF847C9C)

    val positive = green
    val negative = Color(0xFFFF6B6B)
    val warning = Color(0xFFFFC46B)
    val info = Color(0xFF6BB9FF)

    val navBackground = Color(0xFF07050F)
}
