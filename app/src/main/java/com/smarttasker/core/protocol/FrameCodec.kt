package com.smarttasker.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/**
 * LXB-Link frame codec. Ported from AutoLXB's FrameCodec.java.
 */
object FrameCodec {
    const val MAGIC: Short = 0xAA55.toShort()
    const val VERSION_V1: Byte = 0x01
    const val VERSION_V2: Byte = 0x02
    const val VERSION: Byte = VERSION_V2
    const val HEADER_SIZE_V1 = 10
    const val HEADER_SIZE_V2 = 12
    const val HEADER_SIZE = HEADER_SIZE_V2
    const val CRC_SIZE = 4
    const val MIN_FRAME_SIZE = HEADER_SIZE_V1 + CRC_SIZE
    const val MAX_PAYLOAD_SIZE = 16 * 1024 * 1024

    class ProtocolException(message: String) : Exception(message)
    class CRCException(message: String) : Exception(message)

    class DecodedFrame(val version: Byte, val seq: Int, val cmd: Byte, val payload: ByteArray) {
        override fun toString() =
            "Frame[ver=0x${version.toInt().and(0xFF).toString(16).uppercase()}, seq=$seq, cmd=0x${cmd.toInt().and(0xFF).toString(16).uppercase()}, len=${payload.size}]"
    }

    class FrameInfo {
        var magic: Short = 0
        var version: Byte = 0
        var seq: Int = 0
        var cmd: Byte = 0
        var payloadLength: Int = 0
        override fun toString() =
            "FrameInfo[magic=0x${magic.toInt().and(0xFFFF).toString(16).uppercase()}, ver=0x${version.toInt().and(0xFF).toString(16).uppercase()}, seq=$seq, cmd=0x${cmd.toInt().and(0xFF).toString(16).uppercase()}, len=$payloadLength]"
    }

    private fun computeHeaderSizeByVersion(version: Byte): Int = when (version) {
        VERSION_V1 -> HEADER_SIZE_V1
        VERSION_V2 -> HEADER_SIZE_V2
        else -> throw ProtocolException("Unsupported version: 0x${version.toInt().and(0xFF).toString(16).uppercase()}")
    }

    fun encode(seq: Int, cmd: Byte, payload: ByteArray): ByteArray {
        val p = payload.let { it }
        if (p.size > MAX_PAYLOAD_SIZE) throw IllegalArgumentException("Payload too large: ${p.size} (max $MAX_PAYLOAD_SIZE)")
        val frameSize = HEADER_SIZE_V2 + p.size + CRC_SIZE
        val buffer = ByteBuffer.allocate(frameSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(MAGIC)
        buffer.put(VERSION_V2)
        buffer.putInt(seq)
        buffer.put(cmd)
        buffer.putInt(p.size)
        buffer.put(p)
        val crc32 = CRC32()
        crc32.update(buffer.array(), 0, HEADER_SIZE_V2 + p.size)
        buffer.putInt(crc32.value.toInt())
        return buffer.array()
    }

    fun decode(data: ByteArray): DecodedFrame {
        if (data.size < MIN_FRAME_SIZE) throw ProtocolException("Frame too short: ${data.size} bytes")
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.short
        if (magic != MAGIC) throw ProtocolException("Invalid magic")
        val ver = buffer.get()
        val headerSize = computeHeaderSizeByVersion(ver)
        if (data.size < headerSize + CRC_SIZE) throw ProtocolException("Frame too short for header")
        
        // Re-read from start
        val buf2 = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        buf2.short // magic (already checked)
        val ver2 = buf2.get()
        val seq = buf2.int
        val cmd = buf2.get()
        val payloadLen = if (ver2 == VERSION_V2) buf2.int else buf2.short.toInt().and(0xFFFF)
        if (payloadLen < 0 || payloadLen > MAX_PAYLOAD_SIZE) throw ProtocolException("Payload too large: $payloadLen")

        val payload = ByteArray(payloadLen)
        buf2.get(payload)
        val receivedCRC = buf2.int
        
        val crc32 = CRC32()
        crc32.update(data, 0, headerSize + payloadLen)
        val calculatedCRC = crc32.value.toInt()
        if (receivedCRC != calculatedCRC) throw CRCException("CRC mismatch")
        
        return DecodedFrame(ver2, seq, cmd, payload)
    }

    fun validateMagic(data: ByteArray): Boolean {
        if (data.size < 2) return false
        return ByteBuffer.wrap(data, 0, 2).order(ByteOrder.BIG_ENDIAN).short == MAGIC
    }

    fun headerSizeForVersion(version: Byte): Int = computeHeaderSizeByVersion(version)

    fun parsePayloadLengthFromHeader(header: ByteArray): Int {
        if (header.size < HEADER_SIZE_V1) throw ProtocolException("Header too short")
        val hb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val magic = hb.short
        if (magic != MAGIC) throw ProtocolException("Invalid magic in header")
        val ver = hb.get()
        val headerSize = computeHeaderSizeByVersion(ver)
        if (header.size < headerSize) throw ProtocolException("Header length mismatch")
        hb.int // seq
        hb.get() // cmd
        return if (ver == VERSION_V2) hb.int else hb.short.toInt().and(0xFFFF)
    }

    fun getFrameInfo(data: ByteArray): FrameInfo {
        if (data.size < HEADER_SIZE_V1) throw ProtocolException("Data too short")
        val pre = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        pre.short // magic
        val ver = pre.get()
        val headerSize = computeHeaderSizeByVersion(ver)
        if (data.size < headerSize) throw ProtocolException("Data too short for header")
        
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val info = FrameInfo()
        info.magic = buf.short
        info.version = buf.get()
        info.seq = buf.int
        info.cmd = buf.get()
        info.payloadLength = if (info.version == VERSION_V2) buf.int else buf.short.toInt().and(0xFFFF)
        return info
    }
}
