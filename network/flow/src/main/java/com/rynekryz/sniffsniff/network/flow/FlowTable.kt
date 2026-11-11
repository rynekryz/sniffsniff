package com.rynekryz.sniffsniff.network.flow

import com.rynekryz.sniffsniff.core.common.model.ConnectionRecord
import com.rynekryz.sniffsniff.core.common.model.ConnectionState
import com.rynekryz.sniffsniff.core.common.model.Direction
import com.rynekryz.sniffsniff.core.common.model.Protocol
import com.rynekryz.sniffsniff.core.common.model.UNKNOWN_PACKAGE
import com.rynekryz.sniffsniff.core.common.model.UNKNOWN_UID
import com.rynekryz.sniffsniff.network.parser.DnsParser
import com.rynekryz.sniffsniff.network.parser.PacketDissector
import com.rynekryz.sniffsniff.network.parser.ParsedPacket
import com.rynekryz.sniffsniff.network.parser.TcpFlags
import java.net.InetAddress

data class FlowKey(
    val protocol: Protocol,
    val protocolNumber: Int,
    val localAddress: InetAddress,
    val localPort: Int,
    val remoteAddress: InetAddress,
    val remotePort: Int,
)

class FlowEntry(val id: Long, val key: FlowKey, val firstSeen: Long) {
    var uid = UNKNOWN_UID
    var packageName = UNKNOWN_PACKAGE
    var appLabel = "Unknown"
    var uidAttempts = 0
    var host: String? = null
    var state = ConnectionState.ACTIVE
    var uploadBytes = 0L
    var downloadBytes = 0L
    var uploadPackets = 0L
    var downloadPackets = 0L
    var lastSeen = firstSeen
    var finUpload = false
    var finDownload = false
    var persisted = false
    var reportedUpload = 0L
    var reportedDownload = 0L
    var counted = false

    fun toRecord(): ConnectionRecord = ConnectionRecord(
        id = id,
        protocol = key.protocol,
        localAddress = key.localAddress.hostAddress.orEmpty(),
        localPort = key.localPort,
        remoteAddress = key.remoteAddress.hostAddress.orEmpty(),
        remotePort = key.remotePort,
        uid = uid,
        packageName = packageName,
        appLabel = appLabel,
        host = host,
        state = state,
        uploadBytes = uploadBytes,
        downloadBytes = downloadBytes,
        uploadPackets = uploadPackets,
        downloadPackets = downloadPackets,
        firstSeen = firstSeen,
        lastSeen = lastSeen,
    )
}

