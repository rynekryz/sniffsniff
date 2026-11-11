package com.rynekryz.sniffsniff.network.parser

internal fun ByteArray.u8(offset: Int): Int = this[offset].toInt() and 0xFF

internal fun ByteArray.u16(offset: Int): Int = (u8(offset) shl 8) or u8(offset + 1)

internal fun ByteArray.u32(offset: Int): Long =
    (u8(offset).toLong() shl 24) or (u8(offset + 1).toLong() shl 16) or (u8(offset + 2).toLong() shl 8) or u8(offset + 3).toLong()

internal fun ByteArray.putU16(offset: Int, value: Int) {
    this[offset] = (value ushr 8).toByte()
    this[offset + 1] = value.toByte()
}

internal fun ByteArray.putU32(offset: Int, value: Long) {
    this[offset] = (value ushr 24).toByte()
    this[offset + 1] = (value ushr 16).toByte()
    this[offset + 2] = (value ushr 8).toByte()
    this[offset + 3] = value.toByte()
}
