package com.nuvio.app.features.details

import kotlin.test.Test
import kotlin.test.assertEquals

class WatchProviderSortingTest {
    private val netflix = MetaWatchProvider(
        id = 8,
        name = "Netflix",
        accessTypes = listOf(WatchProviderAccessType.SUBSCRIPTION),
    )
    private val primeVideo = MetaWatchProvider(
        id = 119,
        name = "Amazon Prime Video",
        accessTypes = listOf(WatchProviderAccessType.SUBSCRIPTION, WatchProviderAccessType.RENT),
    )
    private val appleTv = MetaWatchProvider(
        id = 2,
        name = "Apple TV",
        accessTypes = listOf(WatchProviderAccessType.RENT, WatchProviderAccessType.BUY),
    )
    private val youtube = MetaWatchProvider(
        id = 192,
        name = "YouTube",
        accessTypes = listOf(WatchProviderAccessType.FREE, WatchProviderAccessType.ADS),
    )

    @Test
    fun defaultSortKeepsTmdbOrder() {
        val providers = listOf(appleTv, netflix, youtube, primeVideo)

        assertEquals(
            listOf(2, 8, 192, 119),
            sortWatchProviders(providers, null).map { it.id },
        )
    }

    @Test
    fun subscriptionSortMovesSubscribersFirstAndKeepsTmdbOrderInsideGroups() {
        val providers = listOf(appleTv, netflix, youtube, primeVideo)

        assertEquals(
            listOf(8, 119, 2, 192),
            sortWatchProviders(providers, WatchProviderAccessType.SUBSCRIPTION).map { it.id },
        )
    }

    @Test
    fun rentSortIncludesProvidersThatAlsoOfferOtherTypes() {
        val providers = listOf(appleTv, netflix, youtube, primeVideo)

        assertEquals(
            listOf(2, 119, 8, 192),
            sortWatchProviders(providers, WatchProviderAccessType.RENT).map { it.id },
        )
    }

    @Test
    fun freeSortKeepsProvidersWithoutThatTypeInTmdbOrder() {
        val providers = listOf(appleTv, netflix, youtube, primeVideo)

        assertEquals(
            listOf(192, 2, 8, 119),
            sortWatchProviders(providers, WatchProviderAccessType.FREE).map { it.id },
        )
    }
}