class FlowTable(
    private val idBase: Long = 0L,
    private val hosts: HostCache = HostCache(),
    private val maxFlows: Int = 4000,
    private val tcpIdleMillis: Long = 10 * 60_000L,
    private val udpIdleMillis: Long = 30_000L,
    private val dnsIdleMillis: Long = 8_000L,
) {
    private val flows = LinkedHashMap<FlowKey, FlowEntry>()
    private var nextId = 1L

    val size: Int get() = flows.size
    val activeCount: Int get() = flows.values.count { it.state == ConnectionState.ACTIVE }

    fun update(packet: ParsedPacket, data: ByteArray, direction: Direction, timestamp: Long): FlowEntry {
        val key = if (direction == Direction.UPLOAD) {
            FlowKey(packet.protocol, packet.protocolNumber, packet.source, packet.sourcePort, packet.destination, packet.destinationPort)
        } else {
            FlowKey(packet.protocol, packet.protocolNumber, packet.destination, packet.destinationPort, packet.source, packet.sourcePort)
        }
        var entry = flows[key]
        if (entry == null) {
            entry = FlowEntry(idBase + nextId++, key, timestamp)
            flows[key] = entry
        } else if (entry.state == ConnectionState.CLOSED && !isTrailingTcp(packet, direction)) {
            entry.state = ConnectionState.ACTIVE
            entry.finUpload = false
            entry.finDownload = false
            entry.persisted = false
        }
        entry.lastSeen = timestamp
        if (direction == Direction.UPLOAD) {
            entry.uploadBytes += packet.totalLength
            entry.uploadPackets++
        } else {
            entry.downloadBytes += packet.totalLength
            entry.downloadPackets++
        }
        inspect(entry, packet, data, direction)
        trackTcpState(entry, packet, direction)
        return entry
    }

    private fun isTrailingTcp(packet: ParsedPacket, direction: Direction): Boolean {
        if (packet.protocol != Protocol.TCP) return false
        val opensConnection = direction == Direction.UPLOAD &&
            packet.tcpFlags and TcpFlags.SYN != 0 && packet.tcpFlags and TcpFlags.ACK == 0
        return !opensConnection
    }

    private fun inspect(entry: FlowEntry, packet: ParsedPacket, data: ByteArray, direction: Direction) {
        if (packet.protocol == Protocol.UDP && (packet.sourcePort == 53 || packet.destinationPort == 53) && packet.payloadLength > 0) {
            val message = DnsParser.parse(data, packet.payloadOffset, packet.payloadLength)
            val name = message?.questions?.firstOrNull()?.name
            if (message != null && name != null) {
                if (entry.host == null) entry.host = name
                if (message.response) {
                    message.answers.forEach { answer -> answer.address?.let { hosts.put(it, name) } }
                }
            }
            return
        }
        if (entry.host != null) return
        if (direction == Direction.UPLOAD) {
            PacketDissector.hostHint(packet, data)?.let {
                entry.host = it
                hosts.put(entry.key.remoteAddress, it)
                return
            }
        }
        hosts.get(entry.key.remoteAddress)?.let { entry.host = it }
    }

    private fun trackTcpState(entry: FlowEntry, packet: ParsedPacket, direction: Direction) {
        if (packet.protocol != Protocol.TCP) return
        val flags = packet.tcpFlags
        if (flags and TcpFlags.RST != 0) {
            entry.state = ConnectionState.CLOSED
            return
        }
        if (flags and TcpFlags.FIN != 0) {
            if (direction == Direction.UPLOAD) entry.finUpload = true else entry.finDownload = true
            if (entry.finUpload && entry.finDownload) entry.state = ConnectionState.CLOSED
        }
    }

    fun sweep(now: Long) {
        for (entry in flows.values) {
            if (entry.state != ConnectionState.ACTIVE) continue
            val limit = when {
                entry.key.protocol == Protocol.TCP -> tcpIdleMillis
                entry.key.remotePort == 53 -> dnsIdleMillis
                else -> udpIdleMillis
            }
            if (now - entry.lastSeen > limit) entry.state = ConnectionState.CLOSED
        }
        if (flows.size > maxFlows) evict(flows.size - maxFlows)
    }

    private fun evict(count: Int) {
        var remaining = count
        val iterator = flows.values.iterator()
        while (iterator.hasNext() && remaining > 0) {
            val entry = iterator.next()
            if (entry.state == ConnectionState.CLOSED && entry.persisted) {
                iterator.remove()
                remaining--
            }
        }
        if (remaining > 0) {
            val second = flows.values.iterator()
            while (second.hasNext() && remaining > 0) {
                val entry = second.next()
                if (entry.state == ConnectionState.CLOSED) {
                    second.remove()
                    remaining--
                }
            }
        }
    }

    fun closeAll() {
        flows.values.forEach { it.state = ConnectionState.CLOSED }
    }

    fun entries(): Collection<FlowEntry> = flows.values

    fun snapshot(): List<ConnectionRecord> =
        flows.values.map { it.toRecord() }.sortedByDescending { it.lastSeen }

    fun drainClosed(): List<ConnectionRecord> {
        val result = ArrayList<ConnectionRecord>()
        for (entry in flows.values) {
            if (entry.state == ConnectionState.CLOSED && !entry.persisted) {
                entry.persisted = true
                result += entry.toRecord()
            }
        }
        return result
    }
}
