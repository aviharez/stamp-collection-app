package com.aviharez.stamp.ui.screens.collections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviharez.stamp.data.billing.ProRules
import com.aviharez.stamp.data.billing.ProStatus
import com.aviharez.stamp.data.repo.AlbumRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CollectionsUiState(
    val loaded: Boolean = false,
    /** Most recently stamped first; empty albums by creation, newest first. */
    val albums: List<AlbumSummary> = emptyList(),
    /** null = "All". */
    val filter: AlbumCategory? = null,
    val today: LocalDate = LocalDate.MIN,
    val isPro: Boolean = false,
    val bonusAlbums: Int = 0,
) {
    /** Free tier keeps [ProRules.FreeAlbumLimit] (+ ad bonus) albums; a new one beyond that needs Pro. */
    val canCreateAlbum: Boolean get() = ProRules.canCreateAlbum(isPro, albums.size, bonusAlbums)

    val shown: List<AlbumSummary> get() = if (filter == null) albums else albums.filter { it.album.category == filter }
    val stampCount: Int get() = albums.sumOf { it.stampCount }

    /** Trips / Everyday / Pets always (as in the design); Other only once an album uses it. */
    val filters: List<AlbumCategory>
        get() = AlbumCategory.entries.filter { it != AlbumCategory.Other || albums.any { a -> a.album.category == it } }
}

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val albums: AlbumRepository,
    private val clock: Clock,
    proStatus: ProStatus,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val filter = MutableStateFlow<AlbumCategory?>(null)

    val state: StateFlow<CollectionsUiState> = combine(
        albums.observeSummaries(),
        filter,
        proStatus.isPro,
        proStatus.bonusAlbums,
    ) { list, f, pro, bonus ->
        CollectionsUiState(
            loaded = true,
            albums = list.sortedByDescending { it.latest?.takenAt ?: it.album.createdAt },
            filter = f,
            today = LocalDate.now(clock),
            isPro = pro,
            bonusAlbums = bonus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionsUiState())

    fun setFilter(category: AlbumCategory?) {
        filter.value = category
    }

    fun create(name: String, category: AlbumCategory, cover: AlbumCoverColor) {
        appScope.launch { albums.create(name, category, cover) }
    }
}
