package com.aviharez.stamp.ui.screens.stamped

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aviharez.stamp.data.repo.AlbumRepository
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumSummary
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.usecase.Streak
import com.aviharez.stamp.ui.navigation.StampedRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StampedUiState(
    val stamp: Stamp? = null,
    val numberInAlbum: Int = 0,
    val streak: Streak = Streak.None,
    val albums: List<AlbumSummary> = emptyList(),
) {
    val album: AlbumSummary? get() = albums.firstOrNull { it.album.id == stamp?.albumId }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StampedViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val stamps: StampRepository,
    private val albums: AlbumRepository,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val stampId = savedState.toRoute<StampedRoute>().stampId

    private val stamp = stamps.observe(stampId)

    val state: StateFlow<StampedUiState> = combine(
        stamp,
        // Re-query when the stamp moves album (the number depends on it).
        stamp.filterNotNull().flatMapLatest { stamps.observeNumberInAlbum(stampId) },
        stamps.observeStreak(),
        albums.observeSummaries(),
    ) { stamp, number, streak, albumList ->
        StampedUiState(stamp, number, streak, albumList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StampedUiState())

    /** The "write on the back" text; the field owns it, saves are debounced. */
    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption.asStateFlow()
    private var captionSave: Job? = null

    init {
        viewModelScope.launch {
            _caption.value = stamp.filterNotNull().first().caption.orEmpty()
        }
    }

    fun onCaptionChange(text: String) {
        val clipped = text.take(CaptionMaxLength)
        _caption.value = clipped
        captionSave?.cancel()
        // App scope: a save that is pending when the user leaves still lands.
        captionSave = appScope.launch {
            delay(CaptionDebounceMillis)
            stamps.setCaption(stampId, clipped)
        }
    }

    fun moveToAlbum(albumId: Long) {
        appScope.launch { stamps.moveToAlbum(stampId, albumId) }
    }

    fun createAlbumAndMove(name: String, category: AlbumCategory, cover: AlbumCoverColor) {
        appScope.launch {
            val id = albums.create(name, category, cover)
            stamps.moveToAlbum(stampId, id)
        }
    }

    /** Throws the stamp away (row + files), then [then] reopens the camera (or the next import). */
    fun retake(then: () -> Unit) {
        captionSave?.cancel()
        viewModelScope.launch {
            stamps.delete(stampId)
            then()
        }
    }

    private companion object {
        const val CaptionMaxLength = 80
        const val CaptionDebounceMillis = 400L
    }
}
