package com.goomistudio.postmory.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goomistudio.postmory.data.repo.AlbumRepository
import com.goomistudio.postmory.data.repo.StampRepository
import com.goomistudio.postmory.domain.model.AlbumPages
import com.goomistudio.postmory.domain.model.AlbumSummary
import com.goomistudio.postmory.domain.model.Stamp
import com.goomistudio.postmory.domain.usecase.Streak
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class HomeUiState(
    val loaded: Boolean = false,
    /** Newest stamps of the album the newest stamp is in, newest first (at most [HomePageStamps]). */
    val pageStamps: List<Stamp> = emptyList(),
    val pageAlbum: AlbumSummary? = null,
    val streak: Streak = Streak.None,
    /** The newest stamp, when it was made today. */
    val today: Stamp? = null,
) {
    /** Page of [pageAlbum] the newest stamp sits on ("p.3"). */
    val pageNumber: Int get() = AlbumPages.latestPage(pageAlbum?.stampCount ?: 0)
}

const val HomePageStamps = 4

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    stamps: StampRepository,
    albums: AlbumRepository,
    private val clock: Clock,
) : ViewModel() {

    private val newest = stamps.observeRecent(1).map { it.firstOrNull() }

    private val page = newest.flatMapLatest { latest ->
        if (latest == null) flowOf(emptyList()) else stamps.observeByAlbum(latest.albumId).map { it.take(HomePageStamps) }
    }

    val state: StateFlow<HomeUiState> = combine(
        newest,
        page,
        albums.observeSummaries(),
        stamps.observeStreak(),
    ) { latest, pageStamps, summaries, streak ->
        HomeUiState(
            loaded = true,
            pageStamps = pageStamps,
            pageAlbum = latest?.let { l -> summaries.firstOrNull { it.album.id == l.albumId } },
            streak = streak,
            today = latest?.takeIf { it.day == LocalDate.now(clock) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
