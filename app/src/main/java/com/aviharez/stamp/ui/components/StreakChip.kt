package com.aviharez.stamp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.usecase.Streak
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow
import androidx.compose.foundation.layout.Column

/** "🔥 13 days in a row · personal best" pill. Hidden by callers when there is no streak. */
@Composable
fun StreakChip(streak: Streak, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val days = pluralStringResource(R.plurals.streak_days, streak.current, streak.current)
    val text = when {
        streak.current <= 1 -> stringResource(R.string.streak_day_one)
        streak.current >= streak.best -> stringResource(R.string.streak_personal_best, days)
        else -> days
    }
    Row(
        modifier
            .semantics(mergeDescendants = true) {}
            .tintedShadow(Elevation.Low, StampTheme.shapes.pill, c.shadowNavy)
            .background(c.paperWhite, StampTheme.shapes.pill)
            .padding(horizontal = s.m, vertical = s.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.xs),
    ) {
        Icon(painterResource(R.drawable.ic_flame), null, tint = c.ember, modifier = Modifier.size(FlameSize))
        Text(text, style = StampTheme.type.label, color = c.textHigh)
    }
}

private val FlameSize = 16.dp

@PaperPreview
@Composable
private fun StreakChipPreview() {
    StampAppTheme {
        Column(Modifier.padding(StampTheme.spacing.m), verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.s)) {
            StreakChip(Streak(1, 1, true))
            StreakChip(Streak(13, 13, true))
            StreakChip(Streak(4, 20, true))
        }
    }
}
