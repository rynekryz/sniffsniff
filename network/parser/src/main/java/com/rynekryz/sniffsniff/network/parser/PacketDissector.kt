package com.rynekryz.sniffsniff.network.parser

import com.rynekryz.sniffsniff.core.common.model.Protocol
import com.rynekryz.sniffsniff.core.common.util.serviceName
import java.util.Locale

class DetailSection(val title: String, val fields: List<Pair<String, String>>)

class PacketDetails(
    val packet: ParsedPacket?,
    val sections: List<DetailSection>,
    val payloadStart: Int,
    val payloadEnd: Int,
)

object PacketDissector {
    fun dissect(data: ByteArray): PacketDetails {
        val packet = PacketParser.parse(data, data.size)
            ?: return PacketDetails(null, listOf(DetailSection("Malformed packet", listOf("Captured bytes" to data.size.toString()))), 0, data.size)
        val sections = ArrayList<DetailSection>()
        sections += if (packet.ipVersion == 4) ipv4(data, packet) else ipv6(data, packet)
        when (packet.protocol) {
            Protocol.TCP -> sections += tcp(data, packet)
            Protocol.UDP -> sections += udp(data, packet)
            Protocol.ICMP, Protocol.ICMPV6 -> sections += icmp(data, packet)
            Protocol.OTHER -> Unit
        }
        application(data, packet)?.let { sections += it }
        return PacketDetails(packet, sections, packet.payloadOffset.coerceAtMost(data.size), packet.totalLength.coerceAtMost(data.size))
    }

    fun summarize(packet: ParsedPacket, data: ByteArray): String = when (packet.protocol) {
        Protocol.TCP -> {
            val base = "[${TcpFlags.describe(packet.tcpFlags)}] seq=${packet.sequence} ack=${packet.acknowledgment} win=${packet.window} len=${packet.payloadLength}"
            val hint = tcpHint(packet, data)
            if (hint != null) "$base  $hint" else base
        }
        Protocol.UDP -> dnsSummary(packet, data) ?: "len=${packet.payloadLength}"
        Protocol.ICMP -> icmpName(data.u8(packet.ipHeaderLength), false)
        Protocol.ICMPV6 -> icmpName(data.u8(packet.ipHeaderLength), true)
        Protocol.OTHER -> "protocol ${packet.protocolNumber}"
    }

    fun hostHint(packet: ParsedPacket, data: ByteArray): String? {
        if (packet.protocol != Protocol.TCP || packet.payloadLength <= 0) return null
        return TlsParser.serverName(data, packet.payloadOffset, packet.payloadLength)
            ?: HttpParser.host(data, packet.payloadOffset, packet.payloadLength)
    }

    private fun tcpHint(packet: ParsedPacket, data: ByteArray): String? {
        if (packet.payloadLength <= 0) return null
        TlsParser.serverName(data, packet.payloadOffset, packet.payloadLength)?.let { return "TLS ClientHello SNI $it" }
        return HttpParser.requestLine(data, packet.payloadOffset, packet.payloadLength)
    }

    private fun dnsSummary(packet: ParsedPacket, data: ByteArray): String? {
        if (packet.sourcePort != 53 && packet.destinationPort != 53) return null
        val message = DnsParser.parse(data, packet.payloadOffset, packet.payloadLength) ?: return null
        val question = message.questions.firstOrNull() ?: return "DNS"
        val name = "${DnsParser.typeName(question.type)} ${question.name}"
        if (!message.response) return "DNS query $name"
        val addresses = message.answers.mapNotNull { it.address?.hostAddress }
        return if (addresses.isEmpty()) "DNS response $name (rcode ${message.responseCode})"
        else "DNS response $name -> ${addresses.joinToString(", ")}"
    }

    private fun ipv4(data: ByteArray, packet: ParsedPacket): DetailSection {
        val flags = data.u16(6) ushr 13
        val flagText = buildList {
            if (flags and 0x2 != 0) add("Don't fragment")
            if (flags and 0x1 != 0) add("More fragments")
        }.ifEmpty { listOf("None") }.joinToString(", ")
        return DetailSection(
            "IPv4",
            listOf(
                "Header length" to "${packet.ipHeaderLength} bytes",
                "DSCP / ECN" to "${data.u8(1) ushr 2} / ${data.u8(1) and 0x3}",
                "Total length" to data.u16(2).toString(),
                "Identification" to hex16(data.u16(4)),
                "Flags" to flagText,
                "Fragment offset" to (data.u16(6) and 0x1FFF).toString(),
                "Time to live" to packet.ttl.toString(),
                "Protocol" to "${protocolName(packet)} (${packet.protocolNumber})",
                "Header checksum" to hex16(data.u16(10)),
                "Source" to packet.source.hostAddress.orEmpty(),
                "Destination" to packet.destination.hostAddress.orEmpty(),
            ),
        )
    }

