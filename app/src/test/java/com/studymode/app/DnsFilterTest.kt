package com.studymode.app

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsFilterTest {
    @Test
    fun normalizesInternationalAndCaseVariants() {
        assertEquals("xn--bcher-kva.example", normalizeBlockedDomain("  BÜCHER.Example. "))
    }

    @Test
    fun rejectsUrlsAndInvalidDomainForms() {
        listOf("https://example.com", "example.com/path", "localhost", "bad_domain.example")
            .forEach { input ->
                assertThrows(IllegalArgumentException::class.java) {
                    normalizeBlockedDomain(input)
                }
            }
    }

    @Test
    fun domainRuleMatchesRootAndSubdomainsOnly() {
        assertTrue(domainRuleMatches("example.com", "example.com"))
        assertTrue(domainRuleMatches("m.example.com", "example.com"))
        assertFalse(domainRuleMatches("notexample.com", "example.com"))
    }

    @Test
    fun parsesQuestionAndBuildsNxdomainResponse() {
        val query = dnsQuery("video.example")

        val question = parseDnsQuestion(query, query.size)
        assertNotNull(question)
        assertEquals("video.example", question?.hostname)

        val response = buildDnsErrorResponse(query, query.size, 3)
        assertNotNull(response)
        assertEquals(0x81, response?.get(2)?.toInt()?.and(0xFF))
        assertEquals(0x83, response?.get(3)?.toInt()?.and(0xFF))
        assertEquals(query[0], response?.get(0))
        assertEquals(1, response?.get(5)?.toInt()?.and(0xFF))
        assertEquals("video.example", parseDnsQuestion(response!!, response.size)?.hostname)
    }

    @Test
    fun parsesDnsPacketAndBuildsRoutableIpv4Response() {
        val query = dnsQuery("blocked.example")
        val requestPacket = ipv4DnsPacket(query)
        val request = parseIpv4DnsRequest(
            requestPacket,
            requestPacket.size,
            byteArrayOf(10, 111, 222.toByte(), 2),
        )
        assertNotNull(request)

        val response = buildIpv4UdpResponse(request!!, query)
        assertNotNull(response)
        assertEquals(192, response!![16].toInt() and 0xFF)
        assertEquals(1, response[19].toInt() and 0xFF)
        assertEquals(53, unsignedShortAt(response, 20))
        assertEquals(54_321, unsignedShortAt(response, 22))
        assertEquals(query.size, response.size - 28)
    }

    @Test
    fun malformedDnsQuestionsAreIgnored() {
        assertNull(parseDnsQuestion(byteArrayOf(0, 1, 0), 3))
    }
}

private fun dnsQuery(hostname: String): ByteArray {
    val packet = ByteArrayOutputStream().apply {
        write(byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
        hostname.split('.').forEach { label ->
            val bytes = label.toByteArray(Charsets.US_ASCII)
            write(bytes.size)
            write(bytes)
        }
        write(0)
        write(byteArrayOf(0, 1, 0, 1))
    }.toByteArray()
    return packet
}

private fun ipv4DnsPacket(query: ByteArray): ByteArray {
    val packet = ByteArray(28 + query.size)
    packet[0] = 0x45
    putUnsignedShort(packet, 2, packet.size)
    putUnsignedShort(packet, 4, 7)
    putUnsignedShort(packet, 6, 0x4000)
    packet[8] = 64
    packet[9] = 17
    byteArrayOf(192.toByte(), 0, 2, 1).copyInto(packet, 12)
    byteArrayOf(10, 111, 222.toByte(), 2).copyInto(packet, 16)
    putUnsignedShort(packet, 20, 54_321)
    putUnsignedShort(packet, 22, 53)
    putUnsignedShort(packet, 24, 8 + query.size)
    query.copyInto(packet, 28)
    return packet
}

private fun putUnsignedShort(bytes: ByteArray, offset: Int, value: Int) {
    bytes[offset] = (value ushr 8).toByte()
    bytes[offset + 1] = value.toByte()
}

private fun unsignedShortAt(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
