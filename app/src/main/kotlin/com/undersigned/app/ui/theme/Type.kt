package com.undersigned.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.undersigned.app.R

// Undersigned typography — Manrope throughout (fleet design system).
//
// Manrope ships as a single variable font (weight axis 200-800), so every weight
// below points at the same resource and differs only in the 'wght' axis value.
// Variation settings need API 26, which is this app's minSdk. Without an explicit
// setting the font would render at its default instance — ExtraLight (200) — so
// each weight must be declared, not inferred.
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
    resId = R.font.manrope_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

/**
 * Screen title style — the design's `.scr-h1`: 800 weight, tight tracking.
 */
val ScreenTitleStyle = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 28.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.5).sp,
)

/**
 * Screen subtitle — the design's `.scr-sub`.
 */
val ScreenSubtitleStyle = TextStyle(
    fontFamily = Manrope,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
)

// Material's own type scale, re-pointed at Manrope so any component that reads
// from MaterialTheme.typography (buttons, text fields, dialogs) picks it up too.
private val base = Typography()

val UndersignedTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Manrope),
    displayMedium = base.displayMedium.copy(fontFamily = Manrope),
    displaySmall = base.displaySmall.copy(fontFamily = Manrope),
    headlineLarge = base.headlineLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Manrope),
    bodyMedium = base.bodyMedium.copy(fontFamily = Manrope),
    bodySmall = base.bodySmall.copy(fontFamily = Manrope),
    labelLarge = base.labelLarge.copy(fontFamily = Manrope, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall.copy(fontFamily = Manrope, fontWeight = FontWeight.Medium),
)
