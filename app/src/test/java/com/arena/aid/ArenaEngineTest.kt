package com.arena.aid

import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.ui.components.MarkdownSegment
import com.arena.aid.ui.components.parseMarkdownSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArenaEngineTest {

    @Test
    fun catalogHasModels() {
        assertTrue(ArenaCatalog.models.size >= 5)
        assertEquals("Gemini 3.1 Pro", ArenaCatalog.models.first().displayName)
        assertEquals("my/custom", ArenaCatalog.byId("my/custom").slug)
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
