package com.arena.aid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.arena.aid.data.local.ArenaThemePreference
import com.arena.aid.data.local.ChatSessionEntity
import com.arena.aid.ui.ArenaViewModel
import com.arena.aid.ui.components.ArenaDialog
import com.arena.aid.ui.components.ArenaTextField
import com.arena.aid.ui.components.ArenaToastHost
import com.arena.aid.ui.screens.auth.AuthScreen
import com.arena.aid.ui.screens.chat.ChatScreen
import com.arena.aid.ui.screens.chat.FilesSheet
import com.arena.aid.ui.screens.chat.ModelSheet
import com.arena.aid.ui.screens.history.HistoryDrawerContent
import com.arena.aid.ui.screens.settings.ConnectionSheet
import com.arena.aid.ui.screens.settings.SettingsScreen
import com.arena.aid.ui.theme.ArenaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ArenaViewModel = hiltViewModel()
            val settings by vm.settings.collectAsState()
            val dark = when (settings.themePreference) {
                ArenaThemePreference.SYSTEM -> isSystemInDarkTheme()
                ArenaThemePreference.LIGHT -> false
                ArenaThemePreference.DARK -> true
            }
            ArenaTheme(darkTheme = dark, reducedMotion = settings.reducedMotion) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ArenaRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun ArenaRoot(vm: ArenaViewModel) {
    val user by vm.user.collectAsState()
    val settings by vm.settings.collectAsState()
    val mode by vm.mode.collectAsState()
    val model by vm.model.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val sessionId by vm.sessionId.collectAsState()
    val messages by vm.messages.collectAsState()
    val steps by vm.agentSteps.collectAsState()
    val artifacts by vm.artifacts.collectAsState()
    val streaming by vm.streaming.collectAsState()
    val extractingCookie by vm.extractingCookie.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<ChatSessionEntity?>(null) }

    LaunchedEffect(Unit) { vm.toasts.collect { snackbar.showSnackbar(it) } }

    val signedIn = user != null

    Box(Modifier.fillMaxSize()) {
        if (!signedIn) {
            AuthScreen(
                onGoogle = { n, e -> vm.signInGoogle(n, e) },
                onEmail = { e, n -> vm.signInEmail(e, n) },
                onMessage = vm::toast
            )
        } else {
            BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets(0)
                    ) {
                        HistoryDrawerContent(
                            sessions = sessions, activeId = sessionId, user = user,
                            onOpen = { vm.openSession(it); scope.launch { drawer.close() } },
                            onNew = { vm.newChat(); scope.launch { drawer.close() } },
                            onPin = vm::togglePin, onRename = { renaming = it }, onDelete = vm::delete,
                            onSettings = { scope.launch { drawer.close() }; showSettings = true }
                        )
                    }
                }
            ) {
                ChatScreen(
                    mode = mode, model = model, messages = messages, steps = steps, artifacts = artifacts,
                    streaming = streaming, lowRam = settings.lowRamTurboEnabled,
                    onMenu = { scope.launch { drawer.open() } },
                    onNewChat = vm::newChat, onModeChange = vm::setMode,
                    onModelClick = { showModels = true }, onFilesClick = { showFiles = true },
                    onSend = vm::send,
                    onStop = vm::stop,
                    onRetry = vm::retry,
                    onToast = vm::toast
                )
            }
            if (showSettings) {
                BackHandler { showSettings = false }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SettingsScreen(
                        user = user, settings = settings, onBack = { showSettings = false },
                        onConnection = { showConnection = true },
                        onUpdate = vm::updateSettings, onClearHistory = vm::clearHistory,
                        onSignOut = { showSettings = false; vm.signOut() }
                    )
                }
            }
            if (showModels) {
                ModelSheet(
                    mode = mode,
                    selected = model,
                    onSelect = vm::selectModel,
                    onRefresh = vm::refreshModels,
                    onDismiss = { showModels = false }
                )
            }
            if (showFiles) FilesSheet(artifacts, vm::toast) { showFiles = false }
            if (showConnection) {
                ConnectionSheet(
                    user = user,
                    extracting = extractingCookie,
                    onAutoExtract = { cb -> vm.autoExtractArenaCookie(forceSignUp = true, onExtracted = cb) },
                    onDismiss = { showConnection = false },
                    onSave = vm::saveConnection
                )
            }
            renaming?.let { s ->
                var title by remember(s.id) { mutableStateOf(s.title) }
                ArenaDialog(
                    onDismiss = { renaming = null }, title = "Rename", confirmLabel = "Save",
                    onConfirm = { vm.rename(s, title); renaming = null }
                ) { ArenaTextField(value = title, onValueChange = { title = it }, modifier = Modifier) }
            }
        }
        ArenaToastHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}
