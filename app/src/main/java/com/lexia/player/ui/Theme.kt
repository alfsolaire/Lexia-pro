package com.lexia.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

const val APP_TITLE = "Alf TV Pro"
const val APP_SUBTITLE = "IPTV pour Fire TV"

val Bg = Color(0xFF0E1117)
val Panel = Color(0xFF161B26)
val CardBg = Color(0xFF1F2633)
val Accent = Color(0xFF2E9BFF)
val TextMain = Color(0xFFEDF1F7)
val TextDim = Color(0xFF9AA4B5)
val Danger = Color(0xFFFF6B6B)

@Composable
fun LexiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = Panel,
            onBackground = TextMain,
            onSurface = TextMain,
        ),
        content = content,
    )
}
