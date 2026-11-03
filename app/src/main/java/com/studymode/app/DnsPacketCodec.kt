package com.studymode.app

import java.util.Locale

internal data class DnsQuestion(
    val hostname: String,
    val questionEndOffset: Int,
)

internal data class UdpDnsRequest(
    val sourceAddress: ByteArray,
    val destinationAddress: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val identification: Int,
    val dnsPayload: ByteArray,
)

internal fun parseDnsQuestion(packet: ByteArray, length: Int): DnsQuestion? {
    if (length < DNS_HEADER_LENGTH || length > packet.size) return null
    if (unsignedShort(packet, 4) == 0) return null

    val labels = mutableListOf<String>()
    val visitedPointers = mutableSetOf<Int>()
    var cursor = DNS_HEADER_LENGTH
    var questionEnd: Int? = null
    var jumped = false

    repeat(MAX_DNS_NAME_STEPS) {
        if (cursor >= length) return null
        val labelLength = packet[cursor].toInt() and 0xFF
        when {
            labelLength == 0 -> {
                if (!jumped) questionEnd = cursor + 1
                val end = (questionEnd ?: return null) + DNS_QUESTION_TAIL_LENGTH
                if (end > length || labels.isEmpty()) return null
                return DnsQuestion(labels.joinToString(".").lowercase(Locale.ROOT), end)
            }
            (labelLength and DNS_POINTER_MASK) == DNS_POINTER_MASK -> {
                if (cursor + 1 >= length) return null
                val pointer = ((labelLength and DNS_POINTER_OFFSET_MASK) shl 8) or
                    (packet[cursor + 1].toInt() and 0xFF)
                if (pointer >= length || !visitedPointers.add(pointer)) return null
                if (!jumped) questionEnd = cursor + 2
                cursor = pointer
                jumped = true
            }
            labelLength > MAX_DNS_LABEL_LENGTH || cursor + 1 + labelLength > length -> return null
            else -> {
                val labelBytes = packet.copyOfRange(cursor + 1, cursor + 1 + labelLength)
                if (labelBytes.any { byte ->
                        val value = byte.toInt() and 0xFF
                        value !in MIN_DNS_LABEL_BYTE..MAX_DNS_LABEL_BYTE
                    }
                ) {
                    return null
                }
                labels += String(labelBytes, Charsets.US_ASCII)
                cursor += labelLength + 1
            }
        }
    }
    return null
}

internal fun buildDnsErrorResponse(query: ByteArray, length: Int, responseCode: Int): ByteArray? {
    require(responseCode in 0..15)
    val question = parseDnsQuestion(query, length) ?: return null
    val response = query.copyOf(question.questionEndOffset)
    response[2] = (DNS_QR_FLAG or (query[2].toInt() and DNS_RD_FLAG)).toByte()
    response[3] = (DNS_RA_FLAG or responseCode).toByte()
    putUnsignedShort(response, 4, 1)
    putUnsignedShort(response, 6, 0)
    putUnsignedShort(response, 8, 0)
    putUnsignedShort(response, 10, 0)
    return response
}

internal fun isDnsResponseForQuery(query: ByteArray, response: ByteArray): Boolean =
    query.size >= 2 &&
        response.size >= DNS_HEADER_LENGTH &&
        query[0] == response[0] &&
        query[1] == response[1] &&
        (response[2].toInt() and DNS_QR_FLAG) != 0

