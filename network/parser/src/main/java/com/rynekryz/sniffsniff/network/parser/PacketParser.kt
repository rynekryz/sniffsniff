package com.rynekryz.sniffsniff.network.parser

import com.rynekryz.sniffsniff.core.common.model.Protocol
import java.net.InetAddress

object PacketParser {
    private const val IPV4_MIN = 20
    private const val IPV6_HEADER = 40
    private const val TCP_MIN = 20
    private const val UDP_HEADER = 8
    private const val ICMP_HEADER = 8

    fun parse(buffer: ByteArray, length: Int): ParsedPacket? {
        if (length < 1 || length > buffer.size) return null
        return when (buffer.u8(0) ushr 4) {
            4 -> parseIpv4(buffer, length)
            6 -> parseIpv6(buffer, length)
            else -> null
        }
    }

    private fun parseIpv4(buffer: ByteArray, length: Int): ParsedPacket? {
        if (length < IPV4_MIN) return null
        val headerLength = (buffer.u8(0) and 0x0F) * 4
        if (headerLength < IPV4_MIN || headerLength > length) return null
        val declared = buffer.u16(2)
        val end = if (declared in headerLength..length) declared else length
        val fragmentOffset = buffer.u16(6) and 0x1FFF
        return parseTransport(
            buffer = buffer,
            end = end,
            version = 4,
            protocolNumber = buffer.u8(9),
            source = address(buffer, 12, 4),
            destination = address(buffer, 16, 4),
            ttl = buffer.u8(8),
            ipHeaderLength = headerLength,
            fragmented = fragmentOffset != 0,
        )
    }

    private fun parseIpv6(buffer: ByteArray, length: Int): ParsedPacket? {
        if (length < IPV6_HEADER) return null
        val payloadLength = buffer.u16(4)
        val end = if (payloadLength > 0 && IPV6_HEADER + payloadLength <= length) IPV6_HEADER + payloadLength else length
        var next = buffer.u8(6)
        var offset = IPV6_HEADER
        var fragmented = false
        while (next == 0 || next == 43 || next == 60 || next == 44) {
            if (offset + 8 > end) return null
            val following = buffer.u8(offset)
            if (next == 44) {
                fragmented = fragmented || (buffer.u16(offset + 2) ushr 3) != 0
                offset += 8
            } else {
                offset += (buffer.u8(offset + 1) + 1) * 8
            }
            next = following
        }
        if (offset > end) return null
        return parseTransport(
            buffer = buffer,
            end = end,
            version = 6,
            protocolNumber = next,
            source = address(buffer, 8, 16),
            destination = address(buffer, 24, 16),
            ttl = buffer.u8(7),
            ipHeaderLength = offset,
            fragmented = fragmented,
        )
    }

    private fun parseTransport(
        buffer: ByteArray,
        end: Int,
        version: Int,
        protocolNumber: Int,
        source: InetAddress,
        destination: InetAddress,
        ttl: Int,
        ipHeaderLength: Int,
        fragmented: Boolean,
    ): ParsedPacket? {
        val protocol = Protocol.fromNumber(protocolNumber)
        var sourcePort = 0
        var destinationPort = 0
        var transportLength = 0
        var flags = 0
        var sequence = 0L
        var acknowledgment = 0L
        var window = 0
        val available = end - ipHeaderLength
        if (!fragmented) {
            when (protocol) {
                Protocol.TCP -> {
                    if (available < TCP_MIN) return null
                    transportLength = ((buffer.u8(ipHeaderLength + 12) ushr 4) * 4)
                    if (transportLength < TCP_MIN || transportLength > available) return null
                    sourcePort = buffer.u16(ipHeaderLength)
                    destinationPort = buffer.u16(ipHeaderLength + 2)
                    sequence = buffer.u32(ipHeaderLength + 4)
                    acknowledgment = buffer.u32(ipHeaderLength + 8)
                    flags = buffer.u8(ipHeaderLength + 13)
                    window = buffer.u16(ipHeaderLength + 14)
                }
                Protocol.UDP -> {
                    if (available < UDP_HEADER) return null
                    transportLength = UDP_HEADER
                    sourcePort = buffer.u16(ipHeaderLength)
                    destinationPort = buffer.u16(ipHeaderLength + 2)
                }
                Protocol.ICMP, Protocol.ICMPV6 -> transportLength = minOf(ICMP_HEADER, available)
                Protocol.OTHER -> Unit
            }
        }
        return ParsedPacket(
            ipVersion = version,
            protocol = protocol,
            protocolNumber = protocolNumber,
            source = source,
            destination = destination,
            sourcePort = sourcePort,
            destinationPort = destinationPort,
            totalLength = end,
            ipHeaderLength = ipHeaderLength,
            transportHeaderLength = transportLength,
            ttl = ttl,
            tcpFlags = flags,
            sequence = sequence,
            acknowledgment = acknowledgment,
            window = window,
            fragmented = fragmented,
        )
    }

    private fun address(buffer: ByteArray, offset: Int, size: Int): InetAddress =
        InetAddress.getByAddress(buffer.copyOfRange(offset, offset + size))
}

object TcpOptions {
    fun mss(buffer: ByteArray, packet: ParsedPacket): Int {
        var index = packet.ipHeaderLength + 20
        val end = packet.ipHeaderLength + packet.transportHeaderLength
        while (index < end) {
            when (val kind = buffer.u8(index)) {
                0 -> return 0
                1 -> index++
                else -> {
                    if (index + 1 >= end) return 0
                    val length = buffer.u8(index + 1)
                    if (length < 2) return 0
                    if (kind == 2 && length == 4 && index + 3 < end) return buffer.u16(index + 2)
                    index += length
                }
            }
        }
        return 0
    }

    fun describe(buffer: ByteArray, packet: ParsedPacket): String {
        val parts = ArrayList<String>()
        var index = packet.ipHeaderLength + 20
        val end = packet.ipHeaderLength + packet.transportHeaderLength
        while (index < end) {
            val kind = buffer.u8(index)
            if (kind == 0) break
            if (kind == 1) {
                index++
                continue
            }
            if (index + 1 >= end) break
            val length = buffer.u8(index + 1)
            if (length < 2 || index + length > end) break
            parts += when (kind) {
                2 -> if (length == 4) "MSS ${buffer.u16(index + 2)}" else "MSS"
                3 -> if (length == 3) "Window scale ${buffer.u8(index + 2)}" else "Window scale"
                4 -> "SACK permitted"
                5 -> "SACK"
                8 -> "Timestamps"
                else -> "Option $kind"
            }
            index += length
        }
        return parts.joinToString(", ")
    }
}
