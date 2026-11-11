package com.rynekryz.sniffsniff

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import com.rynekryz.sniffsniff.core.designsystem.rememberHaptics
import com.rynekryz.sniffsniff.feature.applications.AppDetailScreen
import com.rynekryz.sniffsniff.feature.applications.ApplicationsScreen
import com.rynekryz.sniffsniff.feature.connections.ConnectionDetailScreen
import com.rynekryz.sniffsniff.feature.connections.ConnectionsScreen
import com.rynekryz.sniffsniff.feature.dashboard.DashboardScreen
import com.rynekryz.sniffsniff.feature.inspector.InspectorScreen
import com.rynekryz.sniffsniff.feature.inspector.PacketDetailScreen
import com.rynekryz.sniffsniff.feature.settings.LicensesScreen
import com.rynekryz.sniffsniff.feature.settings.SettingsScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard),
    CONNECTIONS("Connections", Icons.Filled.SwapVert),
    INSPECTOR("Inspector", Icons.Filled.FindInPage),
    APPLICATIONS("Apps", Icons.Filled.Apps),
    SETTINGS("Settings", Icons.Filled.Settings),
}

private sealed interface Overlay {
    data class Connection(val id: Long, val fromHistory: Boolean) : Overlay
    data class Packet(val id: Long) : Overlay
    data class App(val packageName: String) : Overlay
    data object Licenses : Overlay
}

private fun Overlay.encode(): String = when (this) {
    is Overlay.Connection -> "c:$id:${if (fromHistory) 1 else 0}"
    is Overlay.Packet -> "p:$id"
    is Overlay.App -> "a:$packageName"
    Overlay.Licenses -> "l"
}

private fun decode(value: String): Overlay? {
    val parts = value.split(":", limit = 3)
    return when (parts[0]) {
        "c" -> Overlay.Connection(parts[1].toLong(), parts.getOrNull(2) == "1")
        "p" -> Overlay.Packet(parts[1].toLong())
        "a" -> Overlay.App(value.removePrefix("a:"))
        "l" -> Overlay.Licenses
        else -> null
    }
}

private val StackSaver = Saver<List<Overlay>, String>(
    save = { stack -> stack.joinToString("|") { it.encode() } },
    restore = { value -> value.split("|").filter { it.isNotEmpty() }.mapNotNull { runCatching { decode(it) }.getOrNull() } },
)

@Composable
fun SniffSniffApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var stack by rememberSaveable(stateSaver = StackSaver) { mutableStateOf(emptyList<Overlay>()) }
    val haptics = rememberHaptics()

    fun push(overlay: Overlay) {
        if (stack.lastOrNull() != overlay) stack = stack + overlay
    }

    BackHandler(enabled = stack.isNotEmpty()) { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.isEmpty() && tab != 0) { tab = 0 }

    val spatial = spring<IntOffset>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    AnimatedContent(
        targetState = stack,
        transitionSpec = {
            if (targetState.size > initialState.size) {
                (slideInHorizontally(spatial) { it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 5 } + fadeOut(tween(180)))
            } else {
                (slideInHorizontally(spatial) { -it / 3 } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(220)) { it / 5 } + fadeOut(tween(180)))
            }
        },
        label = "navigation",
    ) { current ->
        val top = current.lastOrNull()
        Box(Modifier.fillMaxSize()) {
            if (top == null) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar {
                            Tab.entries.forEachIndexed { index, item ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { if (tab != index) { haptics.click(); tab = index } },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    Box(Modifier.padding(bottom = padding.calculateBottomPadding())) {
                        when (Tab.entries[tab]) {
                            Tab.DASHBOARD -> DashboardScreen(onOpenApp = { push(Overlay.App(it)) })
                            Tab.CONNECTIONS -> ConnectionsScreen(onOpenConnection = { id, history -> push(Overlay.Connection(id, history)) })
                            Tab.INSPECTOR -> InspectorScreen(onOpenPacket = { push(Overlay.Packet(it)) })
                            Tab.APPLICATIONS -> ApplicationsScreen(onOpenApp = { push(Overlay.App(it)) })
                            Tab.SETTINGS -> SettingsScreen(onOpenLicenses = { push(Overlay.Licenses) })
                        }
                    }
                }
            } else {
                val back = { stack = stack.dropLast(1) }
                when (top) {
                    is Overlay.Connection -> ConnectionDetailScreen(top.id, top.fromHistory, onBack = back, onOpenPacket = { push(Overlay.Packet(it)) })
                    is Overlay.Packet -> PacketDetailScreen(top.id, back)
                    is Overlay.App -> AppDetailScreen(top.packageName, onBack = back, onOpenConnection = { push(Overlay.Connection(it, false)) })
                    Overlay.Licenses -> LicensesScreen(back)
                }
            }
        }
    }
}
