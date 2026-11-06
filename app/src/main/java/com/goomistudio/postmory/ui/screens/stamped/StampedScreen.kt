package com.goomistudio.postmory.ui.screens.stamped

import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.goomistudio.postmory.R
import com.goomistudio.postmory.data.ads.AdOffer
import com.goomistudio.postmory.domain.model.PerforationStyle
import com.goomistudio.postmory.domain.model.Place
import com.goomistudio.postmory.domain.model.StampFilter
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.domain.model.StampSource
import com.goomistudio.postmory.domain.usecase.Streak
import com.goomistudio.postmory.ui.components.AdChip
import com.goomistudio.postmory.ui.components.AirmailStripe
import com.goomistudio.postmory.ui.components.GrainedSurface
import com.goomistudio.postmory.ui.components.LookPicker
import com.goomistudio.postmory.ui.components.PillButton
import com.goomistudio.postmory.ui.components.PillStyle
import com.goomistudio.postmory.ui.components.Postmark
import com.goomistudio.postmory.ui.components.RenderedStamp
import com.goomistudio.postmory.ui.components.Stamp
import com.goomistudio.postmory.ui.components.StreakChip
import com.goomistudio.postmory.ui.components.bleed
import com.goomistudio.postmory.ui.components.label
import com.goomistudio.postmory.ui.components.rememberPostmarkLines
import com.goomistudio.postmory.ui.screens.export.ExportSheet
import com.goomistudio.postmory.ui.screens.filter.FilterUi
import com.goomistudio.postmory.ui.screens.filter.keepLookLabel
import com.goomistudio.postmory.ui.screens.importer.rememberPhotoPicker
import com.goomistudio.postmory.ui.screens.paywall.PaywallReason
import com.goomistudio.postmory.ui.theme.Elevation
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.LocalDate

