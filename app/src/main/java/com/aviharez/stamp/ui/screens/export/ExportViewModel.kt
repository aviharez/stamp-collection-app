package com.aviharez.stamp.ui.screens.export

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviharez.stamp.data.ads.AdOffer
import com.aviharez.stamp.data.ads.AdResult
import com.aviharez.stamp.data.ads.RewardedAds
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    /** Null = the stamp on its own; else sent as this postcard look. */
    val postcard: PostcardStyle? = null,
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
    val ad: AdOffer = AdOffer.Idle,
    /** A rewarded ad was watched: this stamp exports without the watermark while the screen is open. */
    val adUnlocked: Boolean = false,
    /** The stamp being shared (drawn by Compose for the postcard looks). */
    val stamp: Stamp? = null,
    val text: PostcardText = PostcardText("", ""),
) {
    val watermark: Boolean get() = !isPro && !adUnlocked
}

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
    private val ads: RewardedAds,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val stampId = MutableStateFlow<String?>(null)
    private val stamp: Flow<Stamp> = stampId.filterNotNull().flatMapLatest { stamps.observe(it) }.filterNotNull()
    // Choices and a watched ad survive Android closing the app in the background (e.g. while sharing).
    private val choice = MutableStateFlow(
        ExportChoice(
            background = savedState.get<String>(KeyBackground)?.let(ExportBackground::valueOf) ?: ExportBackground.Transparent,
            postmark = savedState[KeyPostmark] ?: true,
            postcard = savedState.get<String>(KeyPostcard)?.let(PostcardStyle::valueOf),
        ),
    )
    private val to = savedState.getStateFlow(KeyTo, "")
    /** Null until the sender edits it: the note on the back (or the title) is the message. */
    private val message = savedState.getStateFlow<String?>(KeyMessage, null)
    private val text: Flow<PostcardText> = combine(to, message, stamp) { t, m, s ->
        PostcardText(t, m ?: s.caption ?: s.title ?: "")
    }
    private val work = MutableStateFlow(ExportUiState())
    /** The stamp a rewarded ad was watched for (only that one exports without the watermark). */
    private val unlockedFor = savedState.getMutableStateFlow<String?>(KeyUnlockedFor, null)
    private val adUnlocked: Flow<Boolean> = combine(unlockedFor, stampId) { unlocked, id -> unlocked != null && unlocked == id }
    /** No watermark when Pro, or after a watched rewarded ad. */
    private val clean: Flow<Boolean> = combine(proStatus.isPro, adUnlocked) { pro, ad -> pro || ad }
    private val isPro = proStatus.isPro

    // Postcards are drawn by Compose in the sheet; only the plain stamp needs a rendered preview.
    private val preview: Flow<ImageBitmap?> = combine(stamp, choice.map { it.copy(postcard = null) }.distinctUntilChanged(), clean) { s, c, cl -> Triple(s, c, cl) }
        .mapLatest { (s, c, cl) ->
            withContext(Dispatchers.Default) {
                runCatching { exporter.render(s, c.toOptions(cl), PreviewLongPx).asImageBitmap() }.getOrNull()
            }
        }

    val state: StateFlow<ExportUiState> = combine(
        combine(work, choice, isPro, adUnlocked, preview) { w, c, pro, ad, p -> w.copy(choice = c, isPro = pro, adUnlocked = ad, preview = p) },
        stamp,
        text,
    ) { w, s, t -> w.copy(stamp = s, text = t) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExportUiState())

    private val _events = Channel<ExportEvent>(Channel.BUFFERED)
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

    fun bind(id: String) {
        stampId.value = id
    }

    /** Free user is looking at the watermark row: get an ad ready in case they want it. */
    fun preloadAd() = ads.preload()

    fun watchAd(activity: Activity) {
        if (work.value.ad == AdOffer.Loading) return
        work.update { it.copy(ad = AdOffer.Loading) }
        viewModelScope.launch {
            val result = ads.show(activity)
            if (result == AdResult.Rewarded) unlockedFor.value = stampId.value
            work.update {
                it.copy(
                    ad = if (result == AdResult.Unavailable) AdOffer.Unavailable else AdOffer.Idle,
                    save = if (result == AdResult.Rewarded) SaveState.Idle else it.save,
                )
            }
        }
    }

    fun setBackground(background: ExportBackground) {
        choice.update { it.copy(background = background) }
        savedState[KeyBackground] = background.name
        work.update { it.copy(save = SaveState.Idle) }
    }

    fun setPostmark(on: Boolean) {
        choice.update { it.copy(postmark = on) }
        savedState[KeyPostmark] = on
        work.update { it.copy(save = SaveState.Idle) }
    }

    fun setPostcard(style: PostcardStyle?) {
        choice.update { it.copy(postcard = style) }
        savedState[KeyPostcard] = style?.name
        work.update { it.copy(save = SaveState.Idle) }
    }

    fun setTo(value: String) {
        savedState[KeyTo] = value.take(MaxToChars)
        work.update { it.copy(save = SaveState.Idle) }
    }

    fun setMessage(value: String) {
        savedState[KeyMessage] = value.take(MaxMessageChars)
        work.update { it.copy(save = SaveState.Idle) }
    }

    /** The postcard picture is being captured in the sheet. */
    fun setBusy(sharing: Boolean) = work.update { it.copy(sharing = sharing) }

    fun failed() = work.update { it.copy(sharing = false, save = SaveState.Idle, failed = true) }

    /** A captured postcard: to the share cache, then the chooser. [bitmap] is recycled. */
    fun sharePicture(bitmap: Bitmap) {
        work.update { it.copy(sharing = true, failed = false) }
        viewModelScope.launch {
            val result = runCatching {
                val name = postcardName(stamp.first())
                val file = withContext(Dispatchers.IO) { exporter.writeToCache(bitmap, name) }
                FileProvider.getUriForFile(context, "${context.packageName}$AuthoritySuffix", file)
            }
            work.update { it.copy(sharing = false, failed = result.isFailure) }
            result.getOrNull()?.let { _events.send(ExportEvent.Share(it)) }
        }
    }

    fun savePicture(bitmap: Bitmap) {
        work.update { it.copy(save = SaveState.Saving, failed = false) }
        viewModelScope.launch {
            val result = runCatching {
                val name = postcardName(stamp.first())
                withContext(Dispatchers.IO) { exporter.saveToPictures(name) { bitmap } }
            }
            work.update { it.copy(save = if (result.isSuccess) SaveState.Saved else SaveState.Failed, failed = result.isFailure) }
        }
    }

    private fun postcardName(s: Stamp) = "postcard-${s.day}-${s.id.take(IdChars)}.png"

    fun share() {
        if (work.value.sharing) return
        work.update { it.copy(sharing = true, failed = false) }
        viewModelScope.launch {
            val result = runCatching {
                val options = choice.value.toOptions(clean.first())
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
                val options = choice.value.toOptions(clean.first())
                val s = stamp.first()
                withContext(Dispatchers.IO) { exporter.saveToPictures(s, options) }
            }
            work.update { it.copy(save = if (result.isSuccess) SaveState.Saved else SaveState.Failed, failed = result.isFailure) }
        }
    }

    private fun ExportChoice.toOptions(clean: Boolean) = ExportOptions(background, postmark, watermark = !clean)

    private companion object {
        /** Matches the FileProvider authority in AndroidManifest.xml. */
        const val AuthoritySuffix = ".exports"
        const val PreviewLongPx = 900
        const val IdChars = 8
        const val MaxToChars = 40
        const val MaxMessageChars = 140
    }
}

private const val KeyBackground = "export.background"
private const val KeyPostmark = "export.postmark"
private const val KeyPostcard = "export.postcard"
private const val KeyTo = "export.to"
private const val KeyMessage = "export.message"
private const val KeyUnlockedFor = "export.adUnlockedFor"
