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
import coil.compose.AsyncImage
import com.lexia.player.data.Category
import com.lexia.player.data.Content
import com.lexia.player.data.ContentType
import com.lexia.player.data.Prefs
import com.lexia.player.data.Session
import com.lexia.player.data.favoriteKey
import kotlinx.coroutines.CancellationException

private const val ALL = "__all"
private const val FAV = "__fav"

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
            val allCats = remember(cats) {
                listOf(Category(ALL, "Tout"), Category(FAV, "★ Favoris")) + cats.filter { it.id != ALL && it.id != FAV }
            }
            val visible = remember(items, selected, query, favorites) {
                val q = query.trim()
                when {
                    q.isNotEmpty() -> items.filter { it.name.contains(q, ignoreCase = true) }
                    selected == ALL -> items
                    selected == FAV -> items.filter { favoriteKey(it) in favorites }
                    else -> items.filter { it.categoryId == selected }
                }
            }
            val toggleFavorite: (Content) -> Unit = { favorites = prefs.toggleFavorite(favoriteKey(it)) }
            val open: (Int) -> Unit = { index ->
                val item = visible[index]
                when {
                    item.url == null -> onOpenSeries(item)
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
                            else -> counts[id] ?: 0
                        }
                    },
                    onSelect = {
                        selected = it
                        query = ""
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
                                if (selected == FAV) "Aucun favori pour le moment." else "Aucun contenu.",
                                color = TextDim, fontSize = 18.sp,
                            )
                        }
                    } else if (type == ContentType.LIVE) {
                        ChannelList(visible, favorites, selected + query, open, toggleFavorite)
                    } else {
                        PosterGrid(visible, favorites, selected + query, open, toggleFavorite)
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
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
        ) {
            itemsIndexed(categories) { index, category ->
                FocusCard(
                    onClick = { onSelect(category.id) },
                    selected = category.id == selected,
                    focusScale = 1.02f,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
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
