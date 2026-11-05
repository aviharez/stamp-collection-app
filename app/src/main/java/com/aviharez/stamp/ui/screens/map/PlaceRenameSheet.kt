package com.aviharez.stamp.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/**
 * The place's name as its postmarks print it, e.g. when the phone's geocoder named the district
 * ("Kelapa Dua") instead of the city ("Tangerang"). Blank = the country only.
 */
@Composable
fun PlaceRenameForm(
    current: String?,
    stampCount: Int,
    countryName: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    var name by rememberSaveable { mutableStateOf(current.orEmpty()) }
    val changed = name.trim() != current.orEmpty()
    val save = { if (changed) onSave(name.trim()) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.m)) {
        Column(verticalArrangement = Arrangement.spacedBy(s.xxs)) {
            Text(stringResource(R.string.map_rename_title), style = StampTheme.type.cardTitle, color = c.textHigh)
            HandNote(
                if (name.isBlank()) {
                    stringResource(R.string.map_rename_empty_note, countryName)
                } else {
                    pluralStringResource(R.plurals.map_rename_note, stampCount, stampCount)
                },
                style = StampTheme.type.noteSmall,
                strength = InkStrength.Soft,
            )
        }
        BasicTextField(
            value = name,
            onValueChange = { name = it.take(NameMaxLength) },
            singleLine = true,
            textStyle = StampTheme.type.albumName.copy(color = c.textHigh),
            cursorBrush = SolidColor(c.airmailRed),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(c.paperWhite, StampTheme.shapes.tile)
                        .border(FieldBorder, c.outline, StampTheme.shapes.tile)
                        .padding(horizontal = s.m, vertical = s.s),
                ) {
                    if (name.isEmpty()) {
                        Text(stringResource(R.string.map_rename_hint), style = StampTheme.type.albumName, color = c.textLow)
                    }
                    field()
                }
            },
        )
        PillButton(
            stringResource(R.string.map_rename_save),
            onClick = save,
            enabled = changed,
            style = if (changed) PillStyle.Red else PillStyle.Outline,
            modifier = Modifier.fillMaxWidth().padding(top = s.xs),
        )
    }
}

/** [PlaceRenameForm] in a Paper bottom sheet; closes itself after a save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceRenameSheet(
    place: MapPlace,
    countryName: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.paper,
        contentColor = c.textHigh,
    ) {
        PlaceRenameForm(
            current = place.city,
            stampCount = place.stamps.size,
            countryName = countryName,
            onSave = {
                onSave(it)
                onDismiss()
            },
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = s.screen)
                .padding(bottom = s.l),
        )
    }
}

/** Same limit as an album name: it has to fit on a postmark. */
private const val NameMaxLength = 40
private val FieldBorder = 1.dp

@PaperPreview
@Composable
private fun PlaceRenameFormPreview() {
    StampAppTheme {
        PlaceRenameForm("Kelapa Dua", 7, "Indonesia", onSave = {}, modifier = Modifier.padding(StampTheme.spacing.screen))
    }
}
