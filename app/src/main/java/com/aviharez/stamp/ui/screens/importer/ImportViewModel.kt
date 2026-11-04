package com.aviharez.stamp.ui.screens.importer

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aviharez.stamp.data.image.ImportedPhoto
import com.aviharez.stamp.data.image.NormRect
import com.aviharez.stamp.data.image.PhotoImporter
import com.aviharez.stamp.data.image.StampRenderer
import com.aviharez.stamp.data.image.StampStorage
import com.aviharez.stamp.data.location.PlaceProvider
import com.aviharez.stamp.data.repo.NewStamp
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.Place
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.domain.model.StampSource
import com.aviharez.stamp.ui.navigation.ImportRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class ImportUiState(
    val loading: Boolean = true,
    /** The picked file couldn't be read or isn't an image we can decode. */
    val failed: Boolean = false,
    /** Upright preview (long side ≤ 2048px). */
    val photo: ImageBitmap? = null,
    /** Clockwise quarter turns applied by the rotate button. */
    val quarterTurns: Int = 0,
    val shape: StampShape = StampShape.Portrait,
    val perforation: PerforationStyle = PerforationStyle.Classic,
    /** The photo's own day (postmark + calendar). */
    val day: LocalDate = LocalDate.now(),
    val place: Place = Place.Unknown,
    val saving: Boolean = false,
    /** Id of the saved stamp; the screen navigates to Stamped! with it. */
    val saved: String? = null,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val importer: PhotoImporter,
    private val renderer: StampRenderer,
    private val storage: StampStorage,
    private val stamps: StampRepository,
    private val places: PlaceProvider,
    private val clock: Clock,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val uri = savedState.toRoute<ImportRoute>().uri.toUri()

    private val _state = MutableStateFlow(ImportUiState(day = LocalDate.now(clock)))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var photo: ImportedPhoto? = null
    private var kept = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val imported = importer.import(uri)
        val preview = imported?.let { withContext(Dispatchers.Default) { importer.decodePreview(it.original) } }
        if (imported == null || preview == null) {
            imported?.original?.delete()
            _state.update { it.copy(loading = false, failed = true) }
            return
        }
        photo = imported
        val today = LocalDate.now(clock)
        // A clock set wrong on the camera can put photos in the future: count those as today.
        val day = imported.localTakenAt?.toLocalDate()?.takeIf { !it.isAfter(today) } ?: today
        _state.update {
            it.copy(
                loading = false,
                photo = preview.asImageBitmap(),
                shape = initialShape(imported.width, imported.height),
                day = day,
            )
        }
        _state.update { it.copy(place = placeFor(imported, day == today)) }
    }

    /**
     * The photo's own GPS when it still has it; for a photo from today, where the phone is now
     * (only if location is already allowed); otherwise date-only.
     */
    private suspend fun placeFor(photo: ImportedPhoto, isToday: Boolean): Place {
        val lat = photo.latitude
        val lon = photo.longitude
        return when {
            lat != null && lon != null -> places.placeAt(lat, lon)
            isToday && places.hasPermission() -> places.current()
            else -> Place.Unknown
        }
    }

    fun selectShape(shape: StampShape) = _state.update { it.copy(shape = shape) }

    fun rotate() = _state.update { it.copy(quarterTurns = (it.quarterTurns + 1) % 4) }

    /**
     * Renders what is inside the frame and saves it into the last-used album.
     *
     * @param window the frame's picture area as a fraction of the turned photo.
     * @param frameShortDp short side of the on-screen frame (export scales perforations from it).
     */
    fun makeStamp(window: NormRect, frameShortDp: Float) {
        val picked = photo ?: return
        val snapshot = _state.value
        if (snapshot.saving || snapshot.saved != null) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val rendered = withContext(Dispatchers.Default) {
                    renderer.renderImport(
                        original = picked.original,
                        window = window,
                        quarterTurns = snapshot.quarterTurns,
                        shape = snapshot.shape,
                        perforation = snapshot.perforation,
                        frameShortDp = frameShortDp,
                        output = storage.renderedFile(picked.id),
                    )
                }
                val now = clock.instant()
                val id = stamps.add(
                    NewStamp(
                        id = picked.id,
                        shape = rendered.shape,
                        perforation = snapshot.perforation,
                        imageFile = rendered.file,
                        originalFile = picked.original,
                        place = snapshot.place,
                        source = StampSource.Gallery,
                        takenAt = picked.takenAt?.takeIf { !it.isAfter(now) } ?: now,
                        day = snapshot.day,
                    ),
                )
                kept = true
                _state.update { it.copy(saving = false, saved = id) }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Render failed", e)
                _state.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    /** Call after navigating to Stamped! so the event is not handled twice. */
    fun onSavedHandled() = _state.update { it.copy(saved = null) }

    override fun onCleared() {
        // Closed without making a stamp: drop the copied photo.
        val leftover = photo?.original?.takeIf { !kept } ?: return
        appScope.launch(Dispatchers.IO) { leftover.delete() }
    }

    private companion object {
        const val TAG = "ImportViewModel"
        const val SquareTolerance = 1.1f

        /** Start with the shape that fits the photo best. */
        fun initialShape(width: Int, height: Int): StampShape {
            val aspect = width.toFloat() / height
            return when {
                aspect > SquareTolerance -> StampShape.Landscape
                aspect < 1f / SquareTolerance -> StampShape.Portrait
                else -> StampShape.Square
            }
        }
    }
}
