package dev.spike.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.concurrent.thread

interface Tool {
    val spec: ToolSpec
    suspend fun run(args: JsonObject): String
}

private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull ?: error("missing argument '$k'")

private fun schema(vararg p: Pair<String, String>) =
    """{"type":"object","properties":{${p.joinToString(",") { "\"${it.first}\":{\"type\":\"string\",\"description\":\"${it.second}\"}" }}},"required":[${p.joinToString(",") { "\"${it.first}\"" }}]}"""

private fun tool(name: String, desc: String, vararg p: Pair<String, String>, block: suspend (JsonObject) -> String) =
    object : Tool {
        override val spec = ToolSpec(name, desc, schema(*p))
        override suspend fun run(args: JsonObject) = block(args)
    }

private class Workspace(val root: File) {
    fun resolve(p: String): File {
        val f = (File(p).takeIf { it.isAbsolute } ?: File(root, p)).canonicalFile
        require(f.path.startsWith(root.canonicalPath)) { "path is outside the workspace" }
        return f
    }
}

private val SKIP = setOf(".git", "node_modules", "build", ".gradle", ".cxx")

fun defaultTools(root: File, s: Settings, skills: List<Skill>): List<Tool> {
    root.mkdirs()
    val ws = Workspace(root)
    val http = OkHttpClient()
    val out = mutableListOf<Tool>()

    out += tool("list_dir", "List a workspace directory ('.' = workspace root)", "path" to "relative path") { a ->
        ws.resolve(a.str("path")).listFiles()?.sortedBy { it.name }
            ?.joinToString("\n") { (if (it.isDirectory) "d " else "f ") + it.name } ?: "not a directory"
    }
    out += tool("read_file", "Read a text file from the workspace", "path" to "relative path") { a ->
        val f = ws.resolve(a.str("path")); require(f.isFile) { "no such file" }
        f.readText().take(60_000)
    }
    out += tool("write_file", "Create or overwrite a file in the workspace", "path" to "relative path", "content" to "full file content") { a ->
        val f = ws.resolve(a.str("path")); f.parentFile?.mkdirs()
        f.writeText(a.str("content")); "wrote ${f.length()} bytes to ${a.str("path")}"
    }
    out += tool("edit_file", "Replace one exact occurrence of old with new in a file", "path" to "relative path", "old" to "exact text to replace", "new" to "replacement") { a ->
        val f = ws.resolve(a.str("path")); val t = f.readText(); val old = a.str("old")
        require(old.isNotEmpty()) { "old text is empty" }
        val n = Regex(Regex.escape(old)).findAll(t).count()
        require(n == 1) { "old text must match exactly once, found $n" }
        f.writeText(t.replaceFirst(old, a.str("new"))); "edited ${a.str("path")}"
    }
    out += tool("search", "Regex search over workspace text files", "pattern" to "regular expression", "path" to "directory, '.' for all") { a ->
        val re = Regex(a.str("pattern")); val hits = mutableListOf<String>()
        ws.resolve(a.str("path")).walkTopDown().onEnter { it.name !in SKIP }
            .filter { it.isFile && it.length() < 1_000_000 }.forEach { f ->
                if (hits.size >= 200) return@forEach
                runCatching { f.readLines().forEachIndexed { i, l -> if (re.containsMatchIn(l) && hits.size < 200) hits += "${f.relativeTo(root)}:${i + 1}: ${l.trim().take(200)}" } }
            }
        hits.joinToString("\n").ifBlank { "no matches" }
    }
    if (s.allowShell) out += tool("run_shell", "Run a shell command in the workspace and return its output", "command" to "command line") { a ->
        val win = System.getProperty("os.name").lowercase().contains("win")
        val cmd = if (win) listOf("cmd", "/c", a.str("command")) else listOf("sh", "-c", a.str("command"))
        val p = ProcessBuilder(cmd).directory(root).redirectErrorStream(true).start()
        thread(isDaemon = true) { if (!p.waitFor(s.shellTimeoutSec.toLong(), java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly() }
        val txt = p.inputStream.bufferedReader().readText(); val code = p.waitFor()
        "exit $code\n" + txt.takeLast(12_000)
    }
    out += tool("http_get", "Fetch a web page or API and return its text (HTML is stripped)", "url" to "https URL") { a ->
        http.newCall(Request.Builder().url(a.str("url")).header("User-Agent", "SPIKE/1.0").build()).execute().use { r ->
            val b = r.body?.string().orEmpty()
            val t = if ((r.header("Content-Type") ?: "").contains("html"))
                b.replace(Regex("(?is)<(script|style).*?</\\1>"), " ").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ") else b
            "HTTP ${r.code}\n" + t.take(20_000)
        }
    }
    out += tool("load_skill", "Load the full text of a skill by name", "name" to "skill name") { a ->
        skills.firstOrNull { it.name == a.str("name") }?.body ?: "unknown skill; available: ${skills.joinToString { it.name }}"
    }
    return out
}
