package com.rynekryz.sniffsniff.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rynekryz.sniffsniff.core.common.model.ConnectionRecord
import com.rynekryz.sniffsniff.core.common.model.ConnectionState
import com.rynekryz.sniffsniff.core.common.model.Direction
import com.rynekryz.sniffsniff.core.common.model.PacketRecord
import com.rynekryz.sniffsniff.core.common.model.Protocol
import com.rynekryz.sniffsniff.core.common.util.formatBytes
import com.rynekryz.sniffsniff.core.common.util.formatClock
import com.rynekryz.sniffsniff.core.common.util.formatEndpoint
import com.rynekryz.sniffsniff.core.common.util.serviceName

enum class GroupPosition { TOP, MIDDLE, BOTTOM, SINGLE }

fun groupPosition(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.SINGLE
    index == 0 -> GroupPosition.TOP
    index == count - 1 -> GroupPosition.BOTTOM
    else -> GroupPosition.MIDDLE
}

fun groupShape(position: GroupPosition): Shape = when (position) {
    GroupPosition.TOP -> RoundedCornerShape(24.dp, 24.dp, 4.dp, 4.dp)
    GroupPosition.BOTTOM -> RoundedCornerShape(4.dp, 4.dp, 24.dp, 24.dp)
    GroupPosition.MIDDLE -> RoundedCornerShape(4.dp)
    GroupPosition.SINGLE -> RoundedCornerShape(24.dp)
}

@Composable
fun surfaceTint(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SniffTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val haptics = rememberHaptics()
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = { haptics.click(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 12.dp),
    )
}

@Composable
fun GroupedColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
fun GroupedSurface(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    if (onClick != null) {
        Surface(
            onClick = { haptics.click(); onClick() },
            shape = groupShape(position),
            color = surfaceTint(),
            modifier = modifier.fillMaxWidth(),
            content = content,
        )
    } else {
        Surface(shape = groupShape(position), color = surfaceTint(), modifier = modifier.fillMaxWidth(), content = content)
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    position: GroupPosition = GroupPosition.SINGLE,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    GroupedSurface(position, onClick = onClick) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
            leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = trailing,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}

@Composable
fun <T> SegmentedChoice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val haptics = rememberHaptics()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Surface(
                onClick = { haptics.click(); onSelect(value) },
                shape = RoundedCornerShape(50),
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f),
            ) {
                Box(Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { haptics.click(); onSelect(value) },
                label = { Text(label) },
                shape = RoundedCornerShape(50),
            )
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            unfocusedContainerColor = surfaceTint(),
            focusedContainerColor = surfaceTint(),
        ),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = surfaceTint(), modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ProtocolBadge(protocol: Protocol) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            protocol.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun DetailRow(label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.42f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            modifier = Modifier.weight(0.58f),
        )
    }
}

@Composable
fun ConnectionRow(record: ConnectionRecord, position: GroupPosition, onClick: () -> Unit) {
    GroupedSurface(position, onClick = onClick) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(record.packageName, record.appLabel, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(record.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val service = serviceName(record.protocol, record.remotePort)
                Text(
                    "${record.appLabel} · ${formatEndpoint(record.remoteAddress, record.remotePort)}" + (service?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                ProtocolBadge(record.protocol)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatBytes(record.totalBytes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (record.state == ConnectionState.ACTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun PacketRow(packet: PacketRecord, position: GroupPosition, onClick: () -> Unit) {
    GroupedSurface(position, onClick = onClick) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (packet.direction == Direction.UPLOAD) "↑" else "↓",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${formatEndpoint(packet.sourceAddress, packet.sourcePort)} → ${formatEndpoint(packet.destinationAddress, packet.destinationPort)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ProtocolBadge(packet.protocol)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                packet.info,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${formatClock(packet.timestamp)} · ${packet.length} B · ${packet.appLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
