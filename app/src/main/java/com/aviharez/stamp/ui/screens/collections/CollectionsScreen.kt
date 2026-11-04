package com.aviharez.stamp.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow

/**
 * Collections list (Album tab). Skeleton: two placeholder covers until Room data + the real
 * AlbumCover component arrive in milestone 2.
 */
@Composable
fun CollectionsScreen(
    contentPadding: PaddingValues,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(horizontal = s.screen, vertical = s.l),
        verticalArrangement = Arrangement.spacedBy(s.l),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
            Text(stringResource(R.string.collections_overline).uppercase(locale), style = StampTheme.type.overline)
            Text(stringResource(R.string.collections_title), style = StampTheme.type.screenTitle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(s.m)) {
            PlaceholderCover(1, c.ink, Modifier.weight(1f)) { onOpenAlbum(1) }
            PlaceholderCover(2, c.sage, Modifier.weight(1f)) { onOpenAlbum(2) }
        }
        HandNote(
            stringResource(R.string.placeholder_note, 2),
            rotation = Tilt.note(2),
            strength = InkStrength.Soft,
        )
    }
}

@Composable
private fun PlaceholderCover(number: Long, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val shape = StampTheme.shapes.albumCover
    Box(
        modifier
            .aspectRatio(CoverAspect)
            .tintedShadow(Elevation.Card, shape, c.shadowWarm)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        GrainedSurface(color, Modifier.fillMaxSize())
        // Spine: a darker strip on the start side.
        Box(
            Modifier
                .fillMaxHeight()
                .width(SpineWidth)
                .background(c.ink.copy(alpha = SpineShade)),
        )
        Text(
            stringResource(R.string.album_placeholder_name, number),
            style = StampTheme.type.albumName,
            color = c.ink,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = SpineWidth + s.xs, end = s.xs, bottom = s.m)
                .fillMaxWidth()
                .background(c.paperWhite, StampTheme.shapes.plate)
                .padding(horizontal = s.s, vertical = s.xs),
        )
    }
}

private const val CoverAspect = 0.78f
private const val SpineShade = 0.22f
private val SpineWidth = 12.dp

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 700)
@Composable
private fun CollectionsScreenPreview() {
    StampAppTheme { CollectionsScreen(PaddingValues(), {}) }
}
