package com.arena.aid.data.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class ArenaMode(val label: String, val hint: String, val arenaModality: String) {
    CHAT("Chat", "Ask anything", "chat"),
    CODE("Code", "Build and debug", "webdev"),
    AGENT("Agent", "Research and plan", "search")
}

@Serializable
data class ArenaModelInfo(
    val id: String,
    val slug: String,
    val displayName: String,
    val provider: String,
    val contextWindow: String = "",
    val modalities: Set<String> = setOf("chat"),
    val rankChat: Int = Int.MAX_VALUE,
    val rankCode: Int = Int.MAX_VALUE,
    val rankSearch: Int = Int.MAX_VALUE
)

@Serializable
data class ArenaUserProfile(
    val id: String,
    val name: String,
    val email: String,
    val authMethod: String,
    val avatarUrl: String? = null,
    val sessionCookie: String = "",
    val customApiUrl: String = "https://openrouter.ai/api",
    val customApiKey: String = "",
    val isVerified: Boolean = true
)

@Serializable
data class DeviceRamSnapshot(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val appHeapUsedMb: Long,
    val isLowRamDevice: Boolean,
    val cpuCores: Int,
    val deviceModel: String
)

object ArenaCatalog {
    private val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    fun isUuid(value: String): Boolean = uuidRegex.matches(value.trim())

    private fun m(
        id: String,
        slug: String,
        org: String,
        modes: Set<String>,
        rChat: Int = Int.MAX_VALUE,
        rCode: Int = Int.MAX_VALUE,
        rSearch: Int = Int.MAX_VALUE
    ) = ArenaModelInfo(
        id = id,
        slug = slug,
        displayName = slug,
        provider = formatOrg(org),
        modalities = modes,
        rankChat = rChat,
        rankCode = rCode,
        rankSearch = rSearch
    )

    fun formatOrg(org: String): String = when (org.lowercase()) {
        "boss-bandit" -> "Arena Router"
        "google" -> "Google"
        "anthropic" -> "Anthropic"
        "openai" -> "OpenAI"
        "deepseek" -> "DeepSeek"
        "xiaomi" -> "Xiaomi"
        "alibaba" -> "Alibaba"
        "meta" -> "Meta"
        "moonshot" -> "Moonshot"
        "xai" -> "xAI"
        "zai" -> "Z.ai"
        "thinky" -> "Thinky"
        "baidu" -> "Baidu"
        "amazon" -> "Amazon"
        else -> org.replaceFirstChar { it.uppercase() }
    }

    /**
     * Exact user-selectable models from arena.ai/?mode=direct initialModels catalog.
     */
    val builtInModels: List<ArenaModelInfo> = listOf(
        m("019b24bb-5caf-71c3-b854-37d0c7086f21", "Max", "boss-bandit", setOf("chat", "webdev", "search"), 0, 0, 0),
        m("01a141d8-c8f9-710a-bdf4-983605ae330c", "gemini-4-argon-high", "google", setOf("chat", "webdev"), 2, 18),
        m("01a0681c-b561-76b6-b338-a44ff0cff460", "gemini-3.8-flash-high", "google", setOf("chat", "webdev"), 11, 47),
        m("01a0e982-1984-7919-9dec-0fef46824d29", "claude-sonnet-5-5-high", "anthropic", setOf("chat", "webdev"), 200, 13),
        m("01a0dc86-c4b7-7c3e-812f-891aba9574d1", "mimo-v2.6-pro", "xiaomi", setOf("chat", "webdev"), 31, 35),
        m("01a03a3b-64e3-790c-bdba-bd0e1c04b0f5", "anonymous-0825", "zai", setOf("chat", "webdev"), 37, 40),
        m("01a0c5d1-9000-71c6-9c2c-b1b958c71006", "mimo-v2.6-flash", "xiaomi", setOf("chat", "webdev"), 90, 30),
        m("01a0ca93-91d0-7bb4-843e-02dc64ca64f6", "gpt-6-luna-max", "openai", setOf("chat"), 99, Int.MAX_VALUE),
        m("01a0ca83-3771-78e9-bb46-9bd372c8f86c", "gpt-6-luna-max", "openai", setOf("webdev"), Int.MAX_VALUE, 51),
        m("01a01326-36b7-7b0d-8af1-ccba21cf001f", "qwen3.8-27b", "alibaba", setOf("chat"), 112, Int.MAX_VALUE),
        m("01a01327-1a98-7645-8595-06506a9c1f7f", "qwen3.8-27b", "alibaba", setOf("webdev"), Int.MAX_VALUE, 45),
        m("01a117ba-842b-76c6-bffb-9ca7bc4a6e45", "claude-haiku-5.5-high", "anthropic", setOf("chat", "webdev"), 139, 44),
        m("01a14ae7-0d76-74af-80ac-33cf2b2322aa", "deepseek-v4.1-flash-max", "deepseek", setOf("chat", "webdev"), 210, 23),
        m("01a14c4a-9269-761f-843d-f047a26d1d6c", "deepseek-v4.1-flash-high", "deepseek", setOf("chat", "webdev"), 205, 220),
        m("01a1c1ad-c7fd-71bb-ae40-b95422e61018", "muse-ember-max", "meta", setOf("chat", "webdev"), 215, 42),
        m("01a063c1-adb1-73e2-9c02-10b6e7cfb1eb", "muse-spark-1.3-xhigh", "meta", setOf("chat", "webdev"), 240, 37),
        m("019f7cb6-9dc5-70ef-aadb-e1287c3384e3", "kimi-k3", "moonshot", setOf("chat"), 220, Int.MAX_VALUE),
        m("01a17964-d631-70ad-99d5-54f60ed1b6cd", "gpt-6.1-terra-low", "openai", setOf("chat"), 230, Int.MAX_VALUE),
        m("019f722d-f655-7a00-8eb3-24d9be72a104", "inkling-medium", "thinky", setOf("chat", "webdev"), 235, 230),
        m("019ff69d-0234-7d00-a218-8fefcc9b7a9d", "grok-4.6", "xai", setOf("chat"), 245, Int.MAX_VALUE),
        m("019fc887-69e7-7308-a353-36da657fc18e", "paisley", "alibaba", setOf("webdev"), Int.MAX_VALUE, 21),
        m("01a1bc5c-e389-7e59-a728-9939461d4a62", "ernie-5.5-flash-preview", "baidu", setOf("webdev"), Int.MAX_VALUE, 240),
        m("019fb8c5-4796-7f8d-bd9b-0e278b3d9de7", "amazon-web-search-agent", "amazon", setOf("search"), Int.MAX_VALUE, Int.MAX_VALUE, 1),
        m("019f4e39-a888-7430-a6e7-ca2c33ec78f1", "grok-4.5-search", "xai", setOf("search"), Int.MAX_VALUE, Int.MAX_VALUE, 14),
        m("019ce84a-f675-780c-a2bb-f86498a34de5", "gemini-3.1-pro-grounding", "google", setOf("search"), Int.MAX_VALUE, Int.MAX_VALUE, 16),
        m("019f1a07-de72-7fbe-8d82-56dbd7348360", "claude-sonnet-5-search", "anthropic", setOf("search"), Int.MAX_VALUE, Int.MAX_VALUE, 26),
        m("01a0ca94-48d5-7395-ac81-8220f88816dc", "gpt-6-luna-search-max", "openai", setOf("search"), Int.MAX_VALUE, Int.MAX_VALUE, 35)
    )

