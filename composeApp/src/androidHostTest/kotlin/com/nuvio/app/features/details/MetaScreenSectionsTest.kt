package com.nuvio.app.features.details

import android.app.Application
import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MetaScreenSectionsTest {
    @BeforeTest
    fun initialize() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("nuvio_meta_screen_settings", Context.MODE_PRIVATE).edit().clear().commit()
        MetaScreenSettingsStorage.initialize(context)
        MetaScreenSettingsRepository.clearLocalState()
    }

    @AfterTest
    fun clearState() {
        MetaScreenSettingsRepository.clearLocalState()
    }

    @Test
    fun freshInstallPlacesWhereToWatchRightAfterOverview() {
        MetaScreenSettingsRepository.ensureLoaded()
        val items = MetaScreenSettingsRepository.uiState.value.items

        assertEquals(
            items.indexOfFirst { it.key == MetaScreenSectionKey.OVERVIEW } + 1,
            items.indexOfFirst { it.key == MetaScreenSectionKey.WHERE_TO_WATCH },
        )
        assertTrue(items.first { it.key == MetaScreenSectionKey.WHERE_TO_WATCH }.enabled)
    }

    @Test
    fun legacyPayloadInsertsWhereToWatchAfterOverviewWithoutReorderingTheRest() {
        MetaScreenSettingsStorage.savePayload(LegacyPayload)
        MetaScreenSettingsRepository.onProfileChanged()

        val items = MetaScreenSettingsRepository.uiState.value.items
        val legacyOrder = listOf(
            MetaScreenSectionKey.ACTIONS,
            MetaScreenSectionKey.OVERVIEW,
            MetaScreenSectionKey.PRODUCTION,
            MetaScreenSectionKey.CAST,
            MetaScreenSectionKey.COMMENTS,
            MetaScreenSectionKey.TRAILERS,
            MetaScreenSectionKey.EPISODES,
            MetaScreenSectionKey.DETAILS,
            MetaScreenSectionKey.COLLECTION,
            MetaScreenSectionKey.MORE_LIKE_THIS,
        )

        assertEquals(legacyOrder.size + 1, items.size)
        assertEquals(2, items.indexOfFirst { it.key == MetaScreenSectionKey.WHERE_TO_WATCH })
        assertEquals(
            legacyOrder,
            items.map { it.key }.filter { it != MetaScreenSectionKey.WHERE_TO_WATCH },
        )
        assertTrue(items.first { it.key == MetaScreenSectionKey.WHERE_TO_WATCH }.enabled)
    }

    @Test
    fun disabledWhereToWatchStaysDisabledAcrossReloads() {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.setEnabled(MetaScreenSectionKey.WHERE_TO_WATCH, false)

        MetaScreenSettingsRepository.onProfileChanged()

        val item = MetaScreenSettingsRepository.uiState.value.items
            .first { it.key == MetaScreenSectionKey.WHERE_TO_WATCH }
        assertFalse(item.enabled)
    }

    @Test
    fun reorderingWhereToWatchRoundTripsThroughTheProfilePayload() {
        MetaScreenSettingsRepository.ensureLoaded()
        val originalIndex = MetaScreenSettingsRepository.uiState.value.items
            .indexOfFirst { it.key == MetaScreenSectionKey.WHERE_TO_WATCH }
        MetaScreenSettingsRepository.moveByIndex(originalIndex, 5)

        MetaScreenSettingsRepository.onProfileChanged()

        val items = MetaScreenSettingsRepository.uiState.value.items
        assertEquals(MetaScreenSectionKey.WHERE_TO_WATCH, items[5].key)
        assertEquals(5, items[5].order)
    }

    @Test
    fun watchProviderOrderRoundTripsThroughTheProfilePayload() {
        MetaScreenSettingsRepository.ensureLoaded()
        val customOrder = listOf(
            WatchProviderOfferCategory.RENT,
            WatchProviderOfferCategory.SUBSCRIPTION,
            WatchProviderOfferCategory.BUY,
            WatchProviderOfferCategory.FREE,
            WatchProviderOfferCategory.FREE_WITH_ADS,
            WatchProviderOfferCategory.SUBSCRIPTION_WITH_ADS,
        )
        MetaScreenSettingsRepository.setWatchProviderOrder(customOrder)

        MetaScreenSettingsRepository.onProfileChanged()

        assertEquals(
            customOrder,
            MetaScreenSettingsRepository.uiState.value.watchProviderOrder,
        )
    }

    @Test
    fun watchProviderOrderDefaultsToTheRecommendedPriority() {
        MetaScreenSettingsRepository.ensureLoaded()

        assertEquals(
            WatchProviderOfferCategory.defaultOrder,
            MetaScreenSettingsRepository.uiState.value.watchProviderOrder,
        )
    }

    @Test
    fun legacyWatchProviderSortIsMigratedToTheNewOrder() {
        MetaScreenSettingsStorage.savePayload("""{"watch_provider_sort":"RENT"}""")

        MetaScreenSettingsRepository.onProfileChanged()

        val order = MetaScreenSettingsRepository.uiState.value.watchProviderOrder
        assertEquals(WatchProviderOfferCategory.RENT, order.first())
        assertEquals(WatchProviderOfferCategory.defaultOrder.size, order.size)
        assertEquals(order.size, order.distinct().size)
    }

    @Test
    fun resettingTheOrderRestoresTheRecommendedPriority() {
        MetaScreenSettingsRepository.ensureLoaded()
        MetaScreenSettingsRepository.setWatchProviderOrder(
            listOf(WatchProviderOfferCategory.FREE) + WatchProviderOfferCategory.defaultOrder,
        )

        MetaScreenSettingsRepository.resetWatchProviderOrder()

        assertEquals(
            WatchProviderOfferCategory.defaultOrder,
            MetaScreenSettingsRepository.uiState.value.watchProviderOrder,
        )
    }

    private companion object {
        const val LegacyPayload = """
            {"items":[
              {"key":"ACTIONS","order":0},
              {"key":"OVERVIEW","order":1},
              {"key":"PRODUCTION","order":2},
              {"key":"CAST","order":3},
              {"key":"COMMENTS","order":4},
              {"key":"TRAILERS","order":5},
              {"key":"EPISODES","order":6},
              {"key":"DETAILS","order":7},
              {"key":"COLLECTION","order":8},
              {"key":"MORE_LIKE_THIS","order":9}
            ]}
        """
    }
}
