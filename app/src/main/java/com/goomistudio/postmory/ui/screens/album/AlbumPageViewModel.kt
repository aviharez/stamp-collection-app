package com.goomistudio.postmory.ui.screens.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.goomistudio.postmory.data.billing.ProStatus
import com.goomistudio.postmory.data.repo.AlbumRepository
import com.goomistudio.postmory.data.repo.ScrapbookRepository
import com.goomistudio.postmory.data.repo.StampRepository
import com.goomistudio.postmory.data.settings.SettingsRepository
import com.goomistudio.postmory.di.AppScope
import com.goomistudio.postmory.domain.model.Album
import com.goomistudio.postmory.domain.model.AlbumCategory
import com.goomistudio.postmory.domain.model.AlbumCoverColor
import com.goomistudio.postmory.domain.model.AlbumPages
import com.goomistudio.postmory.domain.model.ScrapPage
import com.goomistudio.postmory.domain.model.Stamp
import com.goomistudio.postmory.ui.navigation.AlbumPageRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One page of an album, as the pager shows it. */
sealed interface AlbumLeaf {
    /** Up to 9 stamps; [isLast] = the last grid page, which keeps a "next stamp…" slot. */
    data class Grid(val stamps: List<Stamp>, val isLast: Boolean) : AlbumLeaf

    /** A scrapbook page, with the stamps its pieces show. */
    data class Scrap(val page: ScrapPage, val stamps: Map<String, Stamp>) : AlbumLeaf
}

data class AlbumPageUiState(
    val loaded: Boolean = false,
    val album: Album? = null,
    /**
     * Grid pages of up to 9 stamps, oldest first, with scrapbook pages tucked in after the grid
     * page holding the newest stamp made before them (before the first page if none was).
     */
    val leaves: List<AlbumLeaf> = listOf(AlbumLeaf.Grid(emptyList(), isLast = true)),
    val stampCount: Int = 0,
    val dayCount: Int = 0,
    val isPro: Boolean = false,
) {
    /** The album opens on its newest grid page. */
    val openIndex: Int get() = leaves.indexOfLast { it is AlbumLeaf.Grid }.coerceAtLeast(0)
}

@HiltViewModel
class AlbumPageViewModel @Inject constructor(
    savedState: SavedStateHandle,
    stamps: StampRepository,
    scrapbook: ScrapbookRepository,
    pro: ProStatus,
    private val albums: AlbumRepository,
    private val settings: SettingsRepository,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    val albumId = savedState.toRoute<AlbumPageRoute>().albumId

    /** Scrapbook pages + every stamp they show (a stamp may since have moved to another album). */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scraps: Flow<Pair<List<ScrapPage>, Map<String, Stamp>>> = scrapbook.observeByAlbum(albumId).flatMapLatest { list ->
        val ids = list.flatMapTo(HashSet()) { it.stampIds }
        stamps.observeByIds(ids).map { found -> list to found.associateBy { it.id } }
    }

    val state: StateFlow<AlbumPageUiState> = combine(
        albums.observe(albumId),
        stamps.observeByAlbum(albumId),
        scraps,
        pro.isPro,
    ) { album, newestFirst, (pages, scrapStamps), isPro ->
        AlbumPageUiState(
            loaded = true,
            album = album,
            leaves = leaves(newestFirst.asReversed(), pages, scrapStamps),
            stampCount = newestFirst.size,
            dayCount = newestFirst.distinctBy { it.day }.size,
            isPro = isPro,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumPageUiState())

    private fun leaves(oldestFirst: List<Stamp>, scrapPages: List<ScrapPage>, scrapStamps: Map<String, Stamp>): List<AlbumLeaf> {
        val grids = AlbumPages.paginate(oldestFirst)
        // Grid page index each scrap page follows (-1 = before the first one).
        val after = scrapPages.groupBy { page ->
            val i = oldestFirst.indexOfLast { !it.takenAt.isAfter(page.createdAt) }
            if (i < 0) -1 else i / AlbumPages.SlotsPerPage
        }
        val out = ArrayList<AlbumLeaf>(grids.size + scrapPages.size)
        fun scrapsAfter(i: Int) = after[i].orEmpty().mapTo(out) { AlbumLeaf.Scrap(it, scrapStamps) }
        scrapsAfter(-1)
        grids.forEachIndexed { i, g ->
            out += AlbumLeaf.Grid(g, isLast = i == grids.lastIndex)
            scrapsAfter(i)
        }
        // A page placed after a grid page that no longer exists (stamps moved away) goes last.
        after.filterKeys { it > grids.lastIndex }.values.flatten().mapTo(out) { AlbumLeaf.Scrap(it, scrapStamps) }
        return out
    }

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
