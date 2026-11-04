package com.aviharez.stamp.ui.screens.stamped

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aviharez.stamp.data.repo.AlbumRepository
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.ui.navigation.StampedRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StampedUiState(
    val stamp: Stamp? = null,
    val albumName: String? = null,
    val numberInAlbum: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StampedViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val stamps: StampRepository,
    albums: AlbumRepository,
) : ViewModel() {

    private val stampId = savedState.toRoute<StampedRoute>().stampId

    private val stamp = stamps.observe(stampId)

    val state: StateFlow<StampedUiState> = combine(
        stamp,
        stamp.filterNotNull().flatMapLatest { albums.observe(it.albumId) }.map { it?.name },
        stamps.observeNumberInAlbum(stampId),
    ) { stamp, albumName, number ->
        StampedUiState(stamp, albumName, number)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StampedUiState())

    /** Throws the stamp away (row + files), then [then] reopens the camera. */
    fun retake(then: () -> Unit) {
        viewModelScope.launch {
            stamps.delete(stampId)
            then()
        }
    }
}
