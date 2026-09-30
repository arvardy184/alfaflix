package com.application.alfaflix.domain.model

/** A video listed for a movie by TMDB. Only [site] `YouTube` videos can be played in-app. */
data class MovieVideo(
    val key: String,
    val name: String,
    val site: String,
    val type: String,
    val official: Boolean,
    /** ISO-8601 timestamp, or null when absent. */
    val publishedAt: String?,
)

/** The video chosen to represent a movie's trailer. */
data class Trailer(
    /** The YouTube video ID. */
    val youtubeKey: String,
    val name: String,
    val isOfficial: Boolean,
) {
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$youtubeKey"
}

/**
 * Picks the trailer from TMDB's video list, deterministically.
 *
 * Only YouTube videos of type `Trailer` qualify — a teaser, clip or featurette is never
 * labelled a trailer. Among candidates an official one wins, then the most recently
 * published, and the key breaks any remaining tie so the same input always yields the same
 * choice regardless of the order TMDB returns them in.
 */
object TrailerSelector {

    fun select(videos: List<MovieVideo>): Trailer? = videos
        .filter { it.site.equals(YOUTUBE, ignoreCase = true) && it.type.equals(TRAILER, ignoreCase = true) }
        .filter { it.key.isNotBlank() }
        .sortedWith(
            compareByDescending<MovieVideo> { it.official }
                .thenByDescending { it.publishedAt.orEmpty() }
                .thenBy { it.key },
        )
        .firstOrNull()
        ?.let { Trailer(youtubeKey = it.key, name = it.name, isOfficial = it.official) }

    private const val YOUTUBE = "YouTube"
    private const val TRAILER = "Trailer"
}
