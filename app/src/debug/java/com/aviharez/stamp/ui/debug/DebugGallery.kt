package com.aviharez.stamp.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.AirmailEnvelope
import com.aviharez.stamp.ui.components.AirmailStripe
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.NavTab
import com.aviharez.stamp.ui.components.Postmark
import com.aviharez.stamp.ui.components.PreviewArt
import com.aviharez.stamp.ui.components.PreviewArtKind
import com.aviharez.stamp.ui.components.Stamp
import com.aviharez.stamp.ui.components.StampNavBar
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** True in debug builds: the debug component gallery is available. */
const val DEBUG_GALLERY_ENABLED = true

/** Debug-only gallery of theme tokens and signature components. */
@Composable
fun DebugGallery(modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .background(StampTheme.colors.paper)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = s.l),
        verticalArrangement = Arrangement.spacedBy(s.xl),
    ) {
        Text(
            stringResource(R.string.debug_gallery_title),
            style = StampTheme.type.screenTitle,
            modifier = Modifier.padding(horizontal = s.screen),
        )
        Section(R.string.debug_section_nav) { NavBar() }
        Section(R.string.debug_section_home) { HomeComposition() }
        Section(R.string.debug_section_stamps) { StampShapes() }
        Section(R.string.debug_section_edges) { EdgeStyles() }
        Section(R.string.debug_section_postmark) { Postmarks() }
        Section(R.string.debug_section_stripe) { Stripes() }
        Section(R.string.debug_section_grain) { Grains() }
        Section(R.string.debug_section_notes) { Notes() }
        Section(R.string.debug_section_tokens) { ThemeSpecimen() }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.s)) {
        Text(
            stringResource(title).uppercase(),
            style = StampTheme.type.overline,
            color = StampTheme.colors.textMedium,
            modifier = Modifier.padding(horizontal = StampTheme.spacing.screen),
        )
        content()
    }
}

