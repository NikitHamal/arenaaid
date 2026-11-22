package com.arena.aid.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val mode: String,          // CHAT, CODE, AGENT
    val subMode: String,       // BATTLE, SIDE_BY_SIDE, DIRECT
    val modelAId: String,
    val modelBId: String?,
    val battleVote: String?,   // null, "A", "B", "TIE", "BOTH_BAD"
    val modelsRevealed: Boolean,
    val pinned: Boolean,
    val updatedAt: Long
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,          // "user" or "assistant"
    val slot: String,          // "USER", "A", "B"
    val modelId: String,
    val modelDisplayName: String,
    val content: String,
    val reasoning: String,
    val tokensPerSec: Int,
    val latencyMs: Long,
    val createdAt: Long
)

@Entity(tableName = "code_artifacts")
data class CodeArtifactEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val filename: String,
    val language: String,
    val content: String,
    val modelName: String,
    val slot: String,          // "A" or "B"
    val updatedAt: Long
)

@Entity(tableName = "agent_steps")
data class AgentStepEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val stepIndex: Int,
    val toolName: String,      // web_search, bash_sandbox, file_write, deep_research, verify_build
    val title: String,
    val inputSummary: String,
    val outputLog: String,
    val status: String,        // Autopilot, Delivered, Solved, Answer
    val durationMs: Long,
    val createdAt: Long
)

@Dao
interface ArenaDao {
    @Query("SELECT * FROM chat_sessions ORDER BY pinned DESC, updatedAt DESC")
    fun observeSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): ChatSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM code_artifacts WHERE sessionId = :sessionId")
    suspend fun deleteArtifactsForSession(sessionId: String)

    @Query("DELETE FROM agent_steps WHERE sessionId = :sessionId")
    suspend fun deleteAgentStepsForSession(sessionId: String)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeMessages(sessionId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    suspend fun getMessages(sessionId: String): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("SELECT * FROM code_artifacts WHERE sessionId = :sessionId ORDER BY updatedAt ASC")
    fun observeArtifacts(sessionId: String): Flow<List<CodeArtifactEntity>>

    @Query("SELECT * FROM code_artifacts ORDER BY updatedAt DESC LIMIT 50")
    fun observeAllArtifacts(): Flow<List<CodeArtifactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtifact(artifact: CodeArtifactEntity)

    @Query("SELECT * FROM agent_steps WHERE sessionId = :sessionId ORDER BY stepIndex ASC")
    fun observeAgentSteps(sessionId: String): Flow<List<AgentStepEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAgentStep(step: AgentStepEntity)

    @Query("DELETE FROM chat_sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM messages")
    suspend fun clearAllMessages()

    @Query("DELETE FROM code_artifacts")
    suspend fun clearAllArtifacts()

    @Query("DELETE FROM agent_steps")
    suspend fun clearAllAgentSteps()
}

@Database(
    entities = [
        ChatSessionEntity::class,
        MessageEntity::class,
        CodeArtifactEntity::class,
        AgentStepEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ArenaDatabase : RoomDatabase() {
    abstract fun dao(): ArenaDao
}
