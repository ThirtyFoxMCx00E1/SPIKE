package dev.spike.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/** Minimal MCP client over stdio (newline-delimited JSON-RPC 2.0). Desktop only: it spawns a process. */
class McpClient(
    private val command: String,
    private val env: Map<String, String>,
    private val workDir: File,
) : AutoCloseable {
    data class RemoteTool(val name: String, val description: String, val schema: JsonElement)

    private var proc: Process? = null
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val nextId = AtomicLong(1)
    private val writeLock = Any()
    private val stderrTail = ArrayDeque<String>()

    var tools: List<RemoteTool> = emptyList()
        private set
    val alive: Boolean get() = proc?.isAlive == true

    suspend fun start(startupTimeoutMs: Long = 180_000) {
        val win = System.getProperty("os.name").lowercase().contains("win")
        val pb = ProcessBuilder(if (win) listOf("cmd", "/c", command) else listOf("sh", "-c", command))
            .directory(workDir.also { it.mkdirs() })
        env.forEach { (k, v) -> if (v.isNotBlank()) pb.environment()[k] = v }
        val p = pb.start().also { proc = it }
        thread(isDaemon = true, name = "mcp-out") { readLoop(p) }
        thread(isDaemon = true, name = "mcp-err") {
            p.errorStream.bufferedReader().forEachLine { l ->
                synchronized(stderrTail) { stderrTail.addLast(l); if (stderrTail.size > 12) stderrTail.removeFirst() }
            }
        }
        try {
            withTimeout(startupTimeoutMs) {
                request("initialize", buildJsonObject {
                    put("protocolVersion", "2025-06-18")
                    put("capabilities", buildJsonObject {})
                    put("clientInfo", buildJsonObject { put("name", "spike"); put("version", "1.0.0") })
                })
                sendNotification("notifications/initialized")
                tools = listAll()
            }
        } catch (e: TimeoutCancellationException) {
            close()
            throw IllegalStateException("MCP server did not answer within ${startupTimeoutMs / 1000} s. ${tail()}")
        }
    }

    private suspend fun listAll(): List<RemoteTool> {
        val all = mutableListOf<RemoteTool>()
        var cursor: String? = null
        do {
            val r = request("tools/list", cursor?.let { buildJsonObject { put("cursor", it) } })
            r["tools"]?.jsonArray?.forEach { t ->
                val o = t.jsonObject
                all += RemoteTool(
                    o["name"]!!.jsonPrimitive.content,
                    o["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    o["inputSchema"] ?: JsonObject(emptyMap()),
                )
            }
            cursor = r["nextCursor"]?.jsonPrimitive?.contentOrNull
        } while (cursor != null)
        return all
    }

    /** Calls one tool and returns its text. Cancelling the coroutine sends notifications/cancelled. */
    suspend fun call(name: String, args: JsonObject): String {
        val r = request("tools/call", buildJsonObject { put("name", name); put("arguments", args) })
        val isErr = r["isError"]?.jsonPrimitive?.booleanOrNull == true
        val text = r["content"]?.jsonArray?.mapNotNull { c ->
            c.jsonObject.takeIf { it["type"]?.jsonPrimitive?.contentOrNull == "text" }?.get("text")?.jsonPrimitive?.contentOrNull
        }?.joinToString("\n").orEmpty()
        val body = text.ifBlank { r["structuredContent"]?.toString().orEmpty() }.ifBlank { "(empty result)" }
        return (if (isErr) "tool error: " else "") + body
    }

    private suspend fun request(method: String, params: JsonObject? = null): JsonObject {
        val id = nextId.getAndIncrement()
        val d = CompletableDeferred<JsonObject>()
        pending[id] = d
        try {
            send(buildJsonObject {
                put("jsonrpc", "2.0"); put("id", id); put("method", method)
                if (params != null) put("params", params)
            })
            return d.await()
        } catch (e: CancellationException) {
            pending.remove(id)
            runCatching { sendNotification("notifications/cancelled", buildJsonObject { put("requestId", id); put("reason", "cancelled") }) }
            throw e
        }
    }

    private fun sendNotification(method: String, params: JsonObject? = null) = send(buildJsonObject {
        put("jsonrpc", "2.0"); put("method", method)
        if (params != null) put("params", params)
    })

    private fun send(o: JsonObject) {
        val p = proc ?: throw IOException("MCP server is not running")
        synchronized(writeLock) {
            p.outputStream.write((o.toString() + "\n").toByteArray(Charsets.UTF_8))
            p.outputStream.flush()
        }
    }

    private fun readLoop(p: Process) {
        p.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            val o = runCatching { JSON.parseToJsonElement(line).jsonObject }.getOrNull() ?: return@forEachLine
            val id = o["id"]?.jsonPrimitive?.longOrNull
            val method = o["method"]?.jsonPrimitive?.contentOrNull
            when {
                method == null && id != null -> {
                    val d = pending.remove(id) ?: return@forEachLine
                    val err = o["error"]
                    if (err != null) d.completeExceptionally(IllegalStateException("MCP error: $err"))
                    else d.complete(o["result"] as? JsonObject ?: JsonObject(emptyMap()))
                }
                method != null && o["id"] != null -> runCatching {   // server -> client request
                    val reply = if (method == "ping") buildJsonObject { put("jsonrpc", "2.0"); put("id", o["id"]!!); put("result", buildJsonObject {}) }
                    else buildJsonObject {
                        put("jsonrpc", "2.0"); put("id", o["id"]!!)
                        put("error", buildJsonObject { put("code", -32601); put("message", "method not supported") })
                    }
                    send(reply)
                }
            }
        }
        val dead = IOException("MCP server exited. ${tail()}")
        pending.values.forEach { it.completeExceptionally(dead) }
        pending.clear()
    }

    private fun tail() = synchronized(stderrTail) { stderrTail.joinToString(" | ") }

    override fun close() {
        pending.values.forEach { it.cancel() }
        val p = proc ?: return
        proc = null
        runCatching { p.toHandle().descendants().forEach { it.destroyForcibly() } }
        p.destroyForcibly()
    }
}
