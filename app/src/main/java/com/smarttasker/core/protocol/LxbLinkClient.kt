package com.smarttasker.core.protocol

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * LXB-Link TCP client - communicates with lxb-core.
 * Ported from AutoLXB LocalLinkClient.
 */
class LxbLinkClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 12345,
    private val defaultTimeoutMs: Int = 8000
) : Closeable {

    companion object {
        private val GLOBAL_SEQ = AtomicInteger(1)
    }

    private val socket = Socket().apply {
        connect(InetSocketAddress(host, port), defaultTimeoutMs)
        soTimeout = defaultTimeoutMs
        tcpNoDelay = true
    }

    private val input = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())

    @Synchronized
    fun handshake(timeoutMs: Int = 3000) {
        sendCommandRaw(CommandIds.CMD_HANDSHAKE, ByteArray(0), timeoutMs)
    }

    @Synchronized
    fun sendCommand(cmd: Byte, payload: ByteArray, timeoutMs: Int = defaultTimeoutMs): ByteArray {
        return sendCommandRaw(cmd, payload, timeoutMs)
    }

    private fun sendCommandRaw(cmd: Byte, payload: ByteArray, timeoutMs: Int): ByteArray {
        val seq = nextSeq()
        val frame = FrameCodec.encode(seq, cmd, payload)
        socket.soTimeout = timeoutMs
        output.write(frame)
        output.flush()

        val respData = try {
            readFrame(timeoutMs)
        } catch (e: SocketTimeoutException) {
            throw RuntimeException("TCP recv timeout for cmd=0x${String.format("%02X", cmd.toInt() and 0xFF)}", e)
        }

        val decoded = FrameCodec.decode(respData)
        val cmdInt = decoded.cmd.toInt() and 0xFF
        val ackInt = CommandIds.CMD_ACK.toInt() and 0xFF
        if (cmdInt != ackInt) {
            throw RuntimeException("Unexpected response cmd: 0x${String.format("%02X", cmdInt)}")
        }
        if (decoded.seq != seq) {
            throw RuntimeException("ACK seq mismatch: got ${decoded.seq}, expected $seq")
        }
        return decoded.payload
    }

    private fun readFrame(timeoutMs: Int): ByteArray {
        socket.soTimeout = timeoutMs
        val prefix = ByteArray(3)
        readFully(prefix, 0, 3)

        val version = prefix[2]
        val headerSize = if (version.toInt() == 0x01) 10 else 12
        val header = ByteArray(headerSize)
        System.arraycopy(prefix, 0, header, 0, 3)
        if (headerSize > 3) readFully(header, 3, headerSize - 3)

        val payloadLength = FrameCodec.decode(header).let {
            // Re-parse to get payload length from header
            val buf = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.BIG_ENDIAN)
            buf.short // magic
            buf.get() // version
            buf.int // seq
            buf.get() // cmd
            buf.int // payload length
        }

        val totalLength = headerSize + payloadLength + FrameCodec.CRC_SIZE
        val frame = ByteArray(totalLength)
        System.arraycopy(header, 0, frame, 0, headerSize)
        readFully(frame, headerSize, payloadLength + FrameCodec.CRC_SIZE)
        return frame
    }

    private fun readFully(buf: ByteArray, offset: Int, length: Int) {
        var read = 0
        while (read < length) {
            val n = input.read(buf, offset + read, length - read)
            if (n < 0) throw RuntimeException("TCP socket closed")
            read += n
        }
    }

    private fun nextSeq(): Int {
        while (true) {
            val cur = GLOBAL_SEQ.get()
            val next = if (cur >= 0x7FFFFFF0) 1 else cur + 1
            if (GLOBAL_SEQ.compareAndSet(cur, next)) return cur
        }
    }

    override fun close() {
        try { socket.close() } catch (_: Exception) {}
    }
}
