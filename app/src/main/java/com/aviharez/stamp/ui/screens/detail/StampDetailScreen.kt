package com.aviharez.stamp.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.ui.components.FitText
import com.aviharez.stamp.ui.components.FilterPicker
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.label
import com.aviharez.stamp.domain.model.StampFilter
import com.aviharez.stamp.ui.screens.filter.FilterUi
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.tintedShadow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.components.Postmark
import com.aviharez.stamp.ui.components.RenderedStamp
import com.aviharez.stamp.ui.components.dashedOutline
import com.aviharez.stamp.ui.components.description
import com.aviharez.stamp.ui.components.previewStamps
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.components.rememberPostmarkLines
import com.aviharez.stamp.ui.screens.export.ExportSheet
import com.aviharez.stamp.ui.screens.paywall.PaywallReason
import com.aviharez.stamp.ui.screens.stamped.AlbumPickerSheet
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import java.io.File

/**
 * One stamp, big and straight (no tilt: this is where you work on it): title, the words on its
 * back, share, frame again from the original, move to another album, delete.
 */
@Composable
fun StampDetailScreen(
    onClose: () -> Unit,
    onReframe: (stampId: String) -> Unit,
    onOpenPaywall: (PaywallReason) -> Unit,
    openNewAlbum: Boolean,
    onNewAlbumOpened: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StampDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val caption by viewModel.caption.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    var filtering by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickerOnForm by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }

    // Deleted (here or elsewhere): nothing left to show.
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(state.loaded, state.stamp) { if (state.loaded && state.stamp == null) close() }
    // Back from the paywall with an album earned by an ad: straight onto the new-album form.
    LaunchedEffect(openNewAlbum) {
        if (openNewAlbum) {
            picking = true
            pickerOnForm = true
            onNewAlbumOpened()
        }
    }

    val stamp = state.stamp ?: return
    StampDetailContent(
        stamp = stamp,
        subtitle = state.album?.let { stringResource(R.string.detail_number, state.numberInAlbum, it.album.name) }.orEmpty(),
        title = title,
        caption = caption,
        canReframe = state.canReframe,
        canShare = state.canShare,
        onBack = onClose,
        onMore = { more = true },
        onChangeAlbum = { picking = true },
        onTitleChange = viewModel::onTitleChange,
        onCaptionChange = viewModel::onCaptionChange,
        onReframe = { onReframe(stamp.id) },
        onShare = { exporting = true },
        filter = filter,
        isPro = state.isPro,
        filtering = filtering && filter.available,
        onOpenFilters = { filtering = true },
        onPickFilter = viewModel::pickFilter,
        onKeepFilter = { onOpenPaywall(PaywallReason.Filters) },
        onCloseFilters = {
            filtering = false
            viewModel.closeFilters()
        },
        modifier = modifier,
    )
    BackHandler(enabled = filtering) {
        filtering = false
        viewModel.closeFilters()
    }

    if (more) {
        MoreSheet(
            onMove = {
                more = false
                picking = true
            },
            onDelete = {
                more = false
                viewModel.delete()
            },
            onDismiss = { more = false },
        )
    }
    if (picking) {
        AlbumPickerSheet(
            albums = state.albums,
            selectedId = stamp.albumId,
            onPick = viewModel::moveToAlbum,
            onCreate = viewModel::createAlbumAndMove,
            canCreate = state.canCreateAlbum,
            onNeedPro = {
                picking = false
                onOpenPaywall(PaywallReason.Albums)
            },
            startCreating = pickerOnForm,
            onDismiss = {
                picking = false
                pickerOnForm = false
            },
        )
    }
    if (exporting) {
        ExportSheet(
            stampId = stamp.id,
            onDismiss = { exporting = false },
            onGetPro = onOpenPaywall,
        )
    }
}

