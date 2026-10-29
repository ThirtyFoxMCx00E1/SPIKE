package dev.spike.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

interface Provider {
    suspend fun complete(messages: List<ChatMessage>, tools: List<ToolSpec>): ModelReply
}

/** Talks to any OpenAI-compatible /chat/completions endpoint. No timeouts: long local generations are fine. */
class OpenAiCompatProvider(private val settings: () -> Settings) : Provider {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    override suspend fun complete(messages: List<ChatMessage>, tools: List<ToolSpec>): ModelReply =
        withContext(Dispatchers.IO) {
            val s = settings()
            val body = buildJsonObject {
                put("model", s.model)
                put("messages", buildJsonArray { messages.forEach { add(it.toJson()) } })
                if (tools.isNotEmpty()) put("tools", buildJsonArray { tools.forEach { add(it.toJson()) } })
            }
            val req = Request.Builder()
                .url(s.baseUrl.trimEnd('/') + "/chat/completions")
                .apply { if (s.apiKey.isNotBlank()) header("Authorization", "Bearer ${s.apiKey}") }
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) error("model HTTP ${r.code}: ${txt.take(400)}")
                parse(txt)
            }
        }

    private fun parse(txt: String): ModelReply {
        val msg = JSON.parseToJsonElement(txt).jsonObject["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject
        val text = msg["content"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val calls = msg["tool_calls"]?.jsonArray?.map { tc ->
            val f = tc.jsonObject["function"]!!.jsonObject
            val a = f["arguments"]
            ToolCall(
                id = tc.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString().take(8),
                name = f["name"]!!.jsonPrimitive.content,
                arguments = if (a is JsonPrimitive) a.content else a?.toString() ?: "{}",
            )
        } ?: emptyList()
        return ModelReply(text, calls)
    }
}

private fun ChatMessage.toJson() = buildJsonObject {
    put("role", role)
    put("content", content ?: "")
    toolCallId?.let { put("tool_call_id", it) }
    if (toolCalls.isNotEmpty()) put("tool_calls", buildJsonArray {
        toolCalls.forEach { c ->
            add(buildJsonObject {
                put("id", c.id)
                put("type", "function")
                put("function", buildJsonObject { put("name", c.name); put("arguments", c.arguments) })
            })
        }
    })
}

private fun ToolSpec.toJson() = buildJsonObject {
    put("type", "function")
    put("function", buildJsonObject {
        put("name", name)
        put("description", description)
        put("parameters", JSON.parseToJsonElement(parametersJson))
    })
}
