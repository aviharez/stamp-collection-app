package com.aviharez.stamp.ui.screens.importer

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aviharez.stamp.data.billing.EdgeAccess
import com.aviharez.stamp.data.image.ImportedPhoto
import com.aviharez.stamp.data.image.NormRect
import com.aviharez.stamp.data.image.PhotoImporter
import com.aviharez.stamp.data.image.StampRenderer
import com.aviharez.stamp.data.image.StampStorage
import com.aviharez.stamp.data.location.PlaceProvider
import com.aviharez.stamp.data.repo.NewStamp
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.data.settings.SettingsRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.Place
import com.aviharez.stamp.domain.model.StampFilter
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.domain.model.StampSource
import com.aviharez.stamp.ui.navigation.ImportRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class ImportUiState(
    val loading: Boolean = true,
    /** The picked file couldn't be read or isn't an image we can decode. */
    val failed: Boolean = false,
    /** Not enough free space to copy the photo or save the stamp. */
    val storageFull: Boolean = false,
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
    /** Framing an existing stamp again: no "another photo", and saving replaces its picture. */
    val reframing: Boolean = false,
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val importer: PhotoImporter,
    private val renderer: StampRenderer,
    private val storage: StampStorage,
    private val stamps: StampRepository,
    private val places: PlaceProvider,
    private val settings: SettingsRepository,
    private val clock: Clock,
    private val edges: EdgeAccess,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val route = savedState.toRoute<ImportRoute>()
    private val reframeId = route.reframeId

    private val _state = MutableStateFlow(ImportUiState(day = LocalDate.now(clock), reframing = reframeId != null))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var photo: ImportedPhoto? = null
    private var kept = false

    /** Reframing keeps the stamp's filter; a new import starts as Original. */
    private var reframeFilter = StampFilter.Original

    init {
        viewModelScope.launch { if (reframeId != null) loadStamp(reframeId) else load() }
    }

    /** Re-frame: the stamp's own original, its shape, day and place. The original is never deleted. */
    private suspend fun loadStamp(id: String) {
        kept = true
        val stamp = stamps.get(id)
        val original = stamp?.originalPath?.let(::File)?.takeIf { it.isFile }
        val preview = original?.let { withContext(Dispatchers.Default) { importer.decodePreview(it) } }
        if (stamp == null || original == null || preview == null) {
            _state.update { it.copy(loading = false, failed = true) }
            return
        }
        reframeFilter = stamp.filter
        photo = ImportedPhoto(
            id = id,
            original = original,
            width = preview.width,
            height = preview.height,
            takenAt = stamp.takenAt,
            localTakenAt = null,
            latitude = null,
            longitude = null,
        )
        _state.update {
            it.copy(
                loading = false,
                photo = preview.asImageBitmap(),
                shape = savedShape() ?: stamp.shape,
                quarterTurns = savedState[KeyTurns] ?: 0,
                perforation = stamp.perforation,
                day = stamp.day,
                place = stamp.place,
            )
        }
    }

    private suspend fun load() {
        // Back after Android closed the app: reuse the copy made the first time.
        val restored = savedState.get<String>(KeyCopy)?.let { path ->
            importer.reopen(File(path), savedState.get<Long>(KeyTakenAt)?.let(Instant::ofEpochMilli))
        }
        if (restored == null && !storage.hasRoomForStamp()) {
            _state.update { it.copy(loading = false, storageFull = true) }
            return
        }
        val imported = restored ?: importer.import(route.uri.toUri())
        val preview = imported?.let { withContext(Dispatchers.Default) { importer.decodePreview(it.original) } }
        if (imported == null || preview == null) {
            imported?.original?.delete()
            _state.update { it.copy(loading = false, failed = true) }
            return
        }
        photo = imported
        savedState[KeyCopy] = imported.original.path
        savedState[KeyTakenAt] = imported.takenAt?.toEpochMilli()
        val today = LocalDate.now(clock)
        // A clock set wrong on the camera can put photos in the future: count those as today.
        val day = imported.localTakenAt?.toLocalDate()?.takeIf { !it.isAfter(today) } ?: today
        _state.update {
            it.copy(
                loading = false,
                photo = preview.asImageBitmap(),
                shape = savedShape() ?: initialShape(imported.width, imported.height),
                quarterTurns = savedState[KeyTurns] ?: 0,
                perforation = edges.edgeForNewStamps.first(),
                day = day,
            )
        }
        _state.update { it.copy(place = placeFor(imported, day == today)) }
    }

    /**
     * The photo's own GPS when it still has it; for a photo from today, where the phone is now
     * (only if location is already allowed); otherwise date-only. Always date-only when place on
     * postmarks is turned off on Profile.
     */
    private suspend fun placeFor(photo: ImportedPhoto, isToday: Boolean): Place {
        if (!settings.postmarkPlace()) return Place.Unknown
        val lat = photo.latitude
        val lon = photo.longitude
        return when {
            lat != null && lon != null -> places.placeAt(lat, lon)
            isToday && places.hasPermission() -> places.current()
            else -> Place.Unknown
        }
    }

    private fun savedShape(): StampShape? = savedState.get<String>(KeyShape)?.let(StampShape::valueOf)

    fun selectShape(shape: StampShape) {
        savedState[KeyShape] = shape.name
        _state.update { it.copy(shape = shape) }
    }

    fun rotate() {
        val turns = (_state.value.quarterTurns + 1) % 4
        savedState[KeyTurns] = turns
        _state.update { it.copy(quarterTurns = turns) }
    }

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
        _state.update { it.copy(saving = true, storageFull = false) }
        val output = storage.renderedFile(picked.id, version = reframeId?.let { clock.millis() })
        viewModelScope.launch {
            if (!storage.hasRoomForStamp()) {
                _state.update { it.copy(saving = false, storageFull = true) }
                return@launch
            }
            try {
                val rendered = withContext(Dispatchers.Default) {
                    renderer.renderImport(
                        original = picked.original,
                        window = window,
                        quarterTurns = snapshot.quarterTurns,
                        shape = snapshot.shape,
                        perforation = snapshot.perforation,
                        frameShortDp = frameShortDp,
                        filter = reframeFilter,
                        output = output,
                    )
                }
                if (reframeId != null) {
                    stamps.replaceImage(reframeId, rendered, reframeFilter, snapshot.perforation)
                    _state.update { it.copy(saving = false, saved = reframeId) }
                    return@launch
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
                        renderSpec = rendered.spec,
                    ),
                )
                kept = true
                _state.update { it.copy(saving = false, saved = id) }
            } catch (e: CancellationException) {
                withContext(NonCancellable + Dispatchers.IO) { output.delete() }
                throw e
            } catch (e: Exception) {
                // A photo that won't decode, or a disk that filled up mid-way: drop the half-made PNG.
                Log.w(TAG, "Render failed", e)
                withContext(Dispatchers.IO) { output.delete() }
                val full = !storage.hasRoomForStamp()
                _state.update { it.copy(saving = false, storageFull = full, failed = !full) }
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
        const val KeyCopy = "copy"
        const val KeyTakenAt = "takenAt"
        const val KeyShape = "shape"
        const val KeyTurns = "turns"

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
