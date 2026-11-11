package com.rynekryz.sniffsniff.feature.connections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rynekryz.sniffsniff.core.designsystem.ChipRow
import com.rynekryz.sniffsniff.core.designsystem.ConnectionRow
import com.rynekryz.sniffsniff.core.designsystem.EmptyState
import com.rynekryz.sniffsniff.core.designsystem.SearchField
import com.rynekryz.sniffsniff.core.designsystem.SniffTopBar
import com.rynekryz.sniffsniff.core.designsystem.groupPosition

@Composable
fun ConnectionsScreen(
    onOpenConnection: (id: Long, fromHistory: Boolean) -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val scope by viewModel.scope.collectAsStateWithLifecycle()
    val protocol by viewModel.protocol.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { SniffTopBar("Connections") },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            SearchField(query, { viewModel.query.value = it }, "Search app, host, IP or port")
            Spacer(Modifier.height(8.dp))
            ChipRow(
                listOf(ConnectionScope.ACTIVE to "Active", ConnectionScope.SESSION to "Session", ConnectionScope.HISTORY to "History"),
                scope,
            ) { viewModel.scope.value = it }
            Spacer(Modifier.height(4.dp))
            ChipRow(
                listOf(ProtocolFilter.ALL to "All", ProtocolFilter.TCP to "TCP", ProtocolFilter.UDP to "UDP", ProtocolFilter.OTHER to "Other"),
                protocol,
            ) { viewModel.protocol.value = it }
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                EmptyState(Icons.Filled.SwapVert, "No connections", "Start a capture to see network connections here")
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    itemsIndexed(items, key = { _, item -> item.id }) { index, record ->
                        ConnectionRow(record, groupPosition(index, items.size)) {
                            onOpenConnection(record.id, scope == ConnectionScope.HISTORY)
                        }
                    }
                }
            }
        }
    }
}
