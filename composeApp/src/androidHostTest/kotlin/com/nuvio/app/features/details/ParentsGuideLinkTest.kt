package com.nuvio.app.features.details

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.NuvioTheme
import com.nuvio.app.features.details.components.DetailMetaInfo
import com.nuvio.app.features.details.components.TabletDetailHero
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w720dp-h1280dp")
class ParentsGuideLinkTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun overviewButtonOpensParentsGuideNextToTheCertification() {
        var openedUrl: String? = null
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(
                        id = "tt1234567",
                        type = "movie",
                        name = "Movie",
                        ageRating = "PG-13",
                    ),
                    onOpenExternalUrl = { openedUrl = it },
                )
            }
        }

        compose.onNodeWithText("Parents Guide", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Open IMDb Parents Guide")
            .assertIsDisplayed()
            .performClick()

        assertEquals("https://www.imdb.com/title/tt1234567/parentalguide/", openedUrl)
    }

    @Test
    fun buttonUsesExplicitImdbIdOverTmdbContentId() {
        var openedUrl: String? = null
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(
                        id = "tmdb:550",
                        type = "movie",
                        name = "Movie",
                        imdbId = "tt0137523",
                        ageRating = "R",
                    ),
                    onOpenExternalUrl = { openedUrl = it },
                )
            }
        }

        compose.onNodeWithContentDescription("Open IMDb Parents Guide").performClick()

        assertEquals("https://www.imdb.com/title/tt0137523/parentalguide/", openedUrl)
    }

    @Test
    fun buttonIsHiddenWhenTheSettingIsDisabled() {
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(id = "tt1234567", type = "movie", name = "Movie", ageRating = "PG-13"),
                    showParentsGuide = false,
                    onOpenExternalUrl = { error("must not open while the setting is off") },
                )
            }
        }

        compose.onNodeWithContentDescription("Open IMDb Parents Guide").assertDoesNotExist()
        compose.onNodeWithText("Parents Guide", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun buttonIsHiddenWithoutAnImdbId() {
        compose.setContent {
            NuvioTheme {
                DetailMetaInfo(
                    meta = MetaDetails(id = "tmdb:550", type = "movie", name = "Movie", ageRating = "PG-13"),
                    onOpenExternalUrl = { error("must not open without an IMDb id") },
                )
            }
        }

        compose.onNodeWithContentDescription("Open IMDb Parents Guide").assertDoesNotExist()
        compose.onNodeWithText("Parents Guide", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tabletHeroButtonOpensParentsGuide() {
        var openedUrl: String? = null
        compose.setContent {
            NuvioTheme {
                TabletDetailHero(
                    meta = MetaDetails(
                        id = "tt1234567",
                        type = "movie",
                        name = "Movie",
                        ageRating = "PG-13",
                    ),
                    showOverview = true,
                    showOverallRatings = true,
                    showParentsGuide = true,
                    isMdbListActive = false,
                    horizontalPadding = 16.dp,
                    heroTrailerSourceUrl = null,
                    heroTrailerReady = false,
                    heroTrailerMuted = true,
                    onHeroTrailerMuteToggle = {},
                    onHeightChanged = {},
                    actions = null,
                    onOpenExternalUrl = { openedUrl = it },
                )
            }
        }

        compose.onNodeWithText("Parents Guide", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Open IMDb Parents Guide")
            .assertIsDisplayed()
            .performClick()

        assertEquals("https://www.imdb.com/title/tt1234567/parentalguide/", openedUrl)
    }

    @Test
    fun parentsGuideUrlUsesExplicitIdThenContentId() {
        assertEquals(
            "https://www.imdb.com/title/tt0137523/parentalguide/",
            MetaDetails(id = "tt0137523", type = "movie", name = "Movie").imdbParentsGuideUrl(),
        )
        assertEquals(
            "https://www.imdb.com/title/tt0137523/parentalguide/",
            MetaDetails(id = "tmdb:550", type = "movie", name = "Movie", imdbId = "tt0137523")
                .imdbParentsGuideUrl(),
        )
        assertEquals(
            "https://www.imdb.com/title/tt1234567/parentalguide/",
            MetaDetails(id = "tt1234567:1:1", type = "series", name = "Episode").imdbParentsGuideUrl(),
        )
        assertNull(MetaDetails(id = "tmdb:550", type = "movie", name = "Movie").imdbParentsGuideUrl())
        assertNull(MetaDetails(id = "mal:123", type = "series", name = "Anime").imdbParentsGuideUrl())
    }
}
