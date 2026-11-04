package com.aviharez.stamp.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Release builds ship no debug gallery. */
const val DEBUG_GALLERY_ENABLED = false

@Composable
@Suppress("UNUSED_PARAMETER")
fun DebugGallery(modifier: Modifier = Modifier) = Unit
