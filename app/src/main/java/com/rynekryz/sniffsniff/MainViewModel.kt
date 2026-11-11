package com.rynekryz.sniffsniff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rynekryz.sniffsniff.core.common.settings.AppSettings
import com.rynekryz.sniffsniff.core.common.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
