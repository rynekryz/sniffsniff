package com.rynekryz.sniffsniff.feature.applications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rynekryz.sniffsniff.core.common.model.AppTraffic
import com.rynekryz.sniffsniff.core.common.util.formatBytes
import com.rynekryz.sniffsniff.core.designsystem.AppIcon
import com.rynekryz.sniffsniff.core.designsystem.ChipRow
import com.rynekryz.sniffsniff.core.designsystem.ConnectionRow
import com.rynekryz.sniffsniff.core.designsystem.DetailRow
import com.rynekryz.sniffsniff.core.designsystem.EmptyState
import com.rynekryz.sniffsniff.core.designsystem.GroupPosition
import com.rynekryz.sniffsniff.core.designsystem.GroupedSurface
import com.rynekryz.sniffsniff.core.designsystem.SectionLabel
import com.rynekryz.sniffsniff.core.designsystem.SniffTopBar
import com.rynekryz.sniffsniff.core.designsystem.groupPosition

@Composable
fun ApplicationsScreen(
    onOpenApp: (String) -> Unit,
    viewModel: ApplicationsViewModel = hiltViewModel(),
) {
    val apps by viewModel.applications.collectAsStateWithLifecycle()
    val period by viewModel.period.collectAsStateWithLifecycle()
    val maxTotal = apps.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1) ?: 1

    Scaffold(
        topBar = { SniffTopBar("Applications") },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ChipRow(
                listOf(UsagePeriod.SESSION to "Session", UsagePeriod.TODAY to "Today", UsagePeriod.WEEK to "7 days", UsagePeriod.MONTH to "30 days"),
                period,
            ) { viewModel.period.value = it }
            Spacer(Modifier.height(8.dp))
            if (apps.isEmpty()) {
                EmptyState(Icons.Filled.Apps, "No application activity", "Per-app traffic shows up once a capture is running")
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                        GroupedSurface(groupPosition(index, apps.size), onClick = { onOpenApp(app.packageName) }) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(app.packageName, app.label, 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            "↑ ${formatBytes(app.uploadBytes)}  ↓ ${formatBytes(app.downloadBytes)} · ${app.connections} conn.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(formatBytes(app.totalBytes), style = MaterialTheme.typography.labelLarge)
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { app.totalBytes.toFloat() / maxTotal }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppDetailScreen(
    packageName: String,
    onBack: () -> Unit,
    onOpenConnection: (Long) -> Unit,
    viewModel: ApplicationsViewModel = hiltViewModel(),
) {
    val app by androidx.compose.runtime.remember(packageName) { viewModel.application(packageName) }
        .collectAsStateWithLifecycle(initialValue = null)
    val connections by androidx.compose.runtime.remember(packageName) { viewModel.connections(packageName) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val current: AppTraffic = app ?: AppTraffic(packageName, packageName)

    Scaffold(
        topBar = { SniffTopBar(current.label, onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item {
                GroupedSurface(GroupPosition.SINGLE) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(current.packageName, current.label, 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(current.label, style = MaterialTheme.typography.titleMedium)
                            Text(current.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionLabel("This session")
                GroupedSurface(GroupPosition.SINGLE) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        DetailRow("Uploaded", formatBytes(current.uploadBytes))
                        DetailRow("Downloaded", formatBytes(current.downloadBytes))
                        DetailRow("Connections", "${current.connections} (${current.activeConnections} active)")
                    }
                }
            }
            item { SectionLabel("Connections") }
            if (connections.isEmpty()) {
                item { Text("No connections in this session", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
            } else {
                itemsIndexed(connections, key = { _, c -> c.id }) { index, record ->
                    ConnectionRow(record, groupPosition(index, connections.size)) { onOpenConnection(record.id) }
                }
            }
        }
    }
}
