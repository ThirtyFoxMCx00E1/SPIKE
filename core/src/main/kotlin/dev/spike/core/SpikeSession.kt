package dev.spike.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate

/** One agent session shared by the Android and Windows UIs. */
class SpikeSession(
    private val home: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val settingsFile = File(home, "settings.json")
    private val skills = Skills(File(home, "skills"))
    private val bus = SpikeBus()
    private val history = mutableListOf<ChatMessage>()
    private var job: Job? = null

    val settings = MutableStateFlow(Settings.load(settingsFile))
    val lines = MutableStateFlow<List<UiLine>>(emptyList())
    val busy = MutableStateFlow(false)
    /** Platforms can plug in a faster estimator (Android uses the NDK one). */
    var estimate: (String) -> Int = { (it.length + 3) / 4 }

    init { connectBus() }

    fun contextTokens(): Int = estimate(history.joinToString("\n") { it.content.orEmpty() })

    fun send(text: String) {
        if (busy.value || text.isBlank()) return
        job = scope.launch {
            busy.value = true
            try {
                add(Kind.USER, text)
                history += ChatMessage("user", text)
                runAgent()
            } catch (e: CancellationException) {
                add(Kind.INFO, "stopped"); throw e
            } catch (e: Exception) {
                add(Kind.ERROR, e.message ?: e.toString())
            } finally {
                busy.value = false
            }
        }
    }

    fun stop() { job?.cancel() }

    fun clear() { stop(); history.clear(); lines.value = emptyList() }

    fun updateSettings(s: Settings) {
        s.save(settingsFile); settings.value = s; connectBus()
    }

    private suspend fun runAgent() {
        val s = settings.value
        val root = File(s.workspace.ifBlank { File(home, "workspace").path }).also { it.mkdirs() }
        val sk = skills.load()
        val base = Agent::class.java.classLoader?.getResourceAsStream("spk/spk-1.0.md")?.bufferedReader()?.readText().orEmpty()
        val system = base + "\n\nEnvironment: ${System.getProperty("os.name")}, date ${LocalDate.now()}, workspace ${root.path}" +
            "\nSkills (call load_skill to read one):\n" + skills.catalog(sk)
        Agent(OpenAiCompatProvider { settings.value }, defaultTools(root, s, sk), system, s.maxSteps, s.contextChars)
            .run(history) { e ->
                when (e) {
                    is AgentEvent.Text -> add(Kind.AGENT, e.text)
                    is AgentEvent.ToolStart -> add(Kind.TOOL, "${e.name} ${e.args}")
                    is AgentEvent.ToolEnd -> add(Kind.TOOL, "-> ${e.out}")
                }
            }
    }

    private fun connectBus() {
        scope.launch {
            runCatching { bus.apply(settings.value) { send(it) } }
                .onFailure { add(Kind.ERROR, "bus: ${it.message}") }
        }
    }

    private fun add(kind: Kind, text: String) {
        lines.update { it + UiLine(kind, text) }
        scope.launch {
            runCatching { bus.publish("events", buildJsonObject { put("kind", kind.name); put("text", text) }.toString()) }
        }
    }
}
