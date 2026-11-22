package com.arena.aid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arena.aid.data.local.AgentStepEntity
import com.arena.aid.data.local.ArenaAppSettings
import com.arena.aid.data.local.ArenaPreferences
import com.arena.aid.data.local.ChatSessionEntity
import com.arena.aid.data.local.CodeArtifactEntity
import com.arena.aid.data.local.MessageEntity
import com.arena.aid.data.model.ArenaCatalog
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.data.model.ArenaModelInfo
import com.arena.aid.data.model.ArenaUserProfile
import com.arena.aid.data.repository.ArenaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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

    private val _mode = MutableStateFlow(ArenaMode.CHAT)
    val mode: StateFlow<ArenaMode> = _mode.asStateFlow()

    private val _model = MutableStateFlow(ArenaCatalog.byId(prefs.settingsFlow.value.defaultDirectModelId))
    val model: StateFlow<ArenaModelInfo> = _model.asStateFlow()

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()

    private val _extractingCookie = MutableStateFlow(false)
    val extractingCookie: StateFlow<Boolean> = _extractingCookie.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private var job: Job? = null

    init {
        viewModelScope.launch {
            repository.syncLiveCatalog()
            _model.value = ArenaCatalog.forModeOrFallback(_model.value, _mode.value)
            if (user.value != null && user.value?.sessionCookie.isNullOrBlank()) {
                repository.autoExtractCookie(forceSignUp = false)
            }
        }
    }

    val sessions: StateFlow<List<ChatSessionEntity>> = repository.observeSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<MessageEntity>> = _sessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentSteps: StateFlow<List<AgentStepEntity>> = _sessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeAgentSteps(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val artifacts: StateFlow<List<CodeArtifactEntity>> = _sessionId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.observeArtifacts(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toast(message: String) { _toasts.tryEmit(message) }

    fun setMode(mode: ArenaMode) {
        _mode.value = mode
        _model.value = ArenaCatalog.forModeOrFallback(_model.value, mode)
    }

    fun refreshModels() {
        viewModelScope.launch {
            val fetched = repository.syncLiveCatalog()
            _model.value = ArenaCatalog.forModeOrFallback(_model.value, _mode.value)
            if (fetched.isNotEmpty()) toast("Synced ${fetched.size} arena.ai models")
        }
    }

    fun autoExtractArenaCookie(forceSignUp: Boolean = true, onExtracted: (String) -> Unit = {}) {
        if (_extractingCookie.value) return
        viewModelScope.launch {
            _extractingCookie.value = true
            try {
                val cookie = repository.autoExtractCookie(forceSignUp = forceSignUp)
                if (cookie.isNotBlank()) {
                    onExtracted(cookie)
                    toast("Extracted arena.ai session cookie")
                } else {
                    toast("Could not auto-extract cookie yet. Sign in via the interactive scraper.")
                }
            } finally {
                _extractingCookie.value = false
            }
        }
    }

    fun selectModel(model: ArenaModelInfo) {
        _model.value = model
        prefs.updateSettings { it.copy(defaultDirectModelId = model.id) }
    }

    fun newChat() {
        job?.cancel()
        _sessionId.value = null
    }

    fun openSession(s: ChatSessionEntity) {
        if (_streaming.value) stop()
        runCatching { ArenaMode.valueOf(s.mode) }.onSuccess { _mode.value = it }
        _model.value = ArenaCatalog.byId(s.modelAId)
        _sessionId.value = s.id
    }

    fun send(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank() || _streaming.value) return
        launchStream { id -> repository.send(id, clean, _mode.value, _model.value) }
    }

    fun retry(message: MessageEntity) {
        if (_streaming.value) return
        launchStream { id -> repository.retry(id, message.id, _mode.value, _model.value) }
    }

    private fun launchStream(block: suspend (String) -> Unit) {
        job = viewModelScope.launch {
            _streaming.value = true
            try {
                val id = _sessionId.value
                    ?: repository.createSession(_mode.value, _model.value).id.also { _sessionId.value = it }
                block(id)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "Something went wrong")
            } finally {
                _streaming.value = false
            }
        }
    }

    fun stop() { job?.cancel() }

    fun togglePin(s: ChatSessionEntity) { viewModelScope.launch { repository.togglePin(s) } }

    fun rename(s: ChatSessionEntity, title: String) { viewModelScope.launch { repository.rename(s, title) } }

    fun delete(s: ChatSessionEntity) {
        viewModelScope.launch {
            if (_sessionId.value == s.id) newChat()
            repository.deleteSession(s.id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { newChat(); repository.clearAllHistory(); toast("History cleared") }
    }

    fun signInGoogle(name: String, email: String) = saveProfile(name, email, "Google")

    fun signInEmail(email: String, name: String): Boolean {
        if (!email.contains("@") || email.length < 5) { toast("Enter a valid email"); return false }
        saveProfile(name, email.trim(), "Email")
        return true
    }

    private fun saveProfile(name: String, email: String, method: String) {
        val old = user.value
        val profile = ArenaUserProfile(
            id = old?.id ?: UUID.randomUUID().toString(),
            name = name.trim().ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } },
            email = email, authMethod = method,
            sessionCookie = old?.sessionCookie.orEmpty(),
            customApiUrl = old?.customApiUrl ?: "https://openrouter.ai/api",
            customApiKey = old?.customApiKey.orEmpty()
        )
        prefs.saveUser(profile)
        if (profile.sessionCookie.isBlank()) {
            autoExtractArenaCookie(forceSignUp = false)
        }
    }

    fun saveConnection(cookie: String, apiUrl: String, apiKey: String) {
        val u = user.value ?: return
        if (cookie.contains("arena-auth")) repository.applyCookie(cookie.trim())
        prefs.saveUser(u.copy(sessionCookie = cookie.trim(), customApiUrl = apiUrl.trim(), customApiKey = apiKey.trim()))
        toast("Connection saved")
    }

    fun updateSettings(transform: (ArenaAppSettings) -> ArenaAppSettings) = prefs.updateSettings(transform)

    fun signOut() { newChat(); prefs.signOut() }
}
