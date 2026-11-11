package com.rynekryz.sniffsniff.network.parser

object Checksum {
    fun sum(buffer: ByteArray, offset: Int, length: Int, initial: Long = 0): Long {
        var total = initial
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            total += buffer.u16(index)
            index += 2
        }
        if (index < end) total += buffer.u8(index) shl 8
        return total
    }

    fun fold(sum: Long): Int {
        var total = sum
        while (total ushr 16 != 0L) total = (total and 0xFFFF) + (total ushr 16)
        return total.toInt().inv() and 0xFFFF
    }

    fun ipv4Header(buffer: ByteArray, offset: Int, headerLength: Int): Int =
        fold(sum(buffer, offset, headerLength))

    fun transport(
        buffer: ByteArray,
        ipOffset: Int,
        ipVersion: Int,
        protocolNumber: Int,
        transportOffset: Int,
        transportLength: Int,
    ): Int {
        val addressOffset = if (ipVersion == 4) ipOffset + 12 else ipOffset + 8
        val addressBytes = if (ipVersion == 4) 8 else 32
        var total = sum(buffer, addressOffset, addressBytes)
        total += protocolNumber
        total += transportLength
        total = sum(buffer, transportOffset, transportLength, total)
        return fold(total)
    }
}
