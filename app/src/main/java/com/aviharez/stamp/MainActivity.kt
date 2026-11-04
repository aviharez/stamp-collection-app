package com.aviharez.stamp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aviharez.stamp.ui.debug.DEBUG_GALLERY_ENABLED
import com.aviharez.stamp.ui.debug.DebugGallery
import com.aviharez.stamp.ui.theme.StampAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always "paper": dark system-bar icons regardless of system dark mode.
        // Dark screens (Camera, Stamped!) switch the bar style themselves.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            StampAppTheme {
                // Navigation host arrives in step 4; until then debug builds show the gallery.
                if (DEBUG_GALLERY_ENABLED) DebugGallery()
            }
        }
    }
}
