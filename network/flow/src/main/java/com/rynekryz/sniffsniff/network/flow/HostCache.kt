package com.rynekryz.sniffsniff.network.flow

import java.net.InetAddress

class HostCache(private val capacity: Int = 4096) {
    private val entries = object : LinkedHashMap<InetAddress, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<InetAddress, String>): Boolean = size > capacity
    }

    fun put(address: InetAddress, host: String) {
        entries[address] = host
    }

    fun get(address: InetAddress): String? = entries[address]
}
