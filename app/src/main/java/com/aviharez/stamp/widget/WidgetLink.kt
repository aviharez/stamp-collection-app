package com.aviharez.stamp.widget

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** Where a widget tap takes the user. Carried as the launch intent's data URI. */
sealed interface WidgetLink {
    data object Camera : WidgetLink
    data object Pro : WidgetLink
    data class Stamp(val stampId: String, val albumId: Long) : WidgetLink

    fun toUri(): Uri = when (this) {
        Camera -> "$Scheme://$HostCamera".toUri()
        Pro -> "$Scheme://$HostPro".toUri()
        is Stamp -> "$Scheme://$HostStamp/$albumId/${Uri.encode(stampId)}".toUri()
    }

    companion object {
        private const val Scheme = "stampwidget"
        private const val HostCamera = "camera"
        private const val HostPro = "pro"
        private const val HostStamp = "stamp"

        /** Null for anything that isn't a widget link (normal launches, other apps' intents). */
        fun from(intent: Intent?): WidgetLink? {
            val uri = intent?.data ?: return null
            if (uri.scheme != Scheme) return null
            return when (uri.host) {
                HostCamera -> Camera
                HostPro -> Pro
                HostStamp -> {
                    val segments = uri.pathSegments
                    val albumId = segments.getOrNull(0)?.toLongOrNull() ?: return null
                    val stampId = segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
                    Stamp(stampId, albumId)
                }
                else -> null
            }
        }
    }
}
