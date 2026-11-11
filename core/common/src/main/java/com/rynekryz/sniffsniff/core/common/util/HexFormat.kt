package com.rynekryz.sniffsniff.core.common.util

class HexLine(val offset: Int, val hex: String, val ascii: String)

fun hexDump(data: ByteArray, start: Int = 0, end: Int = data.size, bytesPerLine: Int = 16): List<HexLine> {
    val lines = ArrayList<HexLine>(((end - start) / bytesPerLine) + 1)
    var offset = start
    while (offset < end) {
        val count = minOf(bytesPerLine, end - offset)
        val hex = StringBuilder(bytesPerLine * 3)
        val ascii = StringBuilder(bytesPerLine)
        for (i in 0 until count) {
            val value = data[offset + i].toInt() and 0xFF
            hex.append(HEX[value ushr 4]).append(HEX[value and 0x0F]).append(' ')
            ascii.append(if (value in 0x20..0x7E) value.toChar() else '.')
        }
        lines += HexLine(offset, hex.toString().trimEnd(), ascii.toString())
        offset += count
    }
    return lines
}

fun hexDumpText(data: ByteArray, start: Int = 0, end: Int = data.size): String =
    hexDump(data, start, end).joinToString("\n") { String.format("%04x  %-47s  %s", it.offset, it.hex, it.ascii) }

fun printableAscii(data: ByteArray, start: Int, end: Int, limit: Int = 512): String {
    val last = minOf(end, start + limit)
    val builder = StringBuilder()
    for (i in start until last) {
        val value = data[i].toInt() and 0xFF
        builder.append(
            when {
                value in 0x20..0x7E -> value.toChar()
                value == 0x0A || value == 0x0D || value == 0x09 -> value.toChar()
                else -> '.'
            }
        )
    }
    return builder.toString()
}

private const val HEX = "0123456789abcdef"
