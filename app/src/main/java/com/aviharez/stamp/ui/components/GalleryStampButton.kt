package com.aviharez.stamp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import java.io.File

/**
 * "Pick from gallery" as a mini stamp (design/camera.png, bottom left): the newest stamp when
 * there is one, else a kraft stamp with a picture icon. Tilted like it was just put down.
 */
@Composable
fun GalleryStampButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    latestPath: String? = null,
    latestShape: StampShape = StampShape.Portrait,
    enabled: Boolean = true,
) {
    val c = StampTheme.colors
    val source = remember { MutableInteractionSource() }
    val scale by pressScale(source)
    Box(
        modifier
            .size(StampTheme.spacing.touchTarget + StampTheme.spacing.xs)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (latestPath != null) {
            RenderedStamp(
                file = File(latestPath),
                shape = latestShape,
                contentDescription = contentDescription,
                tilt = MiniTilt,
                shadowTint = c.shadowNavy,
                modifier = Modifier.width(MiniWidth),
            )
        } else {
            Stamp(
                StampShape.Portrait,
                Modifier.width(MiniWidth),
                tilt = MiniTilt,
                shadowTint = c.shadowNavy,
                contentDescription = contentDescription,
            ) {
                GrainedSurface(c.kraft, Modifier.fillMaxSize()) {
                    Icon(
                        painterResource(R.drawable.ic_gallery),
                        contentDescription = null,
                        tint = c.ink,
                        modifier = Modifier.align(Alignment.Center).size(StampTheme.spacing.m + StampTheme.spacing.xxs),
                    )
                }
            }
        }
    }
}

private val MiniWidth = 40.dp
private const val MiniTilt = -8f

@PaperPreview
@Composable
private fun GalleryStampButtonPreview() {
    StampAppTheme {
        Row(Modifier.padding(StampTheme.spacing.l), horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.l)) {
            GalleryStampButton(onClick = {}, contentDescription = "Gallery")
            GalleryStampButton(onClick = {}, contentDescription = "Gallery", latestPath = "preview-2.png")
        }
    }
}
