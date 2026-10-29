package dev.spike.core

import kotlinx.serialization.json.Json

const val SPK_NAME = "SPK 1.0"

internal val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

data class ToolCall(val id: String, val name: String, val arguments: String)
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)
data class ToolSpec(val name: String, val description: String, val parametersJson: String)
data class ModelReply(val text: String, val toolCalls: List<ToolCall>)

enum class Kind { USER, AGENT, TOOL, ERROR, INFO }
data class UiLine(val kind: Kind, val text: String)

sealed interface AgentEvent {
    data class Text(val text: String) : AgentEvent
    data class ToolStart(val name: String, val args: String) : AgentEvent
    data class ToolEnd(val name: String, val out: String) : AgentEvent
}
