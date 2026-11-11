package com.rynekryz.sniffsniff.network.forwarding

import com.rynekryz.sniffsniff.core.common.model.Direction
import com.rynekryz.sniffsniff.core.common.model.Protocol
import com.rynekryz.sniffsniff.network.parser.PacketParser
import com.rynekryz.sniffsniff.network.parser.ParsedPacket
import com.rynekryz.sniffsniff.network.parser.TcpFlags
import com.rynekryz.sniffsniff.network.parser.TcpOptions
import java.io.IOException
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.CancelledKeyException
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private class UdpSession(val id: FlowId, val channel: DatagramChannel, var lastActive: Long) {
    var key: SelectionKey? = null
}

class TrafficForwarder(
    private val protector: SocketProtector,
    private val writer: TunWriter,
    private val observer: PacketObserver,
    private val mtu: Int = 1500,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val selector: Selector = Selector.open()
    private val inbound = ConcurrentLinkedQueue<ByteArray>()
    private val pending = AtomicInteger()
    private val wakePending = AtomicBoolean(false)
    private val tcpSessions = HashMap<FlowId, TcpSession>()
    private val udpSessions = HashMap<FlowId, UdpSession>()
    private val tcpBuffer = ByteBuffer.allocate(65535)
    private val udpBuffer = ByteBuffer.allocate(65535)
    private val thread = Thread(::loop, "sniffsniff-forwarder")

    @Volatile
    private var running = false

    fun start() {
        running = true
        thread.start()
    }

    fun onPacket(buffer: ByteArray, length: Int) {
        if (!running || length <= 0 || pending.get() >= MAX_PENDING) return
        val copy = buffer.copyOf(length)
        observer.onPacket(copy, length, Direction.UPLOAD)
        pending.incrementAndGet()
        inbound.add(copy)
        if (wakePending.compareAndSet(false, true)) selector.wakeup()
    }

    fun close() {
        if (!running) return
        running = false
        selector.wakeup()
        try {
            thread.join(2000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun loop() {
        var lastTick = clock()
        while (running) {
            try {
                selector.select(TICK_MILLIS)
            } catch (_: IOException) {
                break
            }
            wakePending.set(false)
            drainInbound()
            val keys = selector.selectedKeys().iterator()
            while (keys.hasNext()) {
                val key = keys.next()
                keys.remove()
                if (key.isValid) dispatch(key)
            }
            val now = clock()
            if (now - lastTick >= TICK_MILLIS) {
                housekeeping(now)
                lastTick = now
            }
        }
        shutdown()
    }

    private fun drainInbound() {
        while (true) {
            val packet = inbound.poll() ?: return
            pending.decrementAndGet()
            try {
                handle(packet)
            } catch (_: RuntimeException) {
            }
        }
    }

    private fun handle(buffer: ByteArray) {
        val packet = PacketParser.parse(buffer, buffer.size) ?: return
        if (packet.fragmented) return
        when (packet.protocol) {
            Protocol.TCP -> handleTcp(packet, buffer)
            Protocol.UDP -> handleUdp(packet, buffer)
            else -> Unit
        }
    }

    private fun handleTcp(packet: ParsedPacket, buffer: ByteArray) {
        val id = FlowId(packet.source, packet.sourcePort, packet.destination, packet.destinationPort)
        val flags = packet.tcpFlags
        val isSyn = flags and TcpFlags.SYN != 0 && flags and TcpFlags.ACK == 0
        var session = tcpSessions[id]
        if (session != null && isSyn && session.clientIsn != packet.sequence) {
            session.abort()
            tcpSessions.remove(id)
            session = null
        }
        if (session == null) {
            when {
                isSyn -> openTcp(packet, buffer, id)
                flags and TcpFlags.RST == 0 -> reset(packet)
            }
            return
        }
        session.onSegment(packet, buffer)
        if (session.closed) tcpSessions.remove(id)
    }

    private fun openTcp(packet: ParsedPacket, buffer: ByteArray, id: FlowId) {
        val channel = try {
            SocketChannel.open()
        } catch (_: IOException) {
            reset(packet)
            return
        }
        try {
            channel.configureBlocking(false)
            if (!protector.protect(channel.socket())) throw IOException("socket protection failed")
            val headers = if (packet.ipVersion == 6) 60 else 40
            val session = TcpSession(
                id = id,
                channel = channel,
                writer = ::emit,
                clock = clock,
                maxSegment = mtu - headers,
                clientIsn = packet.sequence,
                clientMss = TcpOptions.mss(buffer, packet),
                clientWindow = packet.window,
            )
            val immediate = channel.connect(InetSocketAddress(packet.destination, packet.destinationPort))
            session.key = channel.register(selector, SelectionKey.OP_CONNECT, session)
            tcpSessions[id] = session
            if (immediate) session.onConnected()
        } catch (_: IOException) {
            try {
                channel.close()
            } catch (_: IOException) {
            }
            reset(packet)
        }
    }

    private fun reset(packet: ParsedPacket) {
        val flags = packet.tcpFlags
        val reply = if (flags and TcpFlags.ACK != 0) {
            PacketBuilder.tcp(
                packet.destination, packet.source, packet.destinationPort, packet.sourcePort,
                packet.acknowledgment, 0, TcpFlags.RST, 0,
            )
        } else {
            var consumed = packet.payloadLength
            if (flags and TcpFlags.SYN != 0) consumed++
            if (flags and TcpFlags.FIN != 0) consumed++
            PacketBuilder.tcp(
                packet.destination, packet.source, packet.destinationPort, packet.sourcePort,
                0, (packet.sequence + consumed) and 0xFFFFFFFFL, TcpFlags.RST or TcpFlags.ACK, 0,
            )
        }
        emit(reply)
    }

    private fun handleUdp(packet: ParsedPacket, buffer: ByteArray) {
        val id = FlowId(packet.source, packet.sourcePort, packet.destination, packet.destinationPort)
        val session = udpSessions[id] ?: openUdp(packet, id) ?: return
        session.lastActive = clock()
        try {
            session.channel.write(ByteBuffer.wrap(buffer, packet.payloadOffset, packet.payloadLength))
        } catch (_: IOException) {
            closeUdp(session)
        }
    }

    private fun openUdp(packet: ParsedPacket, id: FlowId): UdpSession? {
        val channel = try {
            DatagramChannel.open()
        } catch (_: IOException) {
            return null
        }
        return try {
            channel.configureBlocking(false)
            if (!protector.protect(channel.socket())) throw IOException("socket protection failed")
            channel.connect(InetSocketAddress(packet.destination, packet.destinationPort))
            val session = UdpSession(id, channel, clock())
            session.key = channel.register(selector, SelectionKey.OP_READ, session)
            udpSessions[id] = session
            session
        } catch (_: IOException) {
            try {
                channel.close()
            } catch (_: IOException) {
            }
            null
        }
    }

    private fun dispatch(key: SelectionKey) {
        try {
            when (val attachment = key.attachment()) {
                is TcpSession -> dispatchTcp(key, attachment)
                is UdpSession -> if (key.isReadable) readUdp(attachment)
            }
        } catch (_: CancelledKeyException) {
        } catch (_: RuntimeException) {
        }
    }

    private fun dispatchTcp(key: SelectionKey, session: TcpSession) {
        if (key.isConnectable) session.onConnectable()
        if (key.isValid && key.isReadable) session.onReadable(tcpBuffer)
        if (key.isValid && key.isWritable) session.onWritable()
        if (session.closed) tcpSessions.remove(session.id)
    }

    private fun readUdp(session: UdpSession) {
        udpBuffer.clear()
        val read = try {
            session.channel.read(udpBuffer)
        } catch (_: IOException) {
            closeUdp(session)
            return
        }
        if (read < 0) return
        session.lastActive = clock()
        emit(
            PacketBuilder.udp(
                session.id.serverAddress, session.id.clientAddress,
                session.id.serverPort, session.id.clientPort,
                udpBuffer.array(), read,
            ),
        )
    }

    private fun closeUdp(session: UdpSession) {
        udpSessions.remove(session.id)
        session.key?.cancel()
        try {
            session.channel.close()
        } catch (_: IOException) {
        }
    }

    private fun housekeeping(now: Long) {
        val tcpIterator = tcpSessions.values.iterator()
        while (tcpIterator.hasNext()) {
            val session = tcpIterator.next()
            session.tick(now)
            if (session.closed) tcpIterator.remove()
        }
        val expired = udpSessions.values.filter {
            now - it.lastActive > if (it.id.serverPort == 53) DNS_IDLE_MILLIS else UDP_IDLE_MILLIS
        }
        expired.forEach(::closeUdp)
    }

    private fun emit(packet: ByteArray) {
        try {
            writer.write(packet)
        } catch (_: IOException) {
            return
        }
        observer.onPacket(packet, packet.size, Direction.DOWNLOAD)
    }

    private fun shutdown() {
        tcpSessions.values.toList().forEach { it.close() }
        tcpSessions.clear()
        udpSessions.values.toList().forEach(::closeUdp)
        inbound.clear()
        try {
            selector.close()
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val TICK_MILLIS = 250L
        const val MAX_PENDING = 4096
        const val UDP_IDLE_MILLIS = 30_000L
        const val DNS_IDLE_MILLIS = 8_000L
    }
}