/** "Stamped!" for the stamp in the current [com.goomistudio.postmory.ui.navigation.StampedRoute]. */
@Composable
fun StampedScreen(
    onRetake: () -> Unit,
    onPickAnother: (Uri) -> Unit,
    onStickInAlbum: (albumId: Long) -> Unit,
    onOpenPaywall: (PaywallReason) -> Unit,
    modifier: Modifier = Modifier,
    /** True once after "+1 album" was earned on the paywall: reopen the picker on its new-album form. */
    openNewAlbum: Boolean = false,
    onNewAlbumOpened: () -> Unit = {},
    viewModel: StampedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val caption by viewModel.caption.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var exporting by rememberSaveable { mutableStateOf(false) }
    var pickerOnForm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openNewAlbum) {
        if (openNewAlbum) {
            pickerOnForm = true
            picking = true
            onNewAlbumOpened()
        }
    }
    val stamp = state.stamp
    val fromGallery = stamp?.source == StampSource.Gallery
    // An imported stamp is "retaken" by picking another photo; this one goes only once one is picked.
    val pickPhoto = rememberPhotoPicker { uri -> viewModel.retake { onPickAnother(uri) } }
    val activity = LocalActivity.current

    StampedContent(
        // A Pro look tried on shows its preview; the album keeps the saved picture.
        stampPath = filter.preview?.path ?: stamp?.imagePath,
        shape = stamp?.shape ?: StampShape.Portrait,
        place = stamp?.place ?: Place.Unknown,
        day = stamp?.day ?: LocalDate.now(),
        subtitle = state.album?.let { stringResource(R.string.stamped_subtitle, state.numberInAlbum, it.album.name) }
            ?: stringResource(R.string.stamped_subtitle_placeholder),
        streak = state.streak,
        caption = caption,
        onCaptionChange = viewModel::onCaptionChange,
        onChangeAlbum = { picking = true },
        retakeLabel = stringResource(if (fromGallery) R.string.stamped_pick_another else R.string.stamped_retake),
        onRetake = { if (fromGallery) pickPhoto() else viewModel.retake(onRetake) },
        // While a Pro look is tried on, both ways forward ask for Pro first (the look isn't saved
        // without it); picking a free look again goes back to the normal buttons.
        onStickInAlbum = {
            when {
                filter.tryingPro -> onOpenPaywall(filter.keepReason)
                else -> stamp?.let { onStickInAlbum(it.albumId) }
            }
        },
        onShare = {
            when {
                filter.tryingPro -> onOpenPaywall(filter.keepReason)
                stamp != null -> exporting = true
            }
        },
        filter = filter,
        onPickFilter = viewModel::pickFilter,
        onPickEdge = viewModel::pickEdge,
        onKeepLook = { onOpenPaywall(filter.keepReason) },
        onWatchEdgeAd = { activity?.let(viewModel::watchEdgeAd) },
        onPreloadEdgeAd = viewModel::preloadEdgeAd,
        modifier = modifier,
    )

    if (exporting && stamp != null) {
        ExportSheet(
            stampId = stamp.id,
            onDismiss = { exporting = false },
            onGetPro = onOpenPaywall,
        )
    }

    if (picking) {
        AlbumPickerSheet(
            albums = state.albums,
            selectedId = stamp?.albumId,
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
}

/**
 * Stateless "Stamped!" layout + the peak-moment choreography: the stamp drops in, the postmark
 * slams (1.4 → 1, shake, haptic) and tiny stamps fly out. Plays once per stamp; snaps to the
 * end state when reduce motion is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StampedContent(
    stampPath: String?,
    shape: StampShape,
    place: Place,
    day: LocalDate,
    subtitle: String,
    streak: Streak,
    caption: String,
    onCaptionChange: (String) -> Unit,
    onChangeAlbum: () -> Unit,
    retakeLabel: String,
    onRetake: () -> Unit,
    onStickInAlbum: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    filter: FilterUi = FilterUi(),
    onPickFilter: (StampFilter) -> Unit = {},
    onPickEdge: (PerforationStyle) -> Unit = {},
    onKeepLook: () -> Unit = {},
    onWatchEdgeAd: () -> Unit = {},
    onPreloadEdgeAd: () -> Unit = {},
    animate: Boolean = true,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    // While the keyboard is up (writing on the back), the look picker and its Pro pill step aside
    // so the stamp keeps a readable size instead of being squeezed to a sliver. Reads where the
    // keyboard is heading, not where it is: they come back the moment it starts to slide away
    // instead of after its whole hide animation.
    val typing = WindowInsets.imeAnimationTarget.getBottom(LocalDensity.current) > 0
    GrainedSurface(c.cobalt, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AirmailStripe(Modifier.fillMaxWidth().height(s.xs), accent = c.ink)
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = s.screen, vertical = s.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PillButton(
                    retakeLabel,
                    onClick = onRetake,
                    style = PillStyle.Plate,
                    modifier = Modifier.align(Alignment.Start),
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    StampHero(
                        stampPath = stampPath,
                        shape = shape,
                        perforation = filter.shownEdge,
                        place = place,
                        day = day,
                        animate = animate,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (filter.tryingPro && !typing) {
                        LaunchedEffect(filter.edgeAdOffered) { if (filter.edgeAdOffered) onPreloadEdgeAd() }
                        Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                            // Side by side; at large font sizes the ad chip moves under instead of being squeezed.
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(s.xs, Alignment.CenterHorizontally),
                                itemVerticalAlignment = Alignment.CenterVertically,
                            ) {
                                PillButton(keepLookLabel(filter), onClick = onKeepLook, style = PillStyle.Red)
                                if (filter.edgeAdOffered) {
                                    AdChip(stringResource(R.string.edge_ad), loading = filter.ad == AdOffer.Loading, onClick = onWatchEdgeAd, onDark = true)
                                }
                            }
                            if (filter.edgeAdOffered && filter.ad == AdOffer.Unavailable) {
                                Text(stringResource(R.string.ad_unavailable), style = StampTheme.type.label, color = c.textOnDarkMedium)
                            }
                        }
                    }
                }
                if (filter.available && !typing) {
                    LookPicker(
                        photo = filter.thumbnail,
                        selected = filter.selected,
                        access = filter.access,
                        working = filter.working,
                        onPickFilter = onPickFilter,
                        onPickEdge = onPickEdge,
                        onDark = true,
                        modifier = Modifier.bleed(s.screen).padding(bottom = s.xs),
                    )
                }

                Text(stringResource(R.string.stamped_title), style = StampTheme.type.hero, color = c.textOnDarkHigh)
                val changeAlbum = stringResource(R.string.stamped_change_album)
                Row(
                    Modifier
                        .heightIn(min = s.touchTargetMin)
                        .clickable(role = Role.Button, onClickLabel = changeAlbum, onClick = onChangeAlbum)
                        .padding(horizontal = s.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.xxs),
                ) {
                    Text(subtitle, style = StampTheme.type.body, color = c.textOnDarkMedium, textAlign = TextAlign.Center)
                    Icon(painterResource(R.drawable.ic_chevron_down), null, tint = c.textOnDarkMedium, modifier = Modifier.size(s.m))
                }
                if (streak.current > 0) {
                    StreakChip(streak, Modifier.padding(top = s.xxs))
                }
                CaptionField(caption, onCaptionChange, Modifier.fillMaxWidth().padding(top = s.l))
                Row(
                    Modifier.fillMaxWidth().padding(top = s.l),
                    horizontalArrangement = Arrangement.spacedBy(s.s),
                ) {
                    PillButton(
                        stringResource(R.string.stamped_share),
                        onClick = onShare,
                        style = PillStyle.OutlineOnDark,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(
                        stringResource(R.string.stamped_stick),
                        onClick = onStickInAlbum,
                        style = PillStyle.White,
                        trailingIcon = R.drawable.ic_arrow_right,
                        trailingIconTint = c.airmailRed,
                        modifier = Modifier.weight(1.6f),
                    )
                }
            }
        }
    }
}

/** The stamp with its postmark and the little stamps that fly out, sized to the space left. */
@Composable
private fun StampHero(
    stampPath: String?,
    shape: StampShape,
    perforation: PerforationStyle,
    place: Place,
    day: LocalDate,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val reduceMotion = rememberReduceMotion() || !animate
    val haptics = LocalHapticFeedback.current
    var played by rememberSaveable { mutableStateOf(false) }
    val skip = reduceMotion || played

    val drop = remember { Animatable(if (skip) 1f else 0f) }
    val slam = remember { Animatable(if (skip) 1f else 0f) }
    val shake = remember { Animatable(0f) }
    val fly = remember { Animatable(if (skip) 1f else 0f) }

    val context = LocalContext.current
    LaunchedEffect(stampPath) {
        if (stampPath == null || played) return@LaunchedEffect
        // Decode the PNG into Coil's memory cache first, so the photo is there as it drops
        // (not a blank paper that fills in mid-flight). Bounded: never hold the moment hostage.
        withTimeoutOrNull(PreloadTimeoutMillis) {
            SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context).data(File(stampPath)).size(PreloadSizePx).build(),
            )
        }
        played = true
        if (reduceMotion) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            return@LaunchedEffect
        }
        launch { drop.animateTo(1f, spring(dampingRatio = DropDamping, stiffness = Spring.StiffnessMediumLow)) }
        delay(SlamDelayMillis)
        slam.animateTo(1f, tween(SlamMillis, easing = FastOutLinearInEasing))
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        launch {
            shake.animateTo(0f, keyframes {
                durationMillis = ShakeMillis
                -1f at 40
                0.8f at 90
                -0.4f at 150
                0f at ShakeMillis
            })
        }
        fly.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val longSide = min(StampMaxLongSide, min(maxHeight * 0.78f, maxWidth * 0.72f))
        val (w, h) = if (shape.aspectRatio >= 1f) longSide to longSide / shape.aspectRatio else longSide * shape.aspectRatio to longSide
        val postmarkD = (min(w, h) * PostmarkScale).coerceIn(PostmarkMin, PostmarkMax)

        // Tiny stamps fly out from behind the big one.
        TinyStamps.forEach { tiny ->
            Stamp(
                shape = tiny.shape,
                perforation = PerforationStyle.Fine,
                tilt = tiny.tilt * fly.value,
                elevation = Elevation.Low,
                shadowTint = c.shadowNavy,
                modifier = Modifier
                    .size(tiny.size, tiny.size / tiny.shape.aspectRatio)
                    .graphicsLayer {
                        translationX = (w * tiny.dx).toPx() * fly.value
                        translationY = (h * tiny.dy).toPx() * fly.value
                        alpha = fly.value.coerceIn(0f, 1f)
                        val sc = 0.4f + 0.6f * fly.value
                        scaleX = sc; scaleY = sc
                    },
            ) { Box(Modifier.fillMaxSize().background(tiny.color(c))) }
        }

        Box(
            Modifier
                .size(w, h)
                .graphicsLayer {
                    val p = drop.value
                    translationY = -DropDistance.toPx() * (1f - p)
                    translationX = ShakeDistance.toPx() * shake.value
                    rotationZ = StampTilt + DropSpin * (1f - p)
                    val sc = 1f + DropOvershootScale * (1f - p)
                    scaleX = sc; scaleY = sc
                    alpha = (p * 3f).coerceIn(0f, 1f)
                },
        ) {
            if (stampPath != null) {
                RenderedStamp(
                    file = File(stampPath),
                    shape = shape,
                    perforation = perforation,
                    contentDescription = stringResource(R.string.stamped_stamp_cd),
                    shadowTint = c.shadowNavy,
                    elevation = Elevation.Raised,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Stamp(shape = shape, modifier = Modifier.fillMaxSize(), shadowTint = c.shadowNavy) {
                    GrainedSurface(c.kraft, Modifier.fillMaxSize())
                }
            }
            val lines = rememberPostmarkLines(place, day)
            Postmark(
                city = lines.top,
                date = lines.bottom,
                diameter = postmarkD,
                showWaves = true,
                rotation = PostmarkTilt,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = postmarkD * 0.62f, y = postmarkD * 0.22f)
                    .graphicsLayer {
                        val sc = 1f + SlamFromScale * (1f - slam.value)
                        scaleX = sc; scaleY = sc
                        alpha = slam.value
                    },
            )
        }
    }
}

