package com.lexia.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexia.player.data.LoginConfig
import com.lexia.player.data.Prefs
import com.lexia.player.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(prefs: Prefs, onLoggedIn: () -> Unit) {
    val saved = remember { prefs.load() }
    var mode by rememberSaveable { mutableStateOf(saved?.mode ?: LoginConfig.MODE_XTREAM) }
    var server by rememberSaveable { mutableStateOf(saved?.server.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(saved?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(saved?.password.orEmpty()) }
    var m3uUrl by rememberSaveable { mutableStateOf(saved?.m3uUrl.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    fun connect() {
        val config = LoginConfig(mode, server.trim(), username.trim(), password, m3uUrl.trim())
        val missing = if (mode == LoginConfig.MODE_M3U) config.m3uUrl.isBlank()
        else config.server.isBlank() || config.username.isBlank() || config.password.isBlank()
        if (missing) {
            error = "Veuillez remplir tous les champs."
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val provider = Session.build(config, prefs)
                provider.accountInfo()
                prefs.save(config)
                Session.provider = provider
                onLoggedIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = errorText(e)
            } finally {
                loading = false
            }
        }
    }

    Row(Modifier.fillMaxSize().background(Bg).padding(horizontal = 56.dp, vertical = 40.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Lexia Player", color = TextMain, fontSize = 46.sp, fontWeight = FontWeight.Bold)
            Text("Lecteur IPTV pour Fire TV", color = Accent, fontSize = 20.sp)
            Spacer(Modifier.height(28.dp))
            Text(
                "Cette application est uniquement un lecteur : elle ne fournit aucune chaîne ni aucun contenu. " +
                    "Saisissez les identifiants de l'abonnement fourni par votre fournisseur.",
                color = TextDim,
                fontSize = 16.sp,
            )
        }
        Column(
            modifier = Modifier.weight(1.2f).fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(
                    "Xtream Codes", { mode = LoginConfig.MODE_XTREAM },
                    Modifier.focusRequester(firstFocus), selected = mode == LoginConfig.MODE_XTREAM,
                )
                TvButton("Lien M3U", { mode = LoginConfig.MODE_M3U }, selected = mode == LoginConfig.MODE_M3U)
            }
            Spacer(Modifier.height(16.dp))
            if (mode == LoginConfig.MODE_XTREAM) {
                Field("Adresse du serveur (ex. http://exemple.com:8080)", server, { server = it }, KeyboardType.Uri)
                Field("Nom d'utilisateur", username, { username = it })
                Field("Mot de passe", password, { password = it }, isPassword = true)
            } else {
                Field("URL de la playlist M3U", m3uUrl, { m3uUrl = it }, KeyboardType.Uri)
            }
            Spacer(Modifier.height(16.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Accent)
                    Spacer(Modifier.width(14.dp))
                    Text("Connexion en cours…", color = TextDim, fontSize = 18.sp)
                }
            } else {
                TvButton("Se connecter", { connect() })
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Danger, fontSize = 16.sp)
            }
        }
    }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        colors = tvFieldColors(),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    )
}
