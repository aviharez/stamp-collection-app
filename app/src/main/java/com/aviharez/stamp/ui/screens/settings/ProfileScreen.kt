package com.aviharez.stamp.ui.screens.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.data.backup.BackupState
import com.aviharez.stamp.data.settings.ReminderSetting
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.AirmailEnvelope
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.components.PerforatedShape
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.components.Postmark
import com.aviharez.stamp.ui.components.RenderedStamp
import com.aviharez.stamp.ui.components.SwitchRow
import com.aviharez.stamp.ui.components.dashedOutline
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow
import java.io.File
import java.time.LocalDate

/**
 * Profile (concept A + B, 3 reviewed): a kraft "stamp passport" with the album's numbers on top,
 * then Pro (an airmail envelope for free users, a PRO postmark on the passport for Pro users),
 * then each group of settings on its own perforated stamp.
 */
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues,
    onGetPro: () -> Unit,
    modifier: Modifier = Modifier,
    footerExtras: @Composable () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var hasLocation by remember { mutableStateOf(hasLocation(context)) }
    // Asked and refused for good: Android won't show its dialog again, only its settings can.
    var locationBlocked by rememberSaveable { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocation = hasLocation(context)
        viewModel.onLocationAnswered(granted)
        locationBlocked = !granted && activity?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) == false
    }
    LifecycleResumeEffect(Unit) {
        hasLocation = hasLocation(context)
        if (locationBlocked && hasLocation) {
            // Back from Android's settings with location allowed: finish turning it on.
            locationBlocked = false
            viewModel.setPostmarkPlace(true)
        }
        onPauseOrDispose {}
    }
    ProfileContent(
        state = state,
        placeOn = state.postmarkPlace && hasLocation,
        locationBlocked = locationBlocked,
        contentPadding = contentPadding,
        onGetPro = onGetPro,
        onRestore = viewModel::restorePurchases,
        onMirrorSelfie = viewModel::setMirrorSelfie,
        onPostmarkPlace = { on ->
            when {
                !on -> {
                    locationBlocked = false
                    viewModel.setPostmarkPlace(false)
                }
                hasLocation -> viewModel.setPostmarkPlace(true)
                else -> askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        },
        onOpenAppSettings = { context.startActivity(appSettings(context)) },
        onRate = { openPlayStore(context) },
        versionName = remember(context) { versionName(context) },
        reminder = { ReminderEntry() },
        backup = { BackupEntry() },
        footerExtras = {
            AdPrivacyEntry()
            footerExtras()
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileContent(
    state: ProfileUiState,
    placeOn: Boolean,
    locationBlocked: Boolean,
    contentPadding: PaddingValues,
    onGetPro: () -> Unit,
    onRestore: () -> Unit,
    onMirrorSelfie: (Boolean) -> Unit,
    onPostmarkPlace: (Boolean) -> Unit,
    onOpenAppSettings: () -> Unit,
    onRate: () -> Unit,
    versionName: String?,
    reminder: @Composable () -> Unit,
    backup: @Composable () -> Unit,
    footerExtras: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val t = StampTheme.type
    Column(
        modifier
            .fillMaxSize()
            .background(StampTheme.colors.paper)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(contentPadding)
            .padding(horizontal = s.screen, vertical = s.l),
    ) {
        Text(
            stringResource(R.string.nav_profile),
            style = t.screenTitle,
            color = StampTheme.colors.textHigh,
            modifier = Modifier.semantics { heading() },
        )
        state.passport?.let { PassportCard(it, state.isPro, Modifier.padding(top = s.m)) }
        if (!state.isPro) ProCard(state.restore, onGetPro, onRestore, Modifier.padding(top = s.l))

        SettingsStamp(R.string.profile_section_reminder, R.drawable.ic_flame, tilt = ReminderTilt) { reminder() }
        SettingsStamp(R.string.profile_section_backup, R.drawable.ic_download, tilt = BackupTilt) { backup() }
        SettingsStamp(R.string.profile_section_camera, R.drawable.ic_camera, tilt = CameraTilt) {
            SwitchRow(stringResource(R.string.profile_mirror_selfies), state.mirrorSelfie, onMirrorSelfie)
            HandNote(stringResource(R.string.profile_mirror_note), style = t.noteSmall, strength = InkStrength.Soft)
            SwitchRow(
                stringResource(R.string.profile_postmark_place),
                placeOn,
                onPostmarkPlace,
                Modifier.padding(top = s.xs),
            )
            if (locationBlocked) {
                HandNote(
                    stringResource(R.string.profile_place_blocked_note),
                    style = t.noteSmall,
                    strength = InkStrength.Strong,
                    color = StampTheme.colors.airmailRed,
                )
                PaperChip(
                    stringResource(R.string.profile_open_settings),
                    selected = true,
                    onClick = onOpenAppSettings,
                    modifier = Modifier.padding(top = s.xs),
                )
            } else {
                HandNote(
                    stringResource(if (placeOn) R.string.profile_place_on_note else R.string.profile_place_off_note),
                    style = t.noteSmall,
                    strength = InkStrength.Soft,
                )
            }
        }

        FlowRow(
            Modifier.padding(top = s.l),
            horizontalArrangement = Arrangement.spacedBy(s.xs),
            verticalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            PaperChip(stringResource(R.string.profile_rate), selected = false, onClick = onRate)
            footerExtras()
        }
        if (versionName != null) {
            HandNote(
                stringResource(R.string.profile_version, versionName),
                Modifier.padding(top = s.s),
                style = t.noteSmall,
                strength = InkStrength.Soft,
            )
        }
    }
}

/** Kraft passport: newest stamp (or an empty slot) beside the numbers; Pro users get a PRO postmark. */
@Composable
private fun PassportCard(passport: Passport, isPro: Boolean, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val t = StampTheme.type
    Box(modifier.fillMaxWidth()) {
        GrainedSurface(c.kraft, Modifier.fillMaxWidth().tintedShadow(Elevation.Card, StampTheme.shapes.card, c.shadowWarm), shape = StampTheme.shapes.card) {
            Column(Modifier.padding(s.m)) {
                val since = passport.since
                Text(
                    if (since != null) {
                        stringResource(R.string.profile_passport_since, rememberDateText(R.string.pattern_passport_since, since))
                    } else {
                        stringResource(R.string.profile_passport)
                    }.uppercase(),
                    style = t.overline,
                    color = c.textMedium,
                    // Leaves room for the PRO postmark in the corner.
                    modifier = if (isPro) Modifier.padding(end = PostmarkRoom) else Modifier,
                )
                Row(Modifier.padding(top = s.s), horizontalArrangement = Arrangement.spacedBy(s.m)) {
                    val newest = passport.newest
                    if (newest != null) {
                        RenderedStamp(
                            file = File(newest.imagePath),
                            shape = newest.shape,
                            perforation = newest.perforation,
                            contentDescription = stringResource(R.string.profile_newest_stamp),
                            tilt = PassportStampTilt,
                            modifier = Modifier.width(PassportStampWidth),
                        )
                    } else {
                        Box(
                            Modifier
                                .width(PassportStampWidth)
                                .aspectRatio(StampShape.Portrait.aspectRatio)
                                .dashedOutline(c.textLow),
                            contentAlignment = Alignment.Center,
                        ) {
                            HandNote(
                                stringResource(R.string.profile_first_stamp_slot),
                                Modifier.padding(s.xs),
                                style = t.noteSmall,
                                strength = InkStrength.Soft,
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s.xs)) {
                        PassportField(stringResource(R.string.profile_stamps), passport.stamps.toString())
                        PassportField(
                            stringResource(R.string.profile_albums_places),
                            stringResource(R.string.profile_albums_places_value, passport.albums, passport.places),
                        )
                        PassportField(
                            stringResource(R.string.profile_best_streak),
                            pluralStringResource(R.plurals.profile_days, passport.bestStreak, passport.bestStreak),
                        )
                    }
                }
            }
        }
        if (isPro) {
            Postmark(
                city = stringResource(R.string.profile_pro_postmark).uppercase(),
                date = stringResource(R.string.profile_pro_postmark_date).uppercase(),
                color = c.airmailRed,
                diameter = ProPostmarkDiameter,
                showWaves = false,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = s.xs, end = s.xs),
            )
        }
    }
}

@Composable
private fun PassportField(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = StampTheme.type.overline, color = StampTheme.colors.textLow)
        Text(value, style = StampTheme.type.albumName, color = StampTheme.colors.textHigh)
    }
}

/** Free users: Pro in an airmail envelope, with "Already bought it? Restore" under it. */
@Composable
private fun ProCard(restore: RestoreMessage?, onGetPro: () -> Unit, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val t = StampTheme.type
    AirmailEnvelope(modifier.fillMaxWidth()) {
        Column(Modifier.padding(s.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.s)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_pro_title), style = t.cardTitle, color = c.textHigh)
                    Text(stringResource(R.string.profile_pro_perks), style = t.label, color = c.textMedium)
                }
                PillButton(stringResource(R.string.profile_get_pro), onGetPro, style = PillStyle.Red)
            }
            Row(
                Modifier
                    .heightIn(min = s.touchTarget)
                    .clickable(enabled = restore != RestoreMessage.Working, role = Role.Button, onClick = onRestore),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(s.xxs),
            ) {
                Text(stringResource(R.string.profile_restore_prompt), style = t.label, color = c.textMedium)
                Text(stringResource(R.string.paywall_restore), style = t.button, color = c.airmailRed)
            }
            if (restore != null) {
                HandNote(
                    stringResource(
                        when (restore) {
                            RestoreMessage.Working -> R.string.profile_restoring
                            RestoreMessage.NothingToRestore -> R.string.paywall_nothing_to_restore
                            RestoreMessage.Failed -> R.string.paywall_restore_failed
                        },
                    ),
                    style = t.noteSmall,
                    strength = if (restore == RestoreMessage.Working) InkStrength.Soft else InkStrength.Strong,
                    color = if (restore == RestoreMessage.Working) Color.Unspecified else c.airmailRed,
                )
            }
        }
    }
}

