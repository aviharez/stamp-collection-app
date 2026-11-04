package com.aviharez.stamp.ui.screens.stamped

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.domain.model.AlbumSummary
import com.aviharez.stamp.ui.components.AlbumCover
import com.aviharez.stamp.ui.components.dashedOutline
import com.aviharez.stamp.ui.screens.collections.AlbumForm
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.StampTheme

/** Choose which album the new stamp lives in, or start a new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPickerSheet(
    albums: List<AlbumSummary>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onCreate: (String, AlbumCategory, AlbumCoverColor) -> Unit,
    onDismiss: () -> Unit,
    canCreate: Boolean = true,
    onNeedPro: () -> Unit = {},
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var creating by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.paper,
        contentColor = c.textHigh,
    ) {
        Box(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = s.screen)
                .padding(bottom = s.l),
        ) {
            if (creating) {
                AlbumForm(onSubmit = { name, category, cover ->
                    onCreate(name, category, cover)
                    onDismiss()
                })
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(s.s)) {
                    Text(stringResource(R.string.album_picker_title), style = StampTheme.type.cardTitle)
                    LazyColumn(
                        Modifier.heightIn(max = ListMaxHeight),
                        verticalArrangement = Arrangement.spacedBy(s.xs),
                    ) {
                        items(albums, key = { it.album.id }) { summary ->
                            AlbumRow(summary, selected = summary.album.id == selectedId, onClick = {
                                onPick(summary.album.id)
                                onDismiss()
                            })
                        }
                    }
                    NewAlbumRow(onClick = { if (canCreate) creating = true else onNeedPro() })
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(summary: AlbumSummary, selected: Boolean, onClick: () -> Unit) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val selectedText = stringResource(R.string.album_selected_cd)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .background(if (selected) c.paperWhite else c.paper, StampTheme.shapes.tile)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { if (selected) stateDescription = selectedText }
            .padding(horizontal = s.s, vertical = s.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.s),
    ) {
        AlbumCover(summary.album.cover, Modifier.size(CoverWidth, CoverHeight), spineWidth = CoverSpine, elevation = Elevation.Low)
        Column(Modifier.weight(1f)) {
            Text(summary.album.name, style = StampTheme.type.albumName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                pluralStringResource(R.plurals.album_stamp_count, summary.stampCount, summary.stampCount),
                style = StampTheme.type.noteSmall,
                color = c.handInkSoft,
            )
        }
        if (selected) {
            Icon(painterResource(R.drawable.ic_check), null, tint = c.airmailRed, modifier = Modifier.size(s.l))
        }
    }
}

@Composable
private fun NewAlbumRow(onClick: () -> Unit) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = RowHeight)
            .dashedOutline(c.outline)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = s.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.s),
    ) {
        Icon(painterResource(R.drawable.ic_plus), null, tint = c.textHigh, modifier = Modifier.size(s.l))
        Text(stringResource(R.string.album_new), style = StampTheme.type.button, color = c.textHigh)
    }
}

private val RowHeight = 64.dp
private val CoverWidth = 36.dp
private val CoverHeight = 46.dp
private val CoverSpine = 5.dp
private val ListMaxHeight = 320.dp