/** "write something on the back…" — Caveat on a dashed outline. */
@Composable
private fun CaptionField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val label = stringResource(R.string.stamped_caption_label)
    val focusManager = LocalFocusManager.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = StampTheme.type.noteSmall.copy(color = c.textOnDarkHigh),
        cursorBrush = SolidColor(c.paperWhite),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier.semantics { contentDescription = label },
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = s.touchTarget)
                    .drawBehind {
                        val dash = CaptionDash.toPx()
                        drawRoundRect(
                            color = c.outlineOnDark,
                            cornerRadius = CornerRadius(CaptionCorner.toPx()),
                            style = Stroke(width = CaptionStroke.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                        )
                    }
                    .padding(horizontal = s.m, vertical = s.xs),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(stringResource(R.string.stamped_caption_hint), style = StampTheme.type.noteSmall, color = c.textOnDarkLow)
                }
                field()
            }
        },
    )
}

/** A little stamp that flies out: offset (in big-stamp sizes from its centre), tilt, colour. */
private data class TinyStamp(
    val dx: Float,
    val dy: Float,
    val tilt: Float,
    val size: Dp,
    val shape: StampShape,
    val color: (com.goomistudio.postmory.ui.theme.AirmailColors) -> Color,
)

private val TinyStamps = listOf(
    TinyStamp(-0.62f, -0.38f, -14f, 34.dp, StampShape.Portrait) { it.kraftDark },
    TinyStamp(0.6f, -0.44f, 10f, 28.dp, StampShape.Portrait) { it.sage },
    TinyStamp(-0.64f, 0.36f, 8f, 28.dp, StampShape.Square) { it.airmailRed },
    TinyStamp(0.5f, 0.62f, -9f, 34.dp, StampShape.Square) { it.kraft },
)

