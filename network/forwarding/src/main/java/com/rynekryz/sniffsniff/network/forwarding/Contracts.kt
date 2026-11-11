package com.rynekryz.sniffsniff.network.forwarding

import com.rynekryz.sniffsniff.core.common.model.Direction
import java.net.DatagramSocket
import java.net.Socket

interface SocketProtector {
    fun protect(socket: Socket): Boolean
    fun protect(socket: DatagramSocket): Boolean
}

fun interface TunWriter {
    fun write(packet: ByteArray)
}

fun interface PacketObserver {
    fun onPacket(data: ByteArray, length: Int, direction: Direction)
}
