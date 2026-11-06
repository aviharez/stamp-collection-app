package com.goomistudio.postmory.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Release builds ship no debug gallery: both entry points render nothing. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun DebugGallery(modifier: Modifier = Modifier) = Unit

@Composable
@Suppress("UNUSED_PARAMETER")
fun DebugGalleryEntry(onOpen: () -> Unit) = Unit

@Composable
fun DebugProSwitch() = Unit
