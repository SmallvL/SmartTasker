package com.smarttasker.core.bridge

import com.smarttasker.core.protocol.CommandIds
import com.smarttasker.core.protocol.LxbLinkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * CoreBridge implementation using AutoLXB lxb-core TCP protocol.
 */
class AutoLxbCoreBridge(
    private val host: String = "127.0.0.1",
    private val port: Int = 12345
) : CoreBridge {

    override suspend fun getCoreStatus(): CoreStatusResult = withContext(Dispatchers.IO) {
        try {
            LxbLinkClient(host, port, 3000).use { client ->
                client.handshake(3000)
                CoreStatusResult.Running(port = port, pid = -1)
            }
        } catch (e: Exception) {
            when {
                e.message?.contains("connect") == true ||
                e.message?.contains("Connection refused") == true ->
                    CoreStatusResult.Stopped("lxb-core not listening on $host:$port")
                e.message?.contains("timeout") == true ->
                    CoreStatusResult.Error(CoreErrorCode.CORE_NOT_RUNNING, "Connection timeout")
                else ->
                    CoreStatusResult.Error(CoreErrorCode.PROTOCOL_ERROR, e.message ?: "Unknown error")
            }
        }
    }

    override suspend fun submitQuickTask(taskPayload: String): TaskSubmitResult = withContext(Dispatchers.IO) {
        try {
            LxbLinkClient(host, port).use { client ->
                client.handshake()
                val payload = taskPayload.toByteArray(Charsets.UTF_8)
                val response = client.sendCommand(CommandIds.CMD_CORTEX_FSM_RUN, payload)
                val json = JSONObject(String(response, Charsets.UTF_8))
                val taskId = json.optString("task_id", "unknown")
                val runId = json.optString("run_id", taskId)
                TaskSubmitResult.Accepted(taskId = taskId, runId = runId)
            }
        } catch (e: Exception) {
            TaskSubmitResult.Error(CoreErrorCode.TASK_SUBMIT_FAILED, e.message ?: "Submit failed")
        }
    }

    override suspend fun getTaskStatus(taskId: String): TaskStatusResult = withContext(Dispatchers.IO) {
        try {
            LxbLinkClient(host, port).use { client ->
                client.handshake()
                val payload = """{"task_id":"$taskId"}""".toByteArray()
                val response = client.sendCommand(CommandIds.CMD_CORTEX_TASK_STATUS, payload)
                val json = JSONObject(String(response, Charsets.UTF_8))
                TaskStatusResult.Status(
                    taskId = taskId,
                    state = json.optString("state", "unknown"),
                    phase = json.optString("phase", ""),
                    detail = json.optString("detail", "")
                )
            }
        } catch (e: Exception) {
            TaskStatusResult.Error(CoreErrorCode.UNKNOWN_ERROR, e.message ?: "Status check failed")
        }
    }

    override suspend fun cancelTask(taskId: String): CancelResult = withContext(Dispatchers.IO) {
        try {
            LxbLinkClient(host, port).use { client ->
                client.handshake()
                val payload = """{"task_id":"$taskId"}""".toByteArray()
                client.sendCommand(CommandIds.CMD_CORTEX_FSM_CANCEL, payload)
                CancelResult.Success
            }
        } catch (e: Exception) {
            CancelResult.Error(CoreErrorCode.UNKNOWN_ERROR, e.message ?: "Cancel failed")
        }
    }

    override suspend fun getLatestRoute(taskId: String): RouteResult = withContext(Dispatchers.IO) {
        try {
            LxbLinkClient(host, port).use { client ->
                client.handshake()
                val payload = """{"task_id":"$taskId"}""".toByteArray()
                val response = client.sendCommand(CommandIds.CMD_CORTEX_TASK_MAP, payload)
                val json = String(response, Charsets.UTF_8)
                if (json.isBlank() || json == "{}" || json == "null") {
                    RouteResult.NotFound
                } else {
                    RouteResult.Found(json)
                }
            }
        } catch (e: Exception) {
            RouteResult.Error(CoreErrorCode.ROUTE_NOT_FOUND, e.message ?: "Route fetch failed")
        }
    }

    override suspend fun getLatestTrace(taskId: String): TraceResult = withContext(Dispatchers.IO) {
        try {
            LxbLinkClient(host, port).use { client ->
                client.handshake()
                val payload = """{"task_id":"$taskId"}""".toByteArray()
                val response = client.sendCommand(CommandIds.CMD_CORTEX_TRACE_PULL, payload)
                val lines = String(response, Charsets.UTF_8).lines().filter { it.isNotBlank() }
                if (lines.isEmpty()) TraceResult.NotFound else TraceResult.Found(lines)
            }
        } catch (e: Exception) {
            TraceResult.Error(CoreErrorCode.TRACE_NOT_FOUND, e.message ?: "Trace fetch failed")
        }
    }

    override suspend fun runRoute(taskId: String, routeJson: String): RouteRunResult = withContext(Dispatchers.IO) {
        try {
            LxbLinkClient(host, port, 60000).use { client ->
                client.handshake()
                val payload = """{"task_id":"$taskId","route":$routeJson}""".toByteArray()
                val start = System.currentTimeMillis()
                val response = client.sendCommand(CommandIds.CMD_CORTEX_ROUTE_RUN, payload)
                val duration = System.currentTimeMillis() - start
                val json = JSONObject(String(response, Charsets.UTF_8))
                val success = json.optBoolean("success", false)
                if (success) {
                    RouteRunResult.Success(durationMs = duration)
                } else {
                    RouteRunResult.Failed(
                        stepIndex = json.optInt("failed_step", -1),
                        reason = json.optString("reason", "Route execution failed")
                    )
                }
            }
        } catch (e: Exception) {
            RouteRunResult.Error(CoreErrorCode.UNKNOWN_ERROR, e.message ?: "Route run failed")
        }
    }

    override suspend fun screenshot(): ScreenshotResult = withContext(Dispatchers.IO) {
        try {
            LxbLinkClient(host, port).use { client ->
                client.handshake()
                val response = client.sendCommand(CommandIds.CMD_SCREENSHOT, ByteArray(0))
                ScreenshotResult.Success(response)
            }
        } catch (e: Exception) {
            ScreenshotResult.Error(CoreErrorCode.UNKNOWN_ERROR, e.message ?: "Screenshot failed")
        }
    }

    override suspend fun dumpHierarchy(): HierarchyResult = withContext(Dispatchers.IO) {
        try {
            LxbLinkClient(host, port).use { client ->
                client.handshake()
                val response = client.sendCommand(CommandIds.CMD_DUMP_HIERARCHY, ByteArray(0))
                HierarchyResult.Success(String(response, Charsets.UTF_8))
            }
        } catch (e: Exception) {
            HierarchyResult.Error(CoreErrorCode.UNKNOWN_ERROR, e.message ?: "Dump failed")
        }
    }
}
