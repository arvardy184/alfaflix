package com.application.alfaflix.ui.components

/**
 * Builds image URLs from TMDB image paths.
 *
 * TMDB documents `https://image.tmdb.org/t/p/{size}{path}` as the CDN layout. Sizes are the
 * documented fixed set; the base is a constant rather than a `/configuration` call because
 * it is stable and an extra request per launch buys nothing here.
 *
 * These URLs point at a CDN and need no credentials. Coil fetches them with its own client,
 * so the API bearer token is never attached to image requests.
 */
object TmdbImages {

    private const val BASE = "https://image.tmdb.org/t/p/"

    /** Grid and carousel posters. */
    const val POSTER_SIZE = "w342"

    /** Detail hero backdrop. */
    const val BACKDROP_SIZE = "w780"

    fun poster(path: String?): String? = build(POSTER_SIZE, path)

    fun backdrop(path: String?): String? = build(BACKDROP_SIZE, path)

    /** A YouTube video's thumbnail, served by YouTube's image CDN. */
    fun youtubeThumbnail(videoKey: String): String = "https://img.youtube.com/vi/$videoKey/hqdefault.jpg"

    private fun build(size: String, path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let { "$BASE$size${if (it.startsWith("/")) it else "/$it"}" }
}
