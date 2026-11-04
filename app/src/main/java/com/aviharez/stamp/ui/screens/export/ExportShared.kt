package com.aviharez.stamp.ui.screens.export

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.aviharez.stamp.R
import com.aviharez.stamp.data.image.StampExporter
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** System share chooser for an exported PNG (read grant for the receiving app only). */
internal fun Context.sharePng(uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = StampExporter.MimePng
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(send, null))
}

/** "Save": Android 8–9 need the storage permission to write into Pictures; later versions don't. */
@Composable
internal fun rememberSaveToPhotos(save: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(save)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) latest() }
    return {
        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) ask.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else latest()
    }
}

/** Save (outline) + Share (red), as in every export sheet. */
@Composable
internal fun SaveShareRow(
    save: SaveState,
    sharing: Boolean,
    onSave: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.s)) {
        PillButton(
            text = stringResource(
                when (save) {
                    SaveState.Saving -> R.string.export_saving
                    SaveState.Saved -> R.string.export_saved
                    else -> R.string.export_save
                },
            ),
            onClick = onSave,
            enabled = enabled && save != SaveState.Saving && save != SaveState.Saved,
            style = PillStyle.Outline,
            leadingIcon = if (save == SaveState.Saved) R.drawable.ic_check else R.drawable.ic_download,
            modifier = Modifier.weight(1f),
        )
        PillButton(
            text = stringResource(R.string.export_share),
            onClick = onShare,
            enabled = enabled && !sharing,
            style = PillStyle.Red,
            leadingIcon = R.drawable.ic_share,
            modifier = Modifier.weight(1f),
        )
    }
}

@PaperPreview
@Composable
private fun SaveShareRowPreview() {
    StampAppTheme { SaveShareRow(SaveState.Idle, sharing = false, onSave = {}, onShare = {}) }
}