@Composable
fun StampDetailContent(
    stamp: Stamp,
    subtitle: String,
    title: String,
    caption: String,
    canReframe: Boolean,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onChangeAlbum: () -> Unit,
    onTitleChange: (String) -> Unit,
    onCaptionChange: (String) -> Unit,
    onReframe: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    canShare: Boolean = true,
    filter: FilterUi = FilterUi(),
    isPro: Boolean = false,
    /** The filter tray is open (in place of the words and buttons). */
    filtering: Boolean = false,
    onOpenFilters: () -> Unit = {},
    onPickFilter: (StampFilter) -> Unit = {},
    onKeepFilter: () -> Unit = {},
    onCloseFilters: () -> Unit = {},
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier
            .fillMaxSize()
            .background(c.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(vertical = s.m),
    ) {
        // Back · "NO. 12 · JAPAN TRIP" (tap = move) · more
        Row(Modifier.fillMaxWidth().padding(horizontal = s.screenFullBleed), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(R.drawable.ic_back, stringResource(R.string.cd_back), onBack)
            val changeAlbum = stringResource(R.string.stamped_change_album)
            Row(
                Modifier
                    .weight(1f)
                    .heightIn(min = s.touchTargetMin)
                    .clickable(role = Role.Button, onClickLabel = changeAlbum, onClick = onChangeAlbum)
                    .padding(horizontal = s.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                FitText(
                    subtitle.uppercase(locale),
                    StampTheme.type.overline,
                    Modifier.weight(1f, fill = false),
                    color = c.textMedium,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
                Icon(painterResource(R.drawable.ic_chevron_down), null, tint = c.textMedium, modifier = Modifier.size(s.m))
            }
            CircleIconButton(R.drawable.ic_more, stringResource(R.string.detail_more_cd), onMore)
        }

        StampHero(
            stamp,
            // A Pro filter tried on shows its preview; the saved picture stays as it is.
            file = File(filter.preview?.path ?: stamp.imagePath),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = s.screen, vertical = s.m),
        )

        if (filtering) {
            FilterTray(filter, isPro, onPickFilter, onKeepFilter, onCloseFilters)
            return@Column
        }

        Column(Modifier.padding(horizontal = s.screen), verticalArrangement = Arrangement.spacedBy(s.xs)) {
            TitleField(title, onTitleChange)
            val date = rememberDateText(R.string.pattern_detail_date, stamp.day)
            val where = listOfNotNull(stamp.place.city, stamp.place.country).joinToString(", ")
            Text(
                if (where.isEmpty()) date else stringResource(R.string.detail_when_where, date, where),
                style = StampTheme.type.body,
                color = c.textMedium,
            )
            BackNoteField(caption, onCaptionChange, Modifier.fillMaxWidth().padding(top = s.xs))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = s.screen).padding(top = s.l),
            horizontalArrangement = Arrangement.spacedBy(s.s),
        ) {
            // Three buttons share the row: the outline ones drop their icons so the words stay full size.
            val crowded = canReframe && filter.available && canShare
            if (canReframe) {
                PillButton(
                    stringResource(R.string.detail_reframe),
                    onClick = onReframe,
                    style = PillStyle.Outline,
                    leadingIcon = if (crowded) null else R.drawable.ic_rotate,
                    modifier = Modifier.weight(1f),
                )
            }
            if (filter.available) {
                PillButton(
                    stringResource(R.string.filter_button),
                    onClick = onOpenFilters,
                    style = PillStyle.Outline,
                    leadingIcon = if (crowded) null else R.drawable.ic_perk_filters,
                    modifier = Modifier.weight(1f),
                )
            }
            if (canShare) {
                PillButton(
                    stringResource(R.string.stamped_share),
                    onClick = onShare,
                    style = PillStyle.Red,
                    leadingIcon = R.drawable.ic_share,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The stamp as large as fits, its postmark over the bottom-end corner (as on Stamped! and in exports). */
@Composable
private fun StampHero(stamp: Stamp, file: File, modifier: Modifier = Modifier) {
    val lines = rememberPostmarkLines(stamp.place, stamp.day)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val aspect = stamp.shape.aspectRatio
        // Leave room on the end and bottom for the postmark hanging over the edge.
        val maxW = maxWidth * HeroWidthFraction
        val maxH = maxHeight * HeroHeightFraction
        val w: Dp = min(maxW, maxH * aspect)
        val h: Dp = w / aspect
        val d = min(w, h) * PostmarkScale
        Box(Modifier.size(w, h).offset(x = -d * PostmarkOffsetX / 2, y = -d * PostmarkOffsetY / 2)) {
            RenderedStamp(
                file = file,
                shape = stamp.shape,
                perforation = stamp.perforation,
                contentDescription = stamp.description(),
                modifier = Modifier.fillMaxSize(),
            )
            Postmark(
                city = lines.top,
                date = lines.bottom,
                diameter = d,
                rotation = PostmarkTilt,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = d * PostmarkOffsetX, y = d * PostmarkOffsetY),
            )
        }
    }
}

/**
 * Kraft tray under the stamp: the filters as mini stamps (a pick shows on the big stamp straight
 * away and is saved; a Pro filter for a free user is only tried on) and "Done".
 */
@Composable
private fun FilterTray(
    filter: FilterUi,
    isPro: Boolean,
    onPick: (StampFilter) -> Unit,
    onKeep: () -> Unit,
    onDone: () -> Unit,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    GrainedSurface(
        c.kraft,
        Modifier.fillMaxWidth().padding(horizontal = s.screenFullBleed).tintedShadow(Elevation.Card, StampTheme.shapes.card, c.shadowWarm),
        StampTheme.shapes.card,
    ) {
        Column(Modifier.padding(vertical = s.m), verticalArrangement = Arrangement.spacedBy(s.xs)) {
            Text(
                stringResource(R.string.filter_tray_title),
                style = StampTheme.type.cardTitle,
                color = c.textHigh,
                modifier = Modifier.padding(horizontal = s.m),
            )
            FilterPicker(
                photo = filter.thumbnail,
                selected = filter.selected,
                isPro = isPro,
                onPick = onPick,
                contentPadding = PaddingValues(horizontal = s.m),
            )
            if (filter.tryingPro) {
                HandNote(
                    stringResource(R.string.filter_try_note),
                    style = StampTheme.type.noteSmall,
                    strength = InkStrength.Soft,
                    modifier = Modifier.padding(horizontal = s.m),
                )
            }
            Row(Modifier.padding(horizontal = s.m).padding(top = s.xs), horizontalArrangement = Arrangement.spacedBy(s.s)) {
                if (filter.tryingPro) {
                    PillButton(
                        stringResource(R.string.filter_keep_with_pro, stringResource(filter.selected.label)),
                        onClick = onKeep,
                        style = PillStyle.Red,
                        modifier = Modifier.weight(1.6f),
                    )
                }
                PillButton(
                    stringResource(R.string.filter_done),
                    onClick = onDone,
                    style = if (filter.tryingPro) PillStyle.Outline else PillStyle.Red,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The title, written straight on the paper in Fraunces; empty shows a soft hint. */
@Composable
private fun TitleField(value: String, onValueChange: (String) -> Unit) {
    val c = StampTheme.colors
    val focus = LocalFocusManager.current
    val label = stringResource(R.string.detail_title_label)
    val style: TextStyle = StampTheme.type.cardTitle.copy(color = c.textHigh)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(c.airmailRed),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().heightIn(min = StampTheme.spacing.touchTargetMin).semantics { contentDescription = label },
        decorationBox = { field ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(stringResource(R.string.detail_title_hint), style = style, color = c.textLow)
                field()
            }
        },
    )
}

/** "Write on the back": the caption in hand-writing inside a dashed box, as on Stamped!. */
@Composable
private fun BackNoteField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val focus = LocalFocusManager.current
    val label = stringResource(R.string.stamped_caption_label)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = StampTheme.type.noteSmall.copy(color = c.handInk),
        cursorBrush = SolidColor(c.airmailRed),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.semantics { contentDescription = label },
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = s.touchTarget)
                    .dashedOutline(c.outline)
                    .padding(horizontal = s.m, vertical = s.xs),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) Text(stringResource(R.string.stamped_caption_hint), style = StampTheme.type.noteSmall, color = c.textLow)
                field()
            }
        },
    )
}

