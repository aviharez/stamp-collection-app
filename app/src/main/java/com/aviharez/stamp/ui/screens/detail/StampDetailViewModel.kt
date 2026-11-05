package com.aviharez.stamp.ui.screens.detail

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aviharez.stamp.data.ads.RewardedAds
import com.aviharez.stamp.data.billing.EdgeAccess
import com.aviharez.stamp.data.billing.ProRules
import com.aviharez.stamp.data.billing.ProStatus
import com.aviharez.stamp.data.image.StampFilterer
import com.aviharez.stamp.data.repo.AlbumRepository
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumSummary
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.model.StampFilter
import com.aviharez.stamp.ui.navigation.StampDetailRoute
import com.aviharez.stamp.ui.screens.filter.FilterSession
import com.aviharez.stamp.ui.screens.filter.FilterUi
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
import java.io.File
import javax.inject.Inject

data class StampDetailUiState(
    val loaded: Boolean = false,
    /** Null once loaded = the stamp is gone (deleted); the screen closes. */
    val stamp: Stamp? = null,
    val numberInAlbum: Int = 0,
    val albums: List<AlbumSummary> = emptyList(),
    val isPro: Boolean = false,
    val bonusAlbums: Int = 0,
    /** The full-quality original is still there, so the stamp can be framed again. */
    val canReframe: Boolean = false,
    /** The rendered picture is there to export (a stamp whose PNG is gone can't be shared). */
    val canShare: Boolean = false,
) {
    val album: AlbumSummary? get() = albums.firstOrNull { it.album.id == stamp?.albumId }
    val canCreateAlbum: Boolean get() = ProRules.canCreateAlbum(isPro, albums.size, bonusAlbums)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StampDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val stamps: StampRepository,
    private val albums: AlbumRepository,
    proStatus: ProStatus,
    filterer: StampFilterer,
    edges: EdgeAccess,
    ads: RewardedAds,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    val stampId = savedState.toRoute<StampDetailRoute>().stampId
    private val stamp = stamps.observe(stampId)

    private val filters = FilterSession(filterer, edges, ads, viewModelScope, appScope, stamp)
    val filter: StateFlow<FilterUi> = filters.state

    fun pickFilter(filter: StampFilter) = filters.pickFilter(filter)

    fun pickEdge(edge: PerforationStyle) = filters.pickEdge(edge)

    fun preloadEdgeAd() = filters.preloadAd()

    fun watchEdgeAd(activity: Activity) = filters.watchEdgeAd(activity)

    /** The tray closed: a Pro filter that was only tried on goes away. */
    fun closeFilters() = filters.revert()

    val state: StateFlow<StampDetailUiState> = combine(
        stamp,
        stamp.filterNotNull().flatMapLatest { stamps.observeNumberInAlbum(stampId) },
        albums.observeSummaries(),
        combine(proStatus.isPro, proStatus.bonusAlbums, ::Pair),
    ) { stamp, number, albumList, (pro, bonus) ->
        StampDetailUiState(
            loaded = true,
            stamp = stamp,
            numberInAlbum = number,
            albums = albumList,
            isPro = pro,
            bonusAlbums = bonus,
            canReframe = stamp != null && File(stamp.originalPath).isFile,
            canShare = stamp != null && File(stamp.imagePath).isFile,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StampDetailUiState())

    /** The fields own their text; saves are debounced in the app scope so a pending one still lands. */
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()
    private val _caption = MutableStateFlow("")
    val caption: StateFlow<String> = _caption.asStateFlow()
    private var titleSave: Job? = null
    private var captionSave: Job? = null

    init {
        viewModelScope.launch {
            val s = stamp.filterNotNull().first()
            _title.value = s.title.orEmpty()
            _caption.value = s.caption.orEmpty()
        }
    }

    fun onTitleChange(text: String) {
        val clipped = text.take(TitleMaxLength)
        _title.value = clipped
        titleSave?.cancel()
        titleSave = appScope.launch {
            delay(DebounceMillis)
            stamps.setTitle(stampId, clipped)
        }
    }

    fun onCaptionChange(text: String) {
        val clipped = text.take(CaptionMaxLength)
        _caption.value = clipped
        captionSave?.cancel()
        captionSave = appScope.launch {
            delay(DebounceMillis)
            stamps.setCaption(stampId, clipped)
        }
    }

    fun moveToAlbum(albumId: Long) {
        appScope.launch { stamps.moveToAlbum(stampId, albumId) }
    }

    fun createAlbumAndMove(name: String, category: AlbumCategory, cover: AlbumCoverColor) {
        appScope.launch { stamps.moveToAlbum(stampId, albums.create(name, category, cover)) }
    }

    /** Row and both picture files; pending text saves are dropped. */
    fun delete() {
        filters.cancel()
        titleSave?.cancel()
        captionSave?.cancel()
        appScope.launch { stamps.delete(stampId) }
    }

    private companion object {
        const val TitleMaxLength = 40
        /** Same limit as on Stamped!. */
        const val CaptionMaxLength = 80
        const val DebounceMillis = 400L
    }
}
