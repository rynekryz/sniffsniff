package com.rynekryz.sniffsniff.network.parser

import com.rynekryz.sniffsniff.core.common.model.Protocol
import java.net.InetAddress

class ParsedPacket(
    val ipVersion: Int,
    val protocol: Protocol,
    val protocolNumber: Int,
    val source: InetAddress,
    val destination: InetAddress,
    val sourcePort: Int,
    val destinationPort: Int,
    val totalLength: Int,
    val ipHeaderLength: Int,
    val transportHeaderLength: Int,
    val ttl: Int,
    val tcpFlags: Int,
    val sequence: Long,
    val acknowledgment: Long,
    val window: Int,
    val fragmented: Boolean,
) {
    val payloadOffset: Int get() = ipHeaderLength + transportHeaderLength
    val payloadLength: Int get() = (totalLength - payloadOffset).coerceAtLeast(0)
}

object TcpFlags {
    const val FIN = 0x01
    const val SYN = 0x02
    const val RST = 0x04
    const val PSH = 0x08
    const val ACK = 0x10
    const val URG = 0x20
    const val ECE = 0x40
    const val CWR = 0x80

    private val names = listOf(
        FIN to "FIN", SYN to "SYN", RST to "RST", PSH to "PSH",
        ACK to "ACK", URG to "URG", ECE to "ECE", CWR to "CWR",
    )

    fun describe(flags: Int): String =
        names.filter { flags and it.first != 0 }.joinToString(", ") { it.second }
}
