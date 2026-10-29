package dev.spike.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject

class Agent(
    private val provider: Provider,
    private val tools: List<Tool>,
    private val system: String,
    private val maxSteps: Int,
    private val contextChars: Int,
) {
    suspend fun run(history: MutableList<ChatMessage>, emit: (AgentEvent) -> Unit) {
        var step = 0
        while (step < maxSteps) {
            step++
            currentCoroutineContext().ensureActive()
            compact(history)
            val reply = provider.complete(listOf(ChatMessage("system", system)) + history, tools.map { it.spec })
            history += ChatMessage("assistant", reply.text, reply.toolCalls)
            if (reply.text.isNotBlank()) emit(AgentEvent.Text(reply.text.trim()))
            if (reply.toolCalls.isEmpty()) return
            for (c in reply.toolCalls) {
                emit(AgentEvent.ToolStart(c.name, c.arguments.take(300)))
                val out = withContext(Dispatchers.IO) {
                    runCatching {
                        val t = tools.firstOrNull { it.spec.name == c.name } ?: error("unknown tool ${c.name}")
                        t.run(JSON.parseToJsonElement(c.arguments.ifBlank { "{}" }).jsonObject)
                    }.getOrElse { if (it is CancellationException) throw it else "error: ${it.message}" }
                }
                emit(AgentEvent.ToolEnd(c.name, out.take(300)))
                history += ChatMessage("tool", out, toolCallId = c.id)
            }
        }
        emit(AgentEvent.Text("(stopped: reached the step limit of $maxSteps)"))
    }

    /** Keeps long sessions alive without a hard cap: old, large tool outputs are trimmed first. */
    private fun compact(h: MutableList<ChatMessage>) {
        var total = h.sumOf { it.content?.length ?: 0 }
        var i = 0
        while (total > contextChars && i < h.size - 12) {
            val m = h[i]; val len = m.content?.length ?: 0
            if (m.role == "tool" && len > 200) { h[i] = m.copy(content = "[trimmed]"); total -= len - 9 }
            i++
        }
    }
}
