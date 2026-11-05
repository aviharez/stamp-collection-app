package com.aviharez.stamp.ui.screens.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aviharez.stamp.R
import com.aviharez.stamp.data.reminder.StreakReminder
import com.aviharez.stamp.data.settings.ReminderSetting
import com.aviharez.stamp.data.settings.SettingsRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.components.SwitchRow
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @AppScope private val appScope: CoroutineScope,
) : ViewModel() {
    /** Null until DataStore answers, so the entry doesn't flash "off" for someone who has it on. */
    val setting: StateFlow<ReminderSetting?> = settings.reminder.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // App scope: the write (and the re-arm StreakReminder does on it) must finish even if Profile closes.
    fun setOn(on: Boolean) {
        appScope.launch { settings.setReminderOn(on) }
    }

    fun setMinute(minuteOfDay: Int) {
        appScope.launch { settings.setReminderMinute(minuteOfDay) }
    }
}

/**
 * Profile: the evening streak reminder. Switching "Remind me to stamp" on is the pre-prompt; Android's
 * notification permission (13+) is asked only then. If notifications are blocked, says so and
 * offers the app's notification settings; coming back with them allowed finishes turning it on.
 */
@Composable
fun ReminderEntry(modifier: Modifier = Modifier, viewModel: ReminderViewModel = hiltViewModel()) {
    val setting by viewModel.setting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(StreakReminder.canPost(context)) }
    // The user asked to turn it on but notifications are blocked: on as soon as they're allowed.
    var wantsOn by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost = StreakReminder.canPost(context)
        if (granted && canPost) viewModel.setOn(true) else wantsOn = true
    }
    LifecycleResumeEffect(Unit) {
        canPost = StreakReminder.canPost(context)
        if (wantsOn && canPost) {
            wantsOn = false
            viewModel.setOn(true)
        }
        onPauseOrDispose {}
    }
    val current = setting ?: return
    ReminderContent(
        setting = current,
        blocked = !canPost && (current.on || wantsOn),
        onTurnOn = {
            when {
                StreakReminder.canPost(context) -> viewModel.setOn(true)
                needsPermission(context) -> ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                else -> wantsOn = true
            }
        },
        onTurnOff = {
            wantsOn = false
            viewModel.setOn(false)
        },
        onPickMinute = viewModel::setMinute,
        onOpenSettings = { context.startActivity(notificationSettings(context)) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderContent(
    setting: ReminderSetting,
    blocked: Boolean,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onPickMinute: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.xs)) {
        // Blocked counts as "on": the user asked for it, Android is what's stopping it.
        SwitchRow(
            stringResource(R.string.reminder_turn_on),
            checked = setting.on || blocked,
            onCheckedChange = { on -> if (on) onTurnOn() else onTurnOff() },
        )
        when {
            blocked -> {
                HandNote(
                    stringResource(R.string.reminder_blocked_note),
                    style = StampTheme.type.noteSmall,
                    strength = InkStrength.Strong,
                    color = StampTheme.colors.airmailRed,
                )
                PaperChip(stringResource(R.string.reminder_open_settings), selected = true, onClick = onOpenSettings)
            }
            setting.on -> {
                val format = rememberTimeFormat()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(s.xs), verticalArrangement = Arrangement.spacedBy(s.xs)) {
                    for (minute in ReminderMinutes) {
                        val label = format.format(minute.toTime())
                        val description = stringResource(R.string.reminder_time_label, label)
                        PaperChip(
                            label,
                            selected = minute == setting.minuteOfDay,
                            onClick = { onPickMinute(minute) },
                            modifier = Modifier.semantics { contentDescription = description },
                        )
                    }
                }
                HandNote(
                    stringResource(R.string.reminder_on_note, format.format(setting.minuteOfDay.toTime())),
                    style = StampTheme.type.noteSmall,
                    strength = InkStrength.Soft,
                )
            }
            else -> HandNote(stringResource(R.string.reminder_off_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
        }
    }
}

/** Follows the phone's 12/24-hour setting and language ("8:00 PM", "20:00"). */
@Composable
private fun rememberTimeFormat(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24 = DateFormat.is24HourFormat(context)
    return remember(locale, is24) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm"), locale)
    }
}

private fun Int.toTime(): LocalTime = LocalTime.of(this / MinutesPerHour, this % MinutesPerHour)

private fun needsPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED

private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private const val MinutesPerHour = 60

/** Evening choices, on the hour: 6 PM … 10 PM. */
private val ReminderMinutes = (18..22).map { it * MinutesPerHour }

@PaperPreview
@Composable
private fun ReminderContentPreview() {
    StampAppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.m)) {
            ReminderContent(ReminderSetting(on = false, minuteOfDay = ReminderSetting.DefaultMinute), blocked = false, {}, {}, {}, {})
            ReminderContent(ReminderSetting(on = true, minuteOfDay = ReminderSetting.DefaultMinute), blocked = false, {}, {}, {}, {})
            ReminderContent(ReminderSetting(on = true, minuteOfDay = ReminderSetting.DefaultMinute), blocked = true, {}, {}, {}, {})
        }
    }
}