    private fun ipv6(data: ByteArray, packet: ParsedPacket): DetailSection {
        val first = data.u32(0)
        return DetailSection(
            "IPv6",
            listOf(
                "Traffic class" to ((first ushr 20) and 0xFF).toString(),
                "Flow label" to String.format(Locale.US, "0x%05x", first and 0xFFFFF),
                "Payload length" to data.u16(4).toString(),
                "Next header" to "${protocolName(packet)} (${packet.protocolNumber})",
                "Hop limit" to packet.ttl.toString(),
                "Source" to packet.source.hostAddress.orEmpty(),
                "Destination" to packet.destination.hostAddress.orEmpty(),
            ),
        )
    }

    private fun tcp(data: ByteArray, packet: ParsedPacket): DetailSection {
        val base = packet.ipHeaderLength
        val fields = arrayListOf(
            "Source port" to portText(packet.protocol, packet.sourcePort),
            "Destination port" to portText(packet.protocol, packet.destinationPort),
            "Sequence number" to packet.sequence.toString(),
            "Acknowledgment number" to packet.acknowledgment.toString(),
            "Header length" to "${packet.transportHeaderLength} bytes",
            "Flags" to TcpFlags.describe(packet.tcpFlags).ifEmpty { "None" },
            "Window" to packet.window.toString(),
            "Checksum" to hex16(data.u16(base + 16)),
            "Urgent pointer" to data.u16(base + 18).toString(),
        )
        val options = TcpOptions.describe(data, packet)
        if (options.isNotEmpty()) fields += "Options" to options
        return DetailSection("TCP", fields)
    }

    private fun udp(data: ByteArray, packet: ParsedPacket): DetailSection {
        val base = packet.ipHeaderLength
        return DetailSection(
            "UDP",
            listOf(
                "Source port" to portText(packet.protocol, packet.sourcePort),
                "Destination port" to portText(packet.protocol, packet.destinationPort),
                "Length" to data.u16(base + 4).toString(),
                "Checksum" to hex16(data.u16(base + 6)),
            ),
        )
    }

    private fun icmp(data: ByteArray, packet: ParsedPacket): DetailSection {
        val base = packet.ipHeaderLength
        val v6 = packet.protocol == Protocol.ICMPV6
        if (base + 2 > data.size) return DetailSection(if (v6) "ICMPv6" else "ICMP", emptyList())
        return DetailSection(
            if (v6) "ICMPv6" else "ICMP",
            listOf(
                "Type" to "${icmpName(data.u8(base), v6)} (${data.u8(base)})",
                "Code" to data.u8(base + 1).toString(),
            ),
        )
    }

    private fun application(data: ByteArray, packet: ParsedPacket): DetailSection? {
        if (packet.payloadLength <= 0) return null
        if (packet.protocol == Protocol.UDP && (packet.sourcePort == 53 || packet.destinationPort == 53)) {
            val message = DnsParser.parse(data, packet.payloadOffset, packet.payloadLength) ?: return null
            val fields = ArrayList<Pair<String, String>>()
            fields += "Transaction ID" to hex16(message.id)
            fields += "Type" to if (message.response) "Response" else "Query"
            if (message.response) fields += "Response code" to message.responseCode.toString()
            message.questions.forEach { fields += "Question" to "${DnsParser.typeName(it.type)} ${it.name}" }
            message.answers.forEach { fields += "Answer" to "${it.name} ${it.address?.hostAddress ?: DnsParser.typeName(it.type)}" }
            return DetailSection("DNS", fields)
        }
        if (packet.protocol == Protocol.TCP) {
            TlsParser.serverName(data, packet.payloadOffset, packet.payloadLength)?.let {
                return DetailSection("TLS", listOf("Handshake" to "ClientHello", "Server name (SNI)" to it))
            }
            HttpParser.requestLine(data, packet.payloadOffset, packet.payloadLength)?.let {
                val fields = arrayListOf("Request" to it)
                HttpParser.host(data, packet.payloadOffset, packet.payloadLength)?.let { host -> fields += "Host" to host }
                return DetailSection("HTTP", fields)
            }
        }
        return null
    }

    private fun protocolName(packet: ParsedPacket): String =
        if (packet.protocol == Protocol.OTHER) "Other" else packet.protocol.label

    private fun portText(protocol: Protocol, port: Int): String {
        val service = serviceName(protocol, port)
        return if (service != null) "$port ($service)" else port.toString()
    }

    private fun hex16(value: Int): String = String.format(Locale.US, "0x%04x", value)

    private fun icmpName(type: Int, v6: Boolean): String = if (v6) {
        when (type) {
            1 -> "Destination unreachable"
            3 -> "Time exceeded"
            128 -> "Echo request"
            129 -> "Echo reply"
            133 -> "Router solicitation"
            134 -> "Router advertisement"
            135 -> "Neighbor solicitation"
            136 -> "Neighbor advertisement"
            else -> "Type $type"
        }
    } else {
        when (type) {
            0 -> "Echo reply"
            3 -> "Destination unreachable"
            8 -> "Echo request"
            11 -> "Time exceeded"
            else -> "Type $type"
        }
    }
}
