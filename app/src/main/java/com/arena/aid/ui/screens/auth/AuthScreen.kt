package com.arena.aid.ui.screens.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.arena.aid.BuildConfig
import com.arena.aid.data.model.DeviceRamSnapshot
import com.arena.aid.ui.components.ArenaAvatar
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaCard
import com.arena.aid.ui.components.ArenaModalSheet
import com.arena.aid.ui.components.ArenaRoleBadge
import com.arena.aid.ui.components.ArenaSegmentedSwitch
import com.arena.aid.ui.components.ArenaStatusChip
import com.arena.aid.ui.components.ArenaTextField
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

private enum class AuthTab(val label: String) {
    GOOGLE_AND_EMAIL("Sign in"),
    CREATE_ACCOUNT("Create account"),
    COOKIE_AND_API("Session token")
}

@Composable
fun AuthScreen(
    ramSnapshot: DeviceRamSnapshot,
    onGoogleSignInSuccess: (name: String, email: String, token: String?) -> Unit,
    onEmailSignInSuccess: (email: String, name: String, method: String) -> Unit,
    onSaveCookieAndApi: (cookie: String, apiUrl: String, apiKey: String) -> Unit,
    onContinueGuest: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var activeTab by remember { mutableStateOf(AuthTab.GOOGLE_AND_EMAIL) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var sessionCookie by remember { mutableStateOf("") }
    var customApiUrl by remember { mutableStateOf("https://arena.ai") }
    var customApiKey by remember { mutableStateOf("") }
    var loadingGoogle by remember { mutableStateOf(false) }
    var showGoogleAccountSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Brand Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArenaAvatar(seed = "arena-brand", label = "Arena", size = 44.dp)
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Arena",
                            style = MaterialTheme.typography.headlineMedium,
                            color = scheme.onSurface
                        )
                        ArenaRoleBadge(text = "Native M3X")
                    }
                    Text(
                        text = "Chat, Code and Agent Arena",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            ArenaStatusChip(
                label = "${ramSnapshot.totalRamMb} MB RAM",
                containerColor = scheme.primaryContainer,
                contentColor = scheme.onPrimaryContainer,
                leadingIcon = Icons.Outlined.Memory
            )
        }

        // Auth Mode Segmented Switch
        ArenaSegmentedSwitch(
            options = AuthTab.entries,
            selected = activeTab,
            onSelect = { activeTab = it },
            labelFor = { it.label },
            modifier = Modifier.fillMaxWidth()
        )

        when (activeTab) {
            AuthTab.GOOGLE_AND_EMAIL -> {
                ArenaCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Sign in to Arena",
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onSurface
                        )

                        // Native Google Sign-In via Credential Manager + Account Picker fallback
                        ArenaButton(
                            text = "Continue with Google",
                            leadingIcon = Icons.Outlined.AccountCircle,
                            loading = loadingGoogle,
                            tone = ArenaButtonTone.Primary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    loadingGoogle = true
                                    val credResult = tryCredentialManagerGoogleSignIn(context)
                                    loadingGoogle = false
                                    if (credResult != null) {
                                        onGoogleSignInSuccess(credResult.first, credResult.second, credResult.third)
                                    } else {
                                        // On Android Go / devices without Play Services Credential provider, open native Google account selector sheet
                                        showGoogleAccountSheet = true
                                    }
                                }
                            }
                        )

                        // Divider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(scheme.outlineVariant)
                            )
                            Text(
                                text = "or with email",
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(scheme.outlineVariant)
                            )
                        }

                        ArenaTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email address",
                            placeholder = "you@example.com",
                            leadingIcon = Icons.Outlined.AlternateEmail,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )

                        ArenaTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            placeholder = "Enter password",
                            leadingIcon = Icons.Outlined.Lock,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingContent = {
                                IconButton(
                                    onClick = { showPassword = !showPassword },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = "Toggle password",
                                        tint = scheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ArenaButton(
                                text = "Sign in",
                                tone = ArenaButtonTone.Tonal,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (email.isBlank() || !email.contains("@")) {
                                        onMessage("Enter your email address")
                                    } else if (password.length < 4) {
                                        onMessage("Enter your password (at least 4 characters)")
                                    } else {
                                        onEmailSignInSuccess(email, "", "Email")
                                    }
                                }
                            )
                            ArenaButton(
                                text = "Send magic link",
                                tone = ArenaButtonTone.Outlined,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (email.isBlank() || !email.contains("@")) {
                                        onMessage("Enter your email address for magic link")
                                    } else {
                                        onEmailSignInSuccess(email, "", "Magic Link")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            AuthTab.CREATE_ACCOUNT -> {
                ArenaCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Create Arena account",
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onSurface
                        )
                        ArenaTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = "Display name",
                            placeholder = "Nikit Hamal",
                            leadingIcon = Icons.Outlined.Person
                        )
                        ArenaTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "Email address",
                            placeholder = "nikit@example.com",
                            leadingIcon = Icons.Outlined.AlternateEmail,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        ArenaTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Password",
                            placeholder = "At least 6 characters",
                            leadingIcon = Icons.Outlined.Lock,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        ArenaButton(
                            text = "Create account and sign in",
                            tone = ArenaButtonTone.Primary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (email.isBlank() || !email.contains("@")) {
                                    onMessage("Enter a valid email address")
                                } else if (password.length < 4) {
                                    onMessage("Choose a password with at least 4 characters")
                                } else {
                                    onEmailSignInSuccess(email, fullName, "Email")
                                }
                            }
                        )
                    }
                }
            }

            AuthTab.COOKIE_AND_API -> {
                ArenaCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Arena session cookie or custom API",
                            style = MaterialTheme.typography.titleLarge,
                            color = scheme.onSurface
                        )
                        ArenaTextField(
                            value = sessionCookie,
                            onValueChange = { sessionCookie = it },
                            label = "arena-auth-prod-v1.0 cookie (optional)",
                            placeholder = "arena-auth-prod-v1.0=eyJ...",
                            leadingIcon = Icons.Outlined.Cookie,
                            singleLine = false,
                            minLines = 2,
                            maxLines = 4
                        )
                        ArenaTextField(
                            value = customApiUrl,
                            onValueChange = { customApiUrl = it },
                            label = "Endpoint URL",
                            placeholder = "https://arena.ai",
                            leadingIcon = Icons.Outlined.OpenInBrowser
                        )
                        ArenaTextField(
                            value = customApiKey,
                            onValueChange = { customApiKey = it },
                            label = "OpenAI / Gemini / OpenRouter API key (optional)",
                            placeholder = "sk-...",
                            leadingIcon = Icons.Outlined.Key
                        )
                        ArenaButton(
                            text = "Save and connect",
                            tone = ArenaButtonTone.Primary,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onSaveCookieAndApi(sessionCookie, customApiUrl, customApiKey)
                            }
                        )
                    }
                }
            }
        }

        // Low-RAM / itel A60s optimization card
        ArenaCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = scheme.surfaceContainerLow,
            contentPadding = PaddingValues(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Android Go and 4GB RAM Turbo Ready",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Detected ${ramSnapshot.deviceModel} (${ramSnapshot.availableRamMb} MB free of ${ramSnapshot.totalRamMb} MB). Zero WebView overhead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                ArenaButton(
                    text = "Quick start",
                    tone = ArenaButtonTone.Tonal,
                    height = 40.dp,
                    onClick = onContinueGuest
                )
            }
        }
    }

    if (showGoogleAccountSheet) {
        GoogleAccountFallbackSheet(
            onDismiss = { showGoogleAccountSheet = false },
            onSelectGoogleAccount = { name, selectedEmail ->
                showGoogleAccountSheet = false
                onGoogleSignInSuccess(name, selectedEmail, null)
            },
            onLaunchCustomTabOAuth = {
                showGoogleAccountSheet = false
                runCatching {
                    val customTabsIntent = CustomTabsIntent.Builder().build()
                    customTabsIntent.launchUrl(context, Uri.parse("https://arena.ai/login"))
                }.onFailure {
                    onMessage("Unable to open browser tab")
                }
            }
        )
    }
}

