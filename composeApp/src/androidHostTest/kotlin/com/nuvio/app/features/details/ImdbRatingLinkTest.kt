package com.nuvio.app.features.details

import android.app.Application
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.details.components.DetailMetaInfo
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_IMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TMDB
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ImdbRatingLinkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun overviewRatingOpensImdbTitlePage() {
        var openedUrl: String? = null
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(id = "tt1234567", type = "movie", name = "Movie", imdbRating = "7.8"),
                    onOpenImdb = { openedUrl = it },
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
                    onOpenImdb = { openedUrl = it },
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
                    onOpenImdb = { error("must not open without an IMDb id") },
                )
            }
        }

        compose.onNodeWithText("8.8").assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun mdbListImdbChipOpensImdbTitlePage() {
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
                    onOpenImdb = { openedUrl = it },
                )
            }
        }

        compose.onNodeWithText("65").assertHasNoClickAction()
        compose.onNodeWithText("7.8").assertIsDisplayed().performClick()

        assertEquals("https://www.imdb.com/title/tt1234567/", openedUrl)
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
