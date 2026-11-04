package com.aviharez.stamp.ui.screens.export

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviharez.stamp.data.billing.ProStatus
import com.aviharez.stamp.data.image.ExportBackground
import com.aviharez.stamp.data.image.ExportOptions
import com.aviharez.stamp.data.image.StampExporter
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.domain.model.Stamp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** What the user picks in the sheet; the watermark follows Pro, not a choice. */
data class ExportChoice(
    val background: ExportBackground = ExportBackground.Transparent,
    val postmark: Boolean = true,
)

enum class SaveState { Idle, Saving, Saved, Failed }

data class ExportUiState(
    val choice: ExportChoice = ExportChoice(),
    val isPro: Boolean = false,
    /** Downscaled render of exactly what will be exported; null while the first one is made. */
    val preview: ImageBitmap? = null,
    val sharing: Boolean = false,
    val save: SaveState = SaveState.Idle,
    val failed: Boolean = false,
)

sealed interface ExportEvent {
    data class Share(val uri: Uri) : ExportEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExportViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val stamps: StampRepository,
    private val exporter: StampExporter,
    proStatus: ProStatus,
) : ViewModel() {

    private val stampId = MutableStateFlow<String?>(null)
    private val stamp: Flow<Stamp> = stampId.filterNotNull().flatMapLatest { stamps.observe(it) }.filterNotNull()
    private val choice = MutableStateFlow(ExportChoice())
    private val isPro = proStatus.isPro
    private val work = MutableStateFlow(ExportUiState())

    private val preview: Flow<ImageBitmap?> = combine(stamp, choice, isPro) { s, c, pro -> Triple(s, c, pro) }
        .mapLatest { (s, c, pro) ->
            withContext(Dispatchers.Default) {
                runCatching { exporter.render(s, c.toOptions(pro), PreviewLongPx).asImageBitmap() }.getOrNull()
            }
        }

    val state: StateFlow<ExportUiState> = combine(work, choice, isPro, preview) { w, c, pro, p ->
        w.copy(choice = c, isPro = pro, preview = p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState())

    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

    fun bind(id: String) {
        stampId.value = id
    }

    fun setBackground(background: ExportBackground) {
        choice.update { it.copy(background = background) }
        work.update { it.copy(save = SaveState.Idle) }
    }

    fun setPostmark(on: Boolean) {
        choice.update { it.copy(postmark = on) }
        work.update { it.copy(save = SaveState.Idle) }
    }

    fun share() {
        if (work.value.sharing) return
        work.update { it.copy(sharing = true, failed = false) }
        viewModelScope.launch {
            val result = runCatching {
                val options = choice.value.toOptions(isPro.first())
                val s = stamp.first()
                val file = withContext(Dispatchers.IO) { exporter.exportToCache(s, options) }
                FileProvider.getUriForFile(context, "${context.packageName}$AuthoritySuffix", file)
            }
            work.update { it.copy(sharing = false, failed = result.isFailure) }
            result.getOrNull()?.let { _events.send(ExportEvent.Share(it)) }
        }
    }

    fun saveToPhotos() {
        if (work.value.save == SaveState.Saving) return
        work.update { it.copy(save = SaveState.Saving, failed = false) }
        viewModelScope.launch {
            val result = runCatching {
                val options = choice.value.toOptions(isPro.first())
                val s = stamp.first()
                withContext(Dispatchers.IO) { exporter.saveToPictures(s, options) }
            }
            work.update { it.copy(save = if (result.isSuccess) SaveState.Saved else SaveState.Failed, failed = result.isFailure) }
        }
    }

    private fun ExportChoice.toOptions(pro: Boolean) = ExportOptions(background, postmark, watermark = !pro)

    private companion object {
        /** Matches the FileProvider authority in AndroidManifest.xml. */
        const val AuthoritySuffix = ".exports"
        const val PreviewLongPx = 900
    }
}
