package com.rynekryz.sniffsniff.core.common.util

import com.rynekryz.sniffsniff.core.common.model.Protocol

fun serviceName(protocol: Protocol, port: Int): String? = when (protocol) {
    Protocol.TCP -> when (port) {
        20, 21 -> "FTP"
        22 -> "SSH"
        25, 465, 587 -> "SMTP"
        53 -> "DNS"
        80, 8080 -> "HTTP"
        110, 995 -> "POP3"
        143, 993 -> "IMAP"
        443, 8443 -> "HTTPS"
        853 -> "DNS over TLS"
        1883, 8883 -> "MQTT"
        5222, 5228 -> "XMPP/FCM"
        else -> null
    }
    Protocol.UDP -> when (port) {
        53 -> "DNS"
        67, 68 -> "DHCP"
        123 -> "NTP"
        443 -> "QUIC"
        1900 -> "SSDP"
        5353 -> "mDNS"
        else -> null
    }
    else -> null
}
