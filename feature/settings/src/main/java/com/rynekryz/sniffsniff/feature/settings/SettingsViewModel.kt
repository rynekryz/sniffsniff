package com.rynekryz.sniffsniff.feature.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rynekryz.sniffsniff.core.common.repository.HistoryStore
import com.rynekryz.sniffsniff.core.common.repository.TrafficRepository
import com.rynekryz.sniffsniff.core.common.settings.AppSettings
import com.rynekryz.sniffsniff.core.common.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import javax.inject.Inject

class InstalledApp(val packageName: String, val label: String)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val traffic: TrafficRepository,
    private val history: HistoryStore,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    fun clearSession() = traffic.clearSession()

    suspend fun exportPcap(output: OutputStream): Int = traffic.exportPcap(output)

    @Suppress("DEPRECATION")
    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val manager = context.packageManager
        val launcher = manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        launcher.map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { InstalledApp(it.packageName, manager.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }
}
