package com.rynekryz.sniffsniff.feature.inspector

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rynekryz.sniffsniff.core.designsystem.ChipRow
import com.rynekryz.sniffsniff.core.designsystem.EmptyState
import com.rynekryz.sniffsniff.core.designsystem.PacketRow
import com.rynekryz.sniffsniff.core.designsystem.SearchField
import com.rynekryz.sniffsniff.core.designsystem.SniffTopBar
import com.rynekryz.sniffsniff.core.designsystem.groupPosition
import com.rynekryz.sniffsniff.core.designsystem.rememberHaptics
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InspectorScreen(
    onOpenPacket: (Long) -> Unit,
    viewModel: InspectorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val packets by viewModel.packets.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.tcpdump.pcap")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                val stream = context.contentResolver.openOutputStream(uri) ?: error("Cannot open file")
                stream.use { viewModel.export(it) }
            }.fold({ "Exported $it packets" }, { "Export failed: ${it.message}" })
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            SniffTopBar("Packet inspector", actions = {
                IconButton(onClick = {
                    haptics.click()
                    exportLauncher.launch("sniffsniff-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.pcap")
                }) { Icon(Icons.Filled.SaveAlt, contentDescription = "Export PCAP") }
                IconButton(onClick = { haptics.click(); viewModel.clear() }) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear packets")
                }
            })
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SearchField(query, { viewModel.query.value = it }, "Search app, address or info")
            Spacer(Modifier.height(8.dp))
            ChipRow(
                listOf(PacketFilter.ALL to "All", PacketFilter.TCP to "TCP", PacketFilter.UDP to "UDP", PacketFilter.DNS to "DNS", PacketFilter.OTHER to "Other"),
                filter,
            ) { viewModel.filter.value = it }
            Spacer(Modifier.height(8.dp))
            if (packets.isEmpty()) {
                EmptyState(Icons.Filled.FindInPage, "No packets", "Captured packets appear here with headers and a hex viewer")
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    itemsIndexed(packets, key = { _, p -> p.id }) { index, packet ->
                        PacketRow(packet, groupPosition(index, packets.size)) { onOpenPacket(packet.id) }
                    }
                }
            }
        }
    }
}
