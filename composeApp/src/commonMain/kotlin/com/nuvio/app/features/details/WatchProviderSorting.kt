package com.nuvio.app.features.details

/**
 * Acquisition category used to group streaming providers in the detail screen.
 *
 * The declaration order is the default priority shown in settings: subscription before
 * ad-supported subscription, then rent, buy and the free tiers.
 */
enum class WatchProviderOfferCategory {
    SUBSCRIPTION,
    SUBSCRIPTION_WITH_ADS,
    RENT,
    BUY,
    FREE,
    FREE_WITH_ADS,
    ;

    companion object {
        val defaultOrder: List<WatchProviderOfferCategory> = entries.toList()

        fun parse(raw: String?): WatchProviderOfferCategory? =
            raw?.let { value -> entries.firstOrNull { it.name == value } }

        /** Fills in missing categories and drops duplicates, keeping the given order. */
        fun normalize(order: List<WatchProviderOfferCategory>): List<WatchProviderOfferCategory> {
            val unique = order.distinct()
            return unique + defaultOrder.filterNot { it in unique }
        }

        /** Maps the single-type preference stored by older builds onto a category. */
        fun fromLegacySort(raw: String?): WatchProviderOfferCategory? = when (raw) {
            "FREE" -> FREE
            "ADS" -> FREE_WITH_ADS
            "SUBSCRIPTION" -> SUBSCRIPTION
            "RENT" -> RENT
            "BUY" -> BUY
            else -> null
        }
    }
}

/** Label rendered inside the offer capsule of a provider. */
enum class WatchProviderOfferLabel {
    SUBSCRIPTION,
    SUBSCRIPTION_WITH_ADS,
    RENT,
    BUY,
    FREE,
    FREE_WITH_ADS,
    ADS_ONLY,
}

/**
 * Labels for the provider capsule. A provider that offers subscription and the ad-supported
 * subscription at the same time shows both, e.g. "Assinatura | Assinatura com anúncios".
 */
internal fun watchProviderOfferLabels(
    accessTypes: List<WatchProviderAccessType>,
): List<WatchProviderOfferLabel> {
    val labels = mutableListOf<WatchProviderOfferLabel>()
    val hasSubscription = WatchProviderAccessType.SUBSCRIPTION in accessTypes
    val hasAds = WatchProviderAccessType.ADS in accessTypes
    val hasFree = WatchProviderAccessType.FREE in accessTypes

    if (hasSubscription) {
        labels += WatchProviderOfferLabel.SUBSCRIPTION
        if (hasAds) labels += WatchProviderOfferLabel.SUBSCRIPTION_WITH_ADS
    }
    if (WatchProviderAccessType.RENT in accessTypes) labels += WatchProviderOfferLabel.RENT
    if (WatchProviderAccessType.BUY in accessTypes) labels += WatchProviderOfferLabel.BUY
    if (hasFree) {
        labels += WatchProviderOfferLabel.FREE
        if (hasAds) labels += WatchProviderOfferLabel.FREE_WITH_ADS
    }
    if (labels.isEmpty() && hasAds) {
        // TMDB does not tell whether an ads-only provider is free or paid.
        labels += WatchProviderOfferLabel.ADS_ONLY
    }
    return labels
}

/**
 * Category a provider belongs to: the first category it offers in the user's configured order.
 * Reordering the categories in settings therefore also moves providers that offer several types.
 */
internal fun watchProviderCategory(
    provider: MetaWatchProvider,
    order: List<WatchProviderOfferCategory>,
): WatchProviderOfferCategory? = order.firstOrNull { category ->
    category.matches(provider.accessTypes)
}

/**
 * Stable sort by the user's category order. Providers without a matching category keep their
 * TMDB order and are placed at the end.
 */
internal fun sortWatchProviders(
    providers: List<MetaWatchProvider>,
    order: List<WatchProviderOfferCategory>,
): List<MetaWatchProvider> {
    val normalizedOrder = WatchProviderOfferCategory.normalize(order)
    val rankByCategory = normalizedOrder.withIndex().associate { (index, category) -> category to index }
    return providers.sortedBy { provider ->
        watchProviderCategory(provider, normalizedOrder)
            ?.let(rankByCategory::getValue)
            ?: Int.MAX_VALUE
    }
}

private fun WatchProviderOfferCategory.matches(
    accessTypes: List<WatchProviderAccessType>,
): Boolean = when (this) {
    WatchProviderOfferCategory.SUBSCRIPTION ->
        WatchProviderAccessType.SUBSCRIPTION in accessTypes
    WatchProviderOfferCategory.SUBSCRIPTION_WITH_ADS ->
        WatchProviderAccessType.SUBSCRIPTION in accessTypes &&
            WatchProviderAccessType.ADS in accessTypes
    WatchProviderOfferCategory.RENT ->
        WatchProviderAccessType.RENT in accessTypes
    WatchProviderOfferCategory.BUY ->
        WatchProviderAccessType.BUY in accessTypes
    WatchProviderOfferCategory.FREE ->
        WatchProviderAccessType.FREE in accessTypes
    WatchProviderOfferCategory.FREE_WITH_ADS ->
        WatchProviderAccessType.ADS in accessTypes &&
            WatchProviderAccessType.SUBSCRIPTION !in accessTypes
}
