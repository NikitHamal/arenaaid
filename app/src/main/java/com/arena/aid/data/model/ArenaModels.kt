package com.arena.aid.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ArenaMode(val label: String) {
    CHAT("Chat"),
    CODE("Code"),
    AGENT("Agent")
}

@Serializable
enum class ArenaChatSubMode(val label: String) {
    BATTLE("Battle"),
    SIDE_BY_SIDE("Side by side"),
    DIRECT("Direct")
}

@Serializable
enum class ArenaLeaderboardCategory(val label: String, val slug: String) {
    OVERALL("Text arena", "text"),
    CODE("Code arena", "code"),
    AGENT("Agent arena", "agent"),
    HARD_PROMPTS("Hard prompts", "hard"),
    MATH("Math and logic", "math"),
    VISION("Vision", "vision")
}

@Serializable
data class ArenaModelInfo(
    val id: String,
    val slug: String,
    val displayName: String,
    val provider: String,
    val category: ArenaLeaderboardCategory,
    val elo: Int,
    val ci: Int,
    val votes: Int,
    val contextWindow: String,
    val isOpenWeights: Boolean,
    val supportsCode: Boolean = true,
    val supportsAgent: Boolean = true,
    val speedTokensPerSec: Int = 68,
    val specialtyTag: String = "Frontier"
)

@Serializable
data class ArenaUserProfile(
    val id: String,
    val name: String,
    val email: String,
    val authMethod: String,
    val avatarUrl: String? = null,
    val sessionCookie: String = "",
    val customApiUrl: String = "https://arena.ai",
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
    val models: List<ArenaModelInfo> = listOf(
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-gemini31pro",
            slug = "gemini-3.1-pro",
            displayName = "Gemini 3.1 Pro",
            provider = "Google",
            category = ArenaLeaderboardCategory.OVERALL,
            elo = 1398,
            ci = 4,
            votes = 64210,
            contextWindow = "2M",
            isOpenWeights = false,
            speedTokensPerSec = 92,
            specialtyTag = "Multimodal and reasoning"
        ),
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-claudeopus5",
            slug = "claude-opus-4.7-max",
            displayName = "Claude Opus 4.7",
            provider = "Anthropic",
            category = ArenaLeaderboardCategory.AGENT,
            elo = 1395,
            ci = 5,
            votes = 51890,
            contextWindow = "500K",
            isOpenWeights = false,
            speedTokensPerSec = 64,
            specialtyTag = "Agent and architecture"
        ),
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-gpt56sol",
            slug = "gpt-5.6-sol-xhigh",
            displayName = "GPT-5.6 Sol",
            provider = "OpenAI",
            category = ArenaLeaderboardCategory.CODE,
            elo = 1393,
            ci = 4,
            votes = 58400,
            contextWindow = "400K",
            isOpenWeights = false,
            speedTokensPerSec = 78,
            specialtyTag = "Fullstack code"
        ),
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-claudesonnet46",
            slug = "claude-sonnet-4.6",
            displayName = "Claude Sonnet 4.6",
            provider = "Anthropic",
            category = ArenaLeaderboardCategory.CODE,
            elo = 1379,
            ci = 4,
            votes = 72300,
            contextWindow = "400K",
            isOpenWeights = false,
            speedTokensPerSec = 95,
            specialtyTag = "UI and Kotlin"
        ),
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-deepseekr2",
            slug = "deepseek-r2-0528",
            displayName = "DeepSeek R2",
            provider = "DeepSeek",
            category = ArenaLeaderboardCategory.MATH,
            elo = 1374,
            ci = 5,
            votes = 49120,
            contextWindow = "128K",
            isOpenWeights = true,
            speedTokensPerSec = 84,
            specialtyTag = "Open weights reasoning"
        ),
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-kimik3",
            slug = "kimi-k3-instruct",
            displayName = "Kimi K3",
            provider = "Moonshot AI",
            category = ArenaLeaderboardCategory.CODE,
            elo = 1371,
            ci = 5,
            votes = 38900,
            contextWindow = "256K",
            isOpenWeights = true,
            speedTokensPerSec = 88,
            specialtyTag = "Frontend and agent"
        ),
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-qwen35max",
            slug = "qwen-3.5-max-coder",
            displayName = "Qwen 3.5 Max",
            provider = "Alibaba Cloud",
            category = ArenaLeaderboardCategory.HARD_PROMPTS,
            elo = 1366,
            ci = 4,
            votes = 44700,
            contextWindow = "256K",
            isOpenWeights = true,
            speedTokensPerSec = 96,
            specialtyTag = "Systems and SQL"
        ),
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-grok4",
            slug = "grok-4-reasoning",
            displayName = "Grok 4",
            provider = "xAI",
            category = ArenaLeaderboardCategory.OVERALL,
            elo = 1363,
            ci = 5,
            votes = 41200,
            contextWindow = "256K",
            isOpenWeights = false,
            speedTokensPerSec = 91,
            specialtyTag = "Live search"
        ),
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-llama4maverick",
            slug = "llama-4-maverick-400b",
            displayName = "Llama 4 Maverick",
            provider = "Meta",
            category = ArenaLeaderboardCategory.VISION,
            elo = 1351,
            ci = 4,
            votes = 53900,
            contextWindow = "1M",
            isOpenWeights = true,
            speedTokensPerSec = 110,
            specialtyTag = "Fast open weights"
        ),
        ArenaModelInfo(
            id = "019a98f7-afcd-779f-8dcb-gemini3flash",
            slug = "gemini-3.1-flash-lite",
            displayName = "Gemini 3.1 Flash",
            provider = "Google",
            category = ArenaLeaderboardCategory.OVERALL,
            elo = 1346,
            ci = 4,
            votes = 68500,
            contextWindow = "1M",
            isOpenWeights = false,
            speedTokensPerSec = 145,
            specialtyTag = "Ultra fast mobile"
        )
    )

    fun byId(id: String): ArenaModelInfo =
        models.firstOrNull { it.id == id || it.slug == id } ?: models.first()
}
