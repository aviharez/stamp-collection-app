package com.aviharez.stamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow

/** Cloth colour token for an album cover. */
@Composable
fun AlbumCoverColor.color(): Color {
    val c = StampTheme.colors
    return when (this) {
        AlbumCoverColor.Ink -> c.ink
        AlbumCoverColor.Kraft -> c.kraftDark
        AlbumCoverColor.Sage -> c.sage
        AlbumCoverColor.Red -> c.airmailRed
        AlbumCoverColor.Cobalt -> c.cobalt
    }
}

/**
 * Closed album: grained cloth cover, darker spine on the left, optional cream name plate in
 * Fraunces italic. [content] sits on the cover (stamps, postmark…), behind the plate.
 *
 * Corners follow §5: 4dp on the spine side, 12dp on the open side.
 */
@Composable
fun AlbumCover(
    cover: AlbumCoverColor,
    modifier: Modifier = Modifier,
    name: String? = null,
    spineWidth: Dp = SpineWidth,
    elevation: Dp = Elevation.Card,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c = StampTheme.colors
    val cloth = cover.color()
    val spine = lerp(cloth, c.ink, SpineDarken)
    val shape = AlbumCoverShape
    Box(
        modifier
            .tintedShadow(elevation, shape, c.shadowWarm)
            .background(cloth, shape),
    ) {
        GrainedSurface(cloth, Modifier.matchParentSize(), shape = shape)
        Row(Modifier.matchParentSize()) {
            Box(Modifier.width(spineWidth).fillMaxHeight().background(spine, SpineShape))
        }
        Box(Modifier.matchParentSize().padding(start = spineWidth), content = content)
        if (name != null) {
            Text(
                name,
                style = StampTheme.type.albumName,
                color = c.textHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = spineWidth + StampTheme.spacing.s, end = StampTheme.spacing.s, bottom = StampTheme.spacing.m)
                    .fillMaxWidth()
                    .tintedShadow(Elevation.Low, StampTheme.shapes.plate, c.shadowWarm)
                    .background(c.paperWhite, StampTheme.shapes.plate)
                    .padding(horizontal = StampTheme.spacing.s, vertical = StampTheme.spacing.xxs),
            )
        }
    }
}

private val SpineWidth = 12.dp
private const val SpineDarken = 0.28f
private val AlbumCoverShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
private val SpineShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)

@PaperPreview
@Composable
private fun AlbumCoverPreview() {
    StampAppTheme {
        Row(
            Modifier.padding(StampTheme.spacing.m),
            horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.m),
        ) {
            AlbumCover(AlbumCoverColor.Ink, Modifier.width(150.dp).aspectRatio(0.8f), name = "Japan trip")
            AlbumCover(AlbumCoverColor.Sage, Modifier.width(150.dp).aspectRatio(0.8f), name = "Mochi the cat")
        }
    }
}
