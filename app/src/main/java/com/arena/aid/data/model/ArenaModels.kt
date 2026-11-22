package com.arena.aid.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ArenaMode(val label: String, val hint: String) {
    CHAT("Chat", "Ask anything"),
    CODE("Code", "Build and debug"),
    AGENT("Agent", "Research and plan")
}

@Serializable
data class ArenaModelInfo(
    val id: String,
    val slug: String,
    val displayName: String,
    val provider: String,
    val contextWindow: String = ""
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
    private fun m(slug: String, name: String, provider: String, ctx: String) =
        ArenaModelInfo(id = slug, slug = slug, displayName = name, provider = provider, contextWindow = ctx)

    val models: List<ArenaModelInfo> = listOf(
        m("google/gemini-3.1-pro", "Gemini 3.1 Pro", "Google", "1M"),
        m("anthropic/claude-sonnet-4.6", "Claude Sonnet 4.6", "Anthropic", "200K"),
        m("anthropic/claude-opus-4.7", "Claude Opus 4.7", "Anthropic", "200K"),
        m("openai/gpt-5.6", "GPT-5.6", "OpenAI", "400K"),
        m("deepseek/deepseek-r2", "DeepSeek R2", "DeepSeek", "128K"),
        m("moonshotai/kimi-k3", "Kimi K3", "Moonshot AI", "256K"),
        m("qwen/qwen-3.5-max", "Qwen 3.5 Max", "Alibaba", "256K"),
        m("x-ai/grok-4", "Grok 4", "xAI", "256K"),
        m("google/gemini-3.1-flash", "Gemini 3.1 Flash", "Google", "1M")
    )

    fun byId(id: String): ArenaModelInfo =
        models.firstOrNull { it.id == id || it.slug == id }
            ?: if (id.isNotBlank() && !id.startsWith("019a98f7")) {
                ArenaModelInfo(id = id, slug = id, displayName = id.substringAfterLast('/'), provider = "Custom")
            } else models.first()
}
