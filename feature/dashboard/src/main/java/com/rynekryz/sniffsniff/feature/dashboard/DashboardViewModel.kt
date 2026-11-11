package com.rynekryz.sniffsniff.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rynekryz.sniffsniff.core.common.model.AppTraffic
import com.rynekryz.sniffsniff.core.common.model.CaptureState
import com.rynekryz.sniffsniff.core.common.model.RatePoint
import com.rynekryz.sniffsniff.core.common.model.TrafficStats
import com.rynekryz.sniffsniff.core.common.repository.CaptureController
import com.rynekryz.sniffsniff.core.common.repository.TrafficRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DashboardState(
    val captureState: CaptureState = CaptureState.IDLE,
    val stats: TrafficStats = TrafficStats(),
    val history: List<RatePoint> = emptyList(),
    val topApps: List<AppTraffic> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: TrafficRepository,
    private val controller: CaptureController,
) : ViewModel() {

    val state: StateFlow<DashboardState> = combine(
        repository.captureState,
        repository.stats,
        repository.rateHistory,
        repository.applications,
    ) { capture, stats, history, apps ->
        DashboardState(capture, stats, history, apps.take(3))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    val error: StateFlow<String?> = repository.captureError

    fun start() = controller.start()
    fun stop() = controller.stop()
    fun dismissError() = repository.dismissError()
}