/** "…": move to another album, or delete (asks once more, right here, before deleting). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreSheet(onMove: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = StampTheme.colors.paper,
        contentColor = StampTheme.colors.textHigh,
    ) {
        MoreSheetContent(onMove, onDelete, onDismiss, Modifier.navigationBarsPadding())
    }
}

@Composable
private fun MoreSheetContent(onMove: () -> Unit, onDelete: () -> Unit, onKeep: () -> Unit, modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    var confirming by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxWidth().padding(horizontal = s.screen).padding(bottom = s.l),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        if (!confirming) {
            PillButton(stringResource(R.string.detail_move), onMove, Modifier.fillMaxWidth(), style = PillStyle.Outline)
            PillButton(
                stringResource(R.string.detail_delete),
                { confirming = true },
                Modifier.fillMaxWidth(),
                style = PillStyle.Outline,
            )
        } else {
            Text(stringResource(R.string.detail_delete_title), style = StampTheme.type.cardTitle)
            HandNote(stringResource(R.string.detail_delete_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            Row(Modifier.fillMaxWidth().padding(top = s.xs), horizontalArrangement = Arrangement.spacedBy(s.s)) {
                PillButton(stringResource(R.string.detail_keep), onKeep, Modifier.weight(1f), style = PillStyle.Outline)
                PillButton(stringResource(R.string.detail_delete_confirm), onDelete, Modifier.weight(1f), style = PillStyle.Red)
            }
        }
    }
}

private const val HeroWidthFraction = 0.78f
private const val HeroHeightFraction = 0.84f
private const val PostmarkScale = 0.42f
private const val PostmarkOffsetX = 0.62f
private const val PostmarkOffsetY = 0.22f
private const val PostmarkTilt = -12f

@PaperPreview
@Composable
private fun StampDetailPreview() {
    StampAppTheme {
        StampDetailContent(
            stamp = previewStamps(1).first(),
            subtitle = "No. 12 in Japan trip",
            title = "",
            caption = "",
            canReframe = true,
            onBack = {}, onMore = {}, onChangeAlbum = {}, onTitleChange = {}, onCaptionChange = {}, onReframe = {}, onShare = {},
        )
    }
}

@PaperPreview
@Composable
private fun MoreSheetPreview() {
    StampAppTheme { MoreSheetContent(onMove = {}, onDelete = {}, onKeep = {}) }
}
