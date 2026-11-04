package com.aviharez.stamp.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.AirmailEnvelope
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/**
 * Home (Album tab). Skeleton for milestone 1: the real album page with latest stamps, streak
 * chip and "today's stamp" state come with Room data in milestone 2.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onStampIt: () -> Unit,
    onOpenCollections: () -> Unit,
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
            .padding(top = s.l, bottom = s.m),
        verticalArrangement = Arrangement.spacedBy(s.l),
    ) {
        Column(Modifier.padding(horizontal = s.screen), verticalArrangement = Arrangement.spacedBy(s.xs)) {
            Text(rememberDateText(R.string.pattern_overline_date).uppercase(locale), style = StampTheme.type.overline)
            Text(stringResource(R.string.home_title), style = StampTheme.type.screenTitle)
        }

        // Album page — empty until milestone 2.
        GrainedSurface(
            color = c.kraft,
            shape = StampTheme.shapes.card,
            modifier = Modifier
                .padding(horizontal = s.screenFullBleed)
                .fillMaxWidth()
                .weight(1f)
                .rotate(PageTilt),
        ) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(s.xs)
                    .clip(StampTheme.shapes.pill)
                    .clickable(role = Role.Button, onClick = onOpenCollections)
                    .heightIn(min = s.touchTarget)
                    .padding(horizontal = s.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.xxs),
            ) {
                Text(
                    stringResource(R.string.home_all_albums).uppercase(locale),
                    style = StampTheme.type.overline,
                    color = c.textMedium,
                )
                Icon(
                    painterResource(R.drawable.ic_arrow_right),
                    contentDescription = null,
                    tint = c.textMedium,
                    modifier = Modifier.size(s.m),
                )
            }
            HandNote(
                stringResource(R.string.home_empty_page),
                rotation = Tilt.note(HomeSeed),
                strength = InkStrength.Soft,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AirmailEnvelope(Modifier.padding(horizontal = s.screenFullBleed).fillMaxWidth()) {
            Row(
                Modifier.padding(s.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.m),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_today_title), style = StampTheme.type.cardTitle)
                    Text(
                        stringResource(R.string.home_today_body, rememberDateText(R.string.pattern_weekday)),
                        style = StampTheme.type.body,
                        color = c.textMedium,
                    )
                }
                PillButton(
                    text = stringResource(R.string.home_stamp_it),
                    onClick = onStampIt,
                    leadingIcon = R.drawable.ic_camera,
                )
            }
        }
    }
}

private const val PageTilt = -1f
private const val HomeSeed = 7L

@Preview(showBackground = true, backgroundColor = 0xFFF6F0E6, heightDp = 800)
@Composable
private fun HomeScreenPreview() {
    StampAppTheme { Box { HomeScreen(PaddingValues(), {}, {}) } }
}
