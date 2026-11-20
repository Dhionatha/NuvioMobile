package com.nuvio.app.features.tmdb

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal sealed interface EpisodeGuideResolution {
    data class Available(val imdbId: String) : EpisodeGuideResolution
    data object Unavailable : EpisodeGuideResolution
    data object Failed : EpisodeGuideResolution
}

object TmdbService {
    private val log = Logger.withTag("TmdbService")
    private val json = Json { ignoreUnknownKeys = true }
    private val imdbToTmdbCache = linkedMapOf<String, String>()
    private val tmdbToImdbCache = linkedMapOf<String, String>()
    private val episodeImdbCache = linkedMapOf<String, String?>()
    private val cacheMutex = Mutex()

    suspend fun ensureTmdbId(videoId: String, mediaType: String, fallbackImdbId: String? = null): String? {
        val apiKey = TmdbSettingsRepository.effectiveApiKey()

        val normalized = videoId
            .removePrefix("tmdb:")
            .removePrefix("movie:")
            .removePrefix("series:")
            .substringBefore(':')
            .substringBefore('/')
            .trim()

        if (normalized.isBlank()) return null
        if (normalized.all(Char::isDigit)) return normalized
        if (normalized.startsWith("tt", ignoreCase = true)) {
            return imdbToTmdb(imdbId = normalized, mediaType = mediaType, apiKey = apiKey)
        }

        // Fallback: use the IMDB ID supplied by the addon's meta response
        val normalizedFallback = fallbackImdbId
            ?.trim()
            ?.substringBefore(':')
            ?.takeIf { it.startsWith("tt", ignoreCase = true) }
        if (normalizedFallback != null) {
            return imdbToTmdb(imdbId = normalizedFallback, mediaType = mediaType, apiKey = apiKey)
        }

        return null
    }

    suspend fun tmdbToImdb(tmdbId: Int, mediaType: String): String? {
        val apiKey = TmdbSettingsRepository.effectiveApiKey()

        val cacheKey = "$tmdbId:${normalizeMediaType(mediaType)}"
        cacheMutex.withLock {
            tmdbToImdbCache[cacheKey]?.let { return it }
        }

        val endpoint = when (normalizeMediaType(mediaType)) {
            "tv" -> "tv/$tmdbId/external_ids"
            else -> "movie/$tmdbId/external_ids"
        }
        val body = fetch<TmdbExternalIdsResponse>(endpoint = endpoint, apiKey = apiKey) ?: return null
        val imdbId = body.imdbId?.trim()?.takeIf(String::isNotBlank) ?: return null

        cacheMutex.withLock {
            tmdbToImdbCache[cacheKey] = imdbId
            imdbToTmdbCache["$imdbId:${normalizeMediaType(mediaType)}"] = tmdbId.toString()
        }
        return imdbId
    }

    /**
     * Resolves the IMDb id of a single episode, for the episode's Parents Guide shortcut.
     *
     * The lookup only happens when the caller explicitly asks for it (a user tap): there is no
     * bulk TMDB endpoint that exposes `external_ids` for a whole season. The result is validated
     * against the episode the app is showing, and anything that cannot be confirmed fails closed.
     */
    internal suspend fun resolveEpisodeImdbId(
        seriesVideoId: String,
        mediaType: String,
        fallbackImdbId: String?,
        seasonNumber: Int,
        episodeNumber: Int,
        expectedTitle: String?,
        expectedAirDate: String?,
        language: String,
    ): EpisodeGuideResolution {
        val apiKey = TmdbSettingsRepository.effectiveApiKey().trim()
        if (apiKey.isBlank()) return EpisodeGuideResolution.Unavailable

        val seriesTmdbId = when (
            val resolution = resolveSeriesTmdbId(
                seriesVideoId = seriesVideoId,
                mediaType = mediaType,
                fallbackImdbId = fallbackImdbId,
                apiKey = apiKey,
            )
        ) {
            is TmdbIdResolution.Available -> resolution.id
            TmdbIdResolution.Unavailable -> return EpisodeGuideResolution.Unavailable
            TmdbIdResolution.Failed -> return EpisodeGuideResolution.Failed
        }

        val cacheKey = "$seriesTmdbId:$seasonNumber:$episodeNumber"
        cacheMutex.withLock {
            if (episodeImdbCache.containsKey(cacheKey)) {
                return episodeImdbCache[cacheKey]
                    ?.let(EpisodeGuideResolution::Available)
                    ?: EpisodeGuideResolution.Unavailable
            }
        }

        val outcome = fetchOutcome<TmdbEpisodeDetailsResponse>(
            endpoint = "tv/$seriesTmdbId/season/$seasonNumber/episode/$episodeNumber",
            apiKey = apiKey,
            query = mapOf(
                "append_to_response" to "external_ids",
                "language" to normalizeTmdbLanguage(language),
            ),
        )
        if (outcome is FetchOutcome.Failure) return EpisodeGuideResolution.Failed
        val episode = (outcome as FetchOutcome.Success).value

        val imdbId = episode.externalIds?.imdbId
            ?.trim()
            ?.takeIf { ImdbEpisodeIdPattern.matches(it) }
        val resolution = if (
            imdbId != null &&
            episodeIdentityMatches(
                expectedTitle = expectedTitle,
                expectedAirDate = expectedAirDate,
                actualTitle = episode.name,
                actualAirDate = episode.airDate,
            )
        ) {
            EpisodeGuideResolution.Available(imdbId)
        } else {
            EpisodeGuideResolution.Unavailable
        }

        cacheMutex.withLock {
            episodeImdbCache.putBounded(
                key = cacheKey,
                value = (resolution as? EpisodeGuideResolution.Available)?.imdbId,
            )
        }
        return resolution
    }

