package com.goomistudio.postmory.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------------------
// "Airmail" palette — CLAUDE.md §5. These raw tokens are the ONLY colour literals in the app.
// Screens must read colours through StampTheme.colors (or MaterialTheme.colorScheme), never here.
// ---------------------------------------------------------------------------------------------

internal val Paper = Color(0xFFF6F0E6)       // default background (60%)
internal val PaperWhite = Color(0xFFFFFEFB)  // stamp paper, cards, plates
internal val Ink = Color(0xFF18223F)         // text, nav bar, camera background (30%)
internal val AirmailRed = Color(0xFFD7362B)  // primary action, shutter, postmark (~10%)
internal val Cobalt = Color(0xFF2848C8)      // celebration screen, airmail stripe
internal val Kraft = Color(0xFFE3CFAE)       // album pages
internal val KraftDark = Color(0xFFC9A97C)   // album covers
internal val Sage = Color(0xFF6E8F6A)        // album covers / accents
internal val Ember = Color(0xFFFF6A4D)       // streak flame / accents on dark
internal val Surface2 = Color(0xFFEBE1D0)    // icon-button backgrounds on Paper

// Photo-filter inks (only inside stamp pictures, never UI): the Sepia filter's dark and light.
internal val SepiaInk = Color(0xFF3B2414)
internal val SepiaPaper = Color(0xFFF3E3C6)

// Tinted shadow bases (alpha is applied by Modifier.tintedShadow) — never pure black.
private val ShadowWarmBase = Color(0xFF462D0F) // rgba(70,45,15,…) on paper
private val ShadowNavyBase = Color(0xFF0B1550) // on cobalt / ink surfaces

/** Text hierarchy on Paper: Ink at 100 / 80 / 64 %. */
private const val HighAlphaOnLight = 1f
private const val MediumAlphaOnLight = 0.80f
private const val LowAlphaOnLight = 0.64f

/** Text hierarchy on dark: PaperWhite at 100 / 80 / 56 %. */
private const val HighAlphaOnDark = 1f
private const val MediumAlphaOnDark = 0.80f
private const val LowAlphaOnDark = 0.56f

/** Handwritten notes use Ink between 72 % and 100 %. */
private const val HandInkSoftAlpha = 0.72f

/** Postmark ink is AirmailRed at ~85 % on light surfaces. */
private const val PostmarkAlpha = 0.85f

/** Hairlines, dashed slots, outline chips. */
private const val OutlineAlphaOnLight = 0.24f
private const val OutlineAlphaOnDark = 0.32f

/** Translucent plates on dark/cobalt (chips, icon buttons on camera, Retake/Edit on Stamped). */
private const val ScrimOnDarkAlpha = 0.12f

/**
 * Full set of semantic colours for the app. Everything a screen needs lives here so that no
 * screen ever builds a Color itself.
 */
@Immutable
data class AirmailColors(
    // Base palette
    val paper: Color,
    val paperWhite: Color,
    val ink: Color,
    val airmailRed: Color,
    val cobalt: Color,
    val kraft: Color,
    val kraftDark: Color,
    val sage: Color,
    val ember: Color,
    val surface2: Color,

    // Text on light (Paper / PaperWhite / Kraft)
    val textHigh: Color,
    val textMedium: Color,
    val textLow: Color,

    // Text on dark (Ink / Cobalt)
    val textOnDarkHigh: Color,
    val textOnDarkMedium: Color,
    val textOnDarkLow: Color,

    // Handwriting (HandNote)
    val handInk: Color,
    val handInkSoft: Color,

    // Lines
    val outline: Color,
    val outlineOnDark: Color,

    // Translucent plate on dark surfaces
    val plateOnDark: Color,

    // Postmark ink
    val postmarkOnLight: Color,
    val postmarkOnDark: Color,

    // Tinted shadows
    val shadowWarm: Color,
    val shadowNavy: Color,
)

internal val AirmailPalette = AirmailColors(
    paper = Paper,
    paperWhite = PaperWhite,
    ink = Ink,
    airmailRed = AirmailRed,
    cobalt = Cobalt,
    kraft = Kraft,
    kraftDark = KraftDark,
    sage = Sage,
    ember = Ember,
    surface2 = Surface2,

    textHigh = Ink.copy(alpha = HighAlphaOnLight),
    textMedium = Ink.copy(alpha = MediumAlphaOnLight),
    textLow = Ink.copy(alpha = LowAlphaOnLight),

    textOnDarkHigh = PaperWhite.copy(alpha = HighAlphaOnDark),
    textOnDarkMedium = PaperWhite.copy(alpha = MediumAlphaOnDark),
    textOnDarkLow = PaperWhite.copy(alpha = LowAlphaOnDark),

    handInk = Ink,
    handInkSoft = Ink.copy(alpha = HandInkSoftAlpha),

    outline = Ink.copy(alpha = OutlineAlphaOnLight),
    outlineOnDark = PaperWhite.copy(alpha = OutlineAlphaOnDark),

    plateOnDark = PaperWhite.copy(alpha = ScrimOnDarkAlpha),

    postmarkOnLight = AirmailRed.copy(alpha = PostmarkAlpha),
    postmarkOnDark = PaperWhite,

    shadowWarm = ShadowWarmBase,
    shadowNavy = ShadowNavyBase,
)

val LocalAirmailColors = staticCompositionLocalOf { AirmailPalette }
