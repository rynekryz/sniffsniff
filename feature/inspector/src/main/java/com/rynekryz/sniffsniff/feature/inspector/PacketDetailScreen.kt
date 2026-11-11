package com.rynekryz.sniffsniff.feature.inspector

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.rynekryz.sniffsniff.core.common.model.Direction
import com.rynekryz.sniffsniff.core.common.util.formatClock
import com.rynekryz.sniffsniff.core.common.util.formatEndpoint
import com.rynekryz.sniffsniff.core.common.util.hexDump
import com.rynekryz.sniffsniff.core.common.util.hexDumpText
import com.rynekryz.sniffsniff.core.common.util.printableAscii
import com.rynekryz.sniffsniff.core.designsystem.DetailRow
import com.rynekryz.sniffsniff.core.designsystem.EmptyState
import com.rynekryz.sniffsniff.core.designsystem.GroupPosition
import com.rynekryz.sniffsniff.core.designsystem.GroupedSurface
import com.rynekryz.sniffsniff.core.designsystem.SectionLabel
import com.rynekryz.sniffsniff.core.designsystem.SniffTopBar
import com.rynekryz.sniffsniff.core.designsystem.rememberHaptics
import com.rynekryz.sniffsniff.network.parser.PacketDissector

@Composable
fun PacketDetailScreen(
    packetId: Long,
    onBack: () -> Unit,
    viewModel: InspectorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val packet = remember(packetId) { viewModel.find(packetId) }
    val details = remember(packet) { packet?.let { PacketDissector.dissect(it.data) } }

    Scaffold(
        topBar = {
            SniffTopBar("Packet #$packetId", onBack, actions = {
                if (packet != null) {
                    IconButton(onClick = {
                        haptics.click()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("packet", hexDumpText(packet.data)))
                    }) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy hex dump") }
                }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (packet == null || details == null) {
            EmptyState(Icons.Filled.FindInPage, "Packet not available", "It was removed from the buffer", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                GroupedSurface(GroupPosition.SINGLE) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        DetailRow("Time", formatClock(packet.timestamp))
                        DetailRow("Direction", if (packet.direction == Direction.UPLOAD) "Outgoing" else "Incoming")
                        DetailRow("Source", formatEndpoint(packet.sourceAddress, packet.sourcePort), monospace = true)
                        DetailRow("Destination", formatEndpoint(packet.destinationAddress, packet.destinationPort), monospace = true)
                        DetailRow("Length", if (packet.isTruncated) "${packet.length} B (${packet.data.size} B stored)" else "${packet.length} B")
                        DetailRow("Application", packet.appLabel)
                        DetailRow("Summary", packet.info)
                    }
                }
            }
            details.sections.forEach { section ->
                item {
                    SectionLabel(section.title)
                    GroupedSurface(GroupPosition.SINGLE) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            section.fields.forEach { (name, value) -> DetailRow(name, value) }
                        }
                    }
                }
            }
            if (details.payloadEnd > details.payloadStart) {
                item {
                    SectionLabel("Payload preview")
                    GroupedSurface(GroupPosition.SINGLE) {
                        Text(
                            printableAscii(packet.data, details.payloadStart, details.payloadEnd),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }
            }
            item {
                SectionLabel("Hex viewer")
                GroupedSurface(GroupPosition.SINGLE) {
                    val lines = remember(packet) { hexDump(packet.data) }
                    Column(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp)) {
                        lines.forEach { line ->
                            Text(
                                String.format("%04x  %-47s  %s", line.offset, line.hex, line.ascii),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
