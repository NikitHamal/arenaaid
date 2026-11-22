package com.arena.aid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arena.aid.data.local.AgentStepEntity
import com.arena.aid.data.local.ArenaAppSettings
import com.arena.aid.data.local.ArenaPreferences
import com.arena.aid.data.local.ArenaThemePreference
import com.arena.aid.data.local.ChatSessionEntity
import com.arena.aid.data.local.CodeArtifactEntity
import com.arena.aid.data.local.MessageEntity
import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.data.model.ArenaChatSubMode
import com.arena.aid.data.model.ArenaLeaderboardCategory
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.data.model.ArenaUserProfile
import com.arena.aid.data.model.DeviceRamSnapshot
import com.arena.aid.data.repository.ArenaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArenaViewModel @Inject constructor(
    private val repository: ArenaRepository,
    private val prefs: ArenaPreferences
) : ViewModel() {

    val user: StateFlow<ArenaUserProfile?> = prefs.userFlow
    val settings: StateFlow<ArenaAppSettings> = prefs.settingsFlow

    private val _ramSnapshot = MutableStateFlow(prefs.inspectDeviceRam())
    val ramSnapshot: StateFlow<DeviceRamSnapshot> = _ramSnapshot.asStateFlow()

    private val _activeMode = MutableStateFlow(ArenaMode.CHAT)
    val activeMode: StateFlow<ArenaMode> = _activeMode.asStateFlow()

    private val _chatSubMode = MutableStateFlow(ArenaChatSubMode.DIRECT)
    val chatSubMode: StateFlow<ArenaChatSubMode> = _chatSubMode.asStateFlow()

    private val _selectedModelA = MutableStateFlow(ArenaCatalog.models.first())
    val selectedModelA: StateFlow<ArenaModelInfo> = _selectedModelA.asStateFlow()

    private val _selectedModelB = MutableStateFlow(ArenaCatalog.models[1])
    val selectedModelB: StateFlow<ArenaModelInfo> = _selectedModelB.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _leaderboardCategory = MutableStateFlow(ArenaLeaderboardCategory.OVERALL)
    val leaderboardCategory: StateFlow<ArenaLeaderboardCategory> = _leaderboardCategory.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    val sessions: StateFlow<List<ChatSessionEntity>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<MessageEntity>> = _activeSessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artifacts: StateFlow<List<CodeArtifactEntity>> = _activeSessionId
        .flatMapLatest { id ->
            if (id == null) repository.observeAllArtifacts() else repository.observeArtifacts(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allArtifacts: StateFlow<List<CodeArtifactEntity>> = repository.observeAllArtifacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentSteps: StateFlow<List<AgentStepEntity>> = _activeSessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeAgentSteps(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.observeSessions().collect { list ->
                if (_activeSessionId.value == null && list.isNotEmpty()) {
                    _activeSessionId.value = list.first().id
                }
            }
        }
    }

    fun refreshRamTelemetry() {
        _ramSnapshot.value = prefs.inspectDeviceRam()
    }

    fun emitToast(message: String) {
        _toastEvents.tryEmit(message)
    }

    fun setMode(mode: ArenaMode) {
        _activeMode.value = mode
    }

    fun setChatSubMode(subMode: ArenaChatSubMode) {
        _chatSubMode.value = subMode
        if (subMode == ArenaChatSubMode.BATTLE) {
            val pool = ArenaCatalog.models.shuffled()
            _selectedModelA.value = pool[0]
            _selectedModelB.value = pool[1]
        }
    }

    fun selectModelA(model: ArenaModelInfo) {
        _selectedModelA.value = model
    }

    fun selectModelB(model: ArenaModelInfo) {
        _selectedModelB.value = model
    }

    fun setLeaderboardCategory(category: ArenaLeaderboardCategory) {
        _leaderboardCategory.value = category
    }

    fun selectSession(session: ChatSessionEntity) {
        _activeSessionId.value = session.id
        runCatching { _activeMode.value = ArenaMode.valueOf(session.mode) }
        runCatching { _chatSubMode.value = ArenaChatSubMode.valueOf(session.subMode) }
        _selectedModelA.value = ArenaCatalog.byId(session.modelAId)
        session.modelBId?.let { _selectedModelB.value = ArenaCatalog.byId(it) }
    }

    fun startNewSession(mode: ArenaMode = _activeMode.value) {
        viewModelScope.launch {
            _activeMode.value = mode
            if (_chatSubMode.value == ArenaChatSubMode.BATTLE) {
                val pool = ArenaCatalog.models.shuffled()
                _selectedModelA.value = pool[0]
                _selectedModelB.value = pool[1]
            }
            val session = repository.createSession(
                title = "New ${mode.label} session",
                mode = mode,
                subMode = _chatSubMode.value,
                modelAId = _selectedModelA.value.id,
                modelBId = _selectedModelB.value.id
            )
            _activeSessionId.value = session.id
        }
    }

    fun sendPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _isStreaming.value) return
        viewModelScope.launch {
            _isStreaming.value = true
            try {
                val targetId = _activeSessionId.value ?: run {
                    val created = repository.createSession(
                        title = clean.take(48),
                        mode = _activeMode.value,
                        subMode = _chatSubMode.value,
                        modelAId = _selectedModelA.value.id,
                        modelBId = _selectedModelB.value.id
                    )
                    _activeSessionId.value = created.id
                    created.id
                }
                repository.sendPromptAndStream(
                    sessionId = targetId,
                    prompt = clean,
                    mode = _activeMode.value,
                    subMode = _chatSubMode.value,
                    modelA = _selectedModelA.value,
                    modelB = _selectedModelB.value
                )
                refreshRamTelemetry()
            } catch (e: Exception) {
                emitToast("Stream error: ${e.message ?: "check connection"}")
            } finally {
                _isStreaming.value = false
            }
        }
    }

    fun voteInBattle(vote: String) {
        val id = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.submitBattleVote(id, vote)
            emitToast("Vote recorded and models revealed")
        }
    }

    fun togglePin(session: ChatSessionEntity) {
        viewModelScope.launch {
            repository.togglePinSession(session)
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = sessions.value.firstOrNull { it.id != sessionId }?.id
            }
            emitToast("Deleted session")
        }
    }

    fun signInWithGoogleProfile(name: String, email: String, idToken: String? = null) {
        val profile = ArenaUserProfile(
            id = "google_${UUID.randomUUID().toString().take(8)}",
            name = name.ifBlank { email.substringBefore("@") },
            email = email,
            authMethod = "Google",
            sessionCookie = user.value?.sessionCookie.orEmpty(),
            customApiUrl = user.value?.customApiUrl ?: "https://arena.ai",
            customApiKey = user.value?.customApiKey.orEmpty(),
            isVerified = true
        )
        prefs.saveUser(profile)
        emitToast("Signed in with Google as ${profile.name}")
    }

    fun signInWithEmail(email: String, name: String, authMethod: String = "Email") {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            emitToast("Enter a valid email address")
            return
        }
        val resolvedName = name.trim().ifBlank {
            cleanEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
        }
        val profile = ArenaUserProfile(
            id = "email_${cleanEmail.hashCode().toUInt()}",
            name = resolvedName,
            email = cleanEmail,
            authMethod = authMethod,
            sessionCookie = user.value?.sessionCookie.orEmpty(),
            customApiUrl = user.value?.customApiUrl ?: "https://arena.ai",
            customApiKey = user.value?.customApiKey.orEmpty(),
            isVerified = true
        )
        prefs.saveUser(profile)
        emitToast("Signed in as $resolvedName")
    }

    fun saveApiAndCookieConfig(cookie: String, apiUrl: String, apiKey: String) {
        val current = user.value ?: ArenaUserProfile(
            id = "arena_local_user",
            name = "Arena Developer",
            email = "developer@arena.ai",
            authMethod = "Session Cookie"
        )
        prefs.saveUser(
            current.copy(
                sessionCookie = cookie.trim(),
                customApiUrl = apiUrl.trim().ifBlank { "https://arena.ai" },
                customApiKey = apiKey.trim()
            )
        )
        emitToast("Saved Arena credentials")
    }

    fun signOut() {
        prefs.signOut()
        emitToast("Signed out")
    }

    fun setThemePreference(pref: ArenaThemePreference) {
        prefs.updateSettings { it.copy(themePreference = pref) }
    }

    fun setLowRamTurbo(enabled: Boolean) {
        prefs.updateSettings {
            it.copy(
                lowRamTurboEnabled = enabled,
                streamFrameCoalesceMs = if (enabled) 28L else 14L
            )
        }
        refreshRamTelemetry()
        emitToast(if (enabled) "Saved Low-RAM Turbo optimization" else "Updated stream mode")
    }

    fun setReducedMotion(enabled: Boolean) {
        prefs.updateSettings { it.copy(reducedMotion = enabled) }
    }

    fun setSystemInstructions(instructions: String) {
        prefs.updateSettings { it.copy(systemInstructions = instructions) }
        emitToast("Saved system instructions")
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _activeSessionId.value = null
            emitToast("Deleted all local sessions")
        }
    }

    fun trimMemoryNow() {
        System.gc()
        refreshRamTelemetry()
        emitToast("Trimmed memory caches")
    }
}
