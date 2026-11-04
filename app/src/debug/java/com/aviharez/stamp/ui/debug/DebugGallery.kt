package com.aviharez.stamp.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aviharez.stamp.ui.theme.StampTheme

/** True in debug builds: the debug component gallery is available. */
const val DEBUG_GALLERY_ENABLED = true

/** Debug-only gallery of theme tokens and signature components (grows in step 3). */
@Composable
fun DebugGallery(modifier: Modifier = Modifier) {
    ThemeSpecimen(
        modifier
            .fillMaxSize()
            .background(StampTheme.colors.paper)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    )
}
