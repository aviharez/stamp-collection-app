package com.goomistudio.postmory.ui.screens.export

import com.goomistudio.postmory.ui.screens.paywall.PaywallReason
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import android.graphics.Bitmap
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.min
import com.goomistudio.postmory.domain.model.Stamp
import com.goomistudio.postmory.ui.components.ImageLoads
import com.goomistudio.postmory.ui.components.bleed
import com.goomistudio.postmory.ui.components.LocalImageLoads
import com.goomistudio.postmory.ui.components.previewStamps
import com.goomistudio.postmory.ui.theme.Elevation
import com.goomistudio.postmory.ui.theme.tintedShadow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
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
import com.goomistudio.postmory.R
import com.goomistudio.postmory.data.ads.AdOffer
import com.goomistudio.postmory.data.image.ExportBackground
import com.goomistudio.postmory.data.image.StampExporter
import com.goomistudio.postmory.ui.components.AdChip
import com.goomistudio.postmory.ui.components.HandNote
import com.goomistudio.postmory.ui.components.InkStrength
import com.goomistudio.postmory.ui.components.PaperChip
import com.goomistudio.postmory.ui.components.dashedOutline
import com.goomistudio.postmory.ui.components.PillButton
import com.goomistudio.postmory.ui.components.PillStyle
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme

/** Share / save the stamp as a full-resolution PNG. Opened from Stamped! (and later stamp detail). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    stampId: String,
    onDismiss: () -> Unit,
    onGetPro: (PaywallReason) -> Unit,
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
                is ExportEvent.Share -> context.sharePng(event.uri)
            }
        }
    }

    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val stamp = state.stamp
    val loads = remember(stamp?.imagePath) { ImageLoads() }

    /** Postcards: waits for the stamp picture, then for the redraw that has it, then copies the capture. */
    fun capture(then: (Bitmap) -> Unit) {
        val path = stamp?.imagePath ?: return
        scope.launch {
            viewModel.setBusy(true)
            withTimeoutOrNull(ImageWaitMs) { loads.done.first { path in it } }
            repeat(2) { withFrameNanos { } }
            val bitmap = runCatching { layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            viewModel.setBusy(false)
            if (bitmap != null) then(bitmap) else viewModel.failed()
        }
    }
    val postcard = state.choice.postcard
    val save = rememberSaveToPhotos {
        if (postcard == null) viewModel.saveToPhotos() else capture(viewModel::savePicture)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = StampTheme.colors.paper,
        contentColor = StampTheme.colors.textHigh,
    ) {
        if (postcard != null && stamp != null) {
            // The full-size postcard, laid out and recorded off screen (never drawn here).
            OffscreenCapture(layer, postcard.widthPx, postcard.heightPx, postcard.designWidth) {
                CompositionLocalProvider(LocalImageLoads provides loads) {
                    PostcardArt(postcard, stamp, state.text, watermark = state.watermark)
                }
            }
        }
        ExportContent(
            state = state,
            onPostcard = viewModel::setPostcard,
            onBackground = viewModel::setBackground,
            onPostmark = viewModel::setPostmark,
            onTo = viewModel::setTo,
            onMessage = viewModel::setMessage,
            onSave = save,
            onShare = { if (postcard == null) viewModel.share() else capture(viewModel::sharePicture) },
            onGetPro = { onGetPro(PaywallReason.Watermark) },
            onUnlockLook = { onGetPro(PaywallReason.Postcards) },
            onWatchAd = { activity?.let(viewModel::watchAd) },
            modifier = Modifier.navigationBarsPadding().imePadding(),
        )
    }
}

