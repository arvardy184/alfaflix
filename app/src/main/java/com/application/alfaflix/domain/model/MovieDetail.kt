package com.application.alfaflix.domain.model

/**
 * The full record backing the detail screen.
 *
 * Optional fields are genuinely nullable (or empty) rather than holding TMDB's empty
 * strings and zeros, so the UI omits a section with a plain null check. That normalisation
 * happens once, in the mapper.
 */
data class MovieDetail(
    val id: Int,
    /** Optional metadata only — never used as an identity. */
    val imdbId: String?,
    val title: String,
    val tagline: String?,
    val overview: String?,
    val releaseDate: String?,
    val runtimeMinutes: Int?,
    val genres: List<String>,
    /** TMDB community score out of 10. It is not an IMDb or critic rating. */
    val voteAverage: Double?,
    val voteCount: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val status: String?,
) {
    val releaseYear: String? get() = toMovie().releaseYear

    /** The list-shaped projection of this record, for favorites and view history. */
    fun toMovie(): Movie = Movie(
        id = id,
        title = title,
        releaseDate = releaseDate,
        posterPath = posterPath,
        voteAverage = voteAverage,
    )
}
