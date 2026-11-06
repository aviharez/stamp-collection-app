package com.goomistudio.postmory.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.goomistudio.postmory.R

// ---------------------------------------------------------------------------------------------
// Fonts — bundled in res/font (all OFL). Static instances cut at exactly the weights we use.
// ---------------------------------------------------------------------------------------------

/** Fraunces Italic 600 — screen titles, album names, "Stamped!". */
val Fraunces = FontFamily(
    Font(R.font.fraunces_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)

/** Plus Jakarta Sans 500 / 700 — all UI text. */
val PlusJakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
)

/** Caveat 600 — handwritten notes, captions on album pages, counts. */
val Caveat = FontFamily(
    Font(R.font.caveat_semibold, FontWeight.SemiBold),
)

private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

/**
 * The app's type scale. Max 4 sizes per screen (CLAUDE.md §5) — pick from here, never invent.
 * Colours are intentionally unspecified: they come from LocalContentColor / StampTheme.colors.
 */
@Immutable
data class AirmailTypography(
    // Fraunces Italic 600
    /** "Stamped!" — 56sp. */
    val hero: TextStyle,
    /** Screen titles: "Your stamp album", "Collections", "Japan trip" — 44sp. */
    val screenTitle: TextStyle,
    /** Card titles: "Today's stamp is waiting" — 22sp. */
    val cardTitle: TextStyle,
    /** Album names on covers / plates, camera header "Kyoto, 4 Nov" — 18sp. */
    val albumName: TextStyle,

    // Plus Jakarta Sans
    /** Uppercase letter-spaced overline: "WED · 04 NOV", "MY ALBUMS" — 12sp Bold. */
    val overline: TextStyle,
    /** Small labels, captions, chips on camera — 12sp Medium. */
    val label: TextStyle,
    /** Body copy — 14sp Medium. */
    val body: TextStyle,
    /** Filter chips, secondary buttons — 13sp Medium. */
    val chip: TextStyle,
    /** Buttons — 15sp Bold. */
    val button: TextStyle,

    // Caveat 600
    /** Handwritten notes on album pages — 20sp. */
    val note: TextStyle,
    /** Smaller hand notes: dates under stamps, counts under covers — 18sp. */
    val noteSmall: TextStyle,
    /** Big hand note / highlighted count — 22sp. */
    val noteLarge: TextStyle,
)

internal val AirmailType = AirmailTypography(
    hero = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 56.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.02).em,
        lineHeightStyle = TightLineHeight,
    ),
    screenTitle = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-0.02).em,
        lineHeightStyle = TightLineHeight,
    ),
    cardTitle = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).em,
    ),
    albumName = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontStyle = FontStyle.Italic,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    ),
    overline = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.16.em,
    ),
    label = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    body = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    chip = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    button = TextStyle(
        fontFamily = PlusJakartaSans,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    note = TextStyle(
        fontFamily = Caveat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 22.sp,
    ),
    noteSmall = TextStyle(
        fontFamily = Caveat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 20.sp,
    ),
    noteLarge = TextStyle(
        fontFamily = Caveat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 24.sp,
    ),
)

/**
 * Material 3 typography mapped onto the Airmail scale, so any M3 component that falls back to
 * MaterialTheme.typography still renders in our fonts — never Roboto.
 */
internal val AirmailMaterialTypography = Typography(
    displayLarge = AirmailType.hero,
    displayMedium = AirmailType.screenTitle,
    displaySmall = AirmailType.screenTitle,
    headlineLarge = AirmailType.screenTitle,
    headlineMedium = AirmailType.cardTitle,
    headlineSmall = AirmailType.cardTitle,
    titleLarge = AirmailType.cardTitle,
    titleMedium = AirmailType.albumName,
    titleSmall = AirmailType.button,
    bodyLarge = AirmailType.body,
    bodyMedium = AirmailType.body,
    bodySmall = AirmailType.label,
    labelLarge = AirmailType.button,
    labelMedium = AirmailType.chip,
    labelSmall = AirmailType.label,
)

val LocalAirmailTypography = staticCompositionLocalOf { AirmailType }