/** Rebuilds the kraft album card + envelope + nav from design/home.png out of real components. */
@Composable
private fun HomeComposition() {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    Column(
        Modifier.padding(horizontal = s.screenFullBleed),
        verticalArrangement = Arrangement.spacedBy(s.l),
    ) {
        GrainedSurface(
            color = c.kraft,
            shape = StampTheme.shapes.card,
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .rotate(-1f),
        ) {
            Text(
                stringResource(R.string.debug_sample_page),
                style = StampTheme.type.overline,
                color = c.textMedium,
                modifier = Modifier.align(Alignment.TopEnd).padding(s.m),
            )
            Stamp(
                shape = StampShape.Portrait,
                tilt = Tilt.stamp(3),
                denomination = stringResource(R.string.debug_sample_denomination),
                caption = stringResource(R.string.debug_sample_caption),
                modifier = Modifier.offset(16.dp, 36.dp).width(136.dp),
            ) { PreviewArt(PreviewArtKind.Sunset, Modifier.fillMaxSize()) }
            Stamp(
                shape = StampShape.Portrait,
                tilt = Tilt.stamp(7),
                modifier = Modifier.offset(186.dp, 40.dp).width(104.dp),
            ) { PreviewArt(PreviewArtKind.Sea, Modifier.fillMaxSize()) }
            Stamp(
                shape = StampShape.Square,
                tilt = Tilt.stamp(11),
                modifier = Modifier.offset(232.dp, 196.dp).width(92.dp),
            ) { PreviewArt(PreviewArtKind.Night, Modifier.fillMaxSize()) }
            Postmark(
                city = stringResource(R.string.debug_sample_city),
                date = stringResource(R.string.debug_sample_date_short),
                rotation = Tilt.postmark(1),
                color = c.textMedium,
                diameter = 62.dp,
                modifier = Modifier.offset(168.dp, 140.dp),
            )
            HandNote(
                stringResource(R.string.debug_sample_note_1),
                rotation = Tilt.note(1),
                modifier = Modifier.offset(28.dp, 236.dp),
            )
            HandNote(
                stringResource(R.string.debug_sample_note_2),
                rotation = Tilt.note(2),
                strength = InkStrength.Soft,
                style = StampTheme.type.noteSmall,
                modifier = Modifier.offset(36.dp, 276.dp),
            )
            HandNote(
                stringResource(R.string.debug_sample_note_3),
                rotation = Tilt.note(3),
                style = StampTheme.type.noteSmall,
                modifier = Modifier.offset(240.dp, 300.dp),
            )
        }
        AirmailEnvelope(Modifier.fillMaxWidth()) {
            Row(
                Modifier.padding(s.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.m),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.debug_specimen_card_title), style = StampTheme.type.cardTitle)
                    Text(stringResource(R.string.debug_specimen_body), style = StampTheme.type.body, color = c.textMedium)
                }
                // Placeholder for the real red pill button (buttons come with the Home screen).
                Box(
                    Modifier
                        .background(c.airmailRed, StampTheme.shapes.pill)
                        .padding(horizontal = s.l, vertical = s.s),
                ) {
                    Text(stringResource(R.string.debug_specimen_button), style = StampTheme.type.button, color = c.textOnDarkHigh)
                }
            }
        }
        StampNavBar(selected = NavTab.Album, onSelect = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StampShapes() {
    val s = StampTheme.spacing
    GrainedSurface(StampTheme.colors.kraft, Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.padding(s.l),
            horizontalArrangement = Arrangement.spacedBy(s.l),
            verticalArrangement = Arrangement.spacedBy(s.l),
        ) {
            StampShape.entries.forEachIndexed { i, shape ->
                Stamp(
                    shape = shape,
                    tilt = Tilt.stamp(i.toLong() + 20),
                    modifier = Modifier.width(if (shape == StampShape.Landscape) 160.dp else 120.dp),
                ) { PreviewArt(PreviewArtKind.entries[i % PreviewArtKind.entries.size], Modifier.fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun EdgeStyles() {
    val s = StampTheme.spacing
    Column(Modifier.padding(horizontal = s.screen), verticalArrangement = Arrangement.spacedBy(s.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(s.m)) {
            PerforationStyle.entries.forEach { style ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Stamp(StampShape.Portrait, perforation = style, modifier = Modifier.width(72.dp)) {
                        PreviewArt(PreviewArtKind.Sunset, Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(s.xs))
                    Text(style.name, style = StampTheme.type.label, color = StampTheme.colors.textLow)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(s.m)) {
            PerforationStyle.entries.forEach { style ->
                Stamp(StampShape.Circle, perforation = style, modifier = Modifier.width(72.dp)) {
                    PreviewArt(PreviewArtKind.Sea, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun Postmarks() {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    Row(
        Modifier.padding(horizontal = s.screen),
        horizontalArrangement = Arrangement.spacedBy(s.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Postmark(
            city = stringResource(R.string.debug_sample_city),
            date = stringResource(R.string.debug_sample_date_short),
            rotation = Tilt.postmark(4),
        )
        Box(Modifier.background(c.ink, StampTheme.shapes.card).padding(s.m)) {
            Postmark(
                city = stringResource(R.string.debug_sample_city),
                date = stringResource(R.string.debug_sample_date_long),
                color = c.postmarkOnDark,
                showWaves = false,
                dashedOuter = true,
                diameter = 80.dp,
                rotation = Tilt.postmark(5),
            )
        }
    }
}

@Composable
private fun Stripes() {
    val s = StampTheme.spacing
    Column(Modifier.padding(horizontal = s.screen), verticalArrangement = Arrangement.spacedBy(s.s)) {
        AirmailStripe(Modifier.fillMaxWidth().height(s.xs))
        AirmailStripe(Modifier.fillMaxWidth().height(s.xs), accent = StampTheme.colors.ink)
    }
}

@Composable
private fun Grains() {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    Row(Modifier.padding(horizontal = s.screen), horizontalArrangement = Arrangement.spacedBy(s.m)) {
        GrainedSurface(c.kraft, Modifier.size(96.dp), StampTheme.shapes.card)
        GrainedSurface(c.cobalt, Modifier.size(96.dp), StampTheme.shapes.card)
        GrainedSurface(c.sage, Modifier.size(width = 80.dp, height = 96.dp), StampTheme.shapes.albumCover)
    }
}

@Composable
private fun Notes() {
    val s = StampTheme.spacing
    Column(Modifier.padding(horizontal = s.screen), verticalArrangement = Arrangement.spacedBy(s.xs)) {
        HandNote(stringResource(R.string.debug_sample_note_1), rotation = Tilt.note(1))
        HandNote(
            stringResource(R.string.debug_sample_note_2),
            rotation = Tilt.note(2),
            strength = InkStrength.Soft,
            style = StampTheme.type.noteSmall,
        )
        HandNote(stringResource(R.string.debug_specimen_note_small), style = StampTheme.type.noteLarge)
    }
}

@Composable
private fun NavBar() {
    var tab by rememberSaveable { mutableStateOf(NavTab.Album) }
    StampNavBar(
        selected = tab,
        onSelect = { tab = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = StampTheme.spacing.screenFullBleed),
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 2400)
@Composable
private fun DebugGalleryPreview() {
    StampAppTheme { DebugGallery() }
}
