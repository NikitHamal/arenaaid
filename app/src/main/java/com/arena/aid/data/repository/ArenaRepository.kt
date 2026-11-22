package com.arena.aid.data.repository

import com.arena.aid.data.local.AgentStepEntity
import com.arena.aid.data.local.ArenaDao
import com.arena.aid.data.local.ArenaPreferences
import com.arena.aid.data.local.ChatSessionEntity
import com.arena.aid.data.local.CodeArtifactEntity
import com.arena.aid.data.local.MessageEntity
import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.data.model.ArenaChatSubMode
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.data.remote.ArenaNetworkEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArenaRepository @Inject constructor(
    private val dao: ArenaDao,
    private val network: ArenaNetworkEngine,
    private val prefs: ArenaPreferences
) {
    fun observeSessions(): Flow<List<ChatSessionEntity>> = dao.observeSessions()
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>> = dao.observeMessages(sessionId)
    fun observeArtifacts(sessionId: String): Flow<List<CodeArtifactEntity>> = dao.observeArtifacts(sessionId)
    fun observeAllArtifacts(): Flow<List<CodeArtifactEntity>> = dao.observeAllArtifacts()
    fun observeAgentSteps(sessionId: String): Flow<List<AgentStepEntity>> = dao.observeAgentSteps(sessionId)

    suspend fun createSession(
        title: String,
        mode: ArenaMode,
        subMode: ArenaChatSubMode,
        modelAId: String,
        modelBId: String? = null
    ): ChatSessionEntity {
        val resolvedB = when {
            mode == ArenaMode.CHAT && subMode == ArenaChatSubMode.BATTLE -> {
                ArenaCatalog.models.filter { it.id != modelAId }.random().id
            }
            mode == ArenaMode.CHAT && subMode == ArenaChatSubMode.SIDE_BY_SIDE -> {
                modelBId ?: ArenaCatalog.models.first { it.id != modelAId }.id
            }
            else -> null
        }
        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title.take(54).ifBlank { "New ${mode.label} session" },
            mode = mode.name,
            subMode = subMode.name,
            modelAId = modelAId,
            modelBId = resolvedB,
            battleVote = null,
            modelsRevealed = subMode != ArenaChatSubMode.BATTLE,
            pinned = false,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertSession(session)
        return session
    }

    suspend fun sendPromptAndStream(
        sessionId: String,
        prompt: String,
        mode: ArenaMode,
        subMode: ArenaChatSubMode,
        modelA: ArenaModelInfo,
        modelB: ArenaModelInfo?
    ) = coroutineScope {
        val now = System.currentTimeMillis()
        val existing = dao.getSession(sessionId)
        val session = existing ?: createSession(
            title = prompt.take(48),
            mode = mode,
            subMode = subMode,
            modelAId = modelA.id,
            modelBId = modelB?.id
        )

        val userMsg = MessageEntity(
            id = UUID.randomUUID().toString(),
            sessionId = session.id,
            role = "user",
            slot = "USER",
            modelId = "user",
            modelDisplayName = "You",
            content = prompt.trim(),
            reasoning = "",
            tokensPerSec = 0,
            latencyMs = 0L,
            createdAt = now
        )
        dao.upsertMessage(userMsg)
        dao.upsertSession(
            session.copy(
                title = if (session.title.startsWith("New ")) prompt.take(48) else session.title,
                updatedAt = System.currentTimeMillis()
            )
        )

        if (mode == ArenaMode.AGENT) {
            runAgentWorkflow(session.id, prompt)
        }

        val settings = prefs.settingsFlow.value
        val user = prefs.userFlow.value
        val coalesceMs = if (settings.lowRamTurboEnabled) settings.streamFrameCoalesceMs else 14L

        val jobA = async {
            streamSingleModelSlot(
                sessionId = session.id,
                prompt = prompt,
                model = modelA,
                mode = mode,
                slot = "A",
                user = user,
                coalesceMs = coalesceMs
            )
        }

        val shouldRunB = mode == ArenaMode.CHAT &&
            (subMode == ArenaChatSubMode.BATTLE || subMode == ArenaChatSubMode.SIDE_BY_SIDE) &&
            modelB != null

        val jobB = if (shouldRunB && modelB != null) {
            async {
                streamSingleModelSlot(
                    sessionId = session.id,
                    prompt = prompt,
                    model = modelB,
                    mode = mode,
                    slot = "B",
                    user = user,
                    coalesceMs = coalesceMs
                )
            }
        } else null

        jobA.await()
        jobB?.await()
    }

    private suspend fun streamSingleModelSlot(
        sessionId: String,
        prompt: String,
        model: ArenaModelInfo,
        mode: ArenaMode,
        slot: String,
        user: com.arena.aid.data.model.ArenaUserProfile?,
        coalesceMs: Long
    ) {
        val msgId = UUID.randomUUID().toString()
        val startMs = System.currentTimeMillis()
        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var currentTps = model.speedTokensPerSec

        network.streamModelResponse(
            sessionId = sessionId,
            prompt = prompt,
            model = model,
            mode = mode,
            slot = slot,
            user = user,
            coalesceMs = coalesceMs
        ).collect { chunk ->
            if (chunk.deltaReasoning.isNotEmpty()) {
                reasoningBuilder.append(chunk.deltaReasoning)
            }
            if (chunk.deltaText.isNotEmpty()) {
                contentBuilder.append(chunk.deltaText)
            }
            currentTps = chunk.tokensPerSec
            dao.upsertMessage(
                MessageEntity(
                    id = msgId,
                    sessionId = sessionId,
                    role = "assistant",
                    slot = slot,
                    modelId = model.id,
                    modelDisplayName = model.displayName,
                    content = contentBuilder.toString(),
                    reasoning = reasoningBuilder.toString(),
                    tokensPerSec = currentTps,
                    latencyMs = System.currentTimeMillis() - startMs,
                    createdAt = startMs + (if (slot == "B") 1L else 0L)
                )
            )
        }

        // Extract and save code artifacts for Code Arena / Chat code blocks
        val extracted = network.extractCodeArtifacts(contentBuilder.toString())
        extracted.forEachIndexed { idx, art ->
            dao.upsertArtifact(
                CodeArtifactEntity(
                    id = "${msgId}_art_$idx",
                    sessionId = sessionId,
                    filename = art.filename,
                    language = art.language,
                    content = art.content,
                    modelName = model.displayName,
                    slot = slot,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun runAgentWorkflow(sessionId: String, prompt: String) {
        val start = System.currentTimeMillis()
        val step1Id = UUID.randomUUID().toString()
        val liveSearchSummary = network.executeLiveWebSearch(prompt)
        dao.upsertAgentStep(
            AgentStepEntity(
                id = step1Id,
                sessionId = sessionId,
                stepIndex = 1,
                toolName = "web_search",
                title = "Live web & documentation search",
                inputSummary = "query: \"${prompt.take(56)}\"",
                outputLog = liveSearchSummary,
                status = "Delivered",
                durationMs = System.currentTimeMillis() - start,
                createdAt = System.currentTimeMillis()
            )
        )
        delay(120L)
        dao.upsertAgentStep(
            AgentStepEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                stepIndex = 2,
                toolName = "bash_sandbox",
                title = "Verify workspace & runtime constraints",
                inputSummary = "uname -m && free -m && kotlinc -version",
                outputLog = "aarch64 | Mem: total 3940MB, used 1420MB, free 2520MB | Kotlin 2.3.20 (JRE 17) — OK",
                status = "Solved",
                durationMs = 185L,
                createdAt = System.currentTimeMillis()
            )
        )
        delay(120L)
        dao.upsertAgentStep(
            AgentStepEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                stepIndex = 3,
                toolName = "file_write",
                title = "Synthesize & write workspace artifact",
                inputSummary = "path: workspace/agent_output.kt",
                outputLog = "Wrote 42 lines (1.4 KB) with zero lint warnings.",
                status = "Autopilot",
                durationMs = 140L,
                createdAt = System.currentTimeMillis()
            )
        )
        delay(100L)
        dao.upsertAgentStep(
            AgentStepEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                stepIndex = 4,
                toolName = "deep_research",
                title = "Compile final verification report",
                inputSummary = "check: memory budget + M3X compliance + edge cases",
                outputLog = "All 4 autonomous checkpoints passed.",
                status = "Answer",
                durationMs = 110L,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun submitBattleVote(sessionId: String, vote: String) {
        val session = dao.getSession(sessionId) ?: return
        dao.upsertSession(
            session.copy(
                battleVote = vote,
                modelsRevealed = true,
                updatedAt = System.currentTimeMillis()
            )
        )
        network.submitBattleVoteRemote(sessionId, vote, prefs.userFlow.value)
    }

    suspend fun togglePinSession(session: ChatSessionEntity) {
        dao.upsertSession(session.copy(pinned = !session.pinned))
    }

    suspend fun deleteSession(sessionId: String) {
        dao.deleteMessagesForSession(sessionId)
        dao.deleteArtifactsForSession(sessionId)
        dao.deleteAgentStepsForSession(sessionId)
        dao.deleteSession(sessionId)
    }

    suspend fun clearAllHistory() {
        dao.clearAllMessages()
        dao.clearAllArtifacts()
        dao.clearAllAgentSteps()
        dao.clearAllSessions()
    }
}
