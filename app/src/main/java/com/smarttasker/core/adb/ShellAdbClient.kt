package com.smarttasker.core.adb

import com.smarttasker.util.DebugLog
import java.io.Closeable
import java.io.InputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Shell-based ADB client that uses `nc` (netcat) via Runtime.exec
 * instead of Java Socket. This bypasses Android's network restrictions
 * for untrusted_app UIDs that can't use Java Socket but CAN use nc.
 *
 * The nc process stays alive for the duration of the connection,
 * and ADB binary protocol messages are piped through its stdin/stdout.
 */
class ShellAdbClient(
    private val host: String = "127.0.0.1",
    private val port: Int = 5555,
    private val timeoutMs: Int = 5000
) : Closeable {

    private var process: Process? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

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
     * Connect to ADB daemon via nc and authenticate.
     * Returns true if connected successfully.
     */
    fun connect(): Boolean {
        try {
            DebugLog.d("ShellAdb", "Starting nc process for $host:$port")
            // Spawn nc process in shell environment for correct stdio handling
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "nc $host $port"))
            process = proc
            input = DataInputStream(proc.inputStream)
            output = DataOutputStream(proc.outputStream)

            DebugLog.d("ShellAdb", "nc process alive=${proc.isAlive}")

            // Give nc time to establish the TCP connection
            Thread.sleep(100)

            // Send CNXN
            val msg = buildCnxnMessage()
            DebugLog.d("ShellAdb", "Sending CNXN (${msg.size} bytes)")
            sendMsg(CMD_CNXN, A_VERSION, MAX_PAYLOAD, CONNECT_STRING.toByteArray())

            // Read response
            DebugLog.d("ShellAdb", "Waiting for response...")
            val resp = readMsgWithTimeout() ?: run {
                DebugLog.d("ShellAdb", "Response timed out after ${timeoutMs}ms")
                close()
                return false
            }
            DebugLog.d("ShellAdb", "Response cmd=0x${resp.cmd.toString(16)} len=${resp.payload.size}")
            when (resp.cmd) {
                CMD_CNXN -> {
                    connected = true
                    return true
                }
                CMD_AUTH -> {
                    // Need to authenticate - try with empty token first (no auth)
                    sendMsg(CMD_AUTH, AUTH_TYPE_TOKEN, 0, ByteArray(0))
                    DebugLog.d("ShellAdb", "Waiting for AUTH response...")
                    val authResp = readMsgWithTimeout() ?: run {
                        DebugLog.d("ShellAdb", "AUTH response timed out")
                        close()
                        return false
                    }
                    DebugLog.d("ShellAdb", "AUTH response cmd=0x${authResp.cmd.toString(16)}")
                    if (authResp.cmd == CMD_CNXN) {
                        connected = true
                        return true
                    }
                    // Authentication failed - needs pairing
                    close()
                    return false
                }
                else -> {
                    close()
                    return false
                }
            }
        } catch (e: Exception) {
            DebugLog.d("ShellAdb", "Error: ${e.message}")
            e.printStackTrace()
            close()
            return false
        }
    }

    private fun buildCnxnMessage(): ByteArray {
        val payload = CONNECT_STRING.toByteArray()
        return ByteArray(24 + payload.size)
    }

    /**
     * readMsg() with timeout. Returns null if no response within timeoutMs.
     */
    private fun readMsgWithTimeout(): AdbMessage? {
        val result = AtomicReference<AdbMessage?>(null)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit<AdbMessage?> {
                readMsg()
            }
            val msg = future.get(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            result.set(msg)
        } catch (e: Exception) {
            DebugLog.d("ShellAdb", "readMsgWithTimeout exception: ${e.message}")
            return null
        } finally {
            executor.shutdownNow()
        }
        return result.get()
    }

    /**
     * Execute a shell command and return output.
     * Must be connected first.
     */
    fun shell(command: String): String {
        if (!connected) throw IllegalStateException("Not connected")

        try {
            val localId = nextId()

            // Open shell service
            val service = "shell:$command"
            DebugLog.d("ShellAdb", "Sending OPEN for $service (localId=$localId)")
            sendMsg(CMD_OPEN, localId, 0, service.toByteArray())

            // Output buffer (may receive WRTE before or after OKAY)
            val output = StringBuilder()

            // Wait for OKAY (accept any OKAY — ADB daemon may echo different arg0)
            DebugLog.d("ShellAdb", "Waiting for OKAY...")
            var shellRemoteId = 0
            while (true) {
                val msg = readMsg()
                DebugLog.d("ShellAdb", "Got cmd=0x${msg.cmd.toString(16)} arg0=${msg.arg0} arg1=${msg.arg1} payloadLen=${msg.payload.size}")
                when (msg.cmd) {
                    CMD_OKAY -> {
                        shellRemoteId = msg.arg1
                        DebugLog.d("ShellAdb", "OKAY received: using remoteId=$shellRemoteId")
                        break
                    }
                    CMD_WRTE -> {
                        // WRTE before OKAY — buffer data, will be processed after OKAY
                        DebugLog.d("ShellAdb", "Early WRTE (${msg.payload.size} bytes), buffering")
                        output.append(String(msg.payload, Charsets.UTF_8))
                    }
                    CMD_CLSE -> {
                        DebugLog.d("ShellAdb", "Got CLSE during OPEN wait — shell open failed")
                        throw RuntimeException("Shell open failed")
                    }
                    else -> {} // Ignore other messages
                }
            }

            // Read output until CLSE
            while (true) {
                val msg = readMsg()
                DebugLog.d("ShellAdb", "Read cmd=0x${msg.cmd.toString(16)} arg0=${msg.arg0} arg1=${msg.arg1} payloadLen=${msg.payload.size}")
                when (msg.cmd) {
                    CMD_WRTE -> {
                        val data = String(msg.payload, Charsets.UTF_8)
                        DebugLog.d("ShellAdb", "Got WRTE data: '${data.trim()}'")
                        output.append(data)
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

            val result = output.toString().trim()
            DebugLog.d("ShellAdb", "Got CLSE, shell output: '$result'")
            return result
        } catch (e: Exception) {
            DebugLog.d("ShellAdb", "shell() exception: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    /**
     * Open a streaming shell session via a NEW nc process.
     * Returns a Pair of (InputStream with raw command output, Closeable to terminate).
     * The InputStream yields raw bytes from WRTE payloads as they arrive.
     * Returns null on failure.
     */
    fun streamShell(command: String): Pair<InputStream, Closeable>? {
        try {
            DebugLog.d("ShellAdb", "streamShell: starting nc for '$command'")
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "nc $host $port"))
            val procInput = DataInputStream(proc.inputStream)
            val procOutput = DataOutputStream(proc.outputStream)

            Thread.sleep(100)

            // CNXN handshake
            sendMsgTo(procOutput, CMD_CNXN, A_VERSION, MAX_PAYLOAD, CONNECT_STRING.toByteArray())
            val resp = readMsgFrom(procInput) ?: run {
                DebugLog.d("ShellAdb", "streamShell: CNXN timeout")
                proc.destroy()
                return null
            }
            when (resp.cmd) {
                CMD_AUTH -> {
                    sendMsgTo(procOutput, CMD_AUTH, AUTH_TYPE_TOKEN, 0, ByteArray(0))
                    val authResp = readMsgFrom(procInput) ?: run {
                        DebugLog.d("ShellAdb", "streamShell: AUTH timeout")
                        proc.destroy()
                        return null
                    }
                    if (authResp.cmd != CMD_CNXN) {
                        DebugLog.d("ShellAdb", "streamShell: AUTH failed, got 0x${authResp.cmd.toString(16)}")
                        proc.destroy()
                        return null
                    }
                }
                CMD_CNXN -> { /* connected */ }
                else -> {
                    DebugLog.d("ShellAdb", "streamShell: unexpected response 0x${resp.cmd.toString(16)}")
                    proc.destroy()
                    return null
                }
            }
            DebugLog.d("ShellAdb", "streamShell: CNXN OK, opening shell:$command")

            // OPEN shell channel
            val localId = 1
            sendMsgTo(procOutput, CMD_OPEN, localId, 0, "shell:$command".toByteArray())

            // Wait for OKAY
            var remoteId = 0
            while (true) {
                val msg = readMsgFrom(procInput) ?: run {
                    DebugLog.d("ShellAdb", "streamShell: OPEN timeout")
                    proc.destroy()
                    return null
                }
                when (msg.cmd) {
                    CMD_OKAY -> {
                        remoteId = msg.arg1
                        DebugLog.d("ShellAdb", "streamShell: OKAY remoteId=$remoteId")
                        break
                    }
                    CMD_CLSE -> {
                        DebugLog.d("ShellAdb", "streamShell: CLSE during OPEN — command failed")
                        proc.destroy()
                        return null
                    }
                    else -> { /* skip early WRTE */ }
                }
            }

            // Bridge ADB protocol → raw InputStream via PipedStream pair
            val pipedOut = PipedOutputStream()
            val pipedIn = PipedInputStream(pipedOut, 128 * 1024)

            val capturedLocalId = localId
            val capturedRemoteId = remoteId
            val readerThread = Thread {
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        val msg = readMsgFrom(procInput) ?: break
                        when (msg.cmd) {
                            CMD_WRTE -> {
                                if (msg.payload.isNotEmpty()) {
                                    pipedOut.write(msg.payload)
                                    pipedOut.flush()
                                }
                                sendMsgTo(procOutput, CMD_OKAY, capturedLocalId, capturedRemoteId, ByteArray(0))
                            }
                            CMD_CLSE -> {
                                sendMsgTo(procOutput, CMD_CLSE, capturedLocalId, capturedRemoteId, ByteArray(0))
                                break
                            }
                            else -> { /* ignore */ }
                        }
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { pipedOut.close() }
                    runCatching { proc.destroy() }
                }
            }
            readerThread.isDaemon = true
            readerThread.name = "ShellAdb-stream-$command"
            readerThread.start()

            val closer = Closeable {
                readerThread.interrupt()
                runCatching { proc.destroy() }
                runCatching { pipedOut.close() }
            }

            return Pair(pipedIn, closer)
        } catch (e: Exception) {
            DebugLog.d("ShellAdb", "streamShell error: ${e.message}")
            return null
        }
    }

    // ===== Protocol =====

    private fun sendMsg(cmd: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val out = output ?: throw IllegalStateException("Not connected")
        val msg = ByteArray(24 + payload.size)
        val buf = ByteBuffer.wrap(msg).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(cmd)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(payload.size)
        buf.putInt(checksum(payload))
        buf.putInt(cmd xor 0xFFFFFFFF.toInt())
        buf.put(payload)
        out.write(msg)
        out.flush()
    }

    private fun readMsg(): AdbMessage {
        val inp = input ?: throw IllegalStateException("Not connected")
        val header = ByteArray(24)
        inp.readFully(header)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val cmd = buf.int
        val arg0 = buf.int
        val arg1 = buf.int
        val length = buf.int
        val checksum = buf.int
        val magic = buf.int

        val payload = if (length > 0) {
            val data = ByteArray(length)
            inp.readFully(data)
            data
        } else ByteArray(0)

        return AdbMessage(cmd, arg0, arg1, payload)
    }

    /** Send ADB message to a specific output stream (for streamShell's separate nc process). */
    private fun sendMsgTo(out: DataOutputStream, cmd: Int, arg0: Int, arg1: Int, payload: ByteArray) {
        val msg = ByteArray(24 + payload.size)
        val buf = ByteBuffer.wrap(msg).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(cmd)
        buf.putInt(arg0)
        buf.putInt(arg1)
        buf.putInt(payload.size)
        buf.putInt(checksum(payload))
        buf.putInt(cmd xor 0xFFFFFFFF.toInt())
        buf.put(payload)
        out.write(msg)
        out.flush()
    }

    /** Read ADB message from a specific input stream (for streamShell's separate nc process). */
    private fun readMsgFrom(inp: DataInputStream): AdbMessage? {
        return try {
            val header = ByteArray(24)
            inp.readFully(header)
            val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = buf.int
            val arg0 = buf.int
            val arg1 = buf.int
            val length = buf.int
            val checksum = buf.int
            val magic = buf.int
            val payload = if (length > 0) {
                val data = ByteArray(length)
                inp.readFully(data)
                data
            } else ByteArray(0)
            AdbMessage(cmd, arg0, arg1, payload)
        } catch (_: Exception) {
            null
        }
    }

    private fun nextId(): Int = localId++

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (b in data) sum += (b.toInt() and 0xFF)
        return sum
    }

    override fun close() {
        connected = false
        try { output?.close() } catch (_: Exception) {}
        try { input?.close() } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        input = null
        output = null
    }

    data class AdbMessage(val cmd: Int, val arg0: Int, val arg1: Int, val payload: ByteArray)
}
