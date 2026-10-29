package dev.spike.core

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** One agent session shared by the Android and Windows UIs. */
class SpikeSession(
    private val home: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val settingsFile = File(home, "settings.json")
    private val skills = Skills(File(home, "skills"))
    private val bus = SpikeBus()
    private val history = mutableListOf<ChatMessage>()
    private val deviceId = UUID.randomUUID().toString().take(8)
    private var job: Job? = null
    private var rea: McpClient? = null
    private var reaKey = ""

    val settings = MutableStateFlow(Settings.load(settingsFile))
    val lines = MutableStateFlow<List<UiLine>>(emptyList())
    val busy = MutableStateFlow(false)
    /** Platforms can plug in a faster estimator (Android uses the NDK one). */
    var estimate: (String) -> Int = { (it.length + 3) / 4 }
    /** False on Android: REA needs Node.js and a process it can spawn, so it runs on the desktop app. */
    var localRea: Boolean = true

    init { connectBus() }

    fun contextTokens(): Int = estimate(history.joinToString("\n") { it.content.orEmpty() })

    /** remote = the prompt arrived over the HiveMQ bus. */
    fun send(text: String, remote: Boolean = false) {
        if (text.isBlank()) return
        if (!remote && settings.value.busRemoteOnly) { sendToRemote(text); return }
        if (busy.value) { if (remote) add(Kind.INFO, "busy: remote prompt dropped"); return }
        job = scope.launch {
            busy.value = true
            try {
                add(Kind.USER, if (remote) "[remote] $text" else text)
                history += ChatMessage("user", text)
                runAgent(remote)
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

    fun close() { stop(); rea?.close(); rea = null }

    fun updateSettings(s: Settings) {
        s.save(settingsFile); settings.value = s; connectBus()
    }

    private fun sendToRemote(text: String) {
        scope.launch {
            if (!bus.isConnected) { add(Kind.ERROR, "bus is not connected: check broker, login and channel in Settings"); return@launch }
            runCatching { bus.publish("cmd", text) }
                .onSuccess { lines.update { it + UiLine(Kind.INFO, "sent to remote agent: ${text.take(80)}") } }
                .onFailure { add(Kind.ERROR, "bus: ${it.message}") }
        }
    }

    private suspend fun runAgent(remote: Boolean) {
        val s = settings.value
        // Prompts from the bus can only use shell, writes and REA when the user opted in AND the broker has a login.
        val trusted = !remote || (s.busTrustRemote && s.busUser.isNotBlank())
        if (!trusted) add(Kind.INFO, "remote prompt: read-only (shell, writes and REA are off for untrusted bus prompts)")
        val root = File(s.workspace.ifBlank { File(home, "workspace").path }).also { it.mkdirs() }
        val sk = skills.load()
        val extra = if (trusted) loadRea(s) else emptyList()
        val system = res("spk/spk-1.0.md") +
            (if (extra.isNotEmpty()) "\n\n" + res("spk/re-mode.md") else "") +
            "\n\nEnvironment: ${System.getProperty("os.name")}, date ${LocalDate.now()}, workspace ${root.path}" +
            "\nSkills (call load_skill to read one):\n" + skills.catalog(sk)
        Agent(OpenAiCompatProvider { settings.value }, defaultTools(root, s, sk, readOnly = !trusted) + extra, system, s.maxSteps, s.contextChars)
            .run(history) { e ->
                when (e) {
                    is AgentEvent.Text -> add(Kind.AGENT, e.text)
                    is AgentEvent.ToolStart -> add(Kind.TOOL, "${e.name} ${e.args}")
                    is AgentEvent.ToolEnd -> add(Kind.TOOL, "-> ${e.out}")
                }
            }
    }

    /** Starts (or reuses) the REA MCP server and returns its meta-tools. Failure leaves the agent usable without them. */
    private suspend fun loadRea(s: Settings): List<Tool> {
        if (!s.reaEnabled || !localRea) { closeRea(); return emptyList() }
        val env = buildMap<String, String> {
            if (s.reaGhidraDir.isNotBlank()) put("GHIDRA_INSTALL_DIR", s.reaGhidraDir)
            if (s.reaJavaHome.isNotBlank()) put("JAVA_HOME", s.reaJavaHome)
        }
        val key = s.reaCommand + env
        if (key != reaKey || rea?.alive == false) closeRea()
        if (rea == null) {
            add(Kind.INFO, "starting REA (the first run downloads it)")
            // Neutral working directory, outside the target project, as REA's docs advise for package runners.
            val c = McpClient(s.reaCommand, env, File(home, "rea"))
            try {
                c.start()
                rea = c; reaKey = key
                add(Kind.INFO, "REA ready: ${c.tools.size} tools")
            } catch (e: CancellationException) {
                c.close(); throw e
            } catch (e: Exception) {
                c.close()
                add(Kind.ERROR, "REA did not start: ${e.message}. It needs Node.js 22.23.4+, 24.22.1+ or 26.12.1+ on PATH.")
                return emptyList()
            }
        }
        return rea?.let { reaMetaTools(it) }.orEmpty()
    }

    private fun closeRea() { rea?.close(); rea = null; reaKey = "" }

    private fun connectBus() {
        scope.launch {
            runCatching { bus.apply(settings.value, onCommand = { send(it, remote = true) }, onEvent = { mirror(it) }) }
                .onFailure { add(Kind.ERROR, "bus: ${it.message}") }
        }
    }

    /** Shows an event published by another device (remote-only mode). Never re-published. */
    private fun mirror(json: String) {
        runCatching {
            val o = JSON.parseToJsonElement(json).jsonObject
            if (o["src"]?.jsonPrimitive?.contentOrNull == deviceId) return
            val kind = Kind.valueOf(o["kind"]!!.jsonPrimitive.content)
            val text = o["text"]!!.jsonPrimitive.content
            lines.update { it + UiLine(kind, text) }
        }
    }

    private fun add(kind: Kind, text: String) {
        lines.update { it + UiLine(kind, text) }
        scope.launch {
            runCatching {
                bus.publish("events", buildJsonObject { put("kind", kind.name); put("text", text); put("src", deviceId) }.toString())
            }
        }
    }

    private fun res(path: String) =
        Agent::class.java.classLoader?.getResourceAsStream(path)?.bufferedReader()?.readText().orEmpty()
}
