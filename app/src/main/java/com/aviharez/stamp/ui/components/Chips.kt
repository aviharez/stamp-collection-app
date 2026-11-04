package com.aviharez.stamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.aviharez.stamp.R
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** Filter / choice chip on Paper: Ink fill when selected, Ink outline otherwise. */
@Composable
fun PaperChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val shape = StampTheme.shapes.pill
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source)
    val content = if (selected) c.textOnDarkHigh else c.textHigh
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = StampTheme.spacing.touchTargetMin)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = StampTheme.type.chip,
            color = content,
            modifier = Modifier
                .clip(shape)
                .then(if (selected) Modifier.background(c.ink) else Modifier.border(BorderWidth, c.outline, shape))
                .clickableWithRipple(source, true, content, onClick)
                .padding(horizontal = StampTheme.spacing.m, vertical = StampTheme.spacing.xs),
        )
    }
}

/**
 * "… · short ad" chip: always an outline next to the filled Pro chip, so the ad is the quiet,
 * optional choice. [loading] = consent/ad on its way (not tappable again).
 */
@Composable
fun AdChip(text: String, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val shape = StampTheme.shapes.pill
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = StampTheme.spacing.touchTargetMin),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(shape)
                .border(BorderWidth, c.outline, shape)
                .clickableWithRipple(source, !loading, c.textHigh, onClick)
                .padding(horizontal = StampTheme.spacing.s, vertical = StampTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.xxs),
        ) {
            Icon(
                painterResource(R.drawable.ic_play_ad),
                contentDescription = null,
                tint = if (loading) c.textLow else c.textHigh,
                modifier = Modifier.size(AdIconSize),
            )
            FitText(
                if (loading) stringResource(R.string.ad_loading) else text,
                StampTheme.type.chip,
                Modifier.weight(1f, fill = false),
                color = if (loading) c.textMedium else c.textHigh,
            )
        }
    }
}

private val BorderWidth = 1.dp
private val AdIconSize = 16.dp

@PaperPreview
@Composable
private fun PaperChipPreview() {
    StampAppTheme {
        Row(Modifier.padding(StampTheme.spacing.m), horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.xs)) {
            PaperChip("All", selected = true, onClick = {})
            PaperChip("Trips", selected = false, onClick = {})
            PaperChip("Pets", selected = false, onClick = {})
        }
    }
}

@PaperPreview
@Composable
private fun AdChipPreview() {
    StampAppTheme {
        Row(Modifier.padding(StampTheme.spacing.m), horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.xs)) {
            PaperChip("Remove with Pro", selected = true, onClick = {})
            AdChip("Once · short ad", loading = false, onClick = {})
        }
    }
}
