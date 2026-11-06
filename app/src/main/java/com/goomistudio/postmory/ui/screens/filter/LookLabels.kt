package com.goomistudio.postmory.ui.screens.filter

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.goomistudio.postmory.R
import com.goomistudio.postmory.ui.components.label

/** "Keep Sepia with Pro", "Keep the Bold edge with Pro", or both at once: "Keep this look with Pro". */
@Composable
fun keepLookLabel(ui: FilterUi): String {
    val filterLocked = !ui.selected.filter.free && !ui.access.isPro
    val edgeLocked = !ui.access.canUse(ui.selected.edge)
    return when {
        filterLocked && edgeLocked -> stringResource(R.string.look_keep_with_pro)
        filterLocked -> stringResource(R.string.filter_keep_with_pro, stringResource(ui.selected.filter.label))
        else -> stringResource(R.string.edge_keep_with_pro, stringResource(ui.selected.edge.label))
    }
}
