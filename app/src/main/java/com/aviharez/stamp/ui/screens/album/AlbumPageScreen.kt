package com.aviharez.stamp.ui.screens.album

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** Album page (collection detail). Skeleton: the 3-column stamp grid comes in milestone 2. */
@Composable
fun AlbumPageScreen(
    albumId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(vertical = s.m),
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        CircleIconButton(
            icon = R.drawable.ic_back,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
            modifier = Modifier.padding(horizontal = s.screenFullBleed),
        )
        Text(
            stringResource(R.string.album_placeholder_name, albumId),
            style = StampTheme.type.screenTitle,
            modifier = Modifier.padding(horizontal = s.screen),
        )
        GrainedSurface(
            color = StampTheme.colors.kraft,
            shape = StampTheme.shapes.card,
            modifier = Modifier
                .padding(horizontal = s.screenFullBleed)
                .fillMaxWidth()
                .weight(1f),
        ) {
            HandNote(
                stringResource(R.string.album_next_stamp),
                rotation = Tilt.note(albumId),
                strength = InkStrength.Soft,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Row(
            Modifier.padding(horizontal = s.screenFullBleed),
            horizontalArrangement = Arrangement.spacedBy(s.s),
        ) {
            PillButton(stringResource(R.string.album_map), onClick = {}, style = PillStyle.Outline, modifier = Modifier.weight(1f))
            PillButton(stringResource(R.string.album_share_page), onClick = {}, style = PillStyle.Ink, modifier = Modifier.weight(2f))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 800)
@Composable
private fun AlbumPageScreenPreview() {
    StampAppTheme { AlbumPageScreen(albumId = 1, onBack = {}) }
}
