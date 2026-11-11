package com.rynekryz.sniffsniff.network.pcap

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream

class PcapWriter(output: OutputStream, private val snapLength: Int = DEFAULT_SNAP_LENGTH) : Closeable {
    private val stream = BufferedOutputStream(output, 64 * 1024)
    private val header = ByteArray(16)
    private var written = 0

    init {
        val global = ByteArray(24)
        putIntLe(global, 0, MAGIC)
        putShortLe(global, 4, VERSION_MAJOR)
        putShortLe(global, 6, VERSION_MINOR)
        putIntLe(global, 16, snapLength)
        putIntLe(global, 20, LINKTYPE_RAW)
        stream.write(global)
    }

    val packetCount: Int get() = written

    fun write(timestampMillis: Long, data: ByteArray, length: Int = data.size, originalLength: Int = length) {
        val captured = minOf(length, snapLength)
        putIntLe(header, 0, (timestampMillis / 1000).toInt())
        putIntLe(header, 4, ((timestampMillis % 1000) * 1000).toInt())
        putIntLe(header, 8, captured)
        putIntLe(header, 12, maxOf(originalLength, captured))
        stream.write(header)
        stream.write(data, 0, captured)
        written++
    }

    override fun close() {
        stream.flush()
        stream.close()
    }

    private fun putIntLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
        target[offset + 2] = (value ushr 16).toByte()
        target[offset + 3] = (value ushr 24).toByte()
    }

    private fun putShortLe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    companion object {
        const val DEFAULT_SNAP_LENGTH = 65535
        private const val MAGIC = 0xA1B2C3D4.toInt()
        private const val VERSION_MAJOR = 2
        private const val VERSION_MINOR = 4
        private const val LINKTYPE_RAW = 101
    }
}
