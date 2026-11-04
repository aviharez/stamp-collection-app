package com.aviharez.stamp.ui.screens.album

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aviharez.stamp.data.ads.AdOffer
import com.aviharez.stamp.data.ads.AdResult
import com.aviharez.stamp.data.ads.RewardedAds
import com.aviharez.stamp.data.billing.ProStatus
import com.aviharez.stamp.data.image.StampExporter
import com.aviharez.stamp.ui.screens.export.SaveState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PageExportUiState(
    val isPro: Boolean = false,
    /** A rewarded ad was watched: pages export without the watermark while this album is open. */
    val adUnlocked: Boolean = false,
    val ad: AdOffer = AdOffer.Idle,
    val sharing: Boolean = false,
    val save: SaveState = SaveState.Idle,
    val failed: Boolean = false,
) {
    val watermark: Boolean get() = !isPro && !adUnlocked
}

/** Share / save of one album page. The picture itself is drawn by Compose ([PageExportSheet]). */
@HiltViewModel
class PageExportViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exporter: StampExporter,
    proStatus: ProStatus,
    private val ads: RewardedAds,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    // A watched ad stays watched when Android closes the app in the background (e.g. while sharing).
    private val work = MutableStateFlow(PageExportUiState(adUnlocked = savedState[KeyUnlocked] ?: false))

    val state: StateFlow<PageExportUiState> = combine(work, proStatus.isPro) { w, pro -> w.copy(isPro = pro) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PageExportUiState())

    private val _share = Channel<Uri>(Channel.BUFFERED)
    val share: Flow<Uri> = _share.receiveAsFlow()

    /** The sheet was opened again: a new page, so the saved tick goes back to "Save". */
    fun reset() = work.update { it.copy(save = SaveState.Idle, failed = false) }

    fun preloadAd() = ads.preload()

    fun watchAd(activity: Activity) {
        if (work.value.ad == AdOffer.Loading) return
        work.update { it.copy(ad = AdOffer.Loading) }
        viewModelScope.launch {
            val result = ads.show(activity)
            if (result == AdResult.Rewarded) savedState[KeyUnlocked] = true
            work.update {
                it.copy(
                    adUnlocked = it.adUnlocked || result == AdResult.Rewarded,
                    ad = if (result == AdResult.Unavailable) AdOffer.Unavailable else AdOffer.Idle,
                    save = if (result == AdResult.Rewarded) SaveState.Idle else it.save,
                )
            }
        }
    }

    fun setBusy(sharing: Boolean) = work.update { it.copy(sharing = sharing) }

    /** Called once the page picture is captured; [bitmap] is recycled. */
    fun share(bitmap: Bitmap, name: String) {
        work.update { it.copy(sharing = true, failed = false) }
        viewModelScope.launch {
            val result = runCatching {
                val file = withContext(Dispatchers.IO) { exporter.writeToCache(bitmap, name) }
                FileProvider.getUriForFile(context, "${context.packageName}$AuthoritySuffix", file)
            }
            work.update { it.copy(sharing = false, failed = result.isFailure) }
            result.getOrNull()?.let { _share.send(it) }
        }
    }

    fun save(bitmap: Bitmap, name: String) {
        work.update { it.copy(save = SaveState.Saving, failed = false) }
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { exporter.saveToPictures(name) { bitmap } } }
            work.update { it.copy(save = if (result.isSuccess) SaveState.Saved else SaveState.Failed, failed = result.isFailure) }
        }
    }

    fun failed() = work.update { it.copy(sharing = false, save = SaveState.Idle, failed = true) }

    private companion object {
        /** Matches the FileProvider authority in AndroidManifest.xml. */
        const val AuthoritySuffix = ".exports"
    }
}

private const val KeyUnlocked = "pageExport.adUnlocked"