/** One group of settings on a PaperWhite stamp with perforated edges and a slight tilt. */
@Composable
private fun SettingsStamp(@StringRes title: Int, @DrawableRes icon: Int, tilt: Float, content: @Composable ColumnScope.() -> Unit) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val hole = PerforationStyle.Classic.holeRadiusDp.dp
    val shape = remember { PerforatedShape(StampShape.Landscape, PerforationStyle.Classic) }
    Box(
        Modifier
            .padding(top = s.m)
            .fillMaxWidth()
            .graphicsLayer { rotationZ = tilt },
    ) {
        // Shadow from a plain rectangle just inside the holes, as RenderedStamp does.
        Box(
            Modifier
                .matchParentSize()
                .padding(hole)
                .tintedShadow(Elevation.Low, RectangleShape, c.shadowWarm)
                .background(c.paperWhite),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.paperWhite, shape)
                .padding(s.m + hole),
            verticalArrangement = Arrangement.spacedBy(s.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                Icon(painterResource(icon), contentDescription = null, Modifier.size(SectionIconSize), tint = c.airmailRed)
                Text(stringResource(title), style = StampTheme.type.albumName, color = c.textHigh, modifier = Modifier.padding(start = s.xs))
            }
            content()
        }
    }
}

private fun hasLocation(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun appSettings(context: Context): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The Play Store app, or its web page on phones without it. */
private fun openPlayStore(context: Context) {
    val pkg = context.packageName
    val app = Intent(Intent.ACTION_VIEW, context.getString(R.string.play_store_app_link, pkg).toUri())
        .setPackage(PlayStorePackage)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(app)
    } catch (_: ActivityNotFoundException) {
        val web = Intent(Intent.ACTION_VIEW, context.getString(R.string.play_store_web_link, pkg).toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(web) }
    }
}

