package com.smarttasker.core.protocol

import org.junit.Assert.*
import org.junit.Test

class FrameCodecTest {

    @Test
    fun `encode and decode round trip preserves payload`() {
        val payload = "hello world".toByteArray()
        val encoded = FrameCodec.encode(seq = 42, cmd = 0x10, payload = payload)
        val decoded = FrameCodec.decode(encoded)
        assertEquals(FrameCodec.VERSION_V2, decoded.version)
        assertEquals(42, decoded.seq)
        assertEquals(0x10.toByte(), decoded.cmd)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun `empty payload round trips`() {
        val encoded = FrameCodec.encode(seq = 0, cmd = 0, payload = ByteArray(0))
        val decoded = FrameCodec.decode(encoded)
        assertEquals(0, decoded.payload.size)
    }

    @Test
    fun `encoded frame starts with magic 0xAA55`() {
        val encoded = FrameCodec.encode(seq = 1, cmd = 1, payload = byteArrayOf(0))
        assertEquals(FrameCodec.MAGIC, ((encoded[0].toInt() and 0xFF) shl 8 or (encoded[1].toInt() and 0xFF)).toShort())
    }

    @Test(expected = FrameCodec.ProtocolException::class)
    fun `decode rejects too-short frame`() {
        FrameCodec.decode(ByteArray(5))
    }

    @Test(expected = FrameCodec.ProtocolException::class)
    fun `decode rejects invalid magic`() {
        val bad = ByteArray(20) // zeroed, magic 0x0000 != 0xAA55
        FrameCodec.decode(bad)
    }

    @Test(expected = FrameCodec.CRCException::class)
    fun `decode rejects corrupted payload`() {
        val encoded = FrameCodec.encode(seq = 1, cmd = 1, payload = byteArrayOf(1, 2, 3))
        // Corrupt the first payload byte
        encoded[FrameCodec.HEADER_SIZE_V2] = (encoded[FrameCodec.HEADER_SIZE_V2].toInt() xor 0xFF).toByte()
        FrameCodec.decode(encoded)
    }

    @Test
    fun `validateMagic returns true for valid magic`() {
        val encoded = FrameCodec.encode(seq = 0, cmd = 0, payload = byteArrayOf())
        assertTrue(FrameCodec.validateMagic(encoded))
    }

    @Test
    fun `validateMagic returns false for invalid magic`() {
        assertFalse(FrameCodec.validateMagic(byteArrayOf(0, 0)))
    }

    @Test
    fun `getFrameInfo extracts header without full parse`() {
        val encoded = FrameCodec.encode(seq = 7, cmd = 0x20, payload = byteArrayOf(1, 2, 3, 4))
        val info = FrameCodec.getFrameInfo(encoded)
        assertEquals(7, info.seq)
        assertEquals(0x20.toByte(), info.cmd)
        assertEquals(4, info.payloadLength)
    }

    @Test
    fun `parsePayloadLengthFromHeader returns length without full decode`() {
        val payload = ByteArray(256) { it.toByte() }
        val encoded = FrameCodec.encode(seq = 1, cmd = 1, payload = payload)
        val header = encoded.copyOfRange(0, FrameCodec.HEADER_SIZE_V2)
        assertEquals(256, FrameCodec.parsePayloadLengthFromHeader(header))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `encode rejects payload over max`() {
        FrameCodec.encode(seq = 1, cmd = 1, payload = ByteArray(FrameCodec.MAX_PAYLOAD_SIZE + 1))
    }
}
