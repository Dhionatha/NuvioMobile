package com.nuvio.app.features.details

private val ImdbTitleIdPattern = Regex("tt\\d+")

/**
 * Canonical IMDb title page URL for this meta.
 *
 * Prefers the explicit [MetaDetails.imdbId] reported by the metadata provider and falls back to a
 * Stremio-style `tt...` content id. Returns `null` when no IMDb title id is available, so callers can
 * keep the rating non-interactive instead of opening a broken page.
 */
internal fun MetaDetails.imdbTitleUrl(): String? =
    imdbTitleId()?.let { titleId -> "https://www.imdb.com/title/$titleId/" }

/**
 * IMDb Parents Guide URL for this meta, following the same title id resolution as [imdbTitleUrl].
 *
 * Returns `null` when no IMDb title id is available, so the details screen can hide the shortcut
 * instead of opening a broken page.
 */
internal fun MetaDetails.imdbParentsGuideUrl(): String? =
    imdbTitleId()?.let(::imdbParentsGuideUrl)

/** IMDb Parents Guide URL for a resolved IMDb title id, or `null` when [tconst] is not one. */
internal fun imdbParentsGuideUrl(tconst: String): String? =
    ImdbTitleIdPattern.find(tconst)?.value?.let { titleId ->
        "https://www.imdb.com/title/$titleId/parentalguide/"
    }

private fun MetaDetails.imdbTitleId(): String? =
    sequenceOf(imdbId, id)
        .mapNotNull { candidate -> candidate?.let { ImdbTitleIdPattern.find(it)?.value } }
        .firstOrNull()