private fun versionName(context: Context): String? = runCatching {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    info.versionName
}.getOrNull()

private const val PlayStorePackage = "com.android.vending"
private const val ReminderTilt = -0.6f
private const val BackupTilt = 0.5f
private const val CameraTilt = -0.4f
private const val PassportStampTilt = -3f
private val PassportStampWidth = 92.dp
private val ProPostmarkDiameter = 64.dp
private val PostmarkRoom = 64.dp
private val SectionIconSize = 20.dp

@PaperPreview
@Composable
private fun ProfileContentPreview() {
    StampAppTheme {
        ProfileContent(
            state = ProfileUiState(
                passport = Passport(stamps = 128, albums = 6, places = 9, bestStreak = 14, since = LocalDate.of(2026, 11, 4), newest = null),
            ),
            placeOn = true,
            locationBlocked = false,
            contentPadding = PaddingValues(),
            onGetPro = {},
            onRestore = {},
            onMirrorSelfie = {},
            onPostmarkPlace = {},
            onOpenAppSettings = {},
            onRate = {},
            versionName = "1.0",
            reminder = {
                ReminderContent(ReminderSetting(on = true, minuteOfDay = ReminderSetting.DefaultMinute), blocked = false, {}, {}, {}, {})
            },
            backup = { BackupContent(BackupState.Idle, LocalDate.of(2026, 11, 2), onBackUp = {}, onRestore = {}) },
            footerExtras = {},
        )
    }
}

@PaperPreview
@Composable
private fun ProfileProPreview() {
    StampAppTheme {
        ProfileContent(
            state = ProfileUiState(
                passport = Passport(stamps = 0, albums = 1, places = 0, bestStreak = 0, since = null, newest = null),
                isPro = true,
                postmarkPlace = false,
            ),
            placeOn = false,
            locationBlocked = true,
            contentPadding = PaddingValues(),
            onGetPro = {},
            onRestore = {},
            onMirrorSelfie = {},
            onPostmarkPlace = {},
            onOpenAppSettings = {},
            onRate = {},
            versionName = "1.0",
            reminder = {
                ReminderContent(ReminderSetting(on = false, minuteOfDay = ReminderSetting.DefaultMinute), blocked = false, {}, {}, {}, {})
            },
            backup = { BackupContent(BackupState.Idle, null, onBackUp = {}, onRestore = {}) },
            footerExtras = {},
        )
    }
}
