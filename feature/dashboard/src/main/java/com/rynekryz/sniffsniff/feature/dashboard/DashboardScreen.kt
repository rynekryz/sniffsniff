package com.rynekryz.sniffsniff.feature.dashboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rynekryz.sniffsniff.core.common.model.AppTraffic
import com.rynekryz.sniffsniff.core.common.model.CaptureState
import com.rynekryz.sniffsniff.core.common.util.formatBytes
import com.rynekryz.sniffsniff.core.common.util.formatCount
import com.rynekryz.sniffsniff.core.common.util.formatRate
import com.rynekryz.sniffsniff.core.designsystem.AppIcon
import com.rynekryz.sniffsniff.core.designsystem.GroupPosition
import com.rynekryz.sniffsniff.core.designsystem.GroupedSurface
import com.rynekryz.sniffsniff.core.designsystem.SectionLabel
import com.rynekryz.sniffsniff.core.designsystem.SniffTopBar
import com.rynekryz.sniffsniff.core.designsystem.StatTile
import com.rynekryz.sniffsniff.core.designsystem.TrafficChart
import com.rynekryz.sniffsniff.core.designsystem.groupPosition
import com.rynekryz.sniffsniff.core.designsystem.rememberHaptics
import com.rynekryz.sniffsniff.core.designsystem.surfaceTint

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    onOpenApp: (String) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    val vpnLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.start()
    }
    fun requestVpn() {
        val intent = VpnService.prepare(context)
        if (intent == null) viewModel.start() else vpnLauncher.launch(intent)
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { requestVpn() }
    fun begin() {
        val needsNotification = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotification) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else requestVpn()
    }

    val running = state.captureState == CaptureState.RUNNING
    val busy = state.captureState == CaptureState.STARTING || state.captureState == CaptureState.STOPPING

    Scaffold(
        topBar = { SniffTopBar("SniffSniff") },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    haptics.confirm()
                    if (!busy) if (running) viewModel.stop() else begin()
                },
                icon = {
                    if (busy) LoadingIndicator(Modifier.height(24.dp))
                    else Icon(if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                },
                text = { Text(if (running) "Stop capture" else "Start capture") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        ) {
            item { StatusCard(state) }
            item {
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(24.dp), color = surfaceTint()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.ArrowDownward, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(formatRate(state.stats.downloadRate), style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(20.dp))
                            Icon(Icons.Filled.ArrowUpward, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(6.dp))
                            Text(formatRate(state.stats.uploadRate), style = MaterialTheme.typography.titleLarge)
                        }
                        Spacer(Modifier.height(12.dp))
                        TrafficChart(state.history)
                        Text("Last 60 seconds", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Downloaded", formatBytes(state.stats.downloadBytes), Icons.Filled.ArrowDownward, Modifier.weight(1f))
                    StatTile("Uploaded", formatBytes(state.stats.uploadBytes), Icons.Filled.ArrowUpward, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Active connections", "${state.stats.activeConnections} / ${state.stats.totalConnections}", Icons.Filled.SwapVert, Modifier.weight(1f))
                    StatTile("Packets", formatCount(state.stats.packets), Icons.Filled.Hub, Modifier.weight(1f))
                }
            }
            item { SectionLabel("Top applications") }
            if (state.topApps.isEmpty()) {
                item {
                    GroupedSurface(groupPosition(0, 1)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text("Nothing captured yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                itemsIndexed(state.topApps, key = { _, app -> app.packageName }) { index, app ->
                    TopAppRow(app, groupPosition(index, state.topApps.size)) { onOpenApp(app.packageName) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: DashboardState) {
    val (title, subtitle) = when (state.captureState) {
        CaptureState.IDLE -> "Not capturing" to "See what's flowing. Start a capture to inspect traffic locally."
        CaptureState.STARTING -> "Starting" to "Setting up the local VPN tunnel"
        CaptureState.RUNNING -> "Capturing" to "Everything is analysed on this device only"
        CaptureState.STOPPING -> "Stopping" to "Finishing up"
    }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp).fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun TopAppRow(app: AppTraffic, position: GroupPosition, onClick: () -> Unit) {
    GroupedSurface(position, onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, app.label, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text("${app.connections} connections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatBytes(app.totalBytes), style = MaterialTheme.typography.labelLarge)
        }
    }
}
