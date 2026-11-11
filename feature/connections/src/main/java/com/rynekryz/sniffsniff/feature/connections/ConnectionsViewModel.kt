package com.rynekryz.sniffsniff.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rynekryz.sniffsniff.core.common.model.ConnectionRecord
import com.rynekryz.sniffsniff.core.common.model.ConnectionState
import com.rynekryz.sniffsniff.core.common.model.PacketRecord
import com.rynekryz.sniffsniff.core.common.model.Protocol
import com.rynekryz.sniffsniff.core.common.repository.HistoryStore
import com.rynekryz.sniffsniff.core.common.repository.TrafficRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class ConnectionScope { ACTIVE, SESSION, HISTORY }

enum class ProtocolFilter { ALL, TCP, UDP, OTHER }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val repository: TrafficRepository,
    private val history: HistoryStore,
) : ViewModel() {

    val query = MutableStateFlow("")
    val scope = MutableStateFlow(ConnectionScope.ACTIVE)
    val protocol = MutableStateFlow(ProtocolFilter.ALL)

    private val source: Flow<List<ConnectionRecord>> = scope.flatMapLatest { selected ->
        when (selected) {
            ConnectionScope.ACTIVE -> repository.connections.map { list -> list.filter { it.state == ConnectionState.ACTIVE } }
            ConnectionScope.SESSION -> repository.connections
            ConnectionScope.HISTORY -> history.connections(HISTORY_LIMIT)
        }
    }

    val items: StateFlow<List<ConnectionRecord>> = combine(source, query, protocol) { list, text, filter ->
        val needle = text.trim().lowercase()
        list.filter { record ->
            val protocolOk = when (filter) {
                ProtocolFilter.ALL -> true
                ProtocolFilter.TCP -> record.protocol == Protocol.TCP
                ProtocolFilter.UDP -> record.protocol == Protocol.UDP
                ProtocolFilter.OTHER -> record.protocol != Protocol.TCP && record.protocol != Protocol.UDP
            }
            protocolOk && (needle.isEmpty() ||
                record.appLabel.lowercase().contains(needle) ||
                record.packageName.lowercase().contains(needle) ||
                record.host?.lowercase()?.contains(needle) == true ||
                record.remoteAddress.contains(needle) ||
                record.remotePort.toString() == needle)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun connection(id: Long, fromHistory: Boolean): Flow<ConnectionRecord?> =
        if (fromHistory) history.connection(id) else repository.connections.map { list -> list.firstOrNull { it.id == id } }

    fun packets(connectionId: Long): Flow<List<PacketRecord>> =
        repository.packets.map { list -> list.filter { it.connectionId == connectionId }.takeLast(PACKET_LIMIT).reversed() }

    private companion object {
        const val HISTORY_LIMIT = 500
        const val PACKET_LIMIT = 100
    }
}
