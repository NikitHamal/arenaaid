package com.arena.aid.ui.screens.auth

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.arena.aid.BuildConfig
import com.arena.aid.ui.components.ArenaAvatar
import com.arena.aid.ui.components.ArenaButton
import com.arena.aid.ui.components.ArenaButtonTone
import com.arena.aid.ui.components.ArenaTextField
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onGoogle: (name: String, email: String) -> Unit,
    onEmail: (email: String, name: String) -> Unit,
    onMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        ArenaAvatar(seed = "arena", label = "Arena", size = 72.dp)
        Spacer(Modifier.height(20.dp))
        Text("Sign in to Arena", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(32.dp))
        ArenaButton(
            text = "Continue with Google",
            leadingIcon = Icons.Rounded.AccountCircle,
            loading = busy,
            height = 52.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                busy = true
                scope.launch {
                    val result = googleSignIn(context)
                    busy = false
                    if (result != null) onGoogle(result.first, result.second)
                    else onMessage("Google sign-in is unavailable. Continue with email.")
                }
            }
        )
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            Text("or", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
        Spacer(Modifier.height(24.dp))
        ArenaTextField(
            value = name, onValueChange = { name = it },
            placeholder = "Name", modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        ArenaTextField(
            value = email, onValueChange = { email = it },
            placeholder = "Email", leadingIcon = Icons.Rounded.Email,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        ArenaButton(
            text = "Continue with email",
            tone = ArenaButtonTone.Tonal,
            height = 52.dp,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onEmail(email, name) }
        )
        Spacer(Modifier.height(48.dp))
    }
}

private suspend fun googleSignIn(context: Context): Pair<String, String>? = runCatching {
    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
        .setAutoSelectEnabled(false)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    val credential = CredentialManager.create(context).getCredential(context, request).credential
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        val g = GoogleIdTokenCredential.createFrom(credential.data)
        (g.displayName ?: g.id.substringBefore("@")) to g.id
    } else null
}.getOrNull()
