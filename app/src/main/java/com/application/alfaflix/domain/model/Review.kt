package com.application.alfaflix.domain.model

/** A user review of a movie, as published on TMDB. */
data class Review(
    val id: String,
    val author: String,
    /** The reviewer's own score out of 10, when they gave one. */
    val rating: Double?,
    val content: String,
    /** ISO-8601 timestamp, or null when absent. */
    val createdAt: String?,
    val url: String?,
)
