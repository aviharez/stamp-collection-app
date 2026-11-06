package com.goomistudio.postmory.ui.screens.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goomistudio.postmory.data.db.StampDao
import com.goomistudio.postmory.data.settings.SettingsRepository
import com.goomistudio.postmory.di.AppScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides the app's first screen: the welcome only on a fresh install (never answered, no stamps).
 * Someone who already has stamps (older version, restore) is marked as onboarded silently.
 */
@HiltViewModel
class FirstRunViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val stamps: StampDao,
    @param:AppScope private val appScope: CoroutineScope,
) : ViewModel() {
    private val _showWelcome = MutableStateFlow<Boolean?>(null)
    /** null while deciding (a few ms), then whether to start on the welcome. */
    val showWelcome: StateFlow<Boolean?> = _showWelcome.asStateFlow()

    init {
        viewModelScope.launch {
            val fresh = !settings.onboarded() && stamps.count() == 0
            if (!fresh) settings.setOnboarded()
            _showWelcome.value = fresh
        }
    }

    /** Any choice on the welcome (make a stamp, pick a photo, skip) means it isn't shown again. */
    fun done() {
        appScope.launch { settings.setOnboarded() }
    }
}
