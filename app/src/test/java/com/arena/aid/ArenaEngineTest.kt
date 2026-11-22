package com.arena.aid

import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.data.model.ArenaLeaderboardCategory
import com.arena.aid.ui.components.MarkdownSegment
import com.arena.aid.ui.components.parseMarkdownSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArenaEngineTest {

    @Test
    fun catalogHasModelsAcrossAllCategories() {
        assertTrue(ArenaCatalog.models.size >= 10)
        val top = ArenaCatalog.models.first()
        assertEquals("Gemini 3.1 Pro", top.displayName)
        assertTrue(ArenaCatalog.models.any { it.category == ArenaLeaderboardCategory.CODE })
        assertTrue(ArenaCatalog.models.any { it.category == ArenaLeaderboardCategory.AGENT })
        assertTrue(ArenaCatalog.models.any { it.isOpenWeights })
    }

    @Test
    fun markdownParserExtractsFencedCodeAndParagraphs() {
        val sample = """
            ### Architecture Overview
            Here is the Kotlin snippet:
            ```kotlin Main.kt
            fun main() {
                println("Arena")
            }
            ```
            Done.
        """.trimIndent()
        val segments = parseMarkdownSegments(sample)
        assertEquals(3, segments.size)
        assertTrue(segments[0] is MarkdownSegment.Paragraph)
        val codeBlock = segments[1] as MarkdownSegment.CodeBlock
        assertEquals("kotlin", codeBlock.language)
        assertEquals("Main.kt", codeBlock.filename)
        assertTrue(codeBlock.code.contains("println(\"Arena\")"))
    }
}
