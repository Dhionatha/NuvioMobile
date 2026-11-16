package com.nuvio.app.features.mdblist

import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_AUDIENCE
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_IMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_LETTERBOXD
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_MAL
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_METACRITIC
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TMDB
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TOMATOES
import com.nuvio.app.features.mdblist.MdbListMetadataService.PROVIDER_TRAKT

/**
 * Builds the provider page URL for a single MDBList rating.
 *
 * MDBList only ships a relative path for providers that use slugs (Rotten Tomatoes, Metacritic,
 * Letterboxd); the remaining pages are reconstructed from the media `ids`. Returns `null` when the
 * payload lacks the identifiers needed for a working page, so the rating stays non-interactive.
 */
internal object MdbListRatingLinks {
    /**
     * @param mediaType MDBList media type (`movie`/`show`), or the app type (`series`).
     * @param rawUrl `ratings[].url` as sent by MDBList. It may be a relative path, an absolute URL
     * or an unrelated numeric value, so every case is validated here.
     */
    fun resolve(
        source: String,
        ids: Map<String, String>,
        mediaType: String?,
        rawUrl: String?,
    ): String? {
        val trimmedUrl = rawUrl?.trim()?.takeIf { it.isNotEmpty() }
        val absoluteUrl = trimmedUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val path = trimmedUrl
            ?.takeUnless { it.all(Char::isDigit) }
            ?.let { if (it.startsWith("/")) it else "/$it" }
        val isShow = mediaType.equals("show", ignoreCase = true) ||
            mediaType.equals("series", ignoreCase = true)
        fun id(key: String): String? = ids[key]?.trim()?.takeIf { it.isNotEmpty() }

        return when (source) {
            PROVIDER_IMDB -> absoluteUrl
                ?: id("imdb")?.let { "https://www.imdb.com/title/$it/" }
            PROVIDER_TMDB -> absoluteUrl
                ?: id("tmdb")?.let { "https://www.themoviedb.org/${if (isShow) "tv" else "movie"}/$it" }
            PROVIDER_TRAKT -> absoluteUrl
                ?: (id("trakt") ?: id("imdb"))?.let { "https://trakt.tv/${if (isShow) "shows" else "movies"}/$it" }
            PROVIDER_TOMATOES, PROVIDER_AUDIENCE -> absoluteUrl
                ?: path?.let { "https://www.rottentomatoes.com$it" }
            PROVIDER_METACRITIC -> absoluteUrl ?: path?.let { slug ->
                if (slug.startsWith("/movie/") || slug.startsWith("/tv/")) {
                    "https://www.metacritic.com$slug"
                } else {
                    "https://www.metacritic.com${if (isShow) "/tv" else "/movie"}$slug"
                }
            }
            PROVIDER_LETTERBOXD -> absoluteUrl
                ?: path?.let { "https://letterboxd.com$it" }
            PROVIDER_MAL -> absoluteUrl
                ?: trimmedUrl?.takeIf { it.all(Char::isDigit) }?.let { "https://myanimelist.net/anime/$it" }
                ?: id("mal")?.let { "https://myanimelist.net/anime/$it" }
            else -> null
        }
    }
}
