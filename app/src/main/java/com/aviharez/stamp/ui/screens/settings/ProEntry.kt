package com.aviharez.stamp.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aviharez.stamp.R
import com.aviharez.stamp.data.billing.ProStatus
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.PillButton
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProEntryViewModel @Inject constructor(proStatus: ProStatus) : ViewModel() {
    val isPro: StateFlow<Boolean> = proStatus.isPro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

/** Profile: "Get Pro" (opens the paywall), or a thank-you once Pro is on. */
@Composable
fun ProEntry(onGetPro: () -> Unit, modifier: Modifier = Modifier, viewModel: ProEntryViewModel = hiltViewModel()) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    ProEntryContent(isPro, onGetPro, modifier)
}

@Composable
fun ProEntryContent(isPro: Boolean, onGetPro: () -> Unit, modifier: Modifier = Modifier) {
    if (isPro) {
        HandNote(stringResource(R.string.profile_is_pro), modifier = modifier, style = StampTheme.type.noteSmall)
    } else {
        PillButton(stringResource(R.string.profile_get_pro), onGetPro, modifier, trailingIcon = R.drawable.ic_arrow_right)
    }
}

@PaperPreview
@Composable
private fun ProEntryPreview() {
    StampAppTheme { ProEntryContent(isPro = false, onGetPro = {}) }
}
