package com.nuvio.app.features.details

/**
 * Orders providers so the ones offering [sort] come first, keeping the TMDB display order
 * for the remaining ones and between providers with the same offer type.
 */
internal fun sortWatchProviders(
    providers: List<MetaWatchProvider>,
    sort: WatchProviderAccessType?,
): List<MetaWatchProvider> = if (sort == null) {
    providers
} else {
    providers.sortedByDescending { provider -> sort in provider.accessTypes }
}
