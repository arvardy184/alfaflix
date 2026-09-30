package com.application.alfaflix.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale.
 *
 * Named tokens rather than raw dp literals scattered through screens, so rhythm stays
 * consistent and a change to the scale is one edit.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Poster width in a horizontal carousel. */
    val posterWidth = 132.dp
}

/** Posters are 2:3; fixing the ratio stops a missing image from collapsing a row. */
const val POSTER_ASPECT_RATIO = 2f / 3f
