package com.rynekryz.sniffsniff.feature.applications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rynekryz.sniffsniff.core.common.model.AppTraffic
import com.rynekryz.sniffsniff.core.common.model.ConnectionRecord
import com.rynekryz.sniffsniff.core.common.repository.HistoryStore
import com.rynekryz.sniffsniff.core.common.repository.TrafficRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

enum class UsagePeriod { SESSION, TODAY, WEEK, MONTH }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ApplicationsViewModel @Inject constructor(
    private val repository: TrafficRepository,
    private val history: HistoryStore,
) : ViewModel() {

    val period = MutableStateFlow(UsagePeriod.SESSION)

    val applications: StateFlow<List<AppTraffic>> = period.flatMapLatest { selected ->
        when (selected) {
            UsagePeriod.SESSION -> repository.applications
            UsagePeriod.TODAY -> history.appUsage(daysAgo(0))
            UsagePeriod.WEEK -> history.appUsage(daysAgo(6))
            UsagePeriod.MONTH -> history.appUsage(daysAgo(29))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun application(packageName: String): Flow<AppTraffic?> =
        repository.applications.map { list -> list.firstOrNull { it.packageName == packageName } }

    fun connections(packageName: String): Flow<List<ConnectionRecord>> =
        repository.connections.map { list -> list.filter { it.packageName == packageName } }

    private fun daysAgo(days: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -days)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
