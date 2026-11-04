package com.aviharez.stamp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/**
 * A label for a fixed-size control (button, chip, tile, plan stamp). At large font sizes it shrinks
 * just enough to fit its space, never clipped and never broken in the middle of a word, down to
 * [minScale] of the style's size; only below that does it end in "…". Normal sizes are untouched.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    minScale: Float = DefaultMinScale,
) {
    val max = style.fontSize
    Text(
        text,
        modifier = modifier,
        color = color,
        style = if (textAlign != null) style.copy(textAlign = textAlign) else style,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = if (max.isSpecified) WholeWords(max * minScale, max) else null,
    )
}

/** Largest size (binary search, [Step] precision) whose layout fits and keeps every word whole. */
private data class WholeWords(val min: TextUnit, val max: TextUnit) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val step = Step.toPx()
        var low = min.toPx()
        var high = max.toPx()
        if (fits(performLayout(constraints, text, high.toSp()), text)) return max
        while (high - low > step) {
            val mid = (low + high) / 2f
            if (fits(performLayout(constraints, text, mid.toSp()), text)) low = mid else high = mid
        }
        return low.toSp()
    }

    private fun fits(layout: TextLayoutResult, text: AnnotatedString): Boolean {
        if (layout.didOverflowWidth || layout.didOverflowHeight) return false
        if (layout.lineCount > 0 && layout.isLineEllipsized(layout.lineCount - 1)) return false
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            if (end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()) return false
        }
        return true
    }
}

private const val DefaultMinScale = 0.6f
private val Step = 0.5.sp

@PaperPreview
@Composable
private fun FitTextPreview() {
    StampAppTheme {
        Column(Modifier.padding(StampTheme.spacing.s), verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.xs)) {
            FitText("Fits as it is", StampTheme.type.button)
            FitText("A label much too long for this", StampTheme.type.button, Modifier.width(120.dp))
            FitText("Unlimited albums", StampTheme.type.label, Modifier.width(64.dp), maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}
