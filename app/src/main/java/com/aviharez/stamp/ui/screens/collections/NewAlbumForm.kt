package com.aviharez.stamp.ui.screens.collections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.AlbumCategory
import com.aviharez.stamp.domain.model.AlbumCoverColor
import com.aviharez.stamp.ui.components.AlbumCover
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.components.label
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** Name + kind + cover colour for a new album. Used in a bottom sheet (Stamped!, Collections). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewAlbumForm(
    onCreate: (name: String, category: AlbumCategory, cover: AlbumCoverColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    var name by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(AlbumCategory.Trips) }
    var cover by rememberSaveable { mutableStateOf(AlbumCoverColor.Ink) }
    val canCreate = name.isNotBlank()
    val submit = { if (canCreate) onCreate(name.trim(), category, cover) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.m)) {
        Text(stringResource(R.string.album_new_title), style = StampTheme.type.cardTitle, color = c.textHigh)

        BasicTextField(
            value = name,
            onValueChange = { name = it.take(NameMaxLength) },
            singleLine = true,
            textStyle = StampTheme.type.albumName.copy(color = c.textHigh),
            cursorBrush = SolidColor(c.airmailRed),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(c.paperWhite, StampTheme.shapes.tile)
                        .border(1.dp, c.outline, StampTheme.shapes.tile)
                        .padding(horizontal = s.m, vertical = s.s),
                ) {
                    if (name.isEmpty()) {
                        Text(stringResource(R.string.album_new_name_hint), style = StampTheme.type.albumName, color = c.textLow)
                    }
                    field()
                }
            },
        )

        Text(stringResource(R.string.album_new_category).uppercase(), style = StampTheme.type.overline, color = c.textMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.xs), verticalArrangement = Arrangement.spacedBy(s.xs)) {
            AlbumCategory.entries.forEach {
                PaperChip(stringResource(it.label), selected = it == category, onClick = { category = it })
            }
        }

        Text(stringResource(R.string.album_new_cover).uppercase(), style = StampTheme.type.overline, color = c.textMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(s.s)) {
            AlbumCoverColor.entries.forEach { option ->
                val label = stringResource(option.label)
                Box(
                    Modifier
                        .size(SwatchWidth, SwatchHeight)
                        .selectable(selected = option == cover, role = Role.RadioButton, onClick = { cover = option })
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    AlbumCover(option, Modifier.matchParentSize(), spineWidth = SwatchSpine, elevation = Elevation.Low)
                    if (option == cover) {
                        Icon(painterResource(R.drawable.ic_check), null, tint = c.paperWhite, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        PillButton(
            stringResource(R.string.album_new_create),
            onClick = submit,
            enabled = canCreate,
            style = if (canCreate) PillStyle.Red else PillStyle.Outline,
            modifier = Modifier.fillMaxWidth().padding(top = s.xs),
        )
    }
}

private const val NameMaxLength = 40
private val SwatchWidth = 44.dp
private val SwatchHeight = 56.dp
private val SwatchSpine = 6.dp

@PaperPreview
@Composable
private fun NewAlbumFormPreview() {
    StampAppTheme { NewAlbumForm(onCreate = { _, _, _ -> }, modifier = Modifier.padding(StampTheme.spacing.screen)) }
}
