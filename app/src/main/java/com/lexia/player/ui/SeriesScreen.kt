package com.lexia.player.ui

import com.lexia.player.i18n.S
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lexia.player.data.Content
import com.lexia.player.data.Prefs
import com.lexia.player.data.SeriesDetails
import com.lexia.player.data.Session
import kotlinx.coroutines.CancellationException

@Composable
fun SeriesScreen(series: Content, prefs: Prefs, onPlay: (List<Content>, Int) -> Unit) {
    val provider = Session.provider ?: return
    val resumeTick = rememberResumeTick()
    val progress = remember(resumeTick) { prefs.resumeProgress() }
    var details by remember { mutableStateOf<SeriesDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var season by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(series.id, reloadKey) {
        error = null
        try {
            val loaded = provider.seriesDetails(series)
            if (season !in loaded.seasons) season = loaded.seasons.keys.minOrNull() ?: -1
            details = loaded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = errorText(e)
        }
    }

    val d = details
    when {
        error != null -> ErrorMessage(error.orEmpty()) { reloadKey++ }
        d == null -> Loading(S.loadingSeries)
        else -> Row(Modifier.fillMaxSize().background(Bg)) {
            val firstFocus = remember { FocusRequester() }
            val seasons = d.seasons.keys.sorted()
            Column(Modifier.width(330.dp).fillMaxHeight().background(Panel).padding(20.dp)) {
                AsyncImage(
                    model = d.cover ?: series.logo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(8.dp)).background(CardBg),
                )
                Spacer(Modifier.height(10.dp))
                Text(series.name, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                d.plot?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = TextDim, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
                    itemsIndexed(seasons) { index, s ->
                        FocusCard(
                            onClick = { season = s },
                            selected = s == season,
                            focusScale = 1.02f,
                            modifier = Modifier.fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        ) {
                            Text(
                                S.season(s, d.seasons[s]?.size ?: 0), color = TextMain, fontSize = 17.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
            val episodes = d.seasons[season].orEmpty()
            if (episodes.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(S.noEpisodes, color = TextDim, fontSize = 18.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(episodes) { index, ep ->
                        FocusCard(
                            onClick = { onPlay(episodes.map { it.toContent(series) }, index) },
                            focusScale = 1.02f,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(width = 160.dp, height = 90.dp).clip(RoundedCornerShape(6.dp))) {
                                    AsyncImage(
                                        model = ep.cover ?: series.logo,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().background(Panel),
                                    )
                                    progress[ep.url]?.let { WatchProgress(it, Modifier.align(Alignment.BottomStart)) }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        S.episode(ep.number, ep.title), color = TextMain, fontSize = 18.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    ep.plot?.let {
                                        Text(it, color = TextDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
        }
    }
}
