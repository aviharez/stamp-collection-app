package com.aviharez.stamp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aviharez.stamp.ui.navigation.StampApp
import com.aviharez.stamp.ui.theme.StampAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bar icon colours are set per screen by SystemBarsFor (dark icons on Paper by default).
        enableEdgeToEdge()
        setContent {
            StampAppTheme {
                StampApp()
            }
        }
    }
}
