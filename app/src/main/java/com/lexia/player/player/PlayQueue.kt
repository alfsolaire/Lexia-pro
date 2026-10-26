package com.lexia.player.player

import com.lexia.player.data.Content

/** File de lecture partagée avec [PlayerActivity] (évite de passer des milliers de chaînes dans l'Intent). */
object PlayQueue {
    var items: List<Content> = emptyList()
        private set
    var index: Int = 0

    fun set(list: List<Content>, start: Int) {
        items = list
        index = start
    }
}
