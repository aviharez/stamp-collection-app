package com.aviharez.stamp.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow

/** Top-level destinations in the floating nav pill. */
enum class InkNavTab(@param:DrawableRes val icon: Int, @param:StringRes val label: Int) {
    Album(R.drawable.ic_nav_album, R.string.nav_album),
    Calendar(R.drawable.ic_nav_calendar, R.string.nav_calendar),
    Map(R.drawable.ic_nav_map, R.string.nav_map),
    Profile(R.drawable.ic_nav_profile, R.string.nav_profile),
}

/**
 * Floating rounded pill on Ink with four icons; the active icon is full PaperWhite.
 * Place it in the bottom third, centred, with screen padding around it.
 */
@Composable
fun InkNavBar(
    selected: InkNavTab,
    onSelect: (InkNavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val pill = StampTheme.shapes.pill
    Row(
        modifier
            .tintedShadow(Elevation.Floating, pill, c.shadowWarm)
            .background(c.ink, pill)
            .padding(horizontal = StampTheme.spacing.m, vertical = StampTheme.spacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkNavTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val tint by animateColorAsState(
                targetValue = if (isSelected) c.textOnDarkHigh else c.textOnDarkLow,
                animationSpec = tween(NavTintMillis),
                label = "navTint",
            )
            Box(
                Modifier
                    .size(StampTheme.spacing.touchTarget)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(tab) },
                        role = Role.Tab,
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = StampTheme.spacing.l),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(tab.icon),
                    contentDescription = stringResource(tab.label),
                    tint = tint,
                    modifier = Modifier.size(IconSize),
                )
            }
        }
    }
}

private const val NavTintMillis = 200
private val IconSize = 24.dp

@PaperPreview
@Composable
private fun InkNavBarPreview() {
    StampAppTheme {
        var tab by remember { mutableStateOf(InkNavTab.Album) }
        Box(Modifier.padding(StampTheme.spacing.l)) {
            InkNavBar(selected = tab, onSelect = { tab = it })
        }
    }
}
