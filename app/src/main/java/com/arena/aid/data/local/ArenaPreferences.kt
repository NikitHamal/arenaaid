package com.arena.aid.data.local

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.arena.aid.data.model.ArenaUserProfile
import com.arena.aid.data.model.DeviceRamSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ArenaThemePreference(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

data class ArenaAppSettings(
    val themePreference: ArenaThemePreference = ArenaThemePreference.SYSTEM,
    val lowRamTurboEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val streamFrameCoalesceMs: Long = 24L,
    val agentAutopilotDefault: Boolean = true,
    val defaultDirectModelId: String = "01a141d8-c8f9-710a-bdf4-983605ae330c",
    val systemInstructions: String = "Be concise, accurate, and production-ready."
)

@Singleton
class ArenaPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        runCatching {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                "arena_secure_prefs",
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.getSharedPreferences("arena_fallback_prefs", Context.MODE_PRIVATE)
        }
    }

    private val _userFlow = MutableStateFlow(loadUser())
    val userFlow: StateFlow<ArenaUserProfile?> = _userFlow.asStateFlow()

    private val _settingsFlow = MutableStateFlow(loadSettings())
    val settingsFlow: StateFlow<ArenaAppSettings> = _settingsFlow.asStateFlow()

    fun inspectDeviceRam(): DeviceRamSnapshot {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalMb = (memInfo.totalMem / (1024L * 1024L)).coerceAtLeast(1024L)
        val availMb = (memInfo.availMem / (1024L * 1024L)).coerceAtLeast(128L)
        val runtime = Runtime.getRuntime()
        val usedHeapMb = ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)).coerceAtLeast(4L)
        val isLowRam = (am?.isLowRamDevice == true) || totalMb <= 4200L
        return DeviceRamSnapshot(
            totalRamMb = totalMb,
            availableRamMb = availMb,
            appHeapUsedMb = usedHeapMb,
            isLowRamDevice = isLowRam,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        )
    }

    private fun loadUser(): ArenaUserProfile? {
        val email = prefs.getString("user_email", null) ?: return null
        return ArenaUserProfile(
            id = prefs.getString("user_id", "usr_arena_1") ?: "usr_arena_1",
            name = prefs.getString("user_name", "Arena Builder") ?: "Arena Builder",
            email = email,
            authMethod = prefs.getString("user_auth_method", "Google") ?: "Google",
            avatarUrl = prefs.getString("user_avatar", null),
            sessionCookie = prefs.getString("arena_cookie", "") ?: "",
            customApiUrl = prefs.getString("custom_api_url", "https://arena.ai") ?: "https://arena.ai",
            customApiKey = prefs.getString("custom_api_key", "") ?: "",
            isVerified = true
        )
    }

    private fun loadSettings(): ArenaAppSettings {
        val ram = inspectDeviceRam()
        val themeRaw = prefs.getString("theme_pref", ArenaThemePreference.SYSTEM.name)
        val theme = runCatching { ArenaThemePreference.valueOf(themeRaw!!) }
            .getOrDefault(ArenaThemePreference.SYSTEM)
        return ArenaAppSettings(
            themePreference = theme,
            lowRamTurboEnabled = prefs.getBoolean("low_ram_turbo", ram.isLowRamDevice),
            reducedMotion = prefs.getBoolean("reduced_motion", false),
            streamFrameCoalesceMs = prefs.getLong("coalesce_ms", if (ram.isLowRamDevice) 28L else 16L),
            agentAutopilotDefault = prefs.getBoolean("agent_autopilot", true),
            defaultDirectModelId = prefs.getString(
                "default_model_id",
                "01a141d8-c8f9-710a-bdf4-983605ae330c"
            ) ?: "01a141d8-c8f9-710a-bdf4-983605ae330c",
            systemInstructions = prefs.getString(
                "system_instructions",
                "Be concise, accurate, and production-ready."
            ) ?: "Be concise, accurate, and production-ready."
        )
    }

    fun saveUser(profile: ArenaUserProfile) {
        prefs.edit()
            .putString("user_id", profile.id)
            .putString("user_name", profile.name)
            .putString("user_email", profile.email)
            .putString("user_auth_method", profile.authMethod)
            .putString("user_avatar", profile.avatarUrl)
            .putString("arena_cookie", profile.sessionCookie)
            .putString("custom_api_url", profile.customApiUrl)
            .putString("custom_api_key", profile.customApiKey)
            .apply()
        _userFlow.value = profile
    }

    fun signOut() {
        prefs.edit()
            .remove("user_id")
            .remove("user_name")
            .remove("user_email")
            .remove("user_auth_method")
            .remove("user_avatar")
            .remove("arena_cookie")
            .remove("custom_api_key")
            .apply()
        _userFlow.value = null
    }

    fun updateSettings(transform: (ArenaAppSettings) -> ArenaAppSettings) {
        val next = transform(_settingsFlow.value)
        prefs.edit()
            .putString("theme_pref", next.themePreference.name)
            .putBoolean("low_ram_turbo", next.lowRamTurboEnabled)
            .putBoolean("reduced_motion", next.reducedMotion)
            .putLong("coalesce_ms", next.streamFrameCoalesceMs)
            .putBoolean("agent_autopilot", next.agentAutopilotDefault)
            .putString("default_model_id", next.defaultDirectModelId)
            .putString("system_instructions", next.systemInstructions)
            .apply()
        _settingsFlow.value = next
    }
}
