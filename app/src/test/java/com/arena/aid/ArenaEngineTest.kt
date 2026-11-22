package com.arena.aid

import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.remote.ArenaClientInteraction
import com.arena.aid.data.remote.ArenaNetworkEngine
import com.arena.aid.data.remote.ArenaSessionBridge
import com.arena.aid.ui.components.MarkdownSegment
import com.arena.aid.ui.components.parseMarkdownSegments
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArenaEngineTest {

    @Test
    fun catalogHasRealArenaModelsAndModes() {
        assertTrue(ArenaCatalog.models.size >= 20)
        val chatModels = ArenaCatalog.modelsForMode(ArenaMode.CHAT)
        val codeModels = ArenaCatalog.modelsForMode(ArenaMode.CODE)
        val agentModels = ArenaCatalog.modelsForMode(ArenaMode.AGENT)
        assertEquals("Max", chatModels.first().displayName)
        assertTrue(chatModels.any { it.slug == "gemini-4-argon-high" && ArenaCatalog.isUuid(it.id) })
        assertTrue(codeModels.any { it.slug == "claude-sonnet-5-5-high" && ArenaCatalog.isUuid(it.id) })
        assertTrue(agentModels.any { it.slug == "amazon-web-search-agent" && ArenaCatalog.isUuid(it.id) })
        assertEquals("my/custom", ArenaCatalog.byId("my/custom").slug)
    }

    @Test
    fun clientInteractionAndModelScrapersWork() {
        val uuid = ArenaClientInteraction.uuidV7()
        assertTrue(ArenaCatalog.isUuid(uuid))
        val cx = ArenaClientInteraction.encode(24, uuid)
        assertEquals(20, cx.length)

        val cleanedUa = ArenaSessionBridge.sanitizeWebViewUa(
            "Mozilla/5.0 (Linux; Android 13; itel A60s Build/TP1A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.81 Mobile Safari/537.36"
        )
        assertFalse(cleanedUa.contains("; wv"))
        assertFalse(cleanedUa.contains("Version/4.0"))

        val engine = ArenaNetworkEngine(OkHttpClient(), Json { ignoreUnknownKeys = true })
        val sampleHtml = """<script>self.__next_f.push([1,"{\"initialModels\":[{\"id\":\"01a141d8-c8f9-710a-bdf4-983605ae330c\",\"organization\":\"google\",\"provider\":\"googleVertex\",\"publicName\":\"gemini-4-argon-high\",\"displayName\":\"gemini-4-argon-high\",\"capabilities\":{\"outputCapabilities\":{\"text\":true,\"web\":true}},\"userSelectable\":true,\"rank\":2,\"rankByModality\":{\"chat\":2,\"webdev\":18}}]}"])</script>"""
        val parsed = engine.parseInitialModelsFromHtml(sampleHtml)
        assertEquals(1, parsed.size)
        assertEquals("gemini-4-argon-high", parsed.first().slug)

        val catalogJson = """[{"arena":"text","complete":true,"models":[{"id":"01a141d8-c8f9-710a-bdf4-983605ae330c","organization":"google","provider":"googleVertex","publicName":"gemini-4-argon-high","displayName":"gemini-4-argon-high","userSelectable":true,"rank":2}]}]"""
        val fromCatalog = engine.parseModelCatalogJson(catalogJson)
        assertEquals(1, fromCatalog.size)
        assertEquals(2, fromCatalog.first().rankChat)
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
