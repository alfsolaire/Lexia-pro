package com.lexia.player.ui

import com.lexia.player.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexia.player.billing.Licensing
import com.lexia.player.data.AccountInfo
import com.lexia.player.data.ContentType
import com.lexia.player.data.LoginConfig
import com.lexia.player.data.Prefs
import com.lexia.player.data.Session
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(prefs: Prefs, onOpen: (ContentType) -> Unit, onAccounts: () -> Unit) {
    val config = remember { prefs.load() }
    val accountName = remember { prefs.currentAccount?.name }
    var provider by remember { mutableStateOf(Session.provider) }
    var account by remember { mutableStateOf<AccountInfo?>(null) }
    var liveFormat by remember { mutableStateOf(prefs.liveFormat) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(provider) {
        account = null
        account = runCatching { provider?.accountInfo() }.getOrNull()
    }

    fun rebuild() {
        val cfg = config ?: return
        Session.provider = Session.build(cfg, prefs)
        provider = Session.provider
    }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 56.dp, vertical = 40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(APP_TITLE, color = TextMain, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                accountName?.let { Text(S.accountLabel(it), color = Accent, fontSize = 16.sp) }
            }
            Spacer(Modifier.weight(1f))
            account?.let { Text(accountLine(it), color = TextDim, fontSize = 16.sp) }
        }
        if (Licensing.requiresPurchase && !Licensing.unlocked) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val days = Licensing.daysLeft()
                Text(
                    S.trialDaysLeft(days),
                    color = TextMain, fontSize = 18.sp,
                )
                Spacer(Modifier.width(20.dp))
                TvButton(S.yearlyButton(Licensing.priceYearly), { Licensing.buyYearly() })
                Spacer(Modifier.width(12.dp))
                TvButton(S.lifetimeButton(Licensing.priceLifetime), { Licensing.buyLifetime() })
            }
            Licensing.message?.let { Text(it, color = TextDim, fontSize = 14.sp) }
            Spacer(Modifier.height(24.dp))
        } else {
            Spacer(Modifier.height(48.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.fillMaxWidth()) {
            HomeTile(S.liveTv, "📺", Modifier.weight(1f).focusRequester(firstFocus)) { onOpen(ContentType.LIVE) }
            HomeTile(S.movies, "🎬", Modifier.weight(1f)) { onOpen(ContentType.MOVIE) }
            HomeTile(S.series, "🎞", Modifier.weight(1f)) { onOpen(ContentType.SERIES) }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (config?.mode == LoginConfig.MODE_XTREAM) {
                val label = if (liveFormat == "m3u8") "HLS (m3u8)" else "MPEG-TS"
                TvButton(S.liveFormat(label), {
                    liveFormat = if (liveFormat == "ts") "m3u8" else "ts"
                    prefs.liveFormat = liveFormat
                    rebuild()
                })
            }
            TvButton(S.refreshLists, { rebuild() })
            TvButton(S.accounts, onAccounts)
        }
    }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun HomeTile(title: String, emoji: String, modifier: Modifier, onClick: () -> Unit) {
    FocusCard(onClick = onClick, modifier = modifier.height(230.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(emoji, fontSize = 64.sp)
            Spacer(Modifier.height(12.dp))
            Text(title, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun accountLine(a: AccountInfo): String = buildList {
    add(a.username)
    a.status?.let { add(it) }
    a.expiry?.let { add(S.expiresOn(DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(it * 1000)))) }
    if (a.maxConnections != null) add(S.connections(a.activeConnections ?: "0", a.maxConnections))
}.joinToString("  ·  ")
