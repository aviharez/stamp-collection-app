package com.aviharez.stamp.ui.screens.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow

/**
 * One-time, optional ask before the system location dialog: the city only goes on the
 * postmark. "Not now" keeps date-only postmarks and never asks again here.
 */
@Composable
fun LocationAsk(onAllow: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    Column(
        modifier
            .widthIn(max = MaxWidth)
            .tintedShadow(Elevation.Raised, StampTheme.shapes.card, c.shadowNavy)
            .background(c.paperWhite, StampTheme.shapes.card)
            .padding(s.m),
        verticalArrangement = Arrangement.spacedBy(s.xxs),
    ) {
        Text(stringResource(R.string.location_ask_title), style = StampTheme.type.albumName, color = c.textHigh)
        Text(stringResource(R.string.location_ask_body), style = StampTheme.type.body, color = c.textMedium)
        Row(
            Modifier.padding(top = s.xs).align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            PillButton(stringResource(R.string.location_ask_not_now), onClick = onNotNow, style = PillStyle.Outline)
            PillButton(stringResource(R.string.location_ask_allow), onClick = onAllow, style = PillStyle.Red)
        }
    }
}

private val MaxWidth = 300.dp

@PaperPreview
@Composable
private fun LocationAskPreview() {
    StampAppTheme {
        LocationAsk(onAllow = {}, onNotNow = {}, modifier = Modifier.padding(StampTheme.spacing.m))
    }
}
