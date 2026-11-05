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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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

/**
 * Lays [content] out at [widthPx] × [heightPx] with a fixed density ([widthPx] / [designWidth],
 * font scale 1, so it never depends on the phone) and records it into [layer] without drawing it.
 */
@Composable
internal fun OffscreenCapture(layer: GraphicsLayer, widthPx: Int, heightPx: Int, designWidth: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
                layout(0, 0) { placeable.place(0, 0) }
            }
            .drawWithContent { layer.record { this@drawWithContent.drawContent() } }
            .clearAndSetSemantics { },
    ) {
        CompositionLocalProvider(LocalDensity provides Density(widthPx / designWidth.value, fontScale = 1f)) {
            content()
        }
    }
}

/** [content] laid out at [designWidth] (font scale 1) and drawn scaled to [width] × [width] / [aspect]. */
@Composable
internal fun ScaledArt(width: Dp, aspect: Float, designWidth: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(modifier.size(width, width / aspect)) {
        CompositionLocalProvider(LocalDensity provides Density(density.density * width.value / designWidth.value, fontScale = 1f)) {
            content()
        }
    }
}

@PaperPreview
@Composable
private fun SaveShareRowPreview() {
    StampAppTheme { SaveShareRow(SaveState.Idle, sharing = false, onSave = {}, onShare = {}) }
}
