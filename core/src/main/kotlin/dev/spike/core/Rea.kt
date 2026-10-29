package dev.spike.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * REA (Reverse Engineer Anything) exposes a large MCP tool catalog. Small local models cannot carry
 * hundreds of tool schemas, so SPK 1.0 sees three meta-tools: search, describe, call.
 */
fun reaMetaTools(c: McpClient): List<Tool> = listOf(
    tool("rea_tools", "Search REA reverse-engineering tools by keywords (blank lists all). Returns name and summary.", "query" to "keywords, or blank") { a ->
        val q = a["query"]?.jsonPrimitive?.contentOrNull.orEmpty().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val hits = c.tools.filter { t -> q.all { w -> (t.name + " " + t.description).lowercase().contains(w) } }
        "${hits.size} of ${c.tools.size} tools" + (if (hits.size > 60) " (first 60)" else "") + "\n" +
            hits.take(60).joinToString("\n") { "${it.name}: ${it.description.replace('\n', ' ').take(110)}" }
    },
    tool("rea_describe", "Show an REA tool's description and input schema", "name" to "tool name") { a ->
        val t = c.tools.firstOrNull { it.name == a.str("name") } ?: error("unknown REA tool; use rea_tools to search")
        "${t.name}\n${t.description}\nschema: ${t.schema}".take(8_000)
    },
    tool("rea_call", "Call an REA tool. arguments is a JSON object as a string.", "name" to "tool name", "arguments" to "JSON object string, e.g. {}") { a ->
        val name = a.str("name")
        require(c.tools.any { it.name == name }) { "unknown REA tool '$name'; use rea_tools to search" }
        val args = JSON.parseToJsonElement(a["arguments"]?.jsonPrimitive?.contentOrNull?.ifBlank { "{}" } ?: "{}").jsonObject
        val out = c.call(name, args)
        if (out.length > 24_000) out.take(24_000) + "\n[truncated: ask a narrower question or use a tool with limits/paging]" else out
    },
)