@Composable
fun ExportContent(
    state: ExportUiState,
    onPostcard: (PostcardStyle?) -> Unit,
    onBackground: (ExportBackground) -> Unit,
    onPostmark: (Boolean) -> Unit,
    onTo: (String) -> Unit,
    onMessage: (String) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onGetPro: () -> Unit,
    onUnlockLook: () -> Unit,
    onWatchAd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val postcard = state.choice.postcard
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = s.screen).padding(bottom = s.l),
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        Text(stringResource(R.string.export_title), style = StampTheme.type.cardTitle)

        // Four looks don't fit beside a label: the label on top, the chips in one row that scrolls
        // edge to edge when it is wider than the sheet (large fonts, long translations).
        Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
            Text(
                stringResource(R.string.export_send_as).uppercase(),
                style = StampTheme.type.overline,
                color = c.textMedium,
            )
            Row(
                Modifier
                    .bleed(s.screen)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = s.screen)
                    .padding(top = s.xxs),
                horizontalArrangement = Arrangement.spacedBy(s.xs),
            ) {
                PaperChip(stringResource(R.string.export_as_stamp), selected = postcard == null, onClick = { onPostcard(null) })
                PostcardStyle.entries.forEach { style ->
                    LookChip(style, selected = postcard == style, locked = !state.isPro && !style.free, onClick = { onPostcard(style) })
                }
            }
        }

        if (postcard == null) {
            ExportPreview(state.preview, checkered = state.choice.background == ExportBackground.Transparent)
        } else if (state.stamp != null) {
            PostcardPreview(postcard, state.stamp, state.text, state.watermark)
        }

        when {
            state.lookLocked -> HandNote(stringResource(R.string.postcard_pro_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            state.isPro -> HandNote(stringResource(R.string.export_pro_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            state.adUnlocked -> HandNote(stringResource(R.string.export_unlocked_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            else -> WatermarkRow(state.ad, onGetPro, onWatchAd)
        }

        if (postcard == null) {
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
        } else {
            HandField(
                value = state.text.to,
                onValueChange = onTo,
                hint = stringResource(R.string.postcard_to_hint),
                label = stringResource(R.string.postcard_to_label),
                imeAction = ImeAction.Next,
            )
            HandField(
                value = state.text.message,
                onValueChange = onMessage,
                hint = stringResource(R.string.postcard_message_hint),
                label = stringResource(R.string.postcard_message_label),
                singleLine = false,
            )
        }

        if (state.failed) {
            Text(stringResource(R.string.export_failed), style = StampTheme.type.label, color = c.airmailRed)
        }

        if (postcard != null && state.lookLocked) {
            // Try it on for free; sending this look is Pro (the paywall opens with its perk lit).
            PillButton(
                stringResource(R.string.postcard_send_with_pro, stringResource(postcard.label)),
                onClick = onUnlockLook,
                style = PillStyle.Red,
                modifier = Modifier.fillMaxWidth().padding(top = s.xs),
            )
        } else {
            SaveShareRow(state.save, state.sharing, onSave, onShare, Modifier.padding(top = s.xs))
        }
    }
}

/** A "Send as" chip; a Pro look carries the same small red "PRO" tag as the Pro filters. */
@Composable
private fun LookChip(style: PostcardStyle, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val c = StampTheme.colors
    val label = stringResource(style.label)
    val description = if (locked) stringResource(R.string.postcard_pro_cd, label) else label
    Box {
        PaperChip(label, selected = selected, onClick = onClick, modifier = Modifier.semantics { contentDescription = description })
        if (locked) {
            Text(
                stringResource(R.string.postcard_pro_tag),
                style = StampTheme.type.overline,
                color = c.textOnDarkHigh,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = -StampTheme.spacing.xxs)
                    .background(c.airmailRed, StampTheme.shapes.plate)
                    .padding(horizontal = StampTheme.spacing.xxs)
                    .clearAndSetSemantics { },
            )
        }
    }
}

/** The postcard at preview size: the same art as the export, laid out at its design width and scaled down. */
@Composable
private fun PostcardPreview(style: PostcardStyle, stamp: Stamp, text: PostcardText, watermark: Boolean) {
    val description = stringResource(R.string.export_preview_cd)
    val shape = StampTheme.shapes.plate
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(PreviewHeight).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        ScaledArt(
            width = min(maxWidth, maxHeight * style.aspect),
            aspect = style.aspect,
            designWidth = style.designWidth,
            modifier = Modifier
                .tintedShadow(Elevation.Card, shape, StampTheme.colors.shadowWarm)
                .clip(shape)
                .clearAndSetSemantics { },
        ) { PostcardArt(style, stamp, text, watermark) }
    }
}

/** A hand-written line in a dashed box (as "write on the back"); the label is for TalkBack. */
@Composable
private fun HandField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    label: String,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val focus = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else MessageLines,
        textStyle = StampTheme.type.noteSmall.copy(color = c.handInk),
        cursorBrush = SolidColor(c.airmailRed),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focus.moveFocus(FocusDirection.Down) },
            onDone = { focus.clearFocus() },
        ),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = s.touchTarget)
                    .dashedOutline(c.outline)
                    .padding(horizontal = s.m, vertical = s.xs),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(hint, style = StampTheme.type.noteSmall, color = c.textLow)
                field()
            }
        },
    )
}

private val PostcardStyle.label: Int
    get() = when (this) {
        PostcardStyle.Envelope -> R.string.postcard_envelope
        PostcardStyle.Postcard -> R.string.postcard_postcard
        PostcardStyle.Greetings -> R.string.postcard_card
    }

/** The watermark row: the two honest ways out side by side, Pro first, the ad as the quiet option. */
@Composable
internal fun WatermarkRow(ad: AdOffer, onGetPro: () -> Unit, onWatchAd: () -> Unit, note: String = stringResource(R.string.export_watermark_note, stringResource(R.string.app_name))) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    Column(
        Modifier.fillMaxWidth().dashedOutline(c.outline).padding(horizontal = s.m, vertical = s.s),
        verticalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        HandNote(
            note,
            style = StampTheme.type.noteSmall,
            strength = InkStrength.Soft,
        )
        // Side by side; at large font sizes the ad chip moves under instead of being squeezed.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.xs), itemVerticalAlignment = Alignment.CenterVertically) {
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
    // Label column on one line with its chips; at large font sizes the chips wrap under the label.
    FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.xs)) {
        Text(
            label.uppercase(),
            style = StampTheme.type.overline,
            color = StampTheme.colors.textMedium,
            maxLines = 1,
            modifier = Modifier.widthIn(min = LabelWidth),
        )
        chips()
    }
}

private val PreviewHeight = 240.dp
private const val ImageWaitMs = 5_000L
private const val MessageLines = 3
private val CheckerCell = 8.dp
private val LabelWidth = 104.dp

@PaperPreview
@Composable
private fun ExportContentPreview() {
    StampAppTheme {
        ExportContent(ExportUiState(), {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

@PaperPreview
@Composable
private fun ExportContentProPreview() {
    StampAppTheme {
        ExportContent(
            ExportUiState(choice = ExportChoice(ExportBackground.Paper, postmark = false), isPro = true, save = SaveState.Saved),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

@PaperPreview
@Composable
private fun ExportContentPostcardPreview() {
    StampAppTheme {
        ExportContent(
            ExportUiState(
                choice = ExportChoice(postcard = PostcardStyle.Greetings),
                stamp = previewStamps(1).first(),
                text = PostcardText("Mia", "sunrise hike!! wish you were here"),
            ),
            {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}
