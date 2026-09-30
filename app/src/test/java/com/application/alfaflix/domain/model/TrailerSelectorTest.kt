package com.application.alfaflix.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrailerSelectorTest {

    private fun video(
        key: String,
        type: String = "Trailer",
        official: Boolean = true,
        site: String = "YouTube",
        publishedAt: String? = "2020-01-01T00:00:00.000Z",
    ) = MovieVideo(key = key, name = "$type $key", site = site, type = type, official = official, publishedAt = publishedAt)

    @Test
    fun `no videos means no trailer`() {
        assertNull(TrailerSelector.select(emptyList()))
    }

    @Test
    fun `an official trailer beats an unofficial one`() {
        val chosen = TrailerSelector.select(
            listOf(
                video("fan", official = false, publishedAt = "2024-01-01T00:00:00.000Z"),
                video("studio", official = true, publishedAt = "2019-01-01T00:00:00.000Z"),
            ),
        )

        assertEquals("studio", chosen?.youtubeKey)
        assertEquals(true, chosen?.isOfficial)
    }

    @Test
    fun `an unofficial trailer is used when it is the only one`() {
        val chosen = TrailerSelector.select(listOf(video("only", official = false)))

        assertEquals("only", chosen?.youtubeKey)
        assertEquals(false, chosen?.isOfficial)
    }

    @Test
    fun `a teaser is never labelled a trailer`() {
        assertNull(TrailerSelector.select(listOf(video("t", type = "Teaser"))))
        assertNull(TrailerSelector.select(listOf(video("c", type = "Clip"), video("f", type = "Featurette"))))
    }

    @Test
    fun `only YouTube videos qualify`() {
        assertNull(TrailerSelector.select(listOf(video("v", site = "Vimeo"))))
    }

    @Test
    fun `an official teaser does not outrank an unofficial trailer`() {
        val chosen = TrailerSelector.select(
            listOf(video("teaser", type = "Teaser", official = true), video("trailer", official = false)),
        )

        assertEquals("trailer", chosen?.youtubeKey)
    }

    @Test
    fun `among equals the most recently published wins`() {
        val chosen = TrailerSelector.select(
            listOf(
                video("old", publishedAt = "2018-01-01T00:00:00.000Z"),
                video("new", publishedAt = "2022-01-01T00:00:00.000Z"),
            ),
        )

        assertEquals("new", chosen?.youtubeKey)
    }

    @Test
    fun `the choice does not depend on the order TMDB returns videos in`() {
        val videos = listOf(
            video("b", publishedAt = "2020-01-01T00:00:00.000Z"),
            video("a", publishedAt = "2020-01-01T00:00:00.000Z"),
            video("c", official = false),
        )

        assertEquals(TrailerSelector.select(videos), TrailerSelector.select(videos.reversed()))
        assertEquals("a", TrailerSelector.select(videos)?.youtubeKey)
    }

    @Test
    fun `blank keys are ignored`() {
        assertNull(TrailerSelector.select(listOf(video("  "))))
    }

    @Test
    fun `the watch url points at the chosen video`() {
        assertEquals(
            "https://www.youtube.com/watch?v=abc",
            TrailerSelector.select(listOf(video("abc")))?.watchUrl,
        )
    }
}
