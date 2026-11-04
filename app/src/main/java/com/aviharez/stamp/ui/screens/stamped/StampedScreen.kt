package com.aviharez.stamp.ui.screens.stamped

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.ui.components.AirmailStripe
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.components.PillStyle
import com.aviharez.stamp.ui.components.RenderedStamp
import com.aviharez.stamp.ui.components.Stamp
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import java.io.File

/**
 * "Stamped!" — skeleton for step 4. Step 5 shows the captured stamp; the drop-in / postmark
 * slam animation, streak chip and caption field come in milestone 2.
 */
@Composable
fun StampedScreen(
    stampPath: String?,
    shape: StampShape,
    onRetake: () -> Unit,
    onStickInAlbum: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    GrainedSurface(c.cobalt, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AirmailStripe(Modifier.fillMaxWidth().height(s.xs), accent = c.ink)
            Column(
                Modifier
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(s.screen),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PillButton(
                    stringResource(R.string.stamped_retake),
                    onClick = onRetake,
                    style = PillStyle.Plate,
                    modifier = Modifier.align(Alignment.Start),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (stampPath != null) {
                        RenderedStamp(
                            file = File(stampPath),
                            shape = shape,
                            contentDescription = stringResource(R.string.stamped_stamp_cd),
                            tilt = StampTilt,
                            shadowTint = c.shadowNavy,
                            modifier = Modifier.stampSize(shape),
                        )
                    } else {
                        Stamp(shape = shape, modifier = Modifier.stampSize(shape), tilt = StampTilt, shadowTint = c.shadowNavy) {
                            GrainedSurface(c.kraft, Modifier.fillMaxSize())
                        }
                    }
                }
                Text(stringResource(R.string.stamped_title), style = StampTheme.type.hero, color = c.textOnDarkHigh)
                Text(
                    stringResource(R.string.stamped_subtitle_placeholder),
                    style = StampTheme.type.body,
                    color = c.textOnDarkMedium,
                )
                Spacer(Modifier.height(s.xl))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(s.s)) {
                    PillButton(
                        stringResource(R.string.stamped_share),
                        onClick = {},
                        style = PillStyle.OutlineOnDark,
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(
                        stringResource(R.string.stamped_stick),
                        onClick = onStickInAlbum,
                        style = PillStyle.White,
                        trailingIcon = R.drawable.ic_arrow_right,
                        trailingIconTint = c.airmailRed,
                        modifier = Modifier.weight(1.6f),
                    )
                }
            }
        }
    }
}

private val StampLongSide = 280.dp

/** Same long side for every shape so landscape stamps are not oversized. */
private fun Modifier.stampSize(shape: StampShape): Modifier =
    if (shape.aspectRatio >= 1f) width(StampLongSide) else height(StampLongSide)
private const val StampTilt = -4f

@Preview(heightDp = 800)
@Composable
private fun StampedScreenPreview() {
    StampAppTheme { StampedScreen(null, StampShape.Portrait, {}, {}) }
}
