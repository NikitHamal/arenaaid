package com.arena.aid.data.repository

import com.arena.aid.data.local.AgentStepEntity
import com.arena.aid.data.local.ArenaDao
import com.arena.aid.data.local.ArenaPreferences
import com.arena.aid.data.local.ChatSessionEntity
import com.arena.aid.data.local.CodeArtifactEntity
import com.arena.aid.data.local.MessageEntity
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.data.remote.ArenaNetworkEngine
import com.arena.aid.data.remote.ArenaPrompts
import com.arena.aid.data.remote.ChatTurn
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

const val ERROR_PREFIX = "[[error]] "

@Singleton
class ArenaRepository @Inject constructor(
    private val dao: ArenaDao,
    private val network: ArenaNetworkEngine,
    private val prefs: ArenaPreferences
) {
    fun observeSessions(): Flow<List<ChatSessionEntity>> = dao.observeSessions()
    fun observeMessages(id: String): Flow<List<MessageEntity>> = dao.observeMessages(id)
    fun observeArtifacts(id: String): Flow<List<CodeArtifactEntity>> = dao.observeArtifacts(id)
    fun observeAgentSteps(id: String): Flow<List<AgentStepEntity>> = dao.observeAgentSteps(id)

    suspend fun syncLiveCatalog(): List<ArenaModelInfo> = network.scrapeLiveModels()

    fun applyCookie(cookie: String) = network.applyCookie(cookie)

    suspend fun autoExtractCookie(forceSignUp: Boolean = false): String {
        val cookie = network.autoExtractCookie(forceSignUp).trim()
        if (cookie.isNotBlank()) {
            prefs.userFlow.value?.let { u -> prefs.saveUser(u.copy(sessionCookie = cookie)) }
        }
        return cookie
    }

    suspend fun createSession(mode: ArenaMode, model: ArenaModelInfo, title: String = ""): ChatSessionEntity {
        val session = ChatSessionEntity(
            id = UUID.randomUUID().toString(),
            title = title.take(60).ifBlank { "New ${mode.label.lowercase()}" },
            mode = mode.name,
            subMode = "DIRECT",
            modelAId = model.id,
            modelBId = null,
            battleVote = null,
            modelsRevealed = true,
            pinned = false,
            updatedAt = System.currentTimeMillis()
        )
        dao.upsertSession(session)
        return session
    }

    suspend fun send(sessionId: String, prompt: String, mode: ArenaMode, model: ArenaModelInfo) {
        val session = dao.getSession(sessionId) ?: createSession(mode, model, prompt)
        val now = System.currentTimeMillis()
        val isFirst = dao.getMessages(session.id).isEmpty()
        dao.upsertMessage(
            MessageEntity(UUID.randomUUID().toString(), session.id, "user", "USER", "user", "You", prompt, "", 0, 0L, now)
        )
        dao.upsertSession(
            session.copy(
                title = if (isFirst) prompt.lineSequence().first().take(60) else session.title,
                mode = mode.name, modelAId = model.id, updatedAt = now
            )
        )
        runAssistant(session.id, mode, model)
    }

    /** Re-run the assistant for the latest user turn (used by Retry). */
    suspend fun retry(sessionId: String, failedMessageId: String, mode: ArenaMode, model: ArenaModelInfo) {
        dao.deleteMessage(failedMessageId)
        runAssistant(sessionId, mode, model)
    }

    private suspend fun runAssistant(sessionId: String, mode: ArenaMode, model: ArenaModelInfo) {
        val settings = prefs.settingsFlow.value
        val user = prefs.userFlow.value
        val turns = dao.getMessages(sessionId)
            .filter { it.slot == "USER" || (it.slot == "A" && !it.content.startsWith(ERROR_PREFIX)) }
            .map { ChatTurn(if (it.role == "user") "user" else "assistant", it.content) }
        val lastPrompt = turns.lastOrNull { it.role == "user" }?.content.orEmpty()

        var research = ""
        if (mode == ArenaMode.AGENT) {
            val t0 = System.currentTimeMillis()
            research = network.webSearch(lastPrompt)
            dao.upsertAgentStep(
                AgentStepEntity(
                    UUID.randomUUID().toString(), sessionId, 1, "web_search", "Searched the web",
                    lastPrompt.take(80), research.ifBlank { "No results found." }, "Delivered",
                    System.currentTimeMillis() - t0, System.currentTimeMillis()
                )
            )
        }
        val system = ArenaPrompts.systemFor(mode, settings.systemInstructions, research)
        val id = UUID.randomUUID().toString()
        val start = System.currentTimeMillis()
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var error: String? = null
        suspend fun save() = dao.upsertMessage(
            MessageEntity(
                id, sessionId, "assistant", "A", model.id, model.displayName,
                if (error != null && text.isEmpty()) ERROR_PREFIX + error else text.toString(),
                reasoning.toString(), 0, System.currentTimeMillis() - start, start + 1
            )
        )
        try {
            network.stream(turns, system, model, user, settings.streamFrameCoalesceMs.coerceAtLeast(48L), mode)
                .collect { c ->
                    if (!c.extractedCookie.isNullOrBlank()) {
                        prefs.userFlow.value?.let { u -> prefs.saveUser(u.copy(sessionCookie = c.extractedCookie)) }
                    }
                    text.append(c.text); reasoning.append(c.reasoning)
                    if (c.error != null) error = c.error
                    if (c.text.isNotEmpty() || c.reasoning.isNotEmpty() || c.error != null) save()
                }
        } finally {
            if (text.isNotEmpty() || error != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { save() }
            }
        }
        if (text.isNotEmpty() && mode == ArenaMode.CODE) {
            ArenaPrompts.extractArtifacts(text.toString()).forEachIndexed { i, a ->
                dao.upsertArtifact(
                    CodeArtifactEntity("${id}_$i", sessionId, a.filename, a.language, a.content, model.displayName, "A", System.currentTimeMillis())
                )
            }
        }
    }

    suspend fun togglePin(s: ChatSessionEntity) = dao.upsertSession(s.copy(pinned = !s.pinned))

    suspend fun rename(s: ChatSessionEntity, title: String) =
        dao.upsertSession(s.copy(title = title.trim().take(60).ifBlank { s.title }))

    suspend fun deleteSession(id: String) {
        dao.deleteMessagesForSession(id); dao.deleteArtifactsForSession(id)
        dao.deleteAgentStepsForSession(id); dao.deleteSession(id)
    }

    suspend fun clearAllHistory() {
        dao.clearAllMessages(); dao.clearAllArtifacts(); dao.clearAllAgentSteps(); dao.clearAllSessions()
    }
}
