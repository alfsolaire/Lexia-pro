package com.lexia.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexia.player.data.LoginConfig
import com.lexia.player.data.Prefs
import com.lexia.player.data.Session

@Composable
fun AccountsScreen(
    prefs: Prefs,
    onSwitched: () -> Unit,
    onAdd: () -> Unit,
    onCurrentRemoved: () -> Unit,
) {
    var accounts by remember { mutableStateOf(prefs.accounts()) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val currentId = remember(accounts) { prefs.currentAccount?.id }
    val firstFocus = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 56.dp, vertical = 36.dp)) {
        Text("Comptes", color = TextMain, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Choisissez l'abonnement à utiliser. Les favoris et l'historique sont propres à chaque compte.",
            color = TextDim, fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
        ) {
            itemsIndexed(accounts) { index, account ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    FocusCard(
                        onClick = {
                            prefs.switchTo(account.id)
                            Session.provider = Session.build(account.config, prefs)
                            onSwitched()
                        },
                        onFocus = { pendingDelete = null },
                        selected = account.id == currentId,
                        focusScale = 1.02f,
                        modifier = Modifier.weight(1f)
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    ) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            Text(
                                account.name + if (account.id == currentId) "   ✓ actuel" else "",
                                color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (account.config.mode == LoginConfig.MODE_M3U) "Playlist M3U"
                                else "Xtream Codes · ${account.config.server}",
                                color = TextDim, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    val confirming = pendingDelete == account.id
                    TvButton(if (confirming) "Confirmer la suppression" else "Supprimer", {
                        if (!confirming) {
                            pendingDelete = account.id
                        } else {
                            pendingDelete = null
                            val wasCurrent = account.id == currentId
                            prefs.removeAccount(account.id)
                            accounts = prefs.accounts()
                            if (wasCurrent) {
                                Session.provider = null
                                onCurrentRemoved()
                            }
                        }
                    })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TvButton(
            "+ Ajouter un compte", onAdd,
            if (accounts.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
        )
    }

    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}
