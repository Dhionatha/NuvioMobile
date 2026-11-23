package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchProviderSortingTest {
    private val netflix = MetaWatchProvider(
        id = 8,
        name = "Netflix",
        accessTypes = listOf(
            WatchProviderAccessType.SUBSCRIPTION,
            WatchProviderAccessType.ADS,
        ),
    )
    private val primeVideo = MetaWatchProvider(
        id = 119,
        name = "Amazon Prime Video",
        accessTypes = listOf(
            WatchProviderAccessType.SUBSCRIPTION,
            WatchProviderAccessType.RENT,
        ),
    )
    private val appleTv = MetaWatchProvider(
        id = 2,
        name = "Apple TV",
        accessTypes = listOf(
            WatchProviderAccessType.RENT,
            WatchProviderAccessType.BUY,
        ),
    )
    private val youtube = MetaWatchProvider(
        id = 192,
        name = "YouTube",
        accessTypes = listOf(
            WatchProviderAccessType.FREE,
            WatchProviderAccessType.ADS,
        ),
    )
    private val plutoTv = MetaWatchProvider(
        id = 300,
        name = "Pluto TV",
        accessTypes = listOf(WatchProviderAccessType.FREE),
    )
    private val adsOnly = MetaWatchProvider(
        id = 500,
        name = "Ads only",
        accessTypes = listOf(WatchProviderAccessType.ADS),
    )

    @Test
    fun defaultOrderGroupsSubscriptionThenRentThenFree() {
        val providers = listOf(appleTv, netflix, youtube, primeVideo)

        assertEquals(
            listOf(8, 119, 2, 192),
            sortWatchProviders(providers, WatchProviderOfferCategory.defaultOrder).map { it.id },
        )
    }

    @Test
    fun subscriptionWithAdsFallsUnderSubscriptionByDefault() {
        assertEquals(
            WatchProviderOfferCategory.SUBSCRIPTION,
            watchProviderCategory(netflix, WatchProviderOfferCategory.defaultOrder),
        )
    }

    @Test
    fun movingSubscriptionWithAdsUpMovesHybridProviders() {
        val customOrder = listOf(
            WatchProviderOfferCategory.SUBSCRIPTION_WITH_ADS,
            WatchProviderOfferCategory.SUBSCRIPTION,
            WatchProviderOfferCategory.RENT,
            WatchProviderOfferCategory.BUY,
            WatchProviderOfferCategory.FREE,
            WatchProviderOfferCategory.FREE_WITH_ADS,
        )
        val providers = listOf(primeVideo, netflix, youtube, appleTv)

        assertEquals(
            listOf(8, 119, 2, 192),
            sortWatchProviders(providers, customOrder).map { it.id },
        )
    }

    @Test
    fun freeWithAdsStaysInFreeByDefaultAndMovesWhenPrioritized() {
        val providers = listOf(plutoTv, youtube)
        val freeWithAdsFirst = listOf(
            WatchProviderOfferCategory.FREE_WITH_ADS,
            WatchProviderOfferCategory.SUBSCRIPTION,
            WatchProviderOfferCategory.SUBSCRIPTION_WITH_ADS,
            WatchProviderOfferCategory.RENT,
            WatchProviderOfferCategory.BUY,
            WatchProviderOfferCategory.FREE,
        )

        assertEquals(
            listOf(300, 192),
            sortWatchProviders(providers, WatchProviderOfferCategory.defaultOrder).map { it.id },
        )
        assertEquals(
            listOf(192, 300),
            sortWatchProviders(providers, freeWithAdsFirst).map { it.id },
        )
    }

    @Test
    fun adsOnlyProviderFallsUnderFreeWithAds() {
        val providers = listOf(adsOnly, plutoTv)

        assertEquals(
            listOf(300, 500),
            sortWatchProviders(providers, WatchProviderOfferCategory.defaultOrder).map { it.id },
        )
    }

    @Test
    fun providersWithoutMatchingCategoryKeepOrderAtTheEnd() {
        val unmatched = MetaWatchProvider(id = 999, name = "Unknown")
        val anotherUnmatched = MetaWatchProvider(id = 1000, name = "Unknown 2")
        val providers = listOf(unmatched, netflix, anotherUnmatched)

        assertEquals(
            listOf(8, 999, 1000),
            sortWatchProviders(providers, WatchProviderOfferCategory.defaultOrder).map { it.id },
        )
    }

    @Test
    fun labelsShowSubscriptionAndItsAdSupportedVariant() {
        assertEquals(
            listOf(
                WatchProviderOfferLabel.SUBSCRIPTION,
                WatchProviderOfferLabel.SUBSCRIPTION_WITH_ADS,
            ),
            watchProviderOfferLabels(netflix.accessTypes),
        )
        assertEquals(
            listOf(
                WatchProviderOfferLabel.FREE,
                WatchProviderOfferLabel.FREE_WITH_ADS,
            ),
            watchProviderOfferLabels(youtube.accessTypes),
        )
    }

    @Test
    fun labelsShowEveryOfferTypeOfTheProvider() {
        assertEquals(
            listOf(WatchProviderOfferLabel.SUBSCRIPTION, WatchProviderOfferLabel.RENT),
            watchProviderOfferLabels(primeVideo.accessTypes),
        )
        assertEquals(
            listOf(WatchProviderOfferLabel.RENT, WatchProviderOfferLabel.BUY),
            watchProviderOfferLabels(appleTv.accessTypes),
        )
        assertEquals(
            listOf(WatchProviderOfferLabel.ADS_ONLY),
            watchProviderOfferLabels(adsOnly.accessTypes),
        )
    }

    @Test
    fun normalizeFillsMissingCategoriesWithoutDuplicates() {
        assertEquals(
            listOf(
                WatchProviderOfferCategory.RENT,
                WatchProviderOfferCategory.SUBSCRIPTION,
                WatchProviderOfferCategory.SUBSCRIPTION_WITH_ADS,
                WatchProviderOfferCategory.BUY,
                WatchProviderOfferCategory.FREE,
                WatchProviderOfferCategory.FREE_WITH_ADS,
            ),
            WatchProviderOfferCategory.normalize(
                listOf(WatchProviderOfferCategory.RENT, WatchProviderOfferCategory.SUBSCRIPTION),
            ),
        )
    }

    @Test
    fun legacySortMapsToTheEquivalentCategory() {
        assertEquals(WatchProviderOfferCategory.FREE, WatchProviderOfferCategory.fromLegacySort("FREE"))
        assertEquals(
            WatchProviderOfferCategory.FREE_WITH_ADS,
            WatchProviderOfferCategory.fromLegacySort("ADS"),
        )
        assertEquals(
            WatchProviderOfferCategory.SUBSCRIPTION,
            WatchProviderOfferCategory.fromLegacySort("SUBSCRIPTION"),
        )
        assertEquals(null, WatchProviderOfferCategory.fromLegacySort("UNKNOWN"))
    }
}
