package com.rynekryz.sniffsniff.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rynekryz.sniffsniff.core.common.settings.AppFilterMode
import com.rynekryz.sniffsniff.core.common.settings.PayloadMode
import com.rynekryz.sniffsniff.core.common.settings.ThemeMode
import com.rynekryz.sniffsniff.core.designsystem.AppIcon
import com.rynekryz.sniffsniff.core.designsystem.GroupPosition
import com.rynekryz.sniffsniff.core.designsystem.GroupedColumn
import com.rynekryz.sniffsniff.core.designsystem.GroupedSurface
import com.rynekryz.sniffsniff.core.designsystem.SectionLabel
import com.rynekryz.sniffsniff.core.designsystem.SegmentedChoice
import com.rynekryz.sniffsniff.core.designsystem.SettingsItem
import com.rynekryz.sniffsniff.core.designsystem.SniffTopBar
import com.rynekryz.sniffsniff.core.designsystem.rememberHaptics
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SOURCE_URL = "https://github.com/rynekryz/sniffsniff"

@Composable
fun SettingsScreen(
    onOpenLicenses: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showDns by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.tcpdump.pcap")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                val stream = context.contentResolver.openOutputStream(uri) ?: error("Cannot open file")
                stream.use { viewModel.exportPcap(it) }
            }.fold({ "Exported $it packets" }, { "Export failed: ${it.message}" })
            snackbar.showSnackbar(message)
        }
    }

    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"
    }

    Scaffold(
        topBar = { SniffTopBar("Settings") },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            SectionLabel("Appearance")
            GroupedColumn {
                GroupedSurface(GroupPosition.TOP) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Theme", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        SegmentedChoice(
                            listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                            settings.themeMode,
                        ) { mode -> viewModel.update { it.copy(themeMode = mode) } }
                    }
                }
                SettingsItem(Icons.Filled.Contrast, "Pure black", "Use pure black on AMOLED screens in dark mode", GroupPosition.MIDDLE,
                    onClick = { viewModel.update { it.copy(pureBlack = !it.pureBlack) } },
                    trailing = { Switch(settings.pureBlack, { v -> viewModel.update { it.copy(pureBlack = v) } }) })
                SettingsItem(Icons.Filled.Brightness6, "Dynamic color", "Match colors to your wallpaper", GroupPosition.MIDDLE,
                    onClick = { viewModel.update { it.copy(dynamicColor = !it.dynamicColor) } },
                    trailing = { Switch(settings.dynamicColor, { v -> viewModel.update { it.copy(dynamicColor = v) } }) })
                SettingsItem(Icons.Filled.Vibration, "Haptic feedback", "Vibrate on taps and actions", GroupPosition.MIDDLE,
                    onClick = { viewModel.update { it.copy(haptics = !it.haptics) } },
                    trailing = { Switch(settings.haptics, { v -> viewModel.update { it.copy(haptics = v) } }) })
                SettingsItem(Icons.Filled.TextFields, "Use system font", if (settings.systemFont) "Using your device's default font" else "Using Google Sans Flex", GroupPosition.BOTTOM,
                    onClick = { viewModel.update { it.copy(systemFont = !it.systemFont) } },
                    trailing = { Switch(settings.systemFont, { v -> viewModel.update { it.copy(systemFont = v) } }) })
            }

            SectionLabel("Capture")
            GroupedColumn {
                GroupedSurface(GroupPosition.TOP) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Applications", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        SegmentedChoice(
                            listOf(AppFilterMode.ALL to "All", AppFilterMode.ONLY_SELECTED to "Only", AppFilterMode.EXCLUDE_SELECTED to "Exclude"),
                            settings.appFilterMode,
                        ) { mode -> viewModel.update { it.copy(appFilterMode = mode) } }
                    }
                }
                if (settings.appFilterMode != AppFilterMode.ALL) {
                    SettingsItem(Icons.Filled.Apps, "Choose applications", "${settings.filteredPackages.size} selected", GroupPosition.MIDDLE, onClick = { showApps = true })
                }
                SettingsItem(Icons.Filled.Public, "IPv6", "Capture IPv6 traffic as well", GroupPosition.MIDDLE,
                    onClick = { viewModel.update { it.copy(ipv6 = !it.ipv6) } },
                    trailing = { Switch(settings.ipv6, { v -> viewModel.update { it.copy(ipv6 = v) } }) })
                SettingsItem(Icons.Filled.Dns, "DNS server", settings.customDns.ifBlank { "Use the DNS server of the current network" }, GroupPosition.BOTTOM, onClick = { showDns = true })
            }

            SectionLabel("Packets")
            GroupedColumn {
                SettingsItem(Icons.Filled.Visibility, "Keep packets for the inspector", "Stored in memory only until you clear or stop", GroupPosition.TOP,
                    onClick = { viewModel.update { it.copy(storePackets = !it.storePackets) } },
                    trailing = { Switch(settings.storePackets, { v -> viewModel.update { it.copy(storePackets = v) } }) })
                GroupedSurface(GroupPosition.MIDDLE) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Stored data", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text("Payload bytes kept for every packet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        SegmentedChoice(
                            listOf(PayloadMode.HEADERS to "Headers", PayloadMode.PREVIEW to "Preview", PayloadMode.FULL to "Full"),
                            settings.payloadMode,
                        ) { mode -> viewModel.update { it.copy(payloadMode = mode) } }
                    }
                }
                GroupedSurface(GroupPosition.BOTTOM) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Buffer size", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        Text("Applies to the next capture", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        SegmentedChoice(
                            listOf(1000 to "1K", 5000 to "5K", 20000 to "20K"),
                            settings.maxPackets,
                        ) { size -> viewModel.update { it.copy(maxPackets = size) } }
                    }
                }
            }

            SectionLabel("Privacy")
            GroupedColumn {
                GroupedSurface(GroupPosition.TOP) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(Icons.Filled.History, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("History retention", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text("Closed connections and per-app totals kept on this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        SegmentedChoice(
                            listOf(0 to "Off", 7 to "7 days", 30 to "30 days", 90 to "90 days"),
                            settings.historyRetentionDays,
                        ) { days -> viewModel.update { it.copy(historyRetentionDays = days) } }
                    }
                }
                SettingsItem(Icons.Filled.DeleteSweep, "Clear captured data", "Remove live connections and packets", GroupPosition.MIDDLE, onClick = { viewModel.clearSession() })
                SettingsItem(Icons.Filled.DeleteSweep, "Clear history", "Delete stored connections and app usage", GroupPosition.BOTTOM, onClick = { showClear = true })
            }

            SectionLabel("Export")
            SettingsItem(
                Icons.Filled.SaveAlt, "Export PCAP", "Save captured packets for Wireshark and other tools",
                onClick = { exportLauncher.launch("sniffsniff-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.pcap") },
            )

            SectionLabel("About")
            GroupedColumn {
                SettingsItem(Icons.Filled.Info, "SniffSniff", "Version $version · by ryne", GroupPosition.TOP)
                SettingsItem(Icons.Filled.Code, "Source code", "GPL-3.0-or-later · No analytics, ads or accounts", GroupPosition.MIDDLE,
                    onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) } })
                SettingsItem(Icons.Filled.Gavel, "Open source licenses", "Licenses of the app and everything it uses", GroupPosition.BOTTOM, onClick = onOpenLicenses)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDns) {
        var text by remember { mutableStateOf(settings.customDns) }
        AlertDialog(
            onDismissRequest = { showDns = false },
            title = { Text("DNS server") },
            text = {
                Column {
                    Text("Enter an IP address, or leave empty to use the current network's DNS server.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text("e.g. 192.168.1.1") })
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.update { it.copy(customDns = text.trim()) }; showDns = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showDns = false }) { Text("Cancel") } },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("Clear history?") },
            text = { Text("Stored connections and per-app usage will be deleted from this device.") },
            confirmButton = { TextButton(onClick = { viewModel.clearHistory(); showClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Cancel") } },
        )
    }

    if (showApps) {
        var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
        LaunchedEffect(Unit) { apps = viewModel.installedApps() }
        AlertDialog(
            onDismissRequest = { showApps = false },
            title = { Text("Applications") },
            text = {
                val list = apps
                if (list == null) {
                    Text("Loading…")
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(list, key = { it.packageName }) { app ->
                            val selected = app.packageName in settings.filteredPackages
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    haptics.click()
                                    viewModel.update { s ->
                                        s.copy(filteredPackages = if (selected) s.filteredPackages - app.packageName else s.filteredPackages + app.packageName)
                                    }
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName, app.label, 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(app.label, Modifier.weight(1f), maxLines = 1)
                                Checkbox(selected, onCheckedChange = null)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showApps = false }) { Text("Done") } },
        )
    }
}
