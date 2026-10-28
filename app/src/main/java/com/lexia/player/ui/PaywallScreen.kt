package com.lexia.player.ui

import com.lexia.player.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexia.player.billing.Licensing

/** Affiché à la fin de l'essai gratuit tant que l'application n'est pas achetée. */
@Composable
fun PaywallScreen() {
    val buyFocus = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(APP_TITLE, color = TextMain, fontSize = 44.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            S.trialEnded(Licensing.TRIAL_DAYS),
            color = TextMain, fontSize = 22.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            S.chooseOffer,
            color = TextDim, fontSize = 17.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OfferCard(
                title = S.yearly,
                price = S.perYear(Licensing.priceYearly),
                detail = S.yearlyDetail,
                onClick = { Licensing.buyYearly() },
                modifier = Modifier.focusRequester(buyFocus),
            )
            OfferCard(
                title = S.lifetimeBest,
                price = Licensing.priceLifetime,
                detail = S.lifetimeDetail,
                onClick = { Licensing.buyLifetime() },
            )
        }
        Spacer(Modifier.height(18.dp))
        TvButton(S.restorePurchases, { Licensing.restore() })
        Licensing.message?.let {
            Spacer(Modifier.height(18.dp))
            Text(it, color = Accent, fontSize = 16.sp, textAlign = TextAlign.Center)
        }
    }
    LaunchedEffect(Unit) { runCatching { buyFocus.requestFocus() } }
}

@Composable
private fun OfferCard(title: String, price: String, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(onClick = onClick, modifier = modifier.width(300.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(price, color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(detail, color = TextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
