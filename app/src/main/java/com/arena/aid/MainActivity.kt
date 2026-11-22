package com.arena.aid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arena.aid.data.local.ArenaThemePreference
import com.arena.aid.data.model.ArenaChatSubMode
import com.arena.aid.data.model.ArenaMode
import com.arena.aid.ui.ArenaViewModel
import com.arena.aid.ui.components.ArenaAvatar
import com.arena.aid.ui.components.ArenaRoleBadge
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components.ArenaToastHost
import com.arena.aid.ui.screens.agent.AgentArenaScreen
import com.arena.aid.ui.screens.auth.AuthScreen
import com.arena.aid.ui.screens.chat.ChatAndBattleScreen
import com.arena.aid.ui.screens.code.CodeArenaScreen
import com.arena.aid.ui.screens.leaderboard.HistorySheet
import com.arena.aid.ui.screens.leaderboard.LeaderboardScreen
import com.arena.aid.ui.screens.leaderboard.SettingsAndPerformanceScreen
import com.arena.aid.ui.theme.ArenaTheme
import dagger.hilt.android.AndroidEntryPoint

private enum class ArenaNavTab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Outlined.ChatBubbleOutline),
    CODE("Code", Icons.Outlined.Code),
    AGENT("Agent", Icons.Outlined.SmartToy),
    LEADERBOARD("Ranks", Icons.Outlined.EmojiEvents),
    SETTINGS("Settings", Icons.Outlined.Settings)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ArenaViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themePreference) {
                ArenaThemePreference.SYSTEM -> systemDark
                ArenaThemePreference.LIGHT -> false
                ArenaThemePreference.DARK -> true
            }

            ArenaTheme(
                darkTheme = darkTheme,
                reducedMotion = settings.reducedMotion
            ) {
                ArenaRootApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ArenaRootApp(viewModel: ArenaViewModel) {
    val scheme = MaterialTheme.colorScheme
    val snackbarHostState = remember { SnackbarHostState() }

    val user by viewModel.user.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val ramSnapshot by viewModel.ramSnapshot.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val chatSubMode by viewModel.chatSubMode.collectAsState()
    val modelA by viewModel.selectedModelA.collectAsState()
    val modelB by viewModel.selectedModelB.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val artifacts by viewModel.artifacts.collectAsState()
    val allArtifacts by viewModel.allArtifacts.collectAsState()
    val agentSteps by viewModel.agentSteps.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val leaderboardCat by viewModel.leaderboardCategory.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(ArenaNavTab.CHAT) }
    var showAuthScreen by rememberSaveable { mutableStateOf(false) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val currentSession = remember(sessions, activeSessionId) {
        sessions.firstOrNull { it.id == activeSessionId }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { ArenaToastHost(hostState = snackbarHostState) },
        topBar = {
            ArenaTopHeaderBar(
                user = user?.name,
                ramUsedMb = ramSnapshot.appHeapUsedMb,
                onOpenHistory = { showHistorySheet = true },
                onStartNew = {
                    when (currentTab) {
                        ArenaNavTab.CODE -> viewModel.startNewSession(ArenaMode.CODE)
                        ArenaNavTab.AGENT -> viewModel.startNewSession(ArenaMode.AGENT)
                        else -> {
                            currentTab = ArenaNavTab.CHAT
                            viewModel.startNewSession(ArenaMode.CHAT)
                        }
                    }
                },
                onToggleAuth = { showAuthScreen = !showAuthScreen }
            )
        },
        bottomBar = {
            if (!showAuthScreen) {
                ArenaBottomNavBar(
                    selected = currentTab,
                    onSelect = { tab ->
                        currentTab = tab
                        when (tab) {
                            ArenaNavTab.CHAT -> viewModel.setMode(ArenaMode.CHAT)
                            ArenaNavTab.CODE -> viewModel.setMode(ArenaMode.CODE)
                            ArenaNavTab.AGENT -> viewModel.setMode(ArenaMode.AGENT)
                            else -> Unit
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showAuthScreen) {
                AuthScreen(
                    ramSnapshot = ramSnapshot,
                    onGoogleSignInSuccess = { name, email, token ->
                        viewModel.signInWithGoogleProfile(name, email, token)
                        showAuthScreen = false
                    },
                    onEmailSignInSuccess = { email, name, method ->
                        viewModel.signInWithEmail(email, name, method)
                        showAuthScreen = false
                    },
                    onSaveCookieAndApi = { cookie, url, key ->
                        viewModel.saveApiAndCookieConfig(cookie, url, key)
                        showAuthScreen = false
                    },
                    onContinueGuest = { showAuthScreen = false },
                    onMessage = { viewModel.emitToast(it) }
                )
            } else {
                when (currentTab) {
                    ArenaNavTab.CHAT -> {
                        ChatAndBattleScreen(
                            subMode = chatSubMode,
                            onSelectSubMode = { viewModel.setChatSubMode(it) },
                            modelA = modelA,
                            modelB = modelB,
                            onSelectModelA = { viewModel.selectModelA(it) },
                            onSelectModelB = { viewModel.selectModelB(it) },
                            currentSession = currentSession,
                            messages = messages,
                            isStreaming = isStreaming,
                            lowRamMode = settings.lowRamTurboEnabled,
                            onSendPrompt = { prompt ->
                                viewModel.setMode(ArenaMode.CHAT)
                                viewModel.sendPrompt(prompt)
                            },
                            onVoteBattle = { vote -> viewModel.voteInBattle(vote) },
                            onOpenCodeArtifact = { _, _, _ ->
                                viewModel.setMode(ArenaMode.CODE)
                                currentTab = ArenaNavTab.CODE
                            },
                            onToast = { viewModel.emitToast(it) }
                        )
                    }
                    ArenaNavTab.CODE -> {
                        CodeArenaScreen(
                            artifacts = if (artifacts.isNotEmpty()) artifacts else allArtifacts,
                            activeModel = modelA,
                            lowRamMode = settings.lowRamTurboEnabled,
                            isStreaming = isStreaming,
                            onGenerateCodePrompt = { codeTask ->
                                viewModel.setMode(ArenaMode.CODE)
                                viewModel.sendPrompt(codeTask)
                            },
                            onToast = { viewModel.emitToast(it) }
                        )
                    }
                    ArenaNavTab.AGENT -> {
                        AgentArenaScreen(
                            steps = agentSteps,
                            messages = messages,
                            activeModel = modelA,
                            isStreaming = isStreaming,
                            lowRamMode = settings.lowRamTurboEnabled,
                            onRunAgentTask = { goal ->
                                viewModel.setMode(ArenaMode.AGENT)
                                viewModel.sendPrompt(goal)
                            },
                            onOpenCodeArtifact = { _, _, _ ->
                                viewModel.setMode(ArenaMode.CODE)
                                currentTab = ArenaNavTab.CODE
                            },
                            onToast = { viewModel.emitToast(it) }
                        )
                    }
                    ArenaNavTab.LEADERBOARD -> {
                        LeaderboardScreen(
                            selectedCategory = leaderboardCat,
                            onSelectCategory = { viewModel.setLeaderboardCategory(it) },
                            onStartChatWithModel = { chosen ->
                                viewModel.selectModelA(chosen)
                                viewModel.setChatSubMode(ArenaChatSubMode.DIRECT)
                                viewModel.setMode(ArenaMode.CHAT)
                                currentTab = ArenaNavTab.CHAT
                            },
                            onCompareWithModel = { chosen ->
                                viewModel.selectModelB(chosen)
                                viewModel.setChatSubMode(ArenaChatSubMode.SIDE_BY_SIDE)
                                viewModel.setMode(ArenaMode.CHAT)
                                currentTab = ArenaNavTab.CHAT
                            }
                        )
                    }
                    ArenaNavTab.SETTINGS -> {
                        SettingsAndPerformanceScreen(
                            user = user,
                            settings = settings,
                            ramSnapshot = ramSnapshot,
                            onSelectTheme = { viewModel.setThemePreference(it) },
                            onToggleLowRamTurbo = { viewModel.setLowRamTurbo(it) },
                            onToggleReducedMotion = { viewModel.setReducedMotion(it) },
                            onSaveApiConfig = { c, u, k -> viewModel.saveApiAndCookieConfig(c, u, k) },
                            onTrimMemory = { viewModel.trimMemoryNow() },
                            onClearHistory = { viewModel.clearAllHistory() },
                            onOpenSignIn = { showAuthScreen = true },
                            onSignOut = { viewModel.signOut() }
                        )
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        HistorySheet(
            sessions = sessions,
            activeSessionId = activeSessionId,
            onSelectSession = { session ->
                viewModel.selectSession(session)
                currentTab = when (session.mode) {
                    ArenaMode.CODE.name -> ArenaNavTab.CODE
                    ArenaMode.AGENT.name -> ArenaNavTab.AGENT
                    else -> ArenaNavTab.CHAT
                }
            },
            onTogglePin = { viewModel.togglePin(it) },
            onDeleteSession = { viewModel.deleteSession(it) },
            onStartNewSession = { viewModel.startNewSession(activeMode) },
            onDismiss = { showHistorySheet = false }
        )
    }
}

@Composable
private fun ArenaTopHeaderBar(
    user: String?,
    ramUsedMb: Long,
    onOpenHistory: () -> Unit,
    onStartNew: () -> Unit,
    onToggleAuth: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArenaAvatar(seed = "arena-logo", label = "Arena", size = 38.dp)
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Arena",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W700),
                            color = scheme.onSurface
                        )
                        ArenaRoleBadge(text = "ai")
                    }
                    Text(
                        text = "${ramUsedMb}MB heap • 4GB Turbo",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceContainerLow)
                        .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable(onClick = onOpenHistory),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = "History",
                        tint = scheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(scheme.surfaceContainerLow)
                        .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp))
                        .clickable(onClick = onStartNew),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddComment,
                        contentDescription = "New session",
                        tint = scheme.primary,
                        modifier = Modifier.size(19.dp)
                    )
                }
                ArenaStatusChip(
                    label = user?.take(10) ?: "Sign in",
                    leadingIcon = Icons.Outlined.PersonOutline,
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer,
                    modifier = Modifier.clickable(onClick = onToggleAuth)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(scheme.outlineVariant)
        )
    }
}

@Composable
private fun ArenaBottomNavBar(
    selected: ArenaNavTab,
    onSelect: (ArenaNavTab) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLowest)
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(scheme.outlineVariant)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArenaNavTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onSelect(tab) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(
                                if (isSelected) scheme.primaryContainer else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.W600 else FontWeight.W500
                        ),
                        color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
