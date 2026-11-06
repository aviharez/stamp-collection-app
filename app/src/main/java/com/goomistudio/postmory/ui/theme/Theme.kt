package com.goomistudio.postmory.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Material 3 colour scheme mapped entirely onto Airmail tokens, so no default Material colour
 * (purple, dynamic colour, tonal surface tint) can leak into any component.
 *
 * The app is a paper album and has a single light scheme on purpose; dark surfaces (Camera,
 * Stamped!) are drawn explicitly with Ink / Cobalt and the textOnDark* tokens.
 */
private val AirmailColorScheme = lightColorScheme(
    primary = AirmailRed,
    onPrimary = PaperWhite,
    primaryContainer = AirmailRed,
    onPrimaryContainer = PaperWhite,
    inversePrimary = Ember,

    secondary = Ink,
    onSecondary = PaperWhite,
    secondaryContainer = Ink,
    onSecondaryContainer = PaperWhite,

    tertiary = Cobalt,
    onTertiary = PaperWhite,
    tertiaryContainer = Cobalt,
    onTertiaryContainer = PaperWhite,

    background = Paper,
    onBackground = Ink,

    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Surface2,
    onSurfaceVariant = AirmailPalette.textMedium,
    surfaceTint = Paper,
    surfaceBright = PaperWhite,
    surfaceDim = Surface2,
    surfaceContainerLowest = PaperWhite,
    surfaceContainerLow = PaperWhite,
    surfaceContainer = PaperWhite,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,

    inverseSurface = Ink,
    inverseOnSurface = PaperWhite,

    error = AirmailRed,
    onError = PaperWhite,
    errorContainer = AirmailRed,
    onErrorContainer = PaperWhite,

    outline = AirmailPalette.textLow,
    outlineVariant = AirmailPalette.outline,
    scrim = Ink,
)

@Composable
fun StampAppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalAirmailColors provides AirmailPalette,
        LocalAirmailTypography provides AirmailType,
        LocalAirmailShapes provides AirmailShapeSet,
        LocalAirmailSpacing provides AirmailSpacing(),
    ) {
        MaterialTheme(
            colorScheme = AirmailColorScheme,
            typography = AirmailMaterialTypography,
            shapes = AirmailMaterialShapes,
        ) {
            // Default content colour is Ink (never pure black).
            CompositionLocalProvider(LocalContentColor provides AirmailPalette.textHigh) {
                content()
            }
        }
    }
}

/** Single entry point for design tokens: `StampTheme.colors.ink`, `StampTheme.type.note`, … */
object StampTheme {
    val colors: AirmailColors
        @Composable @ReadOnlyComposable get() = LocalAirmailColors.current
    val type: AirmailTypography
        @Composable @ReadOnlyComposable get() = LocalAirmailTypography.current
    val shapes: AirmailShapes
        @Composable @ReadOnlyComposable get() = LocalAirmailShapes.current
    val spacing: AirmailSpacing
        @Composable @ReadOnlyComposable get() = LocalAirmailSpacing.current
}

/**
 * Standard component preview on the light Paper background (CLAUDE.md §7).
 * The hex literal mirrors the Paper token; annotations can only take constants.
 */
@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6)
annotation class PaperPreview
