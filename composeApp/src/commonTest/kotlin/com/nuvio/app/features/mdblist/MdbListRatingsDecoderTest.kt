package com.nuvio.app.features.mdblist

import com.nuvio.app.features.details.MetaExternalRating
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MdbListRatingsDecoderTest {
    @Test
    fun invalidValuesAndUnknownSourcesAreSkippedWhileZeroAndNativeScalesArePreserved() {
        val ratings = parseMdbListRatings(
            """{"ratings":[
                {"source":"imdb","value":null},
                {"source":"imdb","value":11},
                {"source":"imdb","value":0,"score":99},
                {"source":"tmdb","value":101},
                {"source":"tmdb","value":76,"score":90},
                {"source":"trakt","value":-1},
                {"source":"trakt","value":80},
                {"source":"letterboxd","value":5.1},
                {"source":"letterboxd","value":-1,"score":82},
                {"source":"letterboxd","value":4.1,"score":82},
                {"source":"myanimelist","value":10.1},
                {"source":"myanimelist","value":8.3},
                {"source":"metacritic","score":75},
                {"source":"metacriticuser","value":8.5}
            ]}""",
        )

        assertEquals(listOf(
            MetaExternalRating("imdb", 0.0),
            MetaExternalRating("tmdb", 76.0),
            MetaExternalRating("trakt", 80.0),
            MetaExternalRating("letterboxd", 4.1),
            MetaExternalRating("mal", 8.3),
        ), ratings)
    }

    @Test
    fun responseIdsSupportCurrentAndLegacyFormatsWithoutUsingTheNumericMediaId() {
        val ratings = parseMdbListRatingsBatch(
            """[
                {"imdb_id":"tt1","ratings":[{"source":"imdb","value":1}]},
                {"imdbid":"tt2","ratings":[{"source":"imdb","value":2}]},
                {"ids":{"imdb":"tt3","tmdb":3,"mal":null},"ratings":[{"source":"imdb","value":3}]},
                {"id":4,"ratings":[{"source":"imdb","value":4}]}
            ]""",
        )

        assertEquals(mapOf(
            "tt1" to listOf(MetaExternalRating("imdb", 1.0, url = "https://www.imdb.com/title/tt1/")),
            "tt2" to listOf(MetaExternalRating("imdb", 2.0, url = "https://www.imdb.com/title/tt2/")),
            "tt3" to listOf(MetaExternalRating("imdb", 3.0, url = "https://www.imdb.com/title/tt3/")),
        ), ratings)
    }

    @Test
    fun sourceAliasesDoNotProduceDuplicateRatings() {
        val ratings = parseMdbListRatings(
            """{"ratings":[
                {"source":"popcorn","value":81},
                {"source":"tomatoesaudience","value":81},
                {"source":"audience","value":81},
                {"source":"mal","value":8.0},
                {"source":"myanimelist","value":8.0}
            ]}""",
        )

        assertEquals(listOf(MetaExternalRating("audience", 81.0), MetaExternalRating("mal", 8.0)), ratings)
    }

    @Test
    fun letterboxdUsesItsFivePointScaleForBothMediaResponseFormats() {
        for ((value, score, expected) in listOf(Triple(8.0, 80, 4.0), Triple(2.4, 24, 1.2), Triple(4.0, 80, 4.0))) {
            val ratings = parseMdbListRatings(
                """{"ratings":[{"source":"letterboxd","value":$value,"score":$score}]}""",
            )
            assertEquals(listOf(MetaExternalRating("letterboxd", expected)), ratings)
        }
    }

    @Test
    fun liveSingleAndBatchLetterboxdFormatsProduceTheSameRating() {
        val single = parseMdbListRatings(
            """{"ids":{"imdb":"tt0073195"},"ratings":[{"source":"letterboxd","value":3.9,"score":78}]}""",
        )
        val batch = parseMdbListRatingsBatch(
            """[{"ids":{"imdb":"tt0073195"},"ratings":[{"source":"letterboxd","value":7.8,"score":78}]}]""",
        )

        assertEquals(listOf(MetaExternalRating("letterboxd", 3.9)), single)
        assertEquals(single, batch.getValue("tt0073195"))
    }

    @Test
    fun movieRatingsCarryEveryProviderPage() {
        val ratings = parseMdbListRatings(
            """{
                "type":"movie",
                "ids":{"imdb":"tt0073195","trakt":457,"tmdb":578,"mal":null},
                "ratings":[
                    {"source":"imdb","value":8.1,"score":81,"url":99},
                    {"source":"metacritic","value":87,"url":"/jaws"},
                    {"source":"trakt","value":78,"url":null},
                    {"source":"tomatoes","value":97,"url":"/m/jaws"},
                    {"source":"tmdb","value":76,"url":null},
                    {"source":"letterboxd","value":8,"score":80,"url":"/film/jaws/"},
                    {"source":"popcorn","value":93,"url":null}
                ]
            }""",
        ).associateBy { it.source }

        assertEquals("https://www.imdb.com/title/tt0073195/", ratings.getValue("imdb").url)
        assertEquals("https://www.metacritic.com/movie/jaws", ratings.getValue("metacritic").url)
        assertEquals("https://trakt.tv/movies/457", ratings.getValue("trakt").url)
        assertEquals("https://www.rottentomatoes.com/m/jaws", ratings.getValue("tomatoes").url)
        assertEquals("https://www.themoviedb.org/movie/578", ratings.getValue("tmdb").url)
        assertEquals("https://letterboxd.com/film/jaws/", ratings.getValue("letterboxd").url)
        // The audience score lives on the same Rotten Tomatoes page as the critics score.
        assertEquals("https://www.rottentomatoes.com/m/jaws", ratings.getValue("audience").url)
    }

    @Test
    fun showRatingsUseShowRoutesAndThePayloadType() {
        val ratings = parseMdbListRatings(
            """{
                "type":"show",
                "ids":{"imdb":"tt0903747","trakt":1,"tmdb":1396,"mal":21},
                "ratings":[
                    {"source":"tmdb","value":78},
                    {"source":"trakt","value":80},
                    {"source":"metacritic","value":85,"url":"/breaking-bad"},
                    {"source":"tomatoes","value":90,"url":"/tv/breaking_bad"},
                    {"source":"myanimelist","value":8.9}
                ]
            }""",
        ).associateBy { it.source }

        assertEquals("https://www.themoviedb.org/tv/1396", ratings.getValue("tmdb").url)
        assertEquals("https://trakt.tv/shows/1", ratings.getValue("trakt").url)
        assertEquals("https://www.metacritic.com/tv/breaking-bad", ratings.getValue("metacritic").url)
        assertEquals("https://www.rottentomatoes.com/tv/breaking_bad", ratings.getValue("tomatoes").url)
        assertEquals("https://myanimelist.net/anime/21", ratings.getValue("mal").url)
    }

    @Test
    fun explicitMediaTypeOverridesThePayloadType() {
        val rating = parseMdbListRatings(
            """{"type":"movie","ids":{"tmdb":1396},"ratings":[{"source":"tmdb","value":78}]}""",
            mediaType = "show",
        ).single()

        assertEquals("https://www.themoviedb.org/tv/1396", rating.url)
    }

    @Test
    fun numericMalUrlAndIdBecomeAnAnimePage() {
        val fromUrl = parseMdbListRatings(
            """{"ratings":[{"source":"myanimelist","value":8.9,"url":21}]}""",
        )
        val fromIds = parseMdbListRatings(
            """{"ids":{"mal":21},"ratings":[{"source":"myanimelist","value":8.9}]}""",
        )

        assertEquals(listOf(MetaExternalRating("mal", 8.9, url = "https://myanimelist.net/anime/21")), fromUrl)
        assertEquals(fromUrl, fromIds)
    }

    @Test
    fun absoluteProviderUrlsAreKeptAsIs() {
        val rating = parseMdbListRatings(
            """{"ids":{"imdb":"tt0073195"},"ratings":[
                {"source":"tomatoes","value":97,"url":"https://www.rottentomatoes.com/m/jaws"}
            ]}""",
        ).single()

        assertEquals("https://www.rottentomatoes.com/m/jaws", rating.url)
    }

    @Test
    fun ratingsWithoutEnoughIdentifiersStayNonInteractive() {
        val ratings = parseMdbListRatings(
            """{"ratings":[
                {"source":"tmdb","value":76},
                {"source":"metacritic","value":87,"url":99},
                {"source":"tomatoes","value":97,"url":99}
            ]}""",
        ).associateBy { it.source }

        assertNull(ratings.getValue("tmdb").url)
        assertNull(ratings.getValue("metacritic").url)
        assertNull(ratings.getValue("tomatoes").url)
    }

    @Test
    fun batchResponsesCarryProviderUrlsForEveryItem() {
        val ratings = parseMdbListRatingsBatch(
            """[
                {"ids":{"imdb":"tt1","tmdb":1},"ratings":[{"source":"tmdb","value":70}]},
                {"ids":{"imdb":"tt2","tmdb":2},"ratings":[{"source":"tmdb","value":80}]}
            ]""",
            mediaType = "movie",
        )

        assertEquals("https://www.themoviedb.org/movie/1", ratings.getValue("tt1").single().url)
        assertEquals("https://www.themoviedb.org/movie/2", ratings.getValue("tt2").single().url)
    }
}