private val StampMaxLongSide = 300.dp
private const val StampTilt = -4f
private const val PostmarkTilt = -12f
private const val PostmarkScale = 0.42f
private val PostmarkMin = 72.dp
private val PostmarkMax = 112.dp
private val DropDistance = 120.dp
private const val DropSpin = -10f
private const val DropOvershootScale = 0.12f
private const val DropDamping = 0.62f
private const val SlamDelayMillis = 280L
private const val SlamMillis = 140
private const val SlamFromScale = 0.4f
private const val ShakeMillis = 220
private val ShakeDistance = 6.dp
private const val PreloadTimeoutMillis = 800L
/** Bigger than the hero ever draws, so the cached bitmap serves the on-screen request. */
private const val PreloadSizePx = 1200
private val CaptionDash = 6.dp
private val CaptionCorner = 12.dp
private val CaptionStroke = 1.5.dp

@Preview(heightDp = 840, widthDp = 390)
@Composable
private fun StampedContentPreview() {
    StampAppTheme {
        StampedContent(
            stampPath = null,
            shape = StampShape.Portrait,
            place = Place("Kyoto", "Japan", "JP", null, null),
            day = LocalDate.of(2026, 11, 4),
            subtitle = "No. 129 in your Japan trip album",
            streak = Streak(13, 13, true),
            caption = "",
            onCaptionChange = {},
            onChangeAlbum = {},
            retakeLabel = stringResource(R.string.stamped_retake),
            onRetake = {},
            onStickInAlbum = {},
            onShare = {},
            animate = false,
        )
    }
}
