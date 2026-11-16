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
    sequenceOf(imdbId, this.id)
        .mapNotNull { candidate -> candidate?.let { ImdbTitleIdPattern.find(it)?.value } }
        .firstOrNull()
        ?.let { titleId -> "https://www.imdb.com/title/$titleId/" }
