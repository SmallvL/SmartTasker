package com.smarttasker.core.adb

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Minimal ADB protocol client for wireless debugging.
 * Implements CNXN/AUTH/OPEN/WRAY/CLSE/OKAY to execute shell commands
 * via the Android ADB daemon.
 *
 * Requires: Android 11+ wireless debugging enabled, or USB ADB with tcpip.
 */
class AdbClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 5555,
    private val timeoutMs: Int = 5000
) : Closeable {

    private val socket = Socket().apply {
        connect(InetSocketAddress(host, port), timeoutMs)
        soTimeout = timeoutMs
        tcpNoDelay = true
    }
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    private var localId = 1
    private var connected = false

    companion object {
        // ADB message commands
        private const val CMD_CNXN = 0x4e584e43  // CNXN
        private const val CMD_AUTH = 0x48545541  // AUTH
        private const val CMD_OPEN = 0x4e45504f  // OPEN
        private const val CMD_OKAY = 0x59414b4f  // OKAY
        private const val CMD_CLSE = 0x45534c43  // CLSE
        private const val CMD_WRTE = 0x45545257  // WRTE

        // AUTH types
        private const val AUTH_TYPE_TOKEN = 1
        private const val AUTH_TYPE_SIGNATURE = 2
        private const val AUTH_TYPE_RSA = 3

        // ADB protocol version
        private const val A_VERSION = 0x01000000
        private const val MAX_PAYLOAD = 4096

        private const val CONNECT_STRING = "host::features=shell_v2,cmd,stat_v2,ls_v2,fixed_push_mkdir,apex,abb,fixed_push_symlink_timestamp,abb_exec,remount_shell,track_app,sendrecv_v2,sendrecv_v2_brotli,sendrecv_v2_lz4,sendrecv_v2_zstd,sendrecv_v2_dry_run_send,openscreen_mdns\n"
    }

    /**
     * Connect to ADB daemon and authenticate.
     * Returns true if connected successfully.
     */
    fun connect(): Boolean {
        // Send CNXN
        sendMsg(CMD_CNXN, A_VERSION, MAX_PAYLOAD, CONNECT_STRING.toByteArray())

        // Read response
        val resp = readMsg()
        when (resp.cmd) {
            CMD_CNXN -> {
                connected = true
                return true
            }
            CMD_AUTH -> {
                // Need to authenticate - try with empty token first (no auth)
                sendMsg(CMD_AUTH, AUTH_TYPE_TOKEN, 0, ByteArray(0))
                val authResp = readMsg()
                if (authResp.cmd == CMD_CNXN) {
                    connected = true
                    return true
                }
                // Authentication failed - needs pairing
                return false
            }
            else -> return false
        }
    }

    /**
     * Execute a shell command and return output.
     * Must be connected first.
     */
    fun shell(command: String): String {
        if (!connected) throw IllegalStateException("Not connected")

        val localId = nextId()
        val remoteId = 0

        // Open shell service
        val service = "shell:$command"
        sendMsg(CMD_OPEN, localId, 0, service.toByteArray())

        // Wait for OKAY
        var shellRemoteId = 0
        while (true) {
            val msg = readMsg()
            when (msg.cmd) {
                CMD_OKAY -> {
                    if (msg.arg0 == localId) {
                        shellRemoteId = msg.arg1
                        break
                    }
                }
                CMD_CLSE -> throw RuntimeException("Shell open failed")
                else -> {} // Ignore other messages
            }
        }

        // Read output until CLSE
        val output = StringBuilder()
        while (true) {
            val msg = readMsg()
            when (msg.cmd) {
                CMD_WRTE -> {
                    output.append(String(msg.payload, Charsets.UTF_8))
                    // Send OKAY to acknowledge
                    sendMsg(CMD_OKAY, localId, shellRemoteId, ByteArray(0))
                }
                CMD_CLSE -> {
                    // Close our side
                    sendMsg(CMD_CLSE, localId, shellRemoteId, ByteArray(0))
                    break
                }
                else -> {} // Ignore
            }
        }

        return output.toString().trim()
    }

    // ===== Protocol =====

    private fun sendMsg(cmd: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val msg = ByteArray(24 + payload.size)
        val buf = ByteBuffer.wrap(msg).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(cmd)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(payload.size)
        buf.putInt(checksum(payload))
        buf.putInt(cmd xor 0xFFFFFFFF.toInt())
        buf.put(payload)
        output.write(msg)
        output.flush()
    }

    private fun readMsg(): AdbMessage {
        val header = ByteArray(24)
        input.readFully(header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val length = buf.int
        val checksum = buf.int
        val magic = buf.int

        val payload = if (length > 0) {
            val data = ByteArray(length)
            input.readFully(data)
            data
        } else ByteArray(0)

        return AdbMessage(cmd, arg0, arg1, payload)
    }

    private fun nextId(): Int = localId++

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum += (b.toInt() and 0xFF)
        return sum
    }

    override fun close() {
        try { socket.close() } catch (_: Exception) {}
    }

    data class AdbMessage(val cmd: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)
}
