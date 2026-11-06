package com.goomistudio.postmory

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.goomistudio.postmory.ui.navigation.StampApp
import com.goomistudio.postmory.ui.screens.splash.LaunchSplash
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.widget.WidgetLink
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** A home-screen widget tap waiting to be followed by the nav host. */
    private var widgetLink by mutableStateOf<WidgetLink?>(null)

    /** False until StampApp knows whether to open on Home or the welcome. */
    private var ready by mutableStateOf(false)

    /** The envelope splash plays on a fresh launch (not after Android recreated us, not for widget taps). */
    private var showSplash by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // After process death the back stack is restored; don't follow the same tap twice.
        if (savedInstanceState == null) widgetLink = WidgetLink.from(intent)
        showSplash = savedInstanceState == null && widgetLink == null
        // The system splash is plain Paper: with the envelope splash it leaves at the first frame,
        // otherwise it stays until StampApp knows its first screen (so Home never flashes first).
        splash.setKeepOnScreenCondition { !showSplash && !ready }
        // Bar icon colours are set per screen by SystemBarsFor (dark icons on Paper by default).
        enableEdgeToEdge()
        setContent {
            StampAppTheme {
                Box {
                    StampApp(
                        widgetLink = widgetLink,
                        onWidgetLinkHandled = { widgetLink = null },
                        onReady = { ready = true },
                    )
                    if (showSplash) LaunchSplash(appReady = ready, onFinished = { showSplash = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        WidgetLink.from(intent)?.let { widgetLink = it }
    }
}
