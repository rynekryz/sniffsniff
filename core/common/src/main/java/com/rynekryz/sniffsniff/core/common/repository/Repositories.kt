package com.rynekryz.sniffsniff.core.common.repository

import com.rynekryz.sniffsniff.core.common.model.AppTraffic
import com.rynekryz.sniffsniff.core.common.model.CaptureState
import com.rynekryz.sniffsniff.core.common.model.ConnectionRecord
import com.rynekryz.sniffsniff.core.common.model.PacketRecord
import com.rynekryz.sniffsniff.core.common.model.RatePoint
import com.rynekryz.sniffsniff.core.common.model.TrafficStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.OutputStream

interface TrafficRepository {
    val captureState: StateFlow<CaptureState>
    val captureError: StateFlow<String?>
    val stats: StateFlow<TrafficStats>
    val rateHistory: StateFlow<List<RatePoint>>
    val connections: StateFlow<List<ConnectionRecord>>
    val packets: StateFlow<List<PacketRecord>>
    val applications: StateFlow<List<AppTraffic>>

    suspend fun exportPcap(output: OutputStream): Int
    fun clearSession()
    fun dismissError()
}

interface CaptureController {
    fun start()
    fun stop()
}

interface HistoryStore {
    fun connections(limit: Int): Flow<List<ConnectionRecord>>
    fun connection(id: Long): Flow<ConnectionRecord?>
    fun appUsage(sinceDay: Long): Flow<List<AppTraffic>>
    suspend fun saveConnections(connections: List<ConnectionRecord>)
    suspend fun addAppUsage(day: Long, usage: List<AppTraffic>)
    suspend fun prune(olderThan: Long)
    suspend fun clear()
}
