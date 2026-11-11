package com.rynekryz.sniffsniff.core.common.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val units = arrayOf("B", "KB", "MB", "GB", "TB")

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[unit])
}

fun formatRate(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"

fun formatCount(value: Long): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
}

fun formatClock(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(timestamp))

fun formatDateTime(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(timestamp))

fun formatDuration(millis: Long): String {
    val seconds = millis / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

fun formatEndpoint(address: String, port: Int): String = when {
    port <= 0 -> address
    address.contains(':') -> "[$address]:$port"
    else -> "$address:$port"
}
