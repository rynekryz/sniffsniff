package com.rynekryz.sniffsniff.network.capture

import com.rynekryz.sniffsniff.core.common.model.PacketRecord

internal class PacketStore(private val capacity: Int) {
    private val packets = ArrayDeque<PacketRecord>()

    fun add(packet: PacketRecord) {
        if (capacity <= 0) return
        packets.addLast(packet)
        while (packets.size > capacity) packets.removeFirst()
    }

    fun snapshot(): List<PacketRecord> = packets.toList()
}
