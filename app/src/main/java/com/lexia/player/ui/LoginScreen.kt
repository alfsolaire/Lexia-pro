package com.lexia.player.ui

import com.lexia.player.i18n.S
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
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
import com.lexia.player.data.PhoneLoginServer
import com.lexia.player.data.Prefs
import com.lexia.player.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(prefs: Prefs, blank: Boolean, onLoggedIn: () -> Unit) {
    val saved = remember { if (blank) null else prefs.currentAccount }
    var name by rememberSaveable { mutableStateOf(saved?.name.orEmpty()) }
    var mode by rememberSaveable { mutableStateOf(saved?.config?.mode ?: LoginConfig.MODE_XTREAM) }
    var server by rememberSaveable { mutableStateOf(saved?.config?.server.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(saved?.config?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(saved?.config?.password.orEmpty()) }
    var m3uUrl by rememberSaveable { mutableStateOf(saved?.config?.m3uUrl.orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    var received by remember { mutableStateOf<Pair<String, LoginConfig>?>(null) }
    val phoneServer = remember { PhoneLoginServer { n, cfg -> received = n to cfg } }
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var qrReady by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose { phoneServer.stop() }
    }
    LaunchedEffect(Unit) {
        qrUrl = withContext(Dispatchers.IO) { phoneServer.start() }
        qrReady = true
    }

    fun connect() {
        val config = LoginConfig(mode, server.trim(), username.trim(), password, m3uUrl.trim())
        val missing = if (mode == LoginConfig.MODE_M3U) config.m3uUrl.isBlank()
        else config.server.isBlank() || config.username.isBlank() || config.password.isBlank()
        if (missing) {
            error = S.fillAllFields
            return
        }
        loading = true
        error = null
        scope.launch {
            try {
                val provider = Session.build(config, prefs)
                provider.accountInfo()
                prefs.saveAccount(name.trim().ifEmpty { config.defaultName() }, config)
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

    // Identifiants reçus du téléphone : on remplit le formulaire et on se connecte.
    LaunchedEffect(received) {
        val (n, cfg) = received ?: return@LaunchedEffect
        received = null
        name = n
        mode = cfg.mode
        server = cfg.server
        username = cfg.username
        password = cfg.password
        m3uUrl = cfg.m3uUrl
        connect()
    }

    Row(Modifier.fillMaxSize().background(Bg).padding(horizontal = 56.dp, vertical = 32.dp)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(APP_TITLE, color = TextMain, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(S.appSubtitle, color = Accent, fontSize = 18.sp)
            Spacer(Modifier.height(18.dp))
            val url = qrUrl
            if (url != null) {
                val qr = remember(url) { qrBitmap(url) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        bitmap = qr,
                        contentDescription = S.qrDescription,
                        modifier = Modifier.size(170.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(6.dp),
                    )
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Text(S.quickLogin, color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            S.qrInstructions,
                            color = TextDim, fontSize = 14.sp,
                        )
                    }
                }
            } else if (qrReady) {
                Text(
                    S.qrUnavailable,
                    color = TextDim, fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                S.playerOnly,
                color = TextDim,
                fontSize = 13.sp,
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
                TvButton(S.m3uLink, { mode = LoginConfig.MODE_M3U }, selected = mode == LoginConfig.MODE_M3U)
            }
            Spacer(Modifier.height(12.dp))
            Field(S.fieldAccountName, name, { name = it })
            if (mode == LoginConfig.MODE_XTREAM) {
                Field(S.fieldServer, server, { server = it }, KeyboardType.Uri)
                Field(S.fieldUsername, username, { username = it })
                Field(S.fieldPassword, password, { password = it }, isPassword = true)
            } else {
                Field(S.fieldM3uUrl, m3uUrl, { m3uUrl = it }, KeyboardType.Uri)
            }
            Spacer(Modifier.height(16.dp))
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Accent)
                    Spacer(Modifier.width(14.dp))
                    Text(S.connecting, color = TextDim, fontSize = 18.sp)
                }
            } else {
                TvButton(S.connect, { connect() })
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
