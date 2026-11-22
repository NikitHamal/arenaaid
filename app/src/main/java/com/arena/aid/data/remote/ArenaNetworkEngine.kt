package com.arena.aid.data.remote

import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.data.model.ArenaUserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class StreamChunk(
    val text: String = "",
    val reasoning: String = "",
    val error: String? = null,
    val extractedCookie: String? = null
)

@Singleton
class ArenaNetworkEngine(
    private val client: OkHttpClient,
    private val json: Json
) {
    private var bridge: ArenaSessionBridge? = null

    @Inject
    constructor(
        client: OkHttpClient,
        json: Json,
        bridge: ArenaSessionBridge
    ) : this(client, json) {
        this.bridge = bridge
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun isConnected(user: ArenaUserProfile?): Boolean =
        user != null && (user.customApiKey.isNotBlank() || user.sessionCookie.isNotBlank() || bridge?.hasAuthCookie() == true)

    suspend fun autoExtractCookie(forceSignUp: Boolean = false): String =
        bridge?.autoExtractCookie(forceSignUp).orEmpty()

    /**
     * Scrapes live models from arena.ai via the WebView bridge (`/nextjs-api/model-catalog`
     * or `window.__next_f` `initialModels`) with HTTP fallback, updating `ArenaCatalog`.
     */
    suspend fun scrapeLiveModels(): List<ArenaModelInfo> = withContext(Dispatchers.IO) {
        val bridgePayload = runCatching { bridge?.scrapeModelsPayload().orEmpty() }.getOrDefault("")
        if (bridgePayload.isNotBlank()) {
            val fromCatalog = parseModelCatalogJson(bridgePayload)
            if (fromCatalog.isNotEmpty()) {
                ArenaCatalog.updateLiveModels(fromCatalog)
                return@withContext fromCatalog
            }
            val fromNextF = parseInitialModelsFromRscText(bridgePayload)
            if (fromNextF.isNotEmpty()) {
                ArenaCatalog.updateLiveModels(fromNextF)
                return@withContext fromNextF
            }
        }
        runCatching {
            val cookie = bridge?.readCookie().orEmpty()
            val req = Request.Builder()
                .url("https://arena.ai/?mode=direct")
                .header("User-Agent", ArenaSessionBridge.USER_AGENT)
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .build()
            client.newCall(req).execute().use { resp ->
                val html = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || html.isBlank()) return@use emptyList()
                parseInitialModelsFromHtml(html).also { ArenaCatalog.updateLiveModels(it) }
            }
        }.getOrDefault(emptyList())
    }

    internal fun parseModelCatalogJson(raw: String): List<ArenaModelInfo> {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("[")) return emptyList()
        val arr = runCatching { json.parseToJsonElement(trimmed).jsonArray }.getOrNull() ?: return emptyList()
        val arenaMap = mapOf(
            "text" to "chat",
            "vision" to "chat",
            "document" to "chat",
            "code" to "webdev",
            "image-to-code" to "webdev",
            "search" to "search"
        )
        val primaryArena = mapOf("chat" to "text", "webdev" to "code", "search" to "search")
        val merged = linkedMapOf<String, ArenaModelInfo>()
        for (entry in arr) {
            val arenaObj = entry as? JsonObject ?: continue
            val arenaKey = arenaObj["arena"]?.jsonPrimitive?.contentOrNull ?: continue
            val modality = arenaMap[arenaKey] ?: continue
            val isPrimary = primaryArena[modality] == arenaKey
            val modelsArr = arenaObj["models"] as? JsonArray ?: continue
            for (mEl in modelsArr) {
                val o = mEl as? JsonObject ?: continue
                if (o["userSelectable"]?.jsonPrimitive?.booleanOrNull == false) continue
                val id = o["id"]?.jsonPrimitive?.contentOrNull ?: continue
                val pub = o["publicName"]?.jsonPrimitive?.contentOrNull ?: continue
                val org = o["organization"]?.jsonPrimitive?.contentOrNull ?: continue
                val prov = o["provider"]?.jsonPrimitive?.contentOrNull ?: continue
                if (prov == "boss-bandit" && !pub.equals("Max", ignoreCase = true)) continue
                val rank = if (prov == "boss-bandit") 0 else (o["rank"]?.jsonPrimitive?.intOrNull ?: 200)
                val prev = merged[id]
                val modes = (prev?.modalities ?: emptySet()) + modality
                merged[id] = ArenaModelInfo(
                    id = id,
                    slug = pub,
                    displayName = o["displayName"]?.jsonPrimitive?.contentOrNull ?: pub,
                    provider = ArenaCatalog.formatOrg(org),
                    modalities = modes,
                    rankChat = if (modality == "chat" && (isPrimary || prev == null)) rank else (prev?.rankChat ?: Int.MAX_VALUE),
                    rankCode = if (modality == "webdev" && (isPrimary || prev == null)) rank else (prev?.rankCode ?: Int.MAX_VALUE),
                    rankSearch = if (modality == "search" && (isPrimary || prev == null)) rank else (prev?.rankSearch ?: Int.MAX_VALUE)
                )
            }
        }
        return merged.values.toList()
    }

    internal fun parseInitialModelsFromHtml(html: String): List<ArenaModelInfo> {
        val chunkRegex = Regex("""self\.__next_f\.push\((\[1,".*?"\])\)""", setOf(RegexOption.DOT_MATCHES_ALL))
        val full = buildString {
            for (m in chunkRegex.findAll(html)) {
                val arr = runCatching { json.parseToJsonElement(m.groupValues[1]).jsonArray }.getOrNull() ?: continue
                if (arr.size > 1) append(arr[1].jsonPrimitive.contentOrNull.orEmpty())
            }
        }
        return parseInitialModelsFromRscText(full.ifBlank { html })
    }

    internal fun parseInitialModelsFromRscText(full: String): List<ArenaModelInfo> {
        val marker = "\"initialModels\":"
        val start = full.indexOf(marker)
        if (start == -1) return emptyList()
        val arrStart = full.indexOf('[', start + marker.length)
        if (arrStart == -1) return emptyList()
        var depth = 0
        var inStr = false
        var esc = false
        var arrEnd = -1
        for (i in arrStart until full.length) {
            val c = full[i]
            if (esc) { esc = false; continue }
            if (c == '\\' && inStr) { esc = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (!inStr) {
                if (c == '[') depth++
                else if (c == ']') {
                    depth--
                    if (depth == 0) { arrEnd = i; break }
                }
            }
        }
        if (arrEnd == -1) return emptyList()
        val rawArr = runCatching { json.parseToJsonElement(full.substring(arrStart, arrEnd + 1)).jsonArray }.getOrNull()
            ?: return emptyList()
        val out = mutableListOf<ArenaModelInfo>()
        for (el in rawArr) {
            val o = el as? JsonObject ?: continue
            if (o["userSelectable"]?.jsonPrimitive?.booleanOrNull == false) continue
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val pub = o["publicName"]?.jsonPrimitive?.contentOrNull ?: continue
            val org = o["organization"]?.jsonPrimitive?.contentOrNull ?: continue
            val prov = o["provider"]?.jsonPrimitive?.contentOrNull ?: continue
            if (prov == "boss-bandit" && !pub.equals("Max", ignoreCase = true)) continue
            val caps = o["capabilities"]?.jsonObject?.get("outputCapabilities")?.jsonObject ?: continue
            val modes = mutableSetOf<String>()
            if (caps["text"]?.jsonPrimitive?.booleanOrNull == true) modes.add("chat")
            if (caps["web"]?.jsonPrimitive?.booleanOrNull == true) modes.add("webdev")
            if (caps["search"]?.jsonPrimitive?.booleanOrNull == true) modes.add("search")
            if (modes.isEmpty()) continue
            val isRouter = prov == "boss-bandit"
            val fallbackRank = o["rank"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE
            val ranks = o["rankByModality"]?.jsonObject
            fun rFor(k: String): Int {
                if (isRouter) return 0
                val v = ranks?.get(k)?.jsonPrimitive?.intOrNull ?: fallbackRank
                return if (v <= 0 || v > 100_000) fallbackRank.coerceAtLeast(200) else v
            }
            out.add(
                ArenaModelInfo(
                    id = id,
                    slug = pub,
                    displayName = o["displayName"]?.jsonPrimitive?.contentOrNull ?: pub,
                    provider = ArenaCatalog.formatOrg(org),
                    modalities = modes,
                    rankChat = rFor("chat"),
                    rankCode = rFor("webdev"),
                    rankSearch = rFor("search")
                )
            )
        }
        return out
    }

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
        coalesceMs: Long,
        mode: ArenaMode = ArenaMode.CHAT
    ): Flow<StreamChunk> = flow {
        val key = user?.customApiKey.orEmpty().trim()
        val savedCookie = user?.sessionCookie.orEmpty().trim()
        if (key.isNotBlank()) {
            openAiStream(user!!, key, history, system, model, coalesceMs)
            return@flow
        }
        var activeCookie = savedCookie
        val sessionBridge = bridge
        if (activeCookie.isNotBlank()) {
            sessionBridge?.syncCookieToSystem(activeCookie)
        } else if (sessionBridge != null) {
            activeCookie = sessionBridge.autoExtractCookie(forceSignUp = false)
            if (activeCookie.isNotBlank()) {
                emit(StreamChunk(extractedCookie = activeCookie))
            }
        }
        if (activeCookie.isBlank() && sessionBridge == null) {
            emit(StreamChunk(error = "Not connected. Tap Auto-Extract Cookie in Connection settings or add an API key."))
            return@flow
        }
        arenaStream(activeCookie, history, system, model, mode, coalesceMs)
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
        cookie: String,
        history: List<ChatTurn>,
        system: String,
        model: ArenaModelInfo,
        mode: ArenaMode,
        coalesceMs: Long
    ) {
        val resolvedModel = if (ArenaCatalog.isUuid(model.id)) model else ArenaCatalog.byId(model.id)
        val evalId = ArenaClientInteraction.uuidV7()
        val userMsgId = ArenaClientInteraction.uuidV7()
        val modelMsgId = ArenaClientInteraction.uuidV7()
        val prompt = buildString {
            if (system.isNotBlank()) append("[Instructions] ").append(system).append("\n\n")
            if (history.size > 1) {
                history.dropLast(1).forEach {
                    append(if (it.role == "user") "User: " else "Assistant: ").append(it.content).append("\n")
                }
                append("\n")
            }
            append(history.lastOrNull()?.content.orEmpty())
        }
        val modality = when {
            mode == ArenaMode.AGENT && "search" in resolvedModel.modalities -> "search"
            mode == ArenaMode.CODE && "webdev" in resolvedModel.modalities && "chat" !in resolvedModel.modalities -> "webdev"
            else -> "chat"
        }
        val payload = buildJsonObject {
            put("id", evalId)
            put("mode", "direct")
            put("modality", modality)
            put("modelAId", resolvedModel.id)
            put("userMessageId", userMsgId)
            put("modelAMessageId", modelMsgId)
            put("userMessage", buildJsonObject {
                put("content", prompt)
                put("experimental_attachments", JsonArray(emptyList()))
                put("metadata", buildJsonObject {})
            })
            put("cx", ArenaClientInteraction.encode(prompt.length, evalId))
        }.toString()

        val sessionBridge = bridge
        if (sessionBridge != null) {
            val batcher = Batcher(coalesceMs.coerceAtLeast(16L))
            var streamErr: String? = null
            val bridgeErr = sessionBridge.streamViaWebView("/nextjs-api/stream/create-evaluation", payload) { line ->
                runCatching { parseArenaLine(line, batcher) }.onFailure { streamErr = it.message }
                if (batcher.due()) emit(batcher.take())
            }
            if (batcher.pending()) emit(batcher.take())
            val freshCookie = sessionBridge.readCookie()
            if (freshCookie.isNotBlank() && freshCookie != cookie) {
                emit(StreamChunk(extractedCookie = freshCookie))
            }
            if (streamErr != null) {
                emit(StreamChunk(error = streamErr))
                return
            }
            if (bridgeErr == null) return
        }

        val req = Request.Builder().url("https://arena.ai/nextjs-api/stream/create-evaluation")
            .header("Cookie", cookie)
            .header("Origin", "https://arena.ai")
            .header("Referer", "https://arena.ai/?mode=direct")
            .header("User-Agent", ArenaSessionBridge.USER_AGENT)
            .post(payload.toRequestBody(jsonType)).build()
        readLines(req, coalesceMs) { line, b -> parseArenaLine(line, b) }
    }

    private fun parseArenaLine(line: String, b: Batcher) {
        val prefix = line.substringBefore(':', "")
        val value = line.substringAfter(':', "")
        when (prefix) {
            "a0", "0" -> {
                val s = runCatching { json.parseToJsonElement(value).jsonPrimitive.content }.getOrDefault("")
                b.add(s, "")
            }
            "ag", "g" -> {
                val s = runCatching { json.parseToJsonElement(value).jsonPrimitive.content }.getOrDefault("")
                b.add("", s)
            }
            "ah", "h" -> {
                val obj = runCatching { json.parseToJsonElement(value).jsonObject }.getOrNull()
                val url = obj?.get("url")?.jsonPrimitive?.contentOrNull
                val title = obj?.get("title")?.jsonPrimitive?.contentOrNull ?: url
                if (!url.isNullOrBlank()) b.add("\n- [$title]($url)", "")
            }
            "a3", "3" -> {
                val s = runCatching { json.parseToJsonElement(value).jsonPrimitive.content }.getOrDefault("")
                throw java.io.IOException(s.ifBlank { "arena.ai returned an error" })
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
}
