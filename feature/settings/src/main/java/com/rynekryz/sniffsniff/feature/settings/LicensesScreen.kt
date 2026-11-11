package com.rynekryz.sniffsniff.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rynekryz.sniffsniff.core.designsystem.GroupedSurface
import com.rynekryz.sniffsniff.core.designsystem.SniffTopBar
import com.rynekryz.sniffsniff.core.designsystem.groupPosition
import com.rynekryz.sniffsniff.core.designsystem.rememberHaptics

private class LicenseEntry(val title: String, val subtitle: String, val rawRes: Int?)

private val licenseEntries = listOf(
    LicenseEntry("SniffSniff", "GNU General Public License v3.0 or later", R.raw.license_gpl3),
    LicenseEntry("AndroidX libraries", "activity-compose, core-ktx, lifecycle, datastore-preferences, room, hilt · Apache License 2.0", R.raw.license_apache2),
    LicenseEntry("Jetpack Compose", "compose ui, runtime, tooling · Apache License 2.0", R.raw.license_apache2),
    LicenseEntry("Material 3 and Material Icons", "material3, material-icons-extended · Apache License 2.0", R.raw.license_apache2),
    LicenseEntry("Dagger Hilt", "hilt-android, hilt-compiler by Google · Apache License 2.0", R.raw.license_apache2),
    LicenseEntry("Kotlin", "Kotlin standard library and kotlinx.coroutines by JetBrains · Apache License 2.0", R.raw.license_apache2),
    LicenseEntry("Google Sans Flex", "Device font by Google, used through the system font family", null),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    Scaffold(
        topBar = { SniffTopBar("Open source licenses", onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                "SniffSniff is free and open source. These are the licenses of everything the app uses. Tap one to read the full text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
            )
            licenseEntries.forEachIndexed { index, entry ->
                var expanded by rememberSaveable(index) { mutableStateOf(false) }
                val expandable = entry.rawRes != null
                GroupedSurface(groupPosition(index, licenseEntries.size), modifier = Modifier.padding(vertical = 1.dp)) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = expandable) { haptics.click(); expanded = !expanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(2.dp))
                                Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (expandable) {
                                Icon(
                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (expandable && expanded) {
                            val text = remember(index) { context.resources.openRawResource(entry.rawRes).bufferedReader().readText() }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .heightIn(max = 320.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
