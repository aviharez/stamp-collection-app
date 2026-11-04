package com.aviharez.stamp.ui.theme

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Keeps system-bar icons readable: dark icons on Paper screens, light icons on dark screens
 * (Camera, Stamped!). Call once from the app root with the current screen's background.
 */
@Composable
fun SystemBarsFor(darkBackground: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(activity, darkBackground) {
        val style = if (darkBackground) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
