package com.aviharez.stamp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/**
 * A big screen title with a handwritten note beside it ("Collections  3 albums · 14 stamps").
 * The title always gets the full width; the note sits on its baseline side when there is room and
 * drops underneath when there isn't (large fonts, long names), so the title never breaks mid-word.
 */
@Composable
fun TitleWithNote(
    modifier: Modifier = Modifier,
    note: (@Composable () -> Unit)? = null,
    title: @Composable () -> Unit,
) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.s),
        itemVerticalAlignment = Alignment.Bottom,
    ) {
        title()
        note?.invoke()
    }
}

@PaperPreview
@Composable
private fun TitleWithNotePreview() {
    StampAppTheme {
        TitleWithNote(
            Modifier.width(300.dp).padding(StampTheme.spacing.m),
            note = { HandNote("11 stamps · 3 days", style = StampTheme.type.noteLarge) },
        ) {
            Text("Japan trip", style = StampTheme.type.screenTitle)
        }
    }
}
