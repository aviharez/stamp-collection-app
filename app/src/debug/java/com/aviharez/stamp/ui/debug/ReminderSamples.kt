package com.aviharez.stamp.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.aviharez.stamp.R
import com.aviharez.stamp.data.reminder.ReminderEntryPoint
import com.aviharez.stamp.domain.usecase.Streak
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.theme.StampTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Shows the streak reminder note without waiting for the evening (notifications must be allowed). */
@Composable
fun ReminderSamples(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reminder = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java).streakReminder()
    Column(modifier.padding(horizontal = StampTheme.spacing.screen), verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.xs)) {
        PillButton(stringResource(R.string.debug_reminder_streak), onClick = {
            reminder.showSample(Streak(current = SampleStreak, best = SampleStreak, stampedToday = false))
        })
        PillButton(stringResource(R.string.debug_reminder_start), onClick = { reminder.showSample(Streak.None) })
        PillButton(stringResource(R.string.debug_reminder_ring), onClick = { scope.launch { reminder.ring(LocalDate.now()) } })
    }
}

private const val SampleStreak = 12
