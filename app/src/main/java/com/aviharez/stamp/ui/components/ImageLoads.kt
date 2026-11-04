package com.aviharez.stamp.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts stamp images that finished loading (or failed) inside an offscreen capture, so an export
 * waits until every picture is drawn. Provided via [LocalImageLoads]; null everywhere else.
 */
class ImageLoads {
    private val _done = MutableStateFlow<Set<String>>(emptySet())
    val done: StateFlow<Set<String>> = _done.asStateFlow()

    fun finished(key: String) = _done.update { it + key }
}

val LocalImageLoads = staticCompositionLocalOf<ImageLoads?> { null }
