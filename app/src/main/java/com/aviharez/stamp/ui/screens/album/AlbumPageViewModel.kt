package com.aviharez.stamp.ui.screens.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aviharez.stamp.data.repo.AlbumRepository
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.data.settings.SettingsRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.domain.model.Album
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumPages
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.ui.navigation.AlbumPageRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlbumPageUiState(
    val loaded: Boolean = false,
    val album: Album? = null,
    /** Pages of up to 9 stamps, oldest first; the last page has room for the next stamp. */
    val pages: List<List<Stamp>> = listOf(emptyList()),
    val stampCount: Int = 0,
    val dayCount: Int = 0,
)

@HiltViewModel
class AlbumPageViewModel @Inject constructor(
    savedState: SavedStateHandle,
    stamps: StampRepository,
    private val albums: AlbumRepository,
    private val settings: SettingsRepository,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    val albumId = savedState.toRoute<AlbumPageRoute>().albumId

    val state: StateFlow<AlbumPageUiState> = combine(
        albums.observe(albumId),
        stamps.observeByAlbum(albumId),
    ) { album, newestFirst ->
        AlbumPageUiState(
            loaded = true,
            album = album,
            pages = AlbumPages.paginate(newestFirst.asReversed()),
            stampCount = newestFirst.size,
            dayCount = newestFirst.distinctBy { it.day }.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumPageUiState())

    fun edit(name: String, category: AlbumCategory, cover: AlbumCoverColor) {
        appScope.launch { albums.edit(albumId, name, category, cover) }
    }

    /** The next capture goes into this album; then [then] opens the camera. */
    fun takeNextStamp(then: () -> Unit) {
        viewModelScope.launch {
            settings.setLastAlbumId(albumId)
            then()
        }
    }
}
