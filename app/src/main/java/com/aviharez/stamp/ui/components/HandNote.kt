package com.aviharez.stamp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** How hard the pen pressed: full Ink, or Ink at 72 %. */
enum class InkStrength { Strong, Soft }

/**
 * Handwritten note in Caveat with a slight tilt (album captions, dates under stamps, counts).
 * Use [Tilt.note] for [rotation]. Pass [color] only on dark surfaces (e.g. textOnDarkMedium).
 */
@Composable
fun HandNote(
    text: String,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    strength: InkStrength = InkStrength.Strong,
    style: TextStyle = StampTheme.type.note,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = Color.Unspecified,
) {
    val colors = StampTheme.colors
    Text(
        text = text,
        style = style,
        color = color.takeOrElse { if (strength == InkStrength.Strong) colors.handInk else colors.handInkSoft },
        maxLines = maxLines,
        modifier = modifier.rotate(rotation),
    )
}

@PaperPreview
@Composable
private fun HandNotePreview() {
    StampAppTheme {
        Column(
            Modifier.padding(StampTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.xs),
        ) {
            HandNote("sunrise hike!!", rotation = Tilt.note(1))
            HandNote("legs = dead, worth it", rotation = Tilt.note(2), strength = InkStrength.Soft, style = StampTheme.type.noteSmall)
            HandNote("42 stamps · 15 days", style = StampTheme.type.noteLarge)
        }
    }
}
