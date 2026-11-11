package com.rynekryz.sniffsniff.feature.connections

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rynekryz.sniffsniff.core.common.model.ConnectionState
import com.rynekryz.sniffsniff.core.common.util.formatBytes
import com.rynekryz.sniffsniff.core.common.util.formatDateTime
import com.rynekryz.sniffsniff.core.common.util.formatDuration
import com.rynekryz.sniffsniff.core.common.util.formatEndpoint
import com.rynekryz.sniffsniff.core.common.util.serviceName
import com.rynekryz.sniffsniff.core.designsystem.AppIcon
import com.rynekryz.sniffsniff.core.designsystem.DetailRow
import com.rynekryz.sniffsniff.core.designsystem.EmptyState
import com.rynekryz.sniffsniff.core.designsystem.GroupPosition
import com.rynekryz.sniffsniff.core.designsystem.GroupedSurface
import com.rynekryz.sniffsniff.core.designsystem.PacketRow
import com.rynekryz.sniffsniff.core.designsystem.SectionLabel
import com.rynekryz.sniffsniff.core.designsystem.SniffTopBar
import com.rynekryz.sniffsniff.core.designsystem.groupPosition
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert

@Composable
fun ConnectionDetailScreen(
    connectionId: Long,
    fromHistory: Boolean,
    onBack: () -> Unit,
    onOpenPacket: (Long) -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val record by remember(connectionId, fromHistory) { viewModel.connection(connectionId, fromHistory) }
        .collectAsStateWithLifecycle(initialValue = null)
    val packets by remember(connectionId) { viewModel.packets(connectionId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = { SniffTopBar(record?.title ?: "Connection", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val current = record
        if (current == null) {
            EmptyState(Icons.Filled.SwapVert, "Connection not available", "It may have been cleared", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                GroupedSurface(GroupPosition.SINGLE) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(current.packageName, current.appLabel, 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(current.appLabel, style = MaterialTheme.typography.titleMedium)
                            Text(current.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionLabel("Details")
                GroupedSurface(GroupPosition.SINGLE) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        DetailRow("Protocol", current.protocol.label)
                        serviceName(current.protocol, current.remotePort)?.let { DetailRow("Service", it) }
                        current.host?.let { DetailRow("Host", it) }
                        DetailRow("Remote", formatEndpoint(current.remoteAddress, current.remotePort), monospace = true)
                        DetailRow("Local", formatEndpoint(current.localAddress, current.localPort), monospace = true)
                        DetailRow("State", if (current.state == ConnectionState.ACTIVE) "Active" else "Closed")
                        DetailRow("First seen", formatDateTime(current.firstSeen))
                        DetailRow("Last seen", formatDateTime(current.lastSeen))
                        DetailRow("Duration", formatDuration(current.lastSeen - current.firstSeen))
                    }
                }
            }
            item {
                SectionLabel("Traffic")
                GroupedSurface(GroupPosition.SINGLE) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        DetailRow("Uploaded", "${formatBytes(current.uploadBytes)} · ${current.uploadPackets} packets")
                        DetailRow("Downloaded", "${formatBytes(current.downloadBytes)} · ${current.downloadPackets} packets")
                    }
                }
            }
            if (packets.isNotEmpty()) {
                item { SectionLabel("Latest packets") }
                itemsIndexed(packets, key = { _, p -> p.id }) { index, packet ->
                    PacketRow(packet, groupPosition(index, packets.size)) { onOpenPacket(packet.id) }
                }
            }
        }
    }
}
