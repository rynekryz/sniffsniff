package com.rynekryz.sniffsniff.core.common.model

enum class Protocol(val label: String, val number: Int) {
    TCP("TCP", 6),
    UDP("UDP", 17),
    ICMP("ICMP", 1),
    ICMPV6("ICMPv6", 58),
    OTHER("Other", -1);

    companion object {
        fun fromNumber(number: Int): Protocol = entries.firstOrNull { it.number == number } ?: OTHER
    }
}

enum class Direction { UPLOAD, DOWNLOAD }

enum class ConnectionState { ACTIVE, CLOSED }

enum class CaptureState { IDLE, STARTING, RUNNING, STOPPING }

const val UNKNOWN_UID = -1
const val UNKNOWN_PACKAGE = "unknown"

data class ConnectionRecord(
    val id: Long,
    val protocol: Protocol,
    val localAddress: String,
    val localPort: Int,
    val remoteAddress: String,
    val remotePort: Int,
    val uid: Int,
    val packageName: String,
    val appLabel: String,
    val host: String?,
    val state: ConnectionState,
    val uploadBytes: Long,
    val downloadBytes: Long,
    val uploadPackets: Long,
    val downloadPackets: Long,
    val firstSeen: Long,
    val lastSeen: Long,
) {
    val totalBytes: Long get() = uploadBytes + downloadBytes
    val totalPackets: Long get() = uploadPackets + downloadPackets
    val title: String get() = host ?: remoteAddress
}

class PacketRecord(
    val id: Long,
    val connectionId: Long,
    val timestamp: Long,
    val direction: Direction,
    val protocol: Protocol,
    val sourceAddress: String,
    val sourcePort: Int,
    val destinationAddress: String,
    val destinationPort: Int,
    val length: Int,
    val info: String,
    val packageName: String,
    val appLabel: String,
    val data: ByteArray,
) {
    val isTruncated: Boolean get() = data.size < length
}

data class AppTraffic(
    val packageName: String,
    val label: String,
    val uid: Int = UNKNOWN_UID,
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val connections: Int = 0,
    val activeConnections: Int = 0,
    val lastActive: Long = 0,
) {
    val totalBytes: Long get() = uploadBytes + downloadBytes
}

data class TrafficStats(
    val uploadBytes: Long = 0,
    val downloadBytes: Long = 0,
    val uploadRate: Long = 0,
    val downloadRate: Long = 0,
    val packets: Long = 0,
    val activeConnections: Int = 0,
    val totalConnections: Int = 0,
    val startedAt: Long = 0,
)

data class RatePoint(
    val timestamp: Long,
    val upload: Long,
    val download: Long,
)