    private val _liveModels = MutableStateFlow(builtInModels)
    val liveModels: StateFlow<List<ArenaModelInfo>> = _liveModels.asStateFlow()

    val models: List<ArenaModelInfo> get() = _liveModels.value

    fun updateLiveModels(fetched: List<ArenaModelInfo>) {
        if (fetched.isNotEmpty()) {
            _liveModels.value = fetched
        }
    }

    fun modelsForMode(mode: ArenaMode): List<ArenaModelInfo> {
        val current = _liveModels.value
        return when (mode) {
            ArenaMode.CHAT -> current.filter { "chat" in it.modalities }.sortedBy { it.rankChat }
            ArenaMode.CODE -> current.filter { "webdev" in it.modalities }.sortedBy { it.rankCode }
            ArenaMode.AGENT -> {
                val searchModels = current.filter { "search" in it.modalities }.sortedBy { it.rankSearch }
                val chatModels = current.filter { "chat" in it.modalities && "search" !in it.modalities }.sortedBy { it.rankChat }
                searchModels + chatModels
            }
        }
    }

    fun forModeOrFallback(current: ArenaModelInfo, mode: ArenaMode): ArenaModelInfo {
        val list = modelsForMode(mode)
        if (list.any { it.id == current.id }) return current
        val sameName = list.firstOrNull { it.slug.equals(current.slug, ignoreCase = true) }
        return sameName ?: list.firstOrNull { it.slug == "gemini-4-argon-high" } ?: list.first()
    }

    fun byId(id: String): ArenaModelInfo {
        val clean = id.trim()
        val list = _liveModels.value
        list.firstOrNull { it.id == clean || it.slug.equals(clean, ignoreCase = true) }?.let { return it }
        // Migrate legacy OpenRouter-style slugs to real arena.ai UUID models
        return when {
            clean.contains("gemini", ignoreCase = true) && clean.contains("flash", ignoreCase = true) ->
                list.first { it.slug == "gemini-3.8-flash-high" }
            clean.contains("gemini", ignoreCase = true) ->
                list.first { it.slug == "gemini-4-argon-high" }
            clean.contains("claude", ignoreCase = true) && clean.contains("haiku", ignoreCase = true) ->
                list.first { it.slug == "claude-haiku-5.5-high" }
            clean.contains("claude", ignoreCase = true) ->
                list.first { it.slug == "claude-sonnet-5-5-high" }
            clean.contains("gpt", ignoreCase = true) ->
                list.first { it.slug == "gpt-6-luna-max" }
            clean.contains("deepseek", ignoreCase = true) ->
                list.first { it.slug == "deepseek-v4.1-flash-max" }
            clean.contains("kimi", ignoreCase = true) ->
                list.first { it.slug == "kimi-k3" }
            clean.contains("qwen", ignoreCase = true) ->
                list.first { it.slug == "qwen3.8-27b" }
            clean.contains("grok", ignoreCase = true) ->
                list.first { it.slug == "grok-4.6" }
            clean.isNotBlank() && clean.contains("/") ->
                ArenaModelInfo(id = clean, slug = clean, displayName = clean.substringAfterLast('/'), provider = "Custom API")
            else -> list.first { it.slug == "gemini-4-argon-high" }
        }
    }
}
