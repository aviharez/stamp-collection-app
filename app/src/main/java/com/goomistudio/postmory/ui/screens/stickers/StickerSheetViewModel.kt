package com.goomistudio.postmory.ui.screens.stickers

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.goomistudio.postmory.data.billing.ProStatus
import com.goomistudio.postmory.data.repo.AlbumRepository
import com.goomistudio.postmory.data.repo.StampRepository
import com.goomistudio.postmory.domain.model.Stamp
import com.goomistudio.postmory.ui.navigation.StickerSheetRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import com.goomistudio.postmory.data.analytics.ExportAction
import com.goomistudio.postmory.data.analytics.ExportKind
import com.goomistudio.postmory.data.analytics.UsageStats
import javax.inject.Inject

data class StickerChoice(
    val look: SheetLook = SheetLook.Grid,
    val size: StickerSize = StickerSize.Medium,
    val paper: SheetPaper = SheetPaper.A4,
    val postmarks: Boolean = true,
)

/** Making the PDF: page [page] of [pages] is being drawn. */
data class PrintProgress(val page: Int, val pages: Int)

data class StickerSheetUiState(
    val loaded: Boolean = false,
    val albumName: String = "",
    val stampCount: Int = 0,
    val choice: StickerChoice = StickerChoice(),
    val pages: List<SheetPage> = emptyList(),
    val isPro: Boolean = false,
    val progress: PrintProgress? = null,
    val failed: Boolean = false,
)

/** "Print stickers" for one album: the choices, the laid-out pages and the PDF. */
@HiltViewModel
class StickerSheetViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
    albums: AlbumRepository,
    stamps: StampRepository,
    pro: ProStatus,
    private val usage: UsageStats,
) : ViewModel() {
    private val albumId = savedState.toRoute<StickerSheetRoute>().albumId

    // Choices survive Android closing the app in the background.
    private val choice = MutableStateFlow(
        StickerChoice(
            look = savedState.get<String>(KeyLook)?.let(SheetLook::valueOf) ?: SheetLook.Grid,
            size = savedState.get<String>(KeySize)?.let(StickerSize::valueOf) ?: StickerSize.Medium,
            paper = savedState.get<String>(KeyPaper)?.let(SheetPaper::valueOf) ?: defaultPaper(),
            postmarks = savedState[KeyPostmarks] ?: true,
        ),
    )
    private val work = MutableStateFlow(Work())

    private data class Work(val progress: PrintProgress? = null, val failed: Boolean = false)

    val state: StateFlow<StickerSheetUiState> = combine(
        albums.observe(albumId),
        stamps.observeByAlbum(albumId),
        choice,
        pro.isPro,
        work,
    ) { album, newestFirst, c, isPro, w ->
        // Album order (oldest first), as on its pages.
        val printable = newestFirst.asReversed().filter { File(it.imagePath).exists() }
        StickerSheetUiState(
            loaded = true,
            albumName = album?.name.orEmpty(),
            stampCount = printable.size,
            choice = c,
            pages = StickerSheets.layout(printable, c.look, c.size, c.paper),
            isPro = isPro,
            progress = w.progress,
            failed = w.failed,
        )
    }.flowOn(Dispatchers.IO) // checks every picture file
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StickerSheetUiState())

    fun setLook(look: SheetLook) = change { it.copy(look = look) }
    fun setSize(size: StickerSize) = change { it.copy(size = size) }
    fun setPaper(paper: SheetPaper) = change { it.copy(paper = paper) }
    fun setPostmarks(on: Boolean) = change { it.copy(postmarks = on) }

    private fun change(f: (StickerChoice) -> StickerChoice) {
        val c = choice.updateAndGet(f)
        savedState[KeyLook] = c.look.name
        savedState[KeySize] = c.size.name
        savedState[KeyPaper] = c.paper.name
        savedState[KeyPostmarks] = c.postmarks
        work.update { it.copy(failed = false) }
    }

    private var pdf: StickerPdf? = null

    fun startPdf(pages: Int) {
        pdf?.close()
        pdf = StickerPdf(choice.value.paper)
        work.value = Work(progress = PrintProgress(1, pages))
    }

    /** Adds one captured page (and lets the bitmap go). */
    suspend fun addPage(bitmap: Bitmap, pages: Int) {
        val doc = pdf ?: return
        withContext(Dispatchers.Default) {
            doc.addPage(bitmap)
            bitmap.recycle()
        }
        work.update { it.copy(progress = PrintProgress((doc.pageCount + 1).coerceAtMost(pages), pages)) }
    }

    /** Writes the PDF; null (and the error line) if anything went wrong. */
    suspend fun finishPdf(): Pair<File, Int>? {
        val doc = pdf ?: return null
        pdf = null
        val name = state.value.albumName
        val file = StickerPdf.file(context, fileName(name))
        val ok = withContext(Dispatchers.IO) {
            runCatching { doc.writeTo(file) }.isSuccess.also { doc.close() }
        }
        work.value = Work(failed = !ok)
        if (ok) usage.exported(ExportKind.Stickers, ExportAction.Print, choice.value.look.name)
        return if (ok) file to doc.pageCount else null
    }

    fun failed() {
        pdf?.close()
        pdf = null
        work.value = Work(failed = true)
    }

    override fun onCleared() {
        pdf?.close()
    }

    private fun fileName(album: String): String {
        val slug = album.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(SlugChars).ifEmpty { "album" }
        return "$slug-stickers.pdf"
    }

    private companion object {
        const val KeyLook = "stickers.look"
        const val KeySize = "stickers.size"
        const val KeyPaper = "stickers.paper"
        const val KeyPostmarks = "stickers.postmarks"
        const val SlugChars = 40

        /** Letter where it's the usual paper (US, Canada, Mexico, the Philippines, parts of Latin America). */
        val LetterRegions = setOf("US", "CA", "MX", "PH", "CL", "CO", "VE", "GT", "CR", "PR", "DO")

        fun defaultPaper(): SheetPaper = if (Locale.getDefault().country in LetterRegions) SheetPaper.Letter else SheetPaper.A4
    }
}

/** For previews and tests: the same stamps the screen would print. */
internal fun previewSheetState(stamps: List<Stamp>, choice: StickerChoice = StickerChoice()) = StickerSheetUiState(
    loaded = true,
    albumName = "Kyoto trip",
    stampCount = stamps.size,
    choice = choice,
    pages = StickerSheets.layout(stamps, choice.look, choice.size, choice.paper),
)
