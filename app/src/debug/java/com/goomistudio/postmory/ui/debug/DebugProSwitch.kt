package com.goomistudio.postmory.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.goomistudio.postmory.R
import com.goomistudio.postmory.data.billing.ProStatus
import com.goomistudio.postmory.ui.components.PaperChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugProViewModel @Inject constructor(private val proStatus: ProStatus) : ViewModel() {
    val on: StateFlow<Boolean> = proStatus.debugPro.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun toggle() = viewModelScope.launch { proStatus.setDebugPro(!on.value) }
}

/** Debug builds: pretend to own Pro (test the free/Pro paths without a Play purchase). */
@Composable
fun DebugProSwitch(viewModel: DebugProViewModel = hiltViewModel()) {
    val on by viewModel.on.collectAsStateWithLifecycle()
    PaperChip(stringResource(R.string.debug_pro_switch), selected = on, onClick = { viewModel.toggle() })
}