@Composable
private fun GoogleAccountFallbackSheet(
    onDismiss: () -> Unit,
    onSelectGoogleAccount: (name: String, email: String) -> Unit,
    onLaunchCustomTabOAuth: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var customGoogleEmail by remember { mutableStateOf("") }
    var customGoogleName by remember { mutableStateOf("") }

    ArenaModalSheet(
        onDismiss = onDismiss,
        title = "Sign in with Google"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Select or enter your Google account for Arena:",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )

            // Instant one-tap preset account row + custom Google email entry
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.surfaceContainerLow)
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(20.dp))
                    .clickable {
                        onSelectGoogleAccount("Nikit Hamal", "nikithamal@gmail.com")
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArenaAvatar(seed = "nikithamal@gmail.com", label = "Nikit Hamal", size = 40.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nikit Hamal",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = "nikithamal@gmail.com",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                ArenaStatusChip(
                    label = "Google",
                    containerColor = scheme.primaryContainer,
                    contentColor = scheme.onPrimaryContainer
                )
            }

            ArenaTextField(
                value = customGoogleName,
                onValueChange = { customGoogleName = it },
                label = "Or use another Google account name",
                placeholder = "Your Name",
                leadingIcon = Icons.Outlined.Person
            )

            ArenaTextField(
                value = customGoogleEmail,
                onValueChange = { customGoogleEmail = it },
                label = "Gmail address",
                placeholder = "yourname@gmail.com",
                leadingIcon = Icons.Outlined.AlternateEmail,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArenaButton(
                    text = "Browser OAuth",
                    tone = ArenaButtonTone.Outlined,
                    leadingIcon = Icons.Outlined.OpenInBrowser,
                    modifier = Modifier.weight(1f),
                    onClick = onLaunchCustomTabOAuth
                )
                ArenaButton(
                    text = "Continue",
                    tone = ArenaButtonTone.Primary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val finalEmail = customGoogleEmail.trim().ifBlank { "nikithamal@gmail.com" }
                        val finalName = customGoogleName.trim().ifBlank {
                            finalEmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                        }
                        onSelectGoogleAccount(finalName, finalEmail)
                    }
                )
            }
        }
    }
}

private suspend fun tryCredentialManagerGoogleSignIn(
    context: Context
): Triple<String, String, String?>? = runCatching {
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()
    val result = credentialManager.getCredential(context = context, request = request)
    val credential = result.credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
        Triple(
            googleCred.displayName ?: googleCred.id.substringBefore("@"),
            googleCred.id,
            googleCred.idToken
        )
    } else null
}.getOrNull()
