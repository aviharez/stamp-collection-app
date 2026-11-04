package com.aviharez.stamp.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user turned animations off (Developer options / Accessibility "Remove
 * animations"), which sets the animator duration scale to 0. CLAUDE.md §5: respect reduce motion.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Springy but short (CLAUDE.md §5). Snaps instantly when [reduceMotion] is on. */
fun <T> airmailSpring(reduceMotion: Boolean): AnimationSpec<T> =
    if (reduceMotion) {
        snap()
    } else {
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    }
