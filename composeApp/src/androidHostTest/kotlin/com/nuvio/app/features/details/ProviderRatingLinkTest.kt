package com.nuvio.app.features.details

import android.app.Application
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.details.components.DetailMetaInfo
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_IMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_LETTERBOXD
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_MAL
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_METACRITIC
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TOMATOES
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TRAKT
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w720dp-h1280dp")
class ProviderRatingLinkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun overviewRatingOpensImdbTitlePage() {
        var openedUrl: String? = null
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(id = "tt1234567", type = "movie", name = "Movie", imdbRating = "7.8"),
                    onOpenExternalUrl = { openedUrl = it },
                )
            }
        }

        compose.onNodeWithText("7.8").assertIsDisplayed().performClick()

        assertEquals("https://www.imdb.com/title/tt1234567/", openedUrl)
    }

    @Test
    fun explicitImdbIdWinsOverTmdbContentId() {
        var openedUrl: String? = null
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(
                        id = "tmdb:550",
                        type = "movie",
                        name = "Movie",
                        imdbId = "tt0137523",
                        imdbRating = "8.8",
                    ),
                    onOpenExternalUrl = { openedUrl = it },
                )
            }
        }

        compose.onNodeWithText("8.8").performClick()

        assertEquals("https://www.imdb.com/title/tt0137523/", openedUrl)
    }

    @Test
    fun ratingWithoutImdbIdIsNotClickable() {
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(id = "tmdb:550", type = "movie", name = "Movie", imdbRating = "8.8"),
                    onOpenExternalUrl = { error("must not open without an IMDb id") },
                )
            }
        }

        compose.onNodeWithText("8.8").assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun mdbListImdbChipFallsBackToTheTitlePage() {
        var openedUrl: String? = null
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(
                        id = "tt1234567",
                        type = "movie",
                        name = "Movie",
                        imdbRating = "7.8",
                        externalRatings = listOf(
                            MetaExternalRating(PROVIDER_IMDB, 7.8),
                            MetaExternalRating(PROVIDER_TMDB, 65.0),
                        ),
                    ),
                    isMdbListActive = true,
                    onOpenExternalUrl = { openedUrl = it },
                )
            }
        }

        compose.onNodeWithText("65").assertHasNoClickAction()
        compose.onNodeWithText("7.8").assertIsDisplayed().performClick()

        assertEquals("https://www.imdb.com/title/tt1234567/", openedUrl)
    }

    @Test
    fun mdbListChipsOpenEachProviderPage() {
        var openedUrl: String? = null
        val expectedPages = listOf(
            "7.8" to "https://www.imdb.com/title/tt1234567/",
            "65" to "https://www.themoviedb.org/movie/578",
            "70" to "https://trakt.tv/movies/457",
            "4.1" to "https://letterboxd.com/film/jaws/",
            "8.3" to "https://myanimelist.net/anime/21",
            "97%" to "https://www.rottentomatoes.com/m/jaws",
            "87" to "https://www.metacritic.com/movie/jaws",
        )
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(
                        id = "tt0073195",
                        type = "movie",
                        name = "Jaws",
                        externalRatings = listOf(
                            MetaExternalRating(PROVIDER_IMDB, 7.8, url = "https://www.imdb.com/title/tt1234567/"),
                            MetaExternalRating(PROVIDER_TMDB, 65.0, url = "https://www.themoviedb.org/movie/578"),
                            MetaExternalRating(PROVIDER_TRAKT, 70.0, url = "https://trakt.tv/movies/457"),
                            MetaExternalRating(PROVIDER_LETTERBOXD, 4.1, url = "https://letterboxd.com/film/jaws/"),
                            MetaExternalRating(PROVIDER_MAL, 8.3, url = "https://myanimelist.net/anime/21"),
                            MetaExternalRating(PROVIDER_TOMATOES, 97.0, url = "https://www.rottentomatoes.com/m/jaws"),
                            MetaExternalRating(PROVIDER_METACRITIC, 87.0, url = "https://www.metacritic.com/movie/jaws"),
                        ),
                    ),
                    isMdbListActive = true,
                    onOpenExternalUrl = { openedUrl = it },
                )
            }
        }

        expectedPages.forEach { (label, page) ->
            openedUrl = null
            compose.onNodeWithText(label).performScrollTo().performClick()
            assertEquals(page, openedUrl, "clicking $label")
        }
    }

    @Test
    fun imdbTitleUrlUsesExplicitIdThenContentId() {
        assertEquals(
            "https://www.imdb.com/title/tt0137523/",
            MetaDetails(id = "tt0137523", type = "movie", name = "Movie").imdbTitleUrl(),
        )
        assertEquals(
            "https://www.imdb.com/title/tt0137523/",
            MetaDetails(id = "tmdb:550", type = "movie", name = "Movie", imdbId = "tt0137523").imdbTitleUrl(),
        )
        assertEquals(
            "https://www.imdb.com/title/tt1234567/",
            MetaDetails(id = "tt1234567:1:1", type = "series", name = "Episode").imdbTitleUrl(),
        )
        assertNull(MetaDetails(id = "tmdb:550", type = "movie", name = "Movie").imdbTitleUrl())
        assertNull(MetaDetails(id = "mal:123", type = "series", name = "Anime").imdbTitleUrl())
    }
}
