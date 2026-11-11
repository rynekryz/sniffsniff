package com.rynekryz.sniffsniff.network.parser

import java.net.InetAddress

object DnsParser {
    class Question(val name: String, val type: Int)
    class Answer(val name: String, val type: Int, val address: InetAddress?)
    class Message(
        val id: Int,
        val response: Boolean,
        val responseCode: Int,
        val questions: List<Question>,
        val answers: List<Answer>,
    )

    fun parse(buffer: ByteArray, offset: Int, length: Int): Message? {
        if (length < 12) return null
        return try {
            read(buffer, offset, offset + length)
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    private fun read(buffer: ByteArray, base: Int, end: Int): Message? {
        val flags = buffer.u16(base + 2)
        val questionCount = buffer.u16(base + 4)
        val answerCount = buffer.u16(base + 6)
        if (questionCount > 16 || answerCount > 64) return null
        var position = base + 12
        val questions = ArrayList<Question>(questionCount)
        repeat(questionCount) {
            val (name, next) = readName(buffer, base, position, end) ?: return null
            if (next + 4 > end) return null
            questions += Question(name, buffer.u16(next))
            position = next + 4
        }
        val answers = ArrayList<Answer>(answerCount)
        repeat(answerCount) {
            val (name, next) = readName(buffer, base, position, end) ?: return@repeat
            if (next + 10 > end) return@repeat
            val type = buffer.u16(next)
            val dataLength = buffer.u16(next + 8)
            val dataStart = next + 10
            if (dataStart + dataLength > end) return@repeat
            val address = when {
                type == 1 && dataLength == 4 -> InetAddress.getByAddress(buffer.copyOfRange(dataStart, dataStart + 4))
                type == 28 && dataLength == 16 -> InetAddress.getByAddress(buffer.copyOfRange(dataStart, dataStart + 16))
                else -> null
            }
            answers += Answer(name, type, address)
            position = dataStart + dataLength
        }
        return Message(buffer.u16(base), flags and 0x8000 != 0, flags and 0x000F, questions, answers)
    }

    private fun readName(buffer: ByteArray, base: Int, start: Int, end: Int): Pair<String, Int>? {
        val name = StringBuilder()
        var position = start
        var resume = -1
        var jumps = 0
        while (true) {
            if (position >= end) return null
            val length = buffer.u8(position)
            if (length == 0) {
                position++
                break
            }
            if (length and 0xC0 == 0xC0) {
                if (position + 1 >= end) return null
                if (resume < 0) resume = position + 2
                position = base + (((length and 0x3F) shl 8) or buffer.u8(position + 1))
                if (++jumps > 10) return null
            } else if (length and 0xC0 != 0) {
                return null
            } else {
                if (position + 1 + length > end) return null
                if (name.isNotEmpty()) name.append('.')
                for (i in 1..length) name.append(buffer.u8(position + i).toChar())
                position += 1 + length
            }
        }
        return name.toString() to (if (resume >= 0) resume else position)
    }

    fun typeName(type: Int): String = when (type) {
        1 -> "A"
        2 -> "NS"
        5 -> "CNAME"
        6 -> "SOA"
        12 -> "PTR"
        15 -> "MX"
        16 -> "TXT"
        28 -> "AAAA"
        33 -> "SRV"
        65 -> "HTTPS"
        else -> "TYPE$type"
    }
}

object TlsParser {
    fun serverName(buffer: ByteArray, offset: Int, length: Int): String? {
        if (length < 43) return null
        return try {
            read(buffer, offset, offset + length)
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    private fun read(buffer: ByteArray, offset: Int, end: Int): String? {
        if (buffer.u8(offset) != 0x16) return null
        var position = offset + 5
        if (buffer.u8(position) != 0x01) return null
        position += 4 + 2 + 32
        if (position >= end) return null
        position += 1 + buffer.u8(position)
        if (position + 2 > end) return null
        position += 2 + buffer.u16(position)
        if (position >= end) return null
        position += 1 + buffer.u8(position)
        if (position + 2 > end) return null
        val extensionsEnd = minOf(end, position + 2 + buffer.u16(position))
        position += 2
        while (position + 4 <= extensionsEnd) {
            val type = buffer.u16(position)
            val size = buffer.u16(position + 2)
            position += 4
            if (position + size > extensionsEnd) return null
            if (type == 0) {
                if (size < 5) return null
                val nameType = buffer.u8(position + 2)
                val nameLength = buffer.u16(position + 3)
                val nameStart = position + 5
                if (nameType != 0 || nameStart + nameLength > position + size) return null
                return String(buffer, nameStart, nameLength, Charsets.US_ASCII)
            }
            position += size
        }
        return null
    }
}

object HttpParser {
    private val methods = listOf("GET ", "POST ", "HEAD ", "PUT ", "DELETE ", "OPTIONS ", "PATCH ", "CONNECT ")

    fun requestLine(buffer: ByteArray, offset: Int, length: Int): String? {
        if (length < 8) return null
        val head = String(buffer, offset, minOf(length, 1024), Charsets.ISO_8859_1)
        if (methods.none { head.startsWith(it) }) return null
        return head.substringBefore("\r\n").substringBefore('\n').take(160)
    }

    fun host(buffer: ByteArray, offset: Int, length: Int): String? {
        if (requestLine(buffer, offset, length) == null) return null
        val head = String(buffer, offset, minOf(length, 1024), Charsets.ISO_8859_1)
        for (line in head.split("\r\n")) {
            if (line.startsWith("Host:", ignoreCase = true)) {
                val value = line.substring(5).trim()
                return if (value.startsWith("[")) value.substringBefore(']') + "]" else value.substringBefore(':')
            }
        }
        return null
    }
}
