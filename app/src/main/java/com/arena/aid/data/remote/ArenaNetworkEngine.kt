package com.arena.aid.data.remote

import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.data.model.ArenaUserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class StreamChunk(
    val text: String = "",
    val reasoning: String = "",
    val error: String? = null
)

@Singleton
class ArenaNetworkEngine @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** True when at least one real backend is configured. */
    fun isConnected(user: ArenaUserProfile?): Boolean =
        user != null && (user.customApiKey.isNotBlank() || user.sessionCookie.isNotBlank())

    suspend fun webSearch(query: String): String = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim().take(80), "UTF-8")
        val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$q&format=json&srlimit=4"
        runCatching {
            val req = Request.Builder().url(url).header("User-Agent", "ArenaAndroid/1.0").build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) return@use ""
                val items = json.parseToJsonElement(body).jsonObject["query"]
                    ?.jsonObject?.get("search")?.jsonArray ?: JsonArray(emptyList())
                items.mapIndexed { i, el ->
                    val o = el.jsonObject
                    val title = o["title"]?.jsonPrimitive?.content.orEmpty()
                    val snippet = o["snippet"]?.jsonPrimitive?.content.orEmpty()
                        .replace(Regex("<[^>]*>"), "").replace("&quot;", "\"").replace("&amp;", "&")
                    "${i + 1}. $title: $snippet"
                }.joinToString("\n")
            }
        }.getOrDefault("")
    }

    fun stream(
        history: List<ChatTurn>,
        system: String,
        model: ArenaModelInfo,
        user: ArenaUserProfile?,
        coalesceMs: Long
    ): Flow<StreamChunk> = flow {
        val key = user?.customApiKey.orEmpty().trim()
        val cookie = user?.sessionCookie.orEmpty().trim()
        when {
            key.isNotBlank() -> openAiStream(user!!, key, history, system, model, coalesceMs)
            cookie.isNotBlank() -> arenaStream(cookie, history, system, model, coalesceMs)
            else -> emit(StreamChunk(error = "Not connected. Open Settings and connect arena.ai or add an API key."))
        }
    }.flowOn(Dispatchers.IO)

    private class Batcher(val ms: Long) {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var last = 0L
        fun add(t: String, r: String) { text.append(t); reasoning.append(r) }
        fun due() = System.currentTimeMillis() - last >= ms && (text.isNotEmpty() || reasoning.isNotEmpty())
        fun take(): StreamChunk {
            val c = StreamChunk(text.toString(), reasoning.toString())
            text.clear(); reasoning.clear(); last = System.currentTimeMillis()
            return c
        }
        fun pending() = text.isNotEmpty() || reasoning.isNotEmpty()
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamChunk>.openAiStream(
        user: ArenaUserProfile, key: String, history: List<ChatTurn>, system: String,
        model: ArenaModelInfo, coalesceMs: Long
    ) {
        val base = user.customApiUrl.trim().trimEnd('/').ifBlank { "https://openrouter.ai/api" }
        val endpoint = if (base.endsWith("/chat/completions")) base
        else if (base.endsWith("/v1")) "$base/chat/completions" else "$base/v1/chat/completions"
        val body = buildJsonObject {
            put("model", model.slug)
            put("stream", true)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                history.forEach { add(buildJsonObject { put("role", it.role); put("content", it.content) }) }
            })
        }.toString()
        val req = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $key").header("Accept", "text/event-stream")
            .post(body.toRequestBody(jsonType)).build()
        readLines(req, coalesceMs) { line, b ->
            if (!line.startsWith("data:")) return@readLines
            val data = line.removePrefix("data:").trim()
            if (data == "[DONE]" || data.isEmpty()) return@readLines
            val delta = runCatching {
                json.parseToJsonElement(data).jsonObject["choices"]?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("delta")?.jsonObject
            }.getOrNull() ?: return@readLines
            val t = delta["content"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull.orEmpty()
            val r = (delta["reasoning_content"] ?: delta["reasoning"])
                ?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull.orEmpty()
            b.add(t, r)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamChunk>.arenaStream(
        cookie: String, history: List<ChatTurn>, system: String,
        model: ArenaModelInfo, coalesceMs: Long
    ) {
        val evalId = uuidV7()
        val userMsgId = uuidV7()
        val modelMsgId = uuidV7()
        val prompt = buildString {
            if (system.isNotBlank()) append("[Instructions] ").append(system).append("\n\n")
            if (history.size > 1) {
                history.dropLast(1).forEach { append(if (it.role == "user") "User: " else "Assistant: ").append(it.content).append("\n") }
                append("\n")
            }
            append(history.lastOrNull()?.content.orEmpty())
        }
        fun msg(id: String, role: String, content: String, parents: List<String>, pos: String, modelId: String?, status: String) =
            buildJsonObject {
                put("id", id); put("role", role); put("content", content)
                put("experimental_attachments", JsonArray(emptyList()))
                put("parentMessageIds", JsonArray(parents.map { JsonPrimitive(it) }))
                put("participantPosition", pos)
                put("modelId", modelId?.let { JsonPrimitive(it) } ?: JsonNull)
                put("evaluationSessionId", evalId)
                put("status", status)
                put("failureReason", JsonNull)
            }
        val payload = buildJsonObject {
            put("id", evalId); put("mode", "direct"); put("modelAId", model.id)
            put("userMessageId", userMsgId); put("modelAMessageId", modelMsgId)
            put("modality", "chat")
            put("messages", JsonArray(listOf(
                msg(userMsgId, "user", prompt, emptyList(), "a", null, "success"),
                msg(modelMsgId, "assistant", "", listOf(userMsgId), "a", model.id, "pending")
            )))
        }.toString()
        val req = Request.Builder().url("https://arena.ai/nextjs-api/stream/create-evaluation")
            .header("Cookie", cookie).header("Origin", "https://arena.ai")
            .header("Referer", "https://arena.ai/?mode=direct")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
            .post(payload.toRequestBody(jsonType)).build()
        readLines(req, coalesceMs) { line, b ->
            val prefix = line.substringBefore(':', "")
            val value = line.substringAfter(':', "")
            val s = runCatching { json.parseToJsonElement(value).jsonPrimitive.content }.getOrDefault("")
            when (prefix) {
                "a0" -> b.add(s, "")
                "ag" -> b.add("", s)
                "a3" -> throw java.io.IOException(s.ifBlank { "arena.ai returned an error" })
            }
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<StreamChunk>.readLines(
        req: Request, coalesceMs: Long, onLine: (String, Batcher) -> Unit
    ) {
        val call = client.newCall(req)
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val hint = when (resp.code) {
                        401, 403 -> "Access was refused (${resp.code}). Reconnect in Settings."
                        404 -> "Endpoint or model not found (404). Check the model and API URL."
                        429 -> "Rate limited (429). Try again shortly."
                        else -> "Request failed (${resp.code})."
                    }
                    emit(StreamChunk(error = hint))
                    return
                }
                val source = resp.body?.source() ?: return
                val batcher = Batcher(coalesceMs.coerceAtLeast(16L))
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val line = source.readUtf8Line() ?: break
                    if (line.isNotBlank()) onLine(line, batcher)
                    if (batcher.due()) emit(batcher.take())
                }
                if (batcher.pending()) emit(batcher.take())
            }
        } catch (e: java.io.IOException) {
            if (currentCoroutineContext()[Job]?.isActive == true) {
                emit(StreamChunk(error = e.message ?: "Connection lost"))
            }
        }
    }

    private fun uuidV7(): String {
        val r = SecureRandom()
        val b = ByteArray(16).also { r.nextBytes(it) }
        val ts = System.currentTimeMillis()
        for (i in 0..5) b[i] = (ts shr (8 * (5 - i))).toByte()
        b[6] = ((b[6].toInt() and 0x0F) or 0x70).toByte()
        b[8] = ((b[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = b.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