    private suspend fun imdbToTmdb(imdbId: String, mediaType: String, apiKey: String): String? =
        when (val resolution = imdbToTmdbResolved(imdbId = imdbId, mediaType = mediaType, apiKey = apiKey)) {
            is TmdbIdResolution.Available -> resolution.id
            TmdbIdResolution.Unavailable, TmdbIdResolution.Failed -> null
        }

    private suspend fun imdbToTmdbResolved(
        imdbId: String,
        mediaType: String,
        apiKey: String,
    ): TmdbIdResolution {
        val normalizedType = normalizeMediaType(mediaType)
        val cacheKey = "$imdbId:$normalizedType"
        cacheMutex.withLock {
            imdbToTmdbCache[cacheKey]?.let { return TmdbIdResolution.Available(it) }
        }

        return when (
            val outcome = fetchOutcome<TmdbFindResponse>(
                endpoint = "find/$imdbId",
                apiKey = apiKey,
                query = mapOf("external_source" to "imdb_id"),
            )
        ) {
            FetchOutcome.Failure -> TmdbIdResolution.Failed
            is FetchOutcome.Success -> {
                val resultId = when (normalizedType) {
                    "movie" -> outcome.value.movieResults.firstOrNull()?.id
                    "tv" -> outcome.value.tvResults.firstOrNull()?.id
                    else -> outcome.value.movieResults.firstOrNull()?.id
                        ?: outcome.value.tvResults.firstOrNull()?.id
                }?.takeIf { it > 0 }

                if (resultId == null) {
                    log.d { "No TMDB ID found for $imdbId ($normalizedType)" }
                    TmdbIdResolution.Unavailable
                } else {
                    cacheMutex.withLock {
                        imdbToTmdbCache[cacheKey] = resultId.toString()
                        tmdbToImdbCache["$resultId:$normalizedType"] = imdbId
                    }
                    TmdbIdResolution.Available(resultId.toString())
                }
            }
        }
    }

    private suspend fun resolveSeriesTmdbId(
        seriesVideoId: String,
        mediaType: String,
        fallbackImdbId: String?,
        apiKey: String,
    ): TmdbIdResolution {
        val normalized = seriesVideoId
            .removePrefix("tmdb:")
            .removePrefix("movie:")
            .removePrefix("series:")
            .substringBefore(':')
            .substringBefore('/')
            .trim()

        if (normalized.isBlank()) return TmdbIdResolution.Unavailable
        if (normalized.all(Char::isDigit)) return TmdbIdResolution.Available(normalized)

        val imdbId = normalized
            .takeIf { it.startsWith("tt", ignoreCase = true) }
            ?: fallbackImdbId
                ?.trim()
                ?.substringBefore(':')
                ?.takeIf { it.startsWith("tt", ignoreCase = true) }
            ?: return TmdbIdResolution.Unavailable

        return imdbToTmdbResolved(imdbId = imdbId, mediaType = mediaType, apiKey = apiKey)
    }

    private suspend inline fun <reified T> fetch(
        endpoint: String,
        apiKey: String,
        query: Map<String, String> = emptyMap(),
    ): T? {
        val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey, query = query)
        return try {
            json.decodeFromString<T>(httpGetText(url))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.w { "TMDB request failed for $endpoint: ${error.message}" }
            null
        }
    }

    private suspend inline fun <reified T> fetchOutcome(
        endpoint: String,
        apiKey: String,
        query: Map<String, String> = emptyMap(),
    ): FetchOutcome<T> {
        val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey, query = query)
        return try {
            FetchOutcome.Success(json.decodeFromString<T>(httpGetText(url)))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            log.w { "TMDB request failed for $endpoint: ${error.message}" }
            FetchOutcome.Failure
        }
    }

    internal fun normalizeMediaType(mediaType: String): String =
        when (mediaType.trim().lowercase()) {
            "movie", "film" -> "movie"
            "tv", "series", "show", "tvshow" -> "tv"
            else -> mediaType.trim().lowercase()
        }
}