internal fun parseIpv4DnsRequest(
    packet: ByteArray,
    packetLength: Int,
    dnsServerAddress: ByteArray,
): UdpDnsRequest? {
    if (packetLength < IPV4_HEADER_LENGTH + UDP_HEADER_LENGTH || packetLength > packet.size) return null
    if (((packet[0].toInt() and 0xFF) ushr 4) != IPV4_VERSION) return null

    val ipHeaderLength = (packet[0].toInt() and IPV4_HEADER_LENGTH_MASK) * 4
    val totalLength = unsignedShort(packet, 2)
    if (ipHeaderLength < IPV4_HEADER_LENGTH || totalLength > packetLength || totalLength < ipHeaderLength + UDP_HEADER_LENGTH) {
        return null
    }
    if ((packet[9].toInt() and 0xFF) != UDP_PROTOCOL) return null
    if ((unsignedShort(packet, 6) and IPV4_FRAGMENT_OFFSET_MASK) != 0) return null

    val sourceAddress = packet.copyOfRange(IPV4_SOURCE_OFFSET, IPV4_DESTINATION_OFFSET)
    val destinationAddress = packet.copyOfRange(IPV4_DESTINATION_OFFSET, IPV4_HEADER_LENGTH)
    if (!destinationAddress.contentEquals(dnsServerAddress)) return null

    val udpOffset = ipHeaderLength
    val sourcePort = unsignedShort(packet, udpOffset)
    val destinationPort = unsignedShort(packet, udpOffset + 2)
    if (destinationPort != DNS_PORT) return null
    val udpLength = unsignedShort(packet, udpOffset + 4)
    if (udpLength < UDP_HEADER_LENGTH || udpOffset + udpLength > totalLength) return null

    return UdpDnsRequest(
        sourceAddress = sourceAddress,
        destinationAddress = destinationAddress,
        sourcePort = sourcePort,
        destinationPort = destinationPort,
        identification = unsignedShort(packet, 4),
        dnsPayload = packet.copyOfRange(udpOffset + UDP_HEADER_LENGTH, udpOffset + udpLength),
    )
}

internal fun buildIpv4UdpResponse(request: UdpDnsRequest, payload: ByteArray): ByteArray? {
    val udpLength = UDP_HEADER_LENGTH + payload.size
    val totalLength = IPV4_HEADER_LENGTH + udpLength
    if (totalLength > MAX_IPV4_PACKET_LENGTH) return null

    val response = ByteArray(totalLength)
    response[0] = IPV4_VERSION_AND_IHL.toByte()
    putUnsignedShort(response, 2, totalLength)
    putUnsignedShort(response, 4, request.identification)
    putUnsignedShort(response, 6, IPV4_DONT_FRAGMENT)
    response[8] = DEFAULT_TTL.toByte()
    response[9] = UDP_PROTOCOL.toByte()
    request.destinationAddress.copyInto(response, IPV4_SOURCE_OFFSET)
    request.sourceAddress.copyInto(response, IPV4_DESTINATION_OFFSET)
    putUnsignedShort(response, 10, internetChecksum(response, 0, IPV4_HEADER_LENGTH))

    val udpOffset = IPV4_HEADER_LENGTH
    putUnsignedShort(response, udpOffset, request.destinationPort)
    putUnsignedShort(response, udpOffset + 2, request.sourcePort)
    putUnsignedShort(response, udpOffset + 4, udpLength)
    payload.copyInto(response, udpOffset + UDP_HEADER_LENGTH)
    return response
}

private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

private fun putUnsignedShort(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value ushr 8).toByte()
    bytes[offset + 1] = value.toByte()
}

private fun internetChecksum(bytes: ByteArray, offset: Int, length: Int): Int {
    var sum = 0L
    var index = offset
    val end = offset + length
    while (index + 1 < end) {
        sum += unsignedShort(bytes, index)
        index += 2
    }
    if (index < end) sum += (bytes[index].toInt() and 0xFF) shl 8
    while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
    return sum.inv().toInt() and 0xFFFF
}

private const val DNS_HEADER_LENGTH = 12
private const val DNS_QUESTION_TAIL_LENGTH = 4
private const val DNS_POINTER_MASK = 0xC0
private const val DNS_POINTER_OFFSET_MASK = 0x3F
private const val MAX_DNS_LABEL_LENGTH = 63
private const val MAX_DNS_NAME_STEPS = 128
private const val MIN_DNS_LABEL_BYTE = 0x21
private const val MAX_DNS_LABEL_BYTE = 0x7E
private const val DNS_QR_FLAG = 0x80
private const val DNS_RD_FLAG = 0x01
private const val DNS_RA_FLAG = 0x80
private const val IPV4_VERSION = 4
private const val IPV4_VERSION_AND_IHL = 0x45
private const val IPV4_HEADER_LENGTH_MASK = 0x0F
private const val IPV4_HEADER_LENGTH = 20
private const val IPV4_SOURCE_OFFSET = 12
private const val IPV4_DESTINATION_OFFSET = 16
private const val IPV4_FRAGMENT_OFFSET_MASK = 0x1FFF
private const val IPV4_DONT_FRAGMENT = 0x4000
private const val MAX_IPV4_PACKET_LENGTH = 65_535
private const val DEFAULT_TTL = 64
private const val UDP_PROTOCOL = 17
private const val UDP_HEADER_LENGTH = 8
private const val DNS_PORT = 53
