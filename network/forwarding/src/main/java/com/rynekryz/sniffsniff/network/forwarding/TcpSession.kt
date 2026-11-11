package com.rynekryz.sniffsniff.network.forwarding

import com.rynekryz.sniffsniff.network.parser.ParsedPacket
import com.rynekryz.sniffsniff.network.parser.TcpFlags
import java.io.IOException
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.security.SecureRandom

internal data class FlowId(
    val clientAddress: InetAddress,
    val clientPort: Int,
    val serverAddress: InetAddress,
    val serverPort: Int,
)

private class Segment(
    val sequence: Long,
    val data: ByteArray,
    val fin: Boolean,
    var sentAt: Long,
    var retries: Int = 0,
) {
    val length: Int get() = data.size + if (fin) 1 else 0
}

private const val SEQUENCE_MASK = 0xFFFFFFFFL
private fun seqAdd(value: Long, amount: Int): Long = (value + amount) and SEQUENCE_MASK
private fun seqDiff(a: Long, b: Long): Int = (a - b).toInt()

internal class TcpSession(
    val id: FlowId,
    val channel: SocketChannel,
    private val writer: TunWriter,
    private val clock: () -> Long,
    maxSegment: Int,
    val clientIsn: Long,
    clientMss: Int,
    private var clientWindow: Int,
) {
    var key: SelectionKey? = null
    var closed = false
        private set

    private val createdAt = clock()
    private val serverIsn = random.nextInt().toLong() and SEQUENCE_MASK
    private val mss = minOf(maxSegment, if (clientMss > 0) clientMss else DEFAULT_MSS)
    private val advertisedMss = maxSegment
    private var connected = false
    private var receiveNext = seqAdd(clientIsn, 1)
    private var sendUnacked = serverIsn
    private var sendNext = serverIsn
    private var clientFinished = false
    private var finQueued = false
    private var remoteShutdown = false
    private var remoteEof = false
    private var lastActive = createdAt
    private var lastAdvertised = RECEIVE_BUFFER
    private var outboundBytes = 0
    private val outbound = ArrayDeque<ByteBuffer>()
    private val unacked = ArrayDeque<Segment>()

    fun onConnected() {
        connected = true
        sendNext = seqAdd(serverIsn, 1)
        sendControl(TcpFlags.SYN or TcpFlags.ACK, serverIsn, advertisedMss)
        applyInterest()
    }

    fun onConnectable() {
        try {
            if (!channel.finishConnect()) return
        } catch (_: IOException) {
            refuse()
            return
        }
        onConnected()
    }

    fun onSegment(packet: ParsedPacket, buffer: ByteArray) {
        lastActive = clock()
        val flags = packet.tcpFlags
        if (flags and TcpFlags.RST != 0) {
            close()
            return
        }
        if (!connected) return
        if (flags and TcpFlags.SYN != 0) {
            sendControl(TcpFlags.SYN or TcpFlags.ACK, serverIsn, advertisedMss)
            return
        }
        if (flags and TcpFlags.ACK != 0) handleAck(packet.acknowledgment, packet.window)
        val fin = flags and TcpFlags.FIN != 0
        if (packet.payloadLength > 0 || fin) handleData(packet, buffer, fin)
        flush()
        applyInterest()
        finishIfDone()
    }

    fun onReadable(shared: ByteBuffer) {
        lastActive = clock()
        var sent = 0
        while (sent < MAX_BURST && !remoteEof) {
            val available = windowAvailable()
            if (available <= 0) break
            shared.clear()
            shared.limit(minOf(mss, available))
            val read = try {
                channel.read(shared)
            } catch (_: IOException) {
                abort()
                return
            }
            if (read < 0) {
                remoteEof = true
                queueFin()
                break
            }
            if (read == 0) break
            queueData(shared.array(), read)
            sent++
        }
        applyInterest()
        finishIfDone()
    }

    fun onWritable() {
        flush()
        applyInterest()
        finishIfDone()
    }

    fun tick(now: Long) {
        if (closed) return
        if (!connected) {
            if (now - createdAt > CONNECT_TIMEOUT) refuse()
            return
        }
        val first = unacked.firstOrNull()
        if (first != null && now - first.sentAt > RETRANSMIT_TIMEOUT) {
            if (first.retries >= MAX_RETRIES) {
                abort()
                return
            }
            first.retries++
            transmit(first, now)
        }
        if (now - lastActive > IDLE_TIMEOUT) abort()
    }

    fun abort() {
        if (closed) return
        sendControl(TcpFlags.RST or TcpFlags.ACK, sendNext, 0)
        close()
    }

    fun close() {
        if (closed) return
        closed = true
        key?.cancel()
        try {
            channel.close()
        } catch (_: IOException) {
        }
    }

    private fun refuse() {
        if (closed) return
        writer.write(
            PacketBuilder.tcp(
                id.serverAddress, id.clientAddress, id.serverPort, id.clientPort,
                0, receiveNext, TcpFlags.RST or TcpFlags.ACK, 0,
            ),
        )
        close()
    }

    private fun handleAck(acknowledgment: Long, window: Int) {
        clientWindow = window
        if (seqDiff(acknowledgment, sendUnacked) > 0 && seqDiff(acknowledgment, sendNext) <= 0) {
            sendUnacked = acknowledgment
            releaseAcked(acknowledgment)
        }
    }

    private fun releaseAcked(acknowledgment: Long) {
        while (true) {
            val segment = unacked.firstOrNull() ?: return
            val end = seqAdd(segment.sequence, segment.length)
            if (seqDiff(end, acknowledgment) <= 0) {
                unacked.removeFirst()
                continue
            }
            if (seqDiff(segment.sequence, acknowledgment) < 0) {
                val consumed = seqDiff(acknowledgment, segment.sequence)
                unacked[0] = Segment(
                    acknowledgment,
                    segment.data.copyOfRange(minOf(consumed, segment.data.size), segment.data.size),
                    segment.fin,
                    segment.sentAt,
                    segment.retries,
                )
            }
            return
        }
    }

    private fun handleData(packet: ParsedPacket, buffer: ByteArray, fin: Boolean) {
        var sequence = packet.sequence
        var offset = packet.payloadOffset
        var length = packet.payloadLength
        if (seqDiff(sequence, receiveNext) < 0) {
            val overlap = seqDiff(receiveNext, sequence)
            if (overlap >= length + if (fin) 1 else 0) {
                sendAck()
                return
            }
            val skip = minOf(overlap, length)
            offset += skip
            length -= skip
            sequence = seqAdd(sequence, skip)
        }
        if (sequence != receiveNext) {
            sendAck()
            return
        }
        val accepted = minOf(length, (RECEIVE_BUFFER - outboundBytes).coerceAtLeast(0))
        if (accepted > 0) {
            outbound.addLast(ByteBuffer.wrap(buffer.copyOfRange(offset, offset + accepted)))
            outboundBytes += accepted
            receiveNext = seqAdd(receiveNext, accepted)
        }
        if (fin && accepted == length && !clientFinished) {
            clientFinished = true
            receiveNext = seqAdd(receiveNext, 1)
        }
        sendAck()
    }

    private fun flush() {
        while (outbound.isNotEmpty()) {
            val head = outbound.first()
            val before = head.remaining()
            try {
                channel.write(head)
            } catch (_: IOException) {
                abort()
                return
            }
            outboundBytes -= before - head.remaining()
            if (head.hasRemaining()) return
            outbound.removeFirst()
        }
        if (clientFinished && !remoteShutdown) {
            remoteShutdown = true
            try {
                channel.shutdownOutput()
            } catch (_: IOException) {
            }
        }
        val window = advertisedWindow()
        if ((lastAdvertised == 0 && window > 0) || window - lastAdvertised >= WINDOW_UPDATE_STEP) sendAck()
    }

    private fun queueData(source: ByteArray, length: Int) {
        val segment = Segment(sendNext, source.copyOf(length), false, 0)
        unacked.addLast(segment)
        transmit(segment, clock())
        sendNext = seqAdd(sendNext, length)
    }

    private fun queueFin() {
        if (finQueued) return
        finQueued = true
        val segment = Segment(sendNext, ByteArray(0), true, 0)
        unacked.addLast(segment)
        transmit(segment, clock())
        sendNext = seqAdd(sendNext, 1)
    }

    private fun transmit(segment: Segment, now: Long) {
        segment.sentAt = now
        var flags = TcpFlags.ACK
        if (segment.data.isNotEmpty()) flags = flags or TcpFlags.PSH
        if (segment.fin) flags = flags or TcpFlags.FIN
        lastAdvertised = advertisedWindow()
        writer.write(
            PacketBuilder.tcp(
                id.serverAddress, id.clientAddress, id.serverPort, id.clientPort,
                segment.sequence, receiveNext, flags, lastAdvertised, segment.data,
            ),
        )
    }

    private fun sendAck() = sendControl(TcpFlags.ACK, sendNext, 0)

    private fun sendControl(flags: Int, sequence: Long, mssOption: Int) {
        lastAdvertised = advertisedWindow()
        writer.write(
            PacketBuilder.tcp(
                id.serverAddress, id.clientAddress, id.serverPort, id.clientPort,
                sequence, receiveNext, flags, lastAdvertised, mss = mssOption,
            ),
        )
    }

    private fun advertisedWindow(): Int = (RECEIVE_BUFFER - outboundBytes).coerceIn(0, RECEIVE_BUFFER)

    private fun windowAvailable(): Int = clientWindow - seqDiff(sendNext, sendUnacked)

    private fun applyInterest() {
        val selectionKey = key ?: return
        if (closed || !selectionKey.isValid) return
        if (!connected) {
            selectionKey.interestOps(SelectionKey.OP_CONNECT)
            return
        }
        var ops = 0
        if (!remoteEof && windowAvailable() > 0) ops = ops or SelectionKey.OP_READ
        if (outbound.isNotEmpty()) ops = ops or SelectionKey.OP_WRITE
        selectionKey.interestOps(ops)
    }

    private fun finishIfDone() {
        if (clientFinished && finQueued && unacked.isEmpty()) close()
    }

    private companion object {
        val random = SecureRandom()
        const val RECEIVE_BUFFER = 65535
        const val WINDOW_UPDATE_STEP = 16384
        const val DEFAULT_MSS = 536
        const val MAX_BURST = 16
        const val MAX_RETRIES = 6
        const val CONNECT_TIMEOUT = 10_000L
        const val RETRANSMIT_TIMEOUT = 1_000L
        const val IDLE_TIMEOUT = 10 * 60_000L
    }
}
