package com.aviharez.stamp.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow

/** Debug-only sheet of every colour and type token. Swatch labels are token names. */
@Composable
fun ThemeSpecimen(modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val t = StampTheme.type
    val s = StampTheme.spacing
    val swatches = listOf(
        "Paper" to c.paper, "PaperWhite" to c.paperWhite, "Ink" to c.ink,
        "AirmailRed" to c.airmailRed, "Cobalt" to c.cobalt, "Kraft" to c.kraft,
        "KraftDark" to c.kraftDark, "Sage" to c.sage, "Ember" to c.ember, "Surface2" to c.surface2,
    )
    Column(modifier.padding(s.screen), verticalArrangement = Arrangement.spacedBy(s.m)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(s.xs),
            verticalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            swatches.forEach { (name, color) -> Swatch(name, color) }
        }
        Text(stringResource(R.string.debug_specimen_overline), style = t.overline)
        Text(stringResource(R.string.debug_specimen_title), style = t.screenTitle)
        Text(stringResource(R.string.debug_specimen_card_title), style = t.cardTitle)
        Text(stringResource(R.string.debug_specimen_album), style = t.albumName)
        Text(stringResource(R.string.debug_specimen_body), style = t.body, color = c.textMedium)
        Text(stringResource(R.string.debug_specimen_label), style = t.label, color = c.textLow)
        Text(stringResource(R.string.debug_specimen_button), style = t.button)
        Text(stringResource(R.string.debug_specimen_note), style = t.note)
        Text(stringResource(R.string.debug_specimen_note_small), style = t.noteSmall, color = c.textMedium)
        Box(Modifier.background(c.cobalt, StampTheme.shapes.card).padding(s.m)) {
            Column {
                Text(stringResource(R.string.debug_specimen_hero), style = t.hero, color = c.textOnDarkHigh)
                Text(stringResource(R.string.debug_specimen_hero_sub), style = t.body, color = c.textOnDarkMedium)
            }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(StampTheme.spacing.l)
                .tintedShadow(Elevation.Low, StampTheme.shapes.plate, StampTheme.colors.shadowWarm)
                .background(color, StampTheme.shapes.plate),
        )
        Text(
            name,
            style = StampTheme.type.label,
            modifier = Modifier.padding(start = StampTheme.spacing.xxs, end = StampTheme.spacing.xs),
        )
    }
}

@Preview(name = "Theme tokens", showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 900)
@Composable
private fun ThemeSpecimenPreview() {
    StampAppTheme {
        ThemeSpecimen(Modifier.background(StampTheme.colors.paper))
    }
}
