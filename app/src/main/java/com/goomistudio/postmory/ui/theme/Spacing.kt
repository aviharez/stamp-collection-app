package com.goomistudio.postmory.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 8-pt grid (4dp allowed for tight gaps) — CLAUDE.md §5. */
@Immutable
data class AirmailSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val s: Dp = 12.dp,
    val m: Dp = 16.dp,
    val l: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 40.dp,
    val xxxl: Dp = 48.dp,

    /** Standard screen padding. */
    val screen: Dp = 24.dp,
    /** Padding for full-bleed cards (album page, envelope card). */
    val screenFullBleed: Dp = 16.dp,

    /** Minimum touch target. */
    val touchTargetMin: Dp = 44.dp,
    /** Preferred touch target. */
    val touchTarget: Dp = 48.dp,
)

val LocalAirmailSpacing = staticCompositionLocalOf { AirmailSpacing() }
