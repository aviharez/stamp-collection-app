package com.aviharez.stamp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import com.aviharez.stamp.ui.navigation.StampApp
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.widget.WidgetLink
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** A home-screen widget tap waiting to be followed by the nav host. */
    private var widgetLink by mutableStateOf<WidgetLink?>(null)

    /** False until StampApp knows whether to open on Home or the welcome; the splash stays up till then. */
    private var ready = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !ready }
        splash.setOnExitAnimationListener(::leaveSplash)
        // After process death the back stack is restored; don't follow the same tap twice.
        if (savedInstanceState == null) widgetLink = WidgetLink.from(intent)
        // Bar icon colours are set per screen by SystemBarsFor (dark icons on Paper by default).
        enableEdgeToEdge()
        setContent {
            StampAppTheme {
                StampApp(
                    widgetLink = widgetLink,
                    onWidgetLinkHandled = { widgetLink = null },
                    onReady = { ready = true },
                )
            }
        }
    }

    /** The icon lifts a little and the splash fades (200 ms); gone at once with reduce motion. */
    private fun leaveSplash(view: SplashScreenViewProvider) {
        val reduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        if (reduceMotion) return view.remove()
        view.iconView.animate().scaleX(SplashIconScale).scaleY(SplashIconScale).setDuration(SplashExitMillis).start()
        view.view.animate()
            .alpha(0f)
            .setDuration(SplashExitMillis)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction(view::remove)
            .start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        WidgetLink.from(intent)?.let { widgetLink = it }
    }
}

private const val SplashExitMillis = 200L
private const val SplashIconScale = 1.12f
