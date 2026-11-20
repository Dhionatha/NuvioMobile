package com.nuvio.app.features.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TmdbEpisodeGuideTest {
    @Test
    fun exactAirDateMatchAcceptsTheEpisode() {
        assertTrue(
            episodeIdentityMatches(
                expectedTitle = "Pilot",
                expectedAirDate = "2008-01-20T00:00:00.000Z",
                actualTitle = "Pilot",
                actualAirDate = "2008-01-20",
            ),
        )
    }

    @Test
    fun conflictingAirDatesAreRejectedEvenWhenTitlesMatch() {
        assertFalse(
            episodeIdentityMatches(
                expectedTitle = "Pilot",
                expectedAirDate = "2008-01-20",
                actualTitle = "Pilot",
                actualAirDate = "2008-01-27",
            ),
        )
    }

    @Test
    fun titleMatchIsUsedWhenOneSideHasNoAirDate() {
        assertTrue(
            episodeIdentityMatches(
                expectedTitle = "The One with the Blackout",
                expectedAirDate = null,
                actualTitle = "The One with the Blackout",
                actualAirDate = "1994-11-03",
            ),
        )
        assertTrue(
            episodeIdentityMatches(
                expectedTitle = "The One with the Blackout",
                expectedAirDate = "1994-11-03",
                actualTitle = "The One with the Blackout",
                actualAirDate = null,
            ),
        )
    }

    @Test
    fun genericEpisodeTitlesNeverValidate() {
        val genericTitles = listOf("Episode 2", "Ep 2", "Ep. 2", "E2", "TBA", "TBD", "2", "Episode")
        genericTitles.forEach { title ->
            assertFalse(episodeIdentityMatches(title, null, title, null), title)
            assertFalse(episodeIdentityMatches("The Real Title", null, title, null), title)
            assertFalse(episodeIdentityMatches(title, null, "The Real Title", null), title)
        }
    }

    @Test
    fun specificTitlesStillValidate() {
        assertTrue(episodeIdentityMatches("Chapter Two", null, "chapter two!", null))
        assertFalse(episodeIdentityMatches("Chapter Two", null, "Chapter Three", null))
    }

    @Test
    fun blankOrMissingTitlesFailClosed() {
        assertFalse(episodeIdentityMatches(null, null, "Pilot", null))
        assertFalse(episodeIdentityMatches("Pilot", null, null, null))
        assertFalse(episodeIdentityMatches("   ", null, "Pilot", null))
    }

    @Test
    fun addonReleaseTimestampsNormalizeToIsoDates() {
        assertEquals("2024-05-10", normalizeIsoAirDate("2024-05-10T00:00:00.000Z"))
        assertEquals("2024-05-10", normalizeIsoAirDate(" 2024-05-10 "))
        assertEquals("2024-05-10", normalizeIsoAirDate("2024-05-10"))
        assertNull(normalizeIsoAirDate("2024"))
        assertNull(normalizeIsoAirDate("10/05/2024"))
        assertNull(normalizeIsoAirDate(null))
    }

    @Test
    fun titlesNormalizeCasePunctuationAndSpacing() {
        assertEquals("the one with the blackout", normalizeEpisodeTitle("The One with the Blackout!"))
        assertEquals("ep 2", normalizeEpisodeTitle("Ep. 2"))
        assertEquals("", normalizeEpisodeTitle(null))
        assertTrue(isGenericEpisodeTitle("Episode 12"))
        assertFalse(isGenericEpisodeTitle("Chapter 2"))
    }
}
