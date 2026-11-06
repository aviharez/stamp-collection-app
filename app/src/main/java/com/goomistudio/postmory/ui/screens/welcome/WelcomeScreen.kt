package com.goomistudio.postmory.ui.screens.welcome

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.goomistudio.postmory.R
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.ui.components.FitText
import com.goomistudio.postmory.ui.components.AirmailStripe
import com.goomistudio.postmory.ui.components.GrainedSurface
import com.goomistudio.postmory.ui.components.HandNote
import com.goomistudio.postmory.ui.components.PillButton
import com.goomistudio.postmory.ui.components.PillStyle
import com.goomistudio.postmory.ui.components.Postmark
import com.goomistudio.postmory.ui.components.PreviewArt
import com.goomistudio.postmory.ui.components.PreviewArtKind
import com.goomistudio.postmory.ui.components.Stamp
import com.goomistudio.postmory.ui.components.rememberDateText
import com.goomistudio.postmory.ui.screens.importer.rememberPhotoPicker
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme

/**
 * First run, shown once on a fresh install: what the app does, and the way to a first stamp in
 * three taps (this button → the red postmark shutter → "Stick in album").
 */
@Composable
fun WelcomeScreen(
    onMakeStamp: () -> Unit,
    onPicked: (Uri) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pick = rememberPhotoPicker(onPicked)
    WelcomeContent(onMakeStamp = onMakeStamp, onFromPhotos = pick, onSkip = onSkip, modifier = modifier)
}

@Composable
fun WelcomeContent(
    onMakeStamp: () -> Unit,
    onFromPhotos: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    GrainedSurface(c.cobalt, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AirmailStripe(Modifier.fillMaxWidth().height(s.xs), accent = c.ink)
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = s.screen, vertical = s.m),
            ) {
                PillButton(
                    stringResource(R.string.welcome_skip),
                    onClick = onSkip,
                    style = PillStyle.Plate,
                    modifier = Modifier.align(Alignment.End),
                )
                StampFan(Modifier.weight(1f).fillMaxWidth())
                Text(
                    stringResource(R.string.welcome_title),
                    style = StampTheme.type.screenTitle.copy(lineBreak = LineBreak.Heading),
                    color = c.textOnDarkHigh,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.welcome_body),
                    style = StampTheme.type.body,
                    color = c.textOnDarkMedium,
                    modifier = Modifier.padding(top = s.xs),
                )
                Steps(Modifier.fillMaxWidth().padding(top = s.l))
                // Stacked, not side by side: both labels stay whole at large font sizes.
                PillButton(
                    stringResource(R.string.welcome_make_stamp),
                    onClick = onMakeStamp,
                    style = PillStyle.White,
                    trailingIcon = R.drawable.ic_arrow_right,
                    trailingIconTint = c.airmailRed,
                    modifier = Modifier.fillMaxWidth().padding(top = s.l),
                )
                PillButton(
                    stringResource(R.string.welcome_from_photos),
                    onClick = onFromPhotos,
                    style = PillStyle.OutlineOnDark,
                    modifier = Modifier.fillMaxWidth().padding(top = s.xs),
                )
            }
        }
    }
}

/** Three example stamps fanned out, with today's postmark across the front one. */
@Composable
private fun StampFan(modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val description = stringResource(R.string.welcome_art_description)
    val today = rememberDateText(R.string.pattern_postmark_date)
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        // Front stamp is about 44 % of the narrower side; the others are scaled from it.
        val unit: Dp = min(maxWidth, maxHeight * 0.8f) * 0.44f
        Stamp(
            StampShape.Landscape,
            Modifier.width(unit * 1.15f).offset(x = -unit * 0.62f, y = -unit * 0.42f),
            tilt = -8f,
            shadowTint = c.shadowNavy,
        ) { PreviewArt(PreviewArtKind.Sea, Modifier.fillMaxSize()) }
        Stamp(
            StampShape.Square,
            Modifier.width(unit * 0.8f).offset(x = unit * 0.7f, y = -unit * 0.5f),
            tilt = 7f,
            shadowTint = c.shadowNavy,
        ) { PreviewArt(PreviewArtKind.Night, Modifier.fillMaxSize()) }
        Box(Modifier.offset(y = unit * 0.18f)) {
            Stamp(StampShape.Portrait, Modifier.width(unit), tilt = -2f, shadowTint = c.shadowNavy) {
                PreviewArt(PreviewArtKind.Sunset, Modifier.fillMaxSize())
            }
            Postmark(
                city = stringResource(R.string.welcome_postmark_city),
                date = today,
                diameter = unit * 0.55f,
                rotation = -12f,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = unit * 0.55f, y = -unit * 0.1f),
            )
        }
    }
}

/** "1 tap below · 2 snap it · 3 stick it in": numbered mini stamps with hand notes. */
@Composable
private fun Steps(modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    val steps = listOf(R.string.welcome_step_1, R.string.welcome_step_2, R.string.welcome_step_3)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(s.xs)) {
        steps.forEachIndexed { i, step ->
            // Read as one item: "1, tap below".
            Row(Modifier.weight(1f).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.xs)) {
                Stamp(StampShape.Square, Modifier.size(StepStampSize), shadowTint = StampTheme.colors.shadowNavy) {
                    // The stamp keeps its size, so at large font sizes the number shrinks to stay inside.
                    FitText(
                        "${i + 1}",
                        StampTheme.type.albumName,
                        Modifier.align(Alignment.Center),
                        color = StampTheme.colors.airmailRed,
                        minScale = StepNumberMinScale,
                    )
                }
                HandNote(
                    stringResource(step),
                    style = StampTheme.type.noteSmall,
                    color = StampTheme.colors.textOnDarkHigh,
                    maxLines = 2,
                )
            }
        }
    }
}

private val StepStampSize = 36.dp
private const val StepNumberMinScale = 0.4f

@PaperPreview
@Composable
private fun WelcomePreview() {
    StampAppTheme { WelcomeContent(onMakeStamp = {}, onFromPhotos = {}, onSkip = {}) }
}
