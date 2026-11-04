package com.aviharez.stamp.ui.screens.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.AirmailStripe
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/** Camera — skeleton for step 4. CameraX preview, overlay and controls arrive in step 5. */
@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onCaptured: (stampPath: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    Column(modifier.fillMaxSize().background(c.ink)) {
        AirmailStripe(Modifier.fillMaxWidth().height(s.xs), accent = c.cobalt)
        Row(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = s.screenFullBleed, vertical = s.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(R.drawable.ic_close, stringResource(R.string.cd_close), onClose, onDark = true)
            Text(
                rememberDateText(R.string.pattern_short_date),
                style = StampTheme.type.albumName,
                color = c.textOnDarkHigh,
                modifier = Modifier.weight(1f).padding(horizontal = s.xs),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(s.touchTarget))
        }
        Box(
            Modifier
                .padding(horizontal = s.screenFullBleed)
                .fillMaxWidth()
                .weight(1f)
                .background(c.plateOnDark, StampTheme.shapes.viewfinder),
            contentAlignment = Alignment.Center,
        ) {
            HandNote(stringResource(R.string.camera_preview_placeholder), color = c.textOnDarkMedium)
        }
        Box(
            Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(vertical = s.l),
            contentAlignment = Alignment.Center,
        ) {
            ShutterButton(onClick = { onCaptured(null) }, contentDescription = stringResource(R.string.camera_shutter))
        }
    }
}

@Preview(heightDp = 800)
@Composable
private fun CameraScreenPreview() {
    StampAppTheme { CameraScreen({}, {}) }
}