private sealed interface TmdbIdResolution {
    data class Available(val id: String) : TmdbIdResolution
    data object Unavailable : TmdbIdResolution
    data object Failed : TmdbIdResolution
}

private sealed interface FetchOutcome<out T> {
    data class Success<T>(val value: T) : FetchOutcome<T>
    data object Failure : FetchOutcome<Nothing>
}

private val ImdbEpisodeIdPattern = Regex("tt\\d+")
private val IsoAirDatePattern = Regex("\\d{4}-\\d{2}-\\d{2}")
private val GenericEpisodeTitlePatterns = listOf(
    Regex("^(episode|ep|e)$"),
    Regex("^(episode|ep|e)\\s*\\d+$"),
    Regex("^\\d+$"),
    Regex("^tba$"),
    Regex("^tbd$"),
    Regex("^unknown$"),
)

/**
 * Compares the episode shown by the app with the episode returned by TMDB.
 *
 * An exact air-date match wins. When at least one side has no usable date, the normalized titles
 * must match and neither may be a placeholder such as `Episode 2` or `TBA`. Conflicting dates are
 * always rejected; a mismatch must not fall back to a title that happens to be equal.
 */
internal fun episodeIdentityMatches(
    expectedTitle: String?,
    expectedAirDate: String?,
    actualTitle: String?,
    actualAirDate: String?,
): Boolean {
    val expectedDate = normalizeIsoAirDate(expectedAirDate)
    val actualDate = normalizeIsoAirDate(actualAirDate)
    if (expectedDate != null && actualDate != null) {
        return expectedDate == actualDate
    }

    val expected = normalizeEpisodeTitle(expectedTitle)
    val actual = normalizeEpisodeTitle(actualTitle)
    if (expected.isBlank() || actual.isBlank()) return false
    if (isGenericEpisodeTitle(expected) || isGenericEpisodeTitle(actual)) return false
    return expected == actual
}

internal fun normalizeIsoAirDate(value: String?): String? =
    value
        ?.trim()
        ?.substringBefore('T')
        ?.takeIf { IsoAirDatePattern.matches(it) }

internal fun normalizeEpisodeTitle(value: String?): String =
    value
        .orEmpty()
        .lowercase()
        .map { character -> if (character.isLetterOrDigit()) character else ' ' }
        .joinToString("")
        .split(' ')
        .filter(String::isNotEmpty)
        .joinToString(" ")

internal fun isGenericEpisodeTitle(value: String?): Boolean {
    val normalized = normalizeEpisodeTitle(value)
    if (normalized.isBlank()) return true
    return GenericEpisodeTitlePatterns.any { pattern -> pattern.matches(normalized) }
}

private fun LinkedHashMap<String, String?>.putBounded(key: String, value: String?) {
    remove(key)
    this[key] = value
    while (size > MaxCachedEntries) {
        val oldestKey = keys.firstOrNull() ?: break
        remove(oldestKey)
    }
}

private const val MaxCachedEntries = 256

internal fun buildTmdbUrl(
    endpoint: String,
    apiKey: String,
    query: Map<String, String> = emptyMap(),
): String {
    val params = linkedMapOf("api_key" to apiKey)
    query.forEach { (key, value) ->
        if (value.isNotBlank()) {
            params[key] = value
        }
    }
    return buildString {
        append("https://api.themoviedb.org/3/")
        append(endpoint.removePrefix("/"))
        if (params.isNotEmpty()) {
            append("?")
            append(params.entries.joinToString("&") { (key, value) -> "$key=$value" })
        }
    }
}

@Serializable
private data class TmdbFindResponse(
    @SerialName("movie_results") val movieResults: List<TmdbExternalResult> = emptyList(),
    @SerialName("tv_results") val tvResults: List<TmdbExternalResult> = emptyList(),
)

@Serializable
private data class TmdbExternalResult(
    val id: Int,
)

@Serializable
private data class TmdbExternalIdsResponse(
    @SerialName("imdb_id") val imdbId: String? = null,
)

@Serializable
private data class TmdbEpisodeDetailsResponse(
    val name: String? = null,
    @SerialName("air_date") val airDate: String? = null,
    @SerialName("external_ids") val externalIds: TmdbExternalIdsResponse? = null,
)
