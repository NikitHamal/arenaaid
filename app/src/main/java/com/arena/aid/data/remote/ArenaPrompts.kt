package com.arena.aid.data.remote

import com.arena.aid.data.model.ArenaMode

data class ChatTurn(val role: String, val content: String)

data class GeneratedArtifact(val filename: String, val language: String, val content: String)

object ArenaPrompts {
    fun systemFor(mode: ArenaMode, userInstructions: String, research: String): String = buildString {
        append(
            when (mode) {
                ArenaMode.CHAT -> "You are a helpful, precise assistant. Answer clearly and keep formatting light."
                ArenaMode.CODE -> "You are a senior software engineer. Reply with complete, working code in fenced blocks. " +
                    "Put the file name after the language, for example ```kotlin Main.kt. Explain only what matters."
                ArenaMode.AGENT -> "You are an autonomous agent. Break the task into numbered steps, work through them, " +
                    "state assumptions, and finish with a short deliverable summary."
            }
        )
        if (userInstructions.isNotBlank()) append("\n").append(userInstructions)
        if (research.isNotBlank()) append("\n\nWeb research results:\n").append(research)
    }

    private val fence = Regex("```([a-zA-Z0-9_+.-]*)(?:[ \\t]+([^\\n`]+))?\\n([\\s\\S]*?)(?:```|$)")

    fun extractArtifacts(markdown: String): List<GeneratedArtifact> {
        var index = 1
        return fence.findAll(markdown).mapNotNull { m ->
            val lang = m.groupValues[1].trim().ifEmpty { "txt" }
            val code = m.groupValues[3].trimEnd()
            if (code.isBlank()) return@mapNotNull null
            val ext = when (lang.lowercase()) {
                "kotlin", "kt" -> "kt"
                "typescript", "ts" -> "ts"
                "javascript", "js" -> "js"
                "python", "py" -> "py"
                "tsx", "sql", "json", "html", "css", "java", "xml" -> lang.lowercase()
                else -> "txt"
            }
            val name = m.groupValues[2].trim().ifEmpty { "snippet_${index}.$ext" }
            index++
            GeneratedArtifact(name, lang, code)
        }.toList()
    }
}
