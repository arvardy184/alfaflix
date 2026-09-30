package com.application.alfaflix.domain.model

/**
 * A movie as it appears in a list: everything needed to render a poster card, nothing more.
 *
 * [id] is the TMDB movie ID and the app's only movie identity — for navigation, favorites,
 * history and every database key. IMDb IDs exist only as optional metadata on
 * [MovieDetail]; mixing two identifiers would let the same film exist twice.
 */
data class Movie(
    val id: Int,
    val title: String,
    /** ISO `yyyy-MM-dd`, or null when TMDB has no (or an empty) release date. */
    val releaseDate: String?,
    /** TMDB image path such as `/abc.jpg`; the URL is built at the edge, see `TmdbImages`. */
    val posterPath: String?,
    /** TMDB user score out of 10, or null when the movie has no votes yet. */
    val voteAverage: Double?,
) {
    val releaseYear: String? get() = releaseDate?.take(YEAR_LENGTH)?.takeIf { it.length == YEAR_LENGTH }

    private companion object {
        const val YEAR_LENGTH = 4
    }
}
