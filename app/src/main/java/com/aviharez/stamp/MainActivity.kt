package com.aviharez.stamp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aviharez.stamp.ui.navigation.StampApp
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.widget.WidgetLink
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** A home-screen widget tap waiting to be followed by the nav host. */
    private var widgetLink by mutableStateOf<WidgetLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // After process death the back stack is restored; don't follow the same tap twice.
        if (savedInstanceState == null) widgetLink = WidgetLink.from(intent)
        // Bar icon colours are set per screen by SystemBarsFor (dark icons on Paper by default).
        enableEdgeToEdge()
        setContent {
            StampAppTheme {
                StampApp(widgetLink = widgetLink, onWidgetLinkHandled = { widgetLink = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        WidgetLink.from(intent)?.let { widgetLink = it }
    }
}
