package com.goomistudio.postmory.ui.screens.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.goomistudio.postmory.data.map.WorldMapRepository
import com.goomistudio.postmory.data.map.WorldOutline
import com.goomistudio.postmory.data.repo.StampRepository
import com.goomistudio.postmory.data.settings.SettingsRepository
import com.goomistudio.postmory.ui.navigation.MapCountryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    /** False until both the stamps and the bundled map are read (nothing flashes "empty"). */
    val loaded: Boolean = false,
    val outline: WorldOutline? = null,
    val countries: List<MapCountry> = emptyList(),
    /** Stamps whose postmark has no country. */
    val unplaced: Int = 0,
    /** "Place on postmarks" is on (new stamps can land on the map). */
    val placeOn: Boolean = true,
) {
    /** Every place, newest stamp first. */
    val places: List<MapPlace> by lazy { countries.flatMap { it.places }.sortedByDescending { it.newest.takenAt } }
    val visited: Set<String> by lazy { outline?.let { countries.codesOn(it) }.orEmpty() }
    val stampCount: Int get() = countries.sumOf { it.stampCount }
}

private fun StampRepository.grouped(): Flow<List<MapCountry>> =
    observeWithCountry().map(::groupByPlace).flowOn(Dispatchers.Default)

private fun WorldMapRepository.asFlow(): Flow<WorldOutline> = flow { emit(outline()) }

@HiltViewModel
class MapViewModel @Inject constructor(
    stamps: StampRepository,
    world: WorldMapRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<MapUiState> = combine(
        world.asFlow(),
        stamps.grouped(),
        stamps.observeWithoutCountryCount(),
        settings.postmarkPlace,
    ) { outline, countries, unplaced, placeOn ->
        MapUiState(loaded = true, outline = outline, countries = countries, unplaced = unplaced, placeOn = placeOn)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())
}

data class CountryUiState(
    val loaded: Boolean = false,
    val code: String,
    val outline: WorldOutline? = null,
    /** Null once loaded = no stamps from this country any more (deleted or moved). */
    val country: MapCountry? = null,
)

@HiltViewModel
class CountryViewModel @Inject constructor(
    private val stamps: StampRepository,
    world: WorldMapRepository,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val code = savedState.toRoute<MapCountryRoute>().code

    val state: StateFlow<CountryUiState> = combine(world.asFlow(), stamps.grouped()) { outline, countries ->
        CountryUiState(loaded = true, code = code, outline = outline, country = countries.firstOrNull { it.code == code })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CountryUiState(code = code))

    /** Renames [place] on all of its stamps; a name another place has merges the two. */
    fun renamePlace(place: MapPlace, name: String) {
        val ids = place.stamps.map { it.id }
        viewModelScope.launch { stamps.renamePlace(ids, name) }
    }
}
