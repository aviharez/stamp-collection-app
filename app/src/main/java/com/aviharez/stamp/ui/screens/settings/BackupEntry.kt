package com.aviharez.stamp.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aviharez.stamp.R
import com.aviharez.stamp.data.backup.BackupError
import com.aviharez.stamp.data.backup.BackupManager
import com.aviharez.stamp.data.backup.BackupState
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(private val backup: BackupManager) : ViewModel() {
    val state: StateFlow<BackupState> = backup.state
    val lastBackupAt: StateFlow<Long?> = backup.lastBackupAt.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun suggestedName(appName: String) = backup.suggestedName(appName)

    fun backUp(to: Uri) = backup.backUp(to)

    fun restore(from: Uri) = backup.restore(from)

    override fun onCleared() = backup.dismiss()
}

/** Profile: back up everything to one file the user picks, or merge a backup file back in. */
@Composable
fun BackupEntry(modifier: Modifier = Modifier, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val last by viewModel.lastBackupAt.collectAsStateWithLifecycle()
    val appName = stringResource(R.string.app_name)
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZipMime)) { uri ->
        uri?.let(viewModel::backUp)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::restore)
    }
    BackupContent(
        state = state,
        lastBackup = last?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() },
        onBackUp = { create.launch(viewModel.suggestedName(appName)) },
        onRestore = { open.launch(OpenMimes) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupContent(
    state: BackupState,
    lastBackup: LocalDate?,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val working = state is BackupState.Working
    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.xs)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.xs), verticalArrangement = Arrangement.spacedBy(s.xs)) {
            PaperChip(stringResource(R.string.backup_create), selected = false, onClick = { if (!working) onBackUp() })
            PaperChip(stringResource(R.string.backup_restore), selected = false, onClick = { if (!working) onRestore() })
        }
        val status = statusText(state, lastBackup)
        if (status != null) {
            HandNote(
                status,
                style = StampTheme.type.noteSmall,
                strength = if (state is BackupState.Failed) InkStrength.Strong else InkStrength.Soft,
                color = if (state is BackupState.Failed) StampTheme.colors.airmailRed else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun statusText(state: BackupState, lastBackup: LocalDate?): String? = when (state) {
    is BackupState.Working -> when {
        state.total == 0 -> stringResource(if (state.restoring) R.string.backup_reading else R.string.backup_writing)
        state.restoring -> stringResource(R.string.backup_restoring_progress, state.done, state.total)
        else -> stringResource(R.string.backup_writing_progress, state.done, state.total)
    }
    is BackupState.BackedUp -> pluralStringResource(R.plurals.backup_done, state.stamps, state.stamps)
    is BackupState.Restored -> buildList {
        add(
            if (state.stamps == 0 && state.albums == 0) {
                stringResource(R.string.backup_restored_nothing)
            } else {
                pluralStringResource(R.plurals.backup_restored_stamps, state.stamps, state.stamps)
            },
        )
        if (state.albums > 0) add(pluralStringResource(R.plurals.backup_restored_albums, state.albums, state.albums))
        if (state.alreadyHere > 0) add(pluralStringResource(R.plurals.backup_already_here, state.alreadyHere, state.alreadyHere))
    }.joinToString(stringResource(R.string.backup_separator))
    is BackupState.Failed -> stringResource(
        when (state.error) {
            BackupError.NotABackup -> R.string.backup_not_a_backup
            BackupError.NewerVersion -> R.string.backup_newer
            BackupError.Failed -> if (state.restoring) R.string.backup_restore_failed else R.string.backup_failed
        },
    )
    BackupState.Idle -> if (lastBackup != null) {
        stringResource(R.string.backup_last, rememberDateText(R.string.pattern_short_date, lastBackup))
    } else {
        stringResource(R.string.backup_never)
    }
}

private const val ZipMime = "application/zip"

/** Files apps report a .zip as; "octet-stream" covers file managers that don't know the type. */
private val OpenMimes = arrayOf(ZipMime, "application/x-zip-compressed", "application/octet-stream")

@PaperPreview
@Composable
private fun BackupContentPreview() {
    StampAppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.m)) {
            BackupContent(BackupState.Idle, LocalDate.of(2026, 11, 2), onBackUp = {}, onRestore = {})
            BackupContent(BackupState.Working(restoring = false, done = 12, total = 40), null, onBackUp = {}, onRestore = {})
            BackupContent(BackupState.Restored(stamps = 12, albums = 2, alreadyHere = 28), null, onBackUp = {}, onRestore = {})
            BackupContent(BackupState.Failed(restoring = true, BackupError.NotABackup), null, onBackUp = {}, onRestore = {})
        }
    }
}
