package com.lexia.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.ViewGroup
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.lexia.player.player.PreviewPlayer
import com.lexia.player.data.Category
import com.lexia.player.data.Content
import com.lexia.player.data.ContentType
import com.lexia.player.data.EpgEntry
import com.lexia.player.data.XtreamProvider
import com.lexia.player.data.Prefs
import com.lexia.player.data.Session
import com.lexia.player.data.favoriteKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ALL = "__all"
private const val FAV = "__fav"
private const val RECENT = "__recent"

@Composable
fun BrowseScreen(
    type: ContentType,
    prefs: Prefs,
    onPlay: (List<Content>, Int) -> Unit,
    onOpenSeries: (Content) -> Unit,
) {
    val provider = Session.provider ?: return
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var items by remember { mutableStateOf<List<Content>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by remember { mutableStateOf(prefs.favorites()) }
    var pinned by remember(type) { mutableStateOf(prefs.pinnedCategories(type)) }
    var sortAlpha by remember { mutableStateOf(prefs.sortCategoriesAlpha) }
    val resumeTick = rememberResumeTick()
    val recents = remember(resumeTick, type) { prefs.recents(type) }
    val progress = remember(resumeTick) { prefs.resumeProgress() }
    val showEpg = type == ContentType.LIVE && provider is XtreamProvider
    var focusedChannel by remember { mutableStateOf<Content?>(null) }
    var epg by remember { mutableStateOf<List<EpgEntry>?>(null) }

    LaunchedEffect(focusedChannel) {
        epg = null
        val channel = focusedChannel ?: return@LaunchedEffect
        if (!showEpg) return@LaunchedEffect
        delay(350) // évite de solliciter le serveur pendant un défilement rapide
        epg = try {
            provider.shortEpg(channel)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    LaunchedEffect(provider, type, reloadKey) {
        error = null
        categories = null
        try {
            val cats = provider.categories(type)
            items = provider.items(type)
            categories = cats
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = errorText(e)
        }
    }

    val title = when (type) {
        ContentType.LIVE -> "TV en direct"
        ContentType.MOVIE -> "Films"
        ContentType.SERIES -> "Séries"
    }

    val cats = categories
    when {
        error != null -> ErrorMessage(error.orEmpty()) { reloadKey++ }
        cats == null -> Loading("Chargement : $title…")
        else -> {
            val counts = remember(items) { items.groupingBy { it.categoryId }.eachCount() }
            val byKey = remember(items) { items.associateBy { favoriteKey(it) } }
            val recentLabel = if (type == ContentType.LIVE) "🕘 Dernières chaînes" else "🕘 Récemment vus"
            val allCats = remember(cats, pinned, sortAlpha) {
                val regular = cats.filter { it.id != ALL && it.id != FAV && it.id != RECENT }
                val byId = regular.associateBy { it.id }
                val top = pinned.mapNotNull { byId[it] }
                val rest = regular.filter { it.id !in pinned }.let { list ->
                    if (sortAlpha) {
                        val collator = Collator.getInstance(Locale.FRANCE)
                        list.sortedWith { a, b -> collator.compare(a.name, b.name) }
                    } else {
                        list
                    }
                }
                listOf(Category(ALL, "Tout"), Category(FAV, "★ Favoris"), Category(RECENT, recentLabel)) + top + rest
            }
            val visible = remember(items, selected, query, favorites, recents) {
                val q = query.trim()
                when {
                    q.isNotEmpty() -> items.filter { it.name.contains(q, ignoreCase = true) }
                    selected == ALL -> items
                    selected == FAV -> items.filter { favoriteKey(it) in favorites }
                    selected == RECENT -> recents.mapNotNull { byKey[it] }
                    else -> items.filter { it.categoryId == selected }
                }
            }
            val toggleFavorite: (Content) -> Unit = { favorites = prefs.toggleFavorite(favoriteKey(it)) }
            val open: (Int) -> Unit = { index ->
                val item = visible[index]
                when {
                    item.url == null -> {
                        prefs.addRecent(type, favoriteKey(item))
                        onOpenSeries(item)
                    }
                    item.type == ContentType.MOVIE -> onPlay(listOf(item), 0)
                    else -> onPlay(visible, index)
                }
            }

            Row(Modifier.fillMaxSize().background(Bg)) {
                CategoryPanel(
                    title = title,
                    categories = allCats,
                    selected = selected,
                    query = query,
                    onQuery = { query = it },
                    countOf = { id ->
                        when (id) {
                            ALL -> items.size
                            FAV -> items.count { favoriteKey(it) in favorites }
                            RECENT -> recents.count { it in byKey }
                            else -> counts[id] ?: 0
                        }
                    },
                    onSelect = {
                        selected = it
                        query = ""
                    },
                    pinned = pinned,
                    onTogglePin = { if (it != ALL && it != FAV && it != RECENT) pinned = prefs.togglePinned(type, it) },
                    sortAlpha = sortAlpha,
                    onToggleSort = {
                        sortAlpha = !sortAlpha
                        prefs.sortCategoriesAlpha = sortAlpha
                    },
                )
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
                    Text(
                        "${visible.size} élément(s)   ·   OK : ouvrir   ·   ☰ Menu : ajouter/retirer des favoris",
                        color = TextDim,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (visible.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                when (selected) {
                                    FAV -> "Aucun favori pour le moment."
                                    RECENT -> "Rien de regardé récemment."
                                    else -> "Aucun contenu."
                                },
                                color = TextDim, fontSize = 18.sp,
                            )
                        }
                    } else if (type == ContentType.LIVE) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                ChannelList(visible, favorites, selected + query, open, toggleFavorite) { focusedChannel = it }
                            }
                            Spacer(Modifier.width(16.dp))
                            EpgPanel(
                                focusedChannel, if (showEpg) epg else emptyList(), showEpg,
                                Modifier.width(340.dp).fillMaxHeight().padding(bottom = 20.dp),
                            )
                        }
                    } else {
                        PosterGrid(visible, favorites, selected + query, open, toggleFavorite) { item ->
                            item.url?.let { progress[it] }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPanel(
    title: String,
    categories: List<Category>,
    selected: String,
    query: String,
    onQuery: (String) -> Unit,
    countOf: (String) -> Int,
    onSelect: (String) -> Unit,
    pinned: List<String>,
    onTogglePin: (String) -> Unit,
    sortAlpha: Boolean,
    onToggleSort: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val firstFocus = remember { FocusRequester() }
    Column(Modifier.width(310.dp).fillMaxHeight().background(Panel).padding(16.dp)) {
        Text(title, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("Rechercher") },
            singleLine = true,
            colors = tvFieldColors(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.moveFocus(FocusDirection.Right) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FocusCard(onClick = onToggleSort, focusScale = 1.02f, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Tri : " + if (sortAlpha) "A → Z" else "ordre du serveur",
                color = TextDim, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Text("☰ sur une catégorie : l'épingler en haut", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(4.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        ) {
            itemsIndexed(categories) { index, category ->
                FocusCard(
                    onClick = { onSelect(category.id) },
                    onMenu = { onTogglePin(category.id) },
                    selected = category.id == selected,
                    focusScale = 1.02f,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (category.id in pinned) Text("📌 ", fontSize = 14.sp)
                        Text(
                            category.name, color = TextMain, fontSize = 16.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text("${countOf(category.id)}", color = TextDim, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
}

@Composable
private fun ChannelList(
    channels: List<Content>,
    favorites: Set<String>,
    resetKey: String,
    onOpen: (Int) -> Unit,
    onFavorite: (Content) -> Unit,
    onFocused: (Content) -> Unit,
) {
    val state = remember(resetKey) { LazyListState() }
    LazyColumn(
        state = state,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(channels) { index, channel ->
            FocusCard(
                onClick = { onOpen(index) },
                onMenu = { onFavorite(channel) },
                onFocus = { onFocused(channel) },
                focusScale = 1.02f,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = TextDim, fontSize = 15.sp, modifier = Modifier.width(56.dp))
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 64.dp, height = 40.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        channel.name, color = TextMain, fontSize = 18.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    if (favoriteKey(channel) in favorites) Text("★", color = Accent, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun PosterGrid(
    posters: List<Content>,
    favorites: Set<String>,
    resetKey: String,
    onOpen: (Int) -> Unit,
    onFavorite: (Content) -> Unit,
    progressOf: (Content) -> Float?,
) {
    val state = remember(resetKey) { LazyGridState() }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(posters) { index, item ->
            FocusCard(onClick = { onOpen(index) }, onMenu = { onFavorite(item) }) {
                Column {
                    Box {
                        AsyncImage(
                            model = item.logo,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Panel),
                        )
                        if (favoriteKey(item) in favorites) {
                            Text("★", color = Accent, fontSize = 22.sp, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
                        }
                        progressOf(item)?.let { WatchProgress(it, Modifier.align(Alignment.BottomStart)) }
                    }
                    Text(
                        item.name, color = TextMain, fontSize = 14.sp, maxLines = 2, minLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpgPanel(channel: Content?, epg: List<EpgEntry>?, hasGuide: Boolean, modifier: Modifier) {
    Column(modifier.background(Panel, RoundedCornerShape(10.dp)).padding(14.dp)) {
        LivePreview(channel, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.height(10.dp))
        if (channel == null) {
            Text("Placez-vous sur une chaîne pour voir son aperçu et son programme.", color = TextDim, fontSize = 15.sp)
            return@Column
        }
        Text(channel.name, color = TextMain, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(10.dp))
        val now = System.currentTimeMillis()
        val current = epg?.firstOrNull { it.start <= now && it.end > now }
        val next = epg?.firstOrNull { it.start >= (current?.end ?: now) }
        when {
            !hasGuide -> Text("Guide des programmes disponible avec les comptes Xtream Codes.", color = TextDim, fontSize = 14.sp)
            epg == null -> Text("Chargement du programme…", color = TextDim, fontSize = 14.sp)
            current == null && next == null ->
                Text("Le fournisseur ne propose pas de programme pour cette chaîne.", color = TextDim, fontSize = 14.sp)
            else -> {
                current?.let {
                    Text("EN COURS  ·  ${timeRange(it)}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(it.title, color = TextMain, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    val fraction = ((now - it.start).toFloat() / (it.end - it.start).coerceAtLeast(1)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { fraction },
                        color = Accent,
                        trackColor = CardBg,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    it.description?.let { d ->
                        Spacer(Modifier.height(6.dp))
                        Text(d, color = TextDim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                next?.let {
                    Text("ENSUITE  ·  ${timeRange(it)}", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(it.title, color = TextMain, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Aperçu vidéo de la chaîne sélectionnée (démarre après une courte pause sur la chaîne). */
@Composable
private fun LivePreview(channel: Content?, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(true) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    active = false
                    PreviewPlayer.stop()
                }
                Lifecycle.Event.ON_RESUME -> active = true
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            PreviewPlayer.stop()
        }
    }

    LaunchedEffect(channel?.url, active) {
        PreviewPlayer.stop()
        val url = channel?.url ?: return@LaunchedEffect
        if (!active) return@LaunchedEffect
        delay(900) // laisse défiler la liste sans ouvrir un flux par chaîne
        PreviewPlayer.play(context, url)
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = PreviewPlayer.get(ctx)
                }
            },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        if (channel == null) {
            Text("Aperçu", color = TextDim, fontSize = 14.sp)
        }
    }
}

fun timeRange(entry: EpgEntry): String {
    val format = SimpleDateFormat("HH:mm", Locale.FRANCE)
    return "${format.format(Date(entry.start))} – ${format.format(Date(entry.end))}"
}
