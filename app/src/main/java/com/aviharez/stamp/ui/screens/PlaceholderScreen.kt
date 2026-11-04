package com.aviharez.stamp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** Temporary screen for tabs whose real screen arrives in a later [milestone]. */
@Composable
fun PlaceholderScreen(
    title: String,
    comingSoon: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    val s = StampTheme.spacing
    Column(
        modifier
            .fillMaxSize()
            .background(StampTheme.colors.paper)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(horizontal = s.screen, vertical = s.l),
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        Text(title, style = StampTheme.type.screenTitle)
        if (comingSoon) {
            HandNote(stringResource(R.string.placeholder_note), rotation = Tilt.note(NoteSeed), strength = InkStrength.Soft)
        }
        extra()
    }
}

@PaperPreview
@Composable
private fun PlaceholderScreenPreview() {
    StampAppTheme { PlaceholderScreen("Map", comingSoon = true, PaddingValues()) }
}

private const val NoteSeed = 5L
