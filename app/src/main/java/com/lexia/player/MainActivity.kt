package com.lexia.player

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import com.lexia.player.data.Content
import com.lexia.player.data.ContentType
import com.lexia.player.data.Prefs
import com.lexia.player.data.Session
import com.lexia.player.player.PlayQueue
import com.lexia.player.player.PlayerActivity
import com.lexia.player.ui.Bg
import com.lexia.player.ui.BrowseScreen
import com.lexia.player.ui.HomeScreen
import com.lexia.player.ui.LexiaTheme
import com.lexia.player.ui.LoginScreen
import com.lexia.player.ui.SeriesScreen

private sealed interface Screen {
    val key: String

    data object Login : Screen { override val key = "login" }
    data object Home : Screen { override val key = "home" }
    data class Browse(val type: ContentType) : Screen { override val key get() = "browse-$type" }
    data class Series(val series: Content) : Screen { override val key get() = "series-${series.id}" }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        setContent {
            LexiaTheme {
                App(prefs) { items, index ->
                    PlayQueue.set(items, index)
                    startActivity(Intent(this, PlayerActivity::class.java))
                }
            }
        }
    }
}

@Composable
private fun App(prefs: Prefs, onPlay: (List<Content>, Int) -> Unit) {
    val stack = remember {
        mutableStateListOf<Screen>(if (Session.restore(prefs)) Screen.Home else Screen.Login)
    }
    val stateHolder = rememberSaveableStateHolder()
    val current = stack.last()

    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    fun reset(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        stateHolder.SaveableStateProvider(current.key) {
            when (current) {
                Screen.Login -> LoginScreen(prefs, onLoggedIn = { reset(Screen.Home) })
                Screen.Home -> HomeScreen(
                    prefs,
                    onOpen = { stack.add(Screen.Browse(it)) },
                    onLogout = { reset(Screen.Login) },
                )
                is Screen.Browse -> BrowseScreen(
                    current.type, prefs, onPlay,
                    onOpenSeries = { stack.add(Screen.Series(it)) },
                )
                is Screen.Series -> SeriesScreen(current.series, onPlay)
            }
        }
    }
}
