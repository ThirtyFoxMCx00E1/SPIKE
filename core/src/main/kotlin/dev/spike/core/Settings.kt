package dev.spike.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/** Every limit is a setting, and the defaults are "no limit". */
@Serializable
data class Settings(
    // Any OpenAI-compatible endpoint: Ollama, LM Studio, llama.cpp server, OpenRouter, vLLM...
    val baseUrl: String = "http://localhost:11434/v1",
    val model: String = "qwen2.5-coder:7b",
    val apiKey: String = "",
    val maxSteps: Int = Int.MAX_VALUE,      // agent loop steps per prompt
    val contextChars: Int = 120_000,        // old tool output is trimmed past this
    val allowShell: Boolean = true,
    val shellTimeoutSec: Int = 600,
    val workspace: String = "",             // blank = <app data>/workspace
    // HiveMQ / MQTT bus (off by default so prompts are never published by accident)
    val busEnabled: Boolean = false,
    val busHost: String = "broker.hivemq.com",
    val busPort: Int = 1883,
    val busTls: Boolean = false,
    val busUser: String = "",
    val busPass: String = "",
    val busChannel: String = "",
) {
    fun save(f: File) {
        f.parentFile?.mkdirs()
        f.writeText(JSON.encodeToString(this))
    }

    companion object {
        fun load(f: File): Settings =
            runCatching { JSON.decodeFromString<Settings>(f.readText()) }.getOrDefault(Settings())
    }
}
