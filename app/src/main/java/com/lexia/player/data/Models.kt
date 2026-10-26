package com.lexia.player.data

enum class ContentType { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

/** Une chaîne, un film, une série ou un épisode. [url] est null pour une série dont il faut charger les épisodes. */
data class Content(
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val type: ContentType,
    val url: String?,
)

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String,
    val cover: String?,
    val plot: String?,
) {
    fun toContent(series: Content) = Content(
        id = id,
        name = "${series.name} – S${season}E$number",
        logo = cover ?: series.logo,
        categoryId = series.categoryId,
        type = ContentType.SERIES,
        url = url,
    )
}

data class SeriesDetails(val plot: String?, val cover: String?, val seasons: Map<Int, List<Episode>>)

/** Une émission du guide des programmes. Heures en millisecondes. */
data class EpgEntry(val title: String, val description: String?, val start: Long, val end: Long)

data class AccountInfo(
    val username: String,
    val status: String?,
    val expiry: Long?,
    val activeConnections: String?,
    val maxConnections: String?,
)

interface IptvProvider {
    suspend fun accountInfo(): AccountInfo
    suspend fun categories(type: ContentType): List<Category>
    suspend fun items(type: ContentType): List<Content>
    suspend fun seriesDetails(series: Content): SeriesDetails

    /** Programme en cours et suivants d'une chaîne (vide si le fournisseur n'a pas de guide). */
    suspend fun shortEpg(channel: Content): List<EpgEntry> = emptyList()
}

fun favoriteKey(item: Content) = "${item.type}:${item.id}"
