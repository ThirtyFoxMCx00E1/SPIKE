package dev.spike.core

import java.io.File

data class Skill(val name: String, val description: String, val body: String)

/**
 * Skills are SKILL.md files with `name` / `description` front matter.
 * Built-ins ship in resources/skills (listed in index.txt); add your own in <data>/skills/<name>/SKILL.md.
 */
class Skills(private val userDir: File) {
    fun load(): List<Skill> {
        val built = Skills::class.java.classLoader
            ?.getResourceAsStream("skills/index.txt")?.bufferedReader()?.readLines().orEmpty()
            .filter { it.isNotBlank() }
            .mapNotNull { n ->
                Skills::class.java.classLoader?.getResourceAsStream("skills/$n/SKILL.md")
                    ?.bufferedReader()?.readText()?.let { parse(it) }
            }
        val user = userDir.listFiles()?.mapNotNull { File(it, "SKILL.md").takeIf { f -> f.exists() }?.readText()?.let { parse(it) } }.orEmpty()
        return (built + user).associateBy { it.name }.values.toList()
    }

    fun catalog(skills: List<Skill>) = skills.joinToString("\n") { "- ${it.name}: ${it.description}" }

    private fun parse(text: String): Skill? {
        val m = Regex("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", RegexOption.DOT_MATCHES_ALL).find(text.trim()) ?: return null
        val meta = m.groupValues[1].lines().mapNotNull { l -> l.split(":", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }.toMap()
        return Skill(meta["name"] ?: return null, meta["description"].orEmpty(), m.groupValues[2].trim())
    }
}
