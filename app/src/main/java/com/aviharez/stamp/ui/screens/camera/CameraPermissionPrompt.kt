package com.aviharez.stamp.ui.screens.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.AirmailStripe
import com.aviharez.stamp.ui.components.CircleIconButton
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.components.Stamp
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme

/**
 * Friendly pre-prompt before the system camera dialog (CLAUDE.md §6). When access was denied
 * for good, offers Settings instead.
 */
@Composable
fun CameraPermissionPrompt(
    permanentlyDenied: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    Column(modifier.fillMaxSize().background(c.ink)) {
        AirmailStripe(Modifier.fillMaxWidth().height(s.xs))
        CircleIconButton(
            R.drawable.ic_close,
            stringResource(R.string.cd_close),
            onClose,
            onDark = true,
            modifier = Modifier.statusBarsPadding().padding(s.screenFullBleed),
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = s.screen, vertical = s.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Stamp(StampShape.Portrait, Modifier.width(PromptStampWidth), tilt = PromptTilt, shadowTint = c.shadowNavy) {
                GrainedSurface(c.kraft, Modifier.fillMaxSize()) {
                    Icon(
                        painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        tint = c.ink,
                        modifier = Modifier.align(Alignment.Center).size(s.xl),
                    )
                }
            }
            Spacer(Modifier.height(s.xl))
            Text(
                stringResource(R.string.camera_permission_title),
                style = StampTheme.type.cardTitle,
                color = c.textOnDarkHigh,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(s.xs))
            Text(
                stringResource(if (permanentlyDenied) R.string.camera_permission_denied_body else R.string.camera_permission_body),
                style = StampTheme.type.body,
                color = c.textOnDarkMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(s.l))
            PillButton(
                text = stringResource(if (permanentlyDenied) R.string.camera_permission_settings else R.string.camera_permission_allow),
                onClick = if (permanentlyDenied) onOpenSettings else onAllow,
                style = PillStyle.White,
                leadingIcon = R.drawable.ic_camera,
            )
            Spacer(Modifier.height(s.xs))
            // Gallery import never needs the camera (CLAUDE.md §6).
            PillButton(
                text = stringResource(R.string.camera_permission_gallery),
                onClick = onPickPhoto,
                style = PillStyle.Plate,
                leadingIcon = R.drawable.ic_gallery,
            )
            Spacer(Modifier.height(s.xs))
            PillButton(
                text = stringResource(R.string.camera_permission_not_now),
                onClick = onClose,
                style = PillStyle.Plate,
            )
        }
    }
}

private val PromptStampWidth = 120.dp
private const val PromptTilt = -5f

@Preview(heightDp = 800)
@Composable
private fun CameraPermissionPromptPreview() {
    StampAppTheme { CameraPermissionPrompt(permanentlyDenied = false, onAllow = {}, onOpenSettings = {}, onClose = {}, onPickPhoto = {}) }
}
