package com.aviharez.stamp.ui.screens.export

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.data.ads.AdOffer
import com.aviharez.stamp.data.image.ExportBackground
import com.aviharez.stamp.data.image.StampExporter
import com.aviharez.stamp.ui.components.AdChip
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.components.dashedOutline
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** Share / save the stamp as a full-resolution PNG. Opened from Stamped! (and later stamp detail). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    stampId: String,
    onDismiss: () -> Unit,
    onGetPro: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    LaunchedEffect(stampId) { viewModel.bind(stampId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current

    LaunchedEffect(state.watermark) { if (state.watermark) viewModel.preloadAd() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = StampExporter.MimePng
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        clipData = ClipData.newRawUri(null, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
        }
    }

    // Android 8–9 need the storage permission to write into Pictures; later versions don't.
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.saveToPhotos()
    }
    val save = {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) askStorage.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else viewModel.saveToPhotos()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = StampTheme.colors.paper,
        contentColor = StampTheme.colors.textHigh,
    ) {
        ExportContent(
            state = state,
            onBackground = viewModel::setBackground,
            onPostmark = viewModel::setPostmark,
            onSave = save,
            onShare = viewModel::share,
            onGetPro = onGetPro,
            onWatchAd = { activity?.let(viewModel::watchAd) },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
fun ExportContent(
    state: ExportUiState,
    onBackground: (ExportBackground) -> Unit,
    onPostmark: (Boolean) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onGetPro: () -> Unit,
    onWatchAd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    Column(
        modifier.fillMaxWidth().padding(horizontal = s.screen).padding(bottom = s.l),
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        Text(stringResource(R.string.export_title), style = StampTheme.type.cardTitle)

        ExportPreview(state.preview, checkered = state.choice.background == ExportBackground.Transparent)

        when {
            state.isPro -> HandNote(stringResource(R.string.export_pro_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            state.adUnlocked -> HandNote(stringResource(R.string.export_unlocked_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            else -> WatermarkRow(state.ad, onGetPro, onWatchAd)
        }

        OptionRow(stringResource(R.string.export_background)) {
            PaperChip(
                stringResource(R.string.export_bg_transparent),
                selected = state.choice.background == ExportBackground.Transparent,
                onClick = { onBackground(ExportBackground.Transparent) },
            )
            PaperChip(
                stringResource(R.string.export_bg_paper),
                selected = state.choice.background == ExportBackground.Paper,
                onClick = { onBackground(ExportBackground.Paper) },
            )
        }
        OptionRow(stringResource(R.string.export_postmark)) {
            PaperChip(stringResource(R.string.export_on), selected = state.choice.postmark, onClick = { onPostmark(true) })
            PaperChip(stringResource(R.string.export_off), selected = !state.choice.postmark, onClick = { onPostmark(false) })
        }

        if (state.failed) {
            Text(stringResource(R.string.export_failed), style = StampTheme.type.label, color = c.airmailRed)
        }

        Row(Modifier.fillMaxWidth().padding(top = s.xs), horizontalArrangement = Arrangement.spacedBy(s.s)) {
            PillButton(
                text = stringResource(
                    when (state.save) {
                        SaveState.Saving -> R.string.export_saving
                        SaveState.Saved -> R.string.export_saved
                        else -> R.string.export_save
                    },
                ),
                onClick = onSave,
                enabled = state.save != SaveState.Saving && state.save != SaveState.Saved,
                style = PillStyle.Outline,
                leadingIcon = if (state.save == SaveState.Saved) R.drawable.ic_check else R.drawable.ic_download,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.export_share),
                onClick = onShare,
                enabled = !state.sharing,
                style = PillStyle.Red,
                leadingIcon = R.drawable.ic_share,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The watermark row: the two honest ways out side by side, Pro first, the ad as the quiet option. */
@Composable
private fun WatermarkRow(ad: AdOffer, onGetPro: () -> Unit, onWatchAd: () -> Unit) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    Column(
        Modifier.fillMaxWidth().dashedOutline(c.outline).padding(horizontal = s.m, vertical = s.s),
        verticalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        HandNote(
            stringResource(R.string.export_watermark_note, stringResource(R.string.app_name)),
            style = StampTheme.type.noteSmall,
            strength = InkStrength.Soft,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(s.xs), verticalAlignment = Alignment.CenterVertically) {
            PaperChip(stringResource(R.string.export_remove_with_pro), selected = true, onClick = onGetPro)
            AdChip(stringResource(R.string.export_remove_once), loading = ad == AdOffer.Loading, onClick = onWatchAd)
        }
        if (ad == AdOffer.Unavailable) {
            Text(stringResource(R.string.ad_unavailable), style = StampTheme.type.label, color = c.textMedium)
        }
    }
}

/** The real export, downscaled; a soft checkerboard behind it says "transparent". */
@Composable
private fun ExportPreview(preview: ImageBitmap?, checkered: Boolean) {
    val c = StampTheme.colors
    val description = stringResource(R.string.export_preview_cd)
    Box(
        Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .clip(StampTheme.shapes.card)
            .drawBehind {
                drawRect(c.paperWhite)
                if (checkered) {
                    val cell = CheckerCell.toPx()
                    var row = 0
                    var y = 0f
                    while (y < size.height) {
                        var x = if (row % 2 == 0) 0f else cell
                        while (x < size.width) {
                            drawRect(c.surface2, Offset(x, y), Size(cell, cell))
                            x += cell * 2
                        }
                        y += cell
                        row++
                    }
                }
            }
            .padding(StampTheme.spacing.m),
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(preview, description, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun OptionRow(label: String, chips: @Composable () -> Unit) {
    val s = StampTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.xs)) {
        Text(
            label.uppercase(),
            style = StampTheme.type.overline,
            color = StampTheme.colors.textMedium,
            modifier = Modifier.width(LabelWidth),
        )
        chips()
    }
}

private val PreviewHeight = 240.dp
private val CheckerCell = 8.dp
private val LabelWidth = 104.dp

@PaperPreview
@Composable
private fun ExportContentPreview() {
    StampAppTheme {
        ExportContent(ExportUiState(), onBackground = {}, onPostmark = {}, onSave = {}, onShare = {}, onGetPro = {}, onWatchAd = {})
    }
}

@PaperPreview
@Composable
private fun ExportContentProPreview() {
    StampAppTheme {
        ExportContent(
            ExportUiState(choice = ExportChoice(ExportBackground.Paper, postmark = false), isPro = true, save = SaveState.Saved),
            onBackground = {},
            onPostmark = {},
            onSave = {},
            onShare = {},
            onGetPro = {},
            onWatchAd = {},
        )
    }
}
