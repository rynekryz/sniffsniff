package com.rynekryz.sniffsniff.network.forwarding

import com.rynekryz.sniffsniff.network.parser.Checksum
import java.net.Inet6Address
import java.net.InetAddress

internal object PacketBuilder {
    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17
    private const val TTL = 64

    fun tcp(
        source: InetAddress,
        destination: InetAddress,
        sourcePort: Int,
        destinationPort: Int,
        sequence: Long,
        acknowledgment: Long,
        flags: Int,
        window: Int,
        payload: ByteArray = EMPTY,
        mss: Int = 0,
    ): ByteArray {
        val optionLength = if (mss > 0) 4 else 0
        val tcpLength = 20 + optionLength + payload.size
        val version = if (source is Inet6Address) 6 else 4
        val ipLength = if (version == 4) 20 else 40
        val packet = ByteArray(ipLength + tcpLength)
        writeIpHeader(packet, version, source, destination, PROTOCOL_TCP, tcpLength)
        val t = ipLength
        packet.putU16(t, sourcePort)
        packet.putU16(t + 2, destinationPort)
        packet.putU32(t + 4, sequence)
        packet.putU32(t + 8, acknowledgment)
        packet[t + 12] = (((20 + optionLength) / 4) shl 4).toByte()
        packet[t + 13] = flags.toByte()
        packet.putU16(t + 14, window.coerceIn(0, 65535))
        if (mss > 0) {
            packet[t + 20] = 2
            packet[t + 21] = 4
            packet.putU16(t + 22, mss)
        }
        System.arraycopy(payload, 0, packet, t + 20 + optionLength, payload.size)
        packet.putU16(t + 16, Checksum.transport(packet, 0, version, PROTOCOL_TCP, t, tcpLength))
        return packet
    }

    fun udp(
        source: InetAddress,
        destination: InetAddress,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray,
        payloadLength: Int,
    ): ByteArray {
        val udpLength = 8 + payloadLength
        val version = if (source is Inet6Address) 6 else 4
        val ipLength = if (version == 4) 20 else 40
        val packet = ByteArray(ipLength + udpLength)
        writeIpHeader(packet, version, source, destination, PROTOCOL_UDP, udpLength)
        val u = ipLength
        packet.putU16(u, sourcePort)
        packet.putU16(u + 2, destinationPort)
        packet.putU16(u + 4, udpLength)
        System.arraycopy(payload, 0, packet, u + 8, payloadLength)
        val checksum = Checksum.transport(packet, 0, version, PROTOCOL_UDP, u, udpLength)
        packet.putU16(u + 6, if (checksum == 0) 0xFFFF else checksum)
        return packet
    }

    private fun writeIpHeader(
        packet: ByteArray,
        version: Int,
        source: InetAddress,
        destination: InetAddress,
        protocol: Int,
        payloadLength: Int,
    ) {
        if (version == 4) {
            packet[0] = 0x45
            packet.putU16(2, 20 + payloadLength)
            packet.putU16(6, 0x4000)
            packet[8] = TTL.toByte()
            packet[9] = protocol.toByte()
            System.arraycopy(source.address, 0, packet, 12, 4)
            System.arraycopy(destination.address, 0, packet, 16, 4)
            packet.putU16(10, Checksum.ipv4Header(packet, 0, 20))
        } else {
            packet[0] = 0x60
            packet.putU16(4, payloadLength)
            packet[6] = protocol.toByte()
            packet[7] = TTL.toByte()
            System.arraycopy(source.address, 0, packet, 8, 16)
            System.arraycopy(destination.address, 0, packet, 24, 16)
        }
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = (value ushr 8).toByte()
        this[offset + 1] = value.toByte()
    }

    private fun ByteArray.putU32(offset: Int, value: Long) {
        this[offset] = (value ushr 24).toByte()
        this[offset + 1] = (value ushr 16).toByte()
        this[offset + 2] = (value ushr 8).toByte()
        this[offset + 3] = value.toByte()
    }

    private val EMPTY = ByteArray(0)
}
