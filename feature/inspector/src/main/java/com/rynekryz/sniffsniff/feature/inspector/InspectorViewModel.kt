package com.rynekryz.sniffsniff.feature.inspector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rynekryz.sniffsniff.core.common.model.PacketRecord
import com.rynekryz.sniffsniff.core.common.model.Protocol
import com.rynekryz.sniffsniff.core.common.repository.TrafficRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.io.OutputStream
import javax.inject.Inject

enum class PacketFilter { ALL, TCP, UDP, DNS, OTHER }

@HiltViewModel
class InspectorViewModel @Inject constructor(
    private val repository: TrafficRepository,
) : ViewModel() {

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(PacketFilter.ALL)

    val packets: StateFlow<List<PacketRecord>> = combine(repository.packets, query, filter) { list, text, selected ->
        val needle = text.trim().lowercase()
        val filtered = list.asReversed().filter { packet ->
            val protocolOk = when (selected) {
                PacketFilter.ALL -> true
                PacketFilter.TCP -> packet.protocol == Protocol.TCP
                PacketFilter.UDP -> packet.protocol == Protocol.UDP
                PacketFilter.DNS -> packet.sourcePort == 53 || packet.destinationPort == 53
                PacketFilter.OTHER -> packet.protocol != Protocol.TCP && packet.protocol != Protocol.UDP
            }
            protocolOk && (needle.isEmpty() ||
                packet.appLabel.lowercase().contains(needle) ||
                packet.info.lowercase().contains(needle) ||
                packet.sourceAddress.contains(needle) ||
                packet.destinationAddress.contains(needle))
        }
        filtered.take(MAX_VISIBLE)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun find(id: Long): PacketRecord? = repository.packets.value.firstOrNull { it.id == id }

    suspend fun export(output: OutputStream): Int = repository.exportPcap(output)

    fun clear() = repository.clearSession()

    private companion object {
        const val MAX_VISIBLE = 1000
    }
}
