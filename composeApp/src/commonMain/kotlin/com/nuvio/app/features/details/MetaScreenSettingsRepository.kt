package com.nuvio.app.features.details

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

enum class MetaScreenSectionKey {
    ACTIONS,
    OVERVIEW,
    WHERE_TO_WATCH,
    PRODUCTION,
    CAST,
    COMMENTS,
    TRAILERS,
    EPISODES,
    DETAILS,
    COLLECTION,
    MORE_LIKE_THIS,
    ;

    
    val canBeTabbed: Boolean
        get() = this != ACTIONS && this != OVERVIEW
}

data class MetaScreenSectionItem(
    val key: MetaScreenSectionKey,
    val title: String,
    val description: String,
    val enabled: Boolean,
    val order: Int,
    val tabGroup: Int? = null,
)

data class MetaScreenSettingsUiState(
    val items: List<MetaScreenSectionItem> = emptyList(),
    val backgroundMode: MetaScreenBackgroundMode = MetaScreenBackgroundMode.Normal,
    val cinematicBackground: Boolean = false,
    val heroTrailerPlayback: Boolean = false,
    val tabLayout: Boolean = false,
    val episodeCardStyle: MetaEpisodeCardStyle = MetaEpisodeCardStyle.Horizontal,
    val blurUnwatchedEpisodes: Boolean = false,
    val posterTransitionEnabled: Boolean = false,
    val showOverallRatings: Boolean = true,
    val showParentsGuide: Boolean = true,
    val episodeRatingsVisibility: EpisodeRatingsVisibility = EpisodeRatingsVisibility.SHOW_ALL,
    val watchProviderOrder: List<WatchProviderOfferCategory> = WatchProviderOfferCategory.defaultOrder,
)

enum class MetaScreenBackgroundMode {
    Normal,
    Cinematic,
    DominantColor,
    ;

    val usesBackdropBackground: Boolean
        get() = this != Normal

    companion object {
        fun parse(raw: String?): MetaScreenBackgroundMode? = when (raw?.lowercase()) {
            "normal" -> Normal
            "cinematic" -> Cinematic
            "dominant_color" -> DominantColor
            else -> null
        }

        fun persist(mode: MetaScreenBackgroundMode): String = when (mode) {
            Normal -> "normal"
            Cinematic -> "cinematic"
            DominantColor -> "dominant_color"
        }

        fun fromLegacyCinematic(enabled: Boolean): MetaScreenBackgroundMode =
            if (enabled) Cinematic else Normal
    }
}

enum class MetaEpisodeCardStyle {
    Horizontal,
    List,
    ;

    companion object {
        fun parse(raw: String?): MetaEpisodeCardStyle? = when (raw?.lowercase()) {
            "horizontal" -> Horizontal
            "list" -> List
            else -> null
        }

        fun persist(style: MetaEpisodeCardStyle): String = when (style) {
            Horizontal -> "horizontal"
            List -> "list"
        }
    }
}

internal fun MetaScreenSectionItem.tabGroupForRendering(
    episodeCardStyle: MetaEpisodeCardStyle,
): Int? = if (
    key == MetaScreenSectionKey.EPISODES && episodeCardStyle == MetaEpisodeCardStyle.List
) {
    null
} else {
    tabGroup
}

@Serializable
private data class StoredMetaScreenSectionPreference(
    val key: String,
    val enabled: Boolean = true,
    val order: Int = 0,
    val tabGroup: Int? = null,
)

@Serializable
private data class StoredMetaScreenSettingsPayload(
    val items: List<StoredMetaScreenSectionPreference> = emptyList(),
    @SerialName("background_mode")
    val backgroundMode: String? = null,
    val cinematicBackground: Boolean = false,
    @SerialName("hero_trailer_playback")
    val heroTrailerPlayback: Boolean = false,
    @SerialName("tvStyleLayout")
    val tabLayout: Boolean = false,
    val episodeCardStyle: String = "horizontal",
    @SerialName("blur_unwatched_episodes")
    val blurUnwatchedEpisodes: Boolean = false,
    @SerialName("poster_transition_enabled")
    val posterTransitionEnabled: Boolean = false,
    @SerialName("show_overall_ratings")
    val showOverallRatings: Boolean = true,
    @SerialName("show_parents_guide")
    val showParentsGuide: Boolean = true,
    @SerialName("episode_ratings_visibility")
    val episodeRatingsVisibility: String = EpisodeRatingsVisibility.SHOW_ALL.name,
    @SerialName("watch_provider_order")
    val watchProviderOrder: List<String> = emptyList(),
    @SerialName("watch_provider_sort")
    val legacyWatchProviderSort: String? = null,
)

private data class MetaScreenSectionDefinition(
    val key: MetaScreenSectionKey,
    val titleRes: StringResource,
    val descriptionRes: StringResource,
)

object MetaScreenSettingsRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val definitions = listOf(
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.ACTIONS,
            titleRes = Res.string.meta_section_actions_title,
            descriptionRes = Res.string.meta_section_actions_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.OVERVIEW,
            titleRes = Res.string.meta_section_overview_title,
            descriptionRes = Res.string.meta_section_overview_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.WHERE_TO_WATCH,
            titleRes = Res.string.meta_section_where_to_watch_title,
            descriptionRes = Res.string.meta_section_where_to_watch_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.PRODUCTION,
            titleRes = Res.string.meta_section_production_title,
            descriptionRes = Res.string.meta_section_production_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.CAST,
            titleRes = Res.string.settings_meta_cast,
            descriptionRes = Res.string.meta_section_cast_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.COMMENTS,
            titleRes = Res.string.settings_meta_comments,
            descriptionRes = Res.string.meta_section_comments_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.TRAILERS,
            titleRes = Res.string.settings_meta_trailers,
            descriptionRes = Res.string.meta_section_trailers_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.EPISODES,
            titleRes = Res.string.settings_meta_episodes,
            descriptionRes = Res.string.meta_section_episodes_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.DETAILS,
            titleRes = Res.string.meta_section_details_title,
            descriptionRes = Res.string.meta_section_details_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.COLLECTION,
            titleRes = Res.string.meta_section_collection_title,
            descriptionRes = Res.string.meta_section_collection_description,
        ),
        MetaScreenSectionDefinition(
            key = MetaScreenSectionKey.MORE_LIKE_THIS,
            titleRes = Res.string.meta_section_more_like_this_title,
            descriptionRes = Res.string.meta_section_more_like_this_description,
        ),
    )

    private val _uiState = MutableStateFlow(MetaScreenSettingsUiState())
    val uiState: StateFlow<MetaScreenSettingsUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var preferences: MutableMap<MetaScreenSectionKey, StoredMetaScreenSectionPreference> = mutableMapOf()
    private var backgroundMode: MetaScreenBackgroundMode = MetaScreenBackgroundMode.Normal
    private var heroTrailerPlayback: Boolean = false
    private var tabLayout: Boolean = false
    private var episodeCardStyle: MetaEpisodeCardStyle = MetaEpisodeCardStyle.Horizontal
    private var blurUnwatchedEpisodes: Boolean = false
    private var showOverallRatings: Boolean = true
    private var showParentsGuide: Boolean = true
    private var episodeRatingsVisibility: EpisodeRatingsVisibility = EpisodeRatingsVisibility.SHOW_ALL
    private var posterTransitionEnabled: Boolean = false
    private var watchProviderOrder: List<WatchProviderOfferCategory> = WatchProviderOfferCategory.defaultOrder
    private fun localizedString(resource: StringResource): String = runBlocking { getString(resource) }

    private fun resolveWatchProviderOrder(parsed: StoredMetaScreenSettingsPayload): List<WatchProviderOfferCategory> {
        val stored = parsed.watchProviderOrder.mapNotNull(WatchProviderOfferCategory::parse)
        if (stored.isNotEmpty()) return WatchProviderOfferCategory.normalize(stored)
        val legacy = WatchProviderOfferCategory.fromLegacySort(parsed.legacyWatchProviderSort)
            ?: return WatchProviderOfferCategory.defaultOrder
        return WatchProviderOfferCategory.normalize(listOf(legacy) + WatchProviderOfferCategory.defaultOrder)
    }

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true

        val payload = MetaScreenSettingsStorage.loadPayload().orEmpty().trim()
        if (payload.isNotEmpty()) {
            val parsed = runCatching {
                json.decodeFromString<StoredMetaScreenSettingsPayload>(payload)
            }.getOrNull()
            if (parsed != null) {
                backgroundMode = MetaScreenBackgroundMode.parse(parsed.backgroundMode)
                    ?: MetaScreenBackgroundMode.fromLegacyCinematic(parsed.cinematicBackground)
                heroTrailerPlayback = parsed.heroTrailerPlayback
                tabLayout = parsed.tabLayout
                episodeCardStyle = MetaEpisodeCardStyle.parse(parsed.episodeCardStyle)
                    ?: MetaEpisodeCardStyle.Horizontal
                blurUnwatchedEpisodes = parsed.blurUnwatchedEpisodes
                posterTransitionEnabled = parsed.posterTransitionEnabled
                showOverallRatings = parsed.showOverallRatings
                showParentsGuide = parsed.showParentsGuide
                episodeRatingsVisibility = EpisodeRatingsVisibility.parse(parsed.episodeRatingsVisibility)
                watchProviderOrder = resolveWatchProviderOrder(parsed)
                preferences = parsed.items.mapNotNull { item ->
                    val key = runCatching { MetaScreenSectionKey.valueOf(item.key) }.getOrNull() ?: return@mapNotNull null
                    key to item
                }.toMap().toMutableMap()
            }
        }

        normalizePreferences()
        publish()
        persist()
    }

    fun onProfileChanged() {
        hasLoaded = false
        preferences.clear()
        backgroundMode = MetaScreenBackgroundMode.Normal
        heroTrailerPlayback = false
        tabLayout = false
        episodeCardStyle = MetaEpisodeCardStyle.Horizontal
        blurUnwatchedEpisodes = false
        posterTransitionEnabled = false
        showOverallRatings = true
        showParentsGuide = true
        episodeRatingsVisibility = EpisodeRatingsVisibility.SHOW_ALL
        watchProviderOrder = WatchProviderOfferCategory.defaultOrder
        _uiState.value = MetaScreenSettingsUiState()
        ensureLoaded()
    }

    fun setCinematicBackground(enabled: Boolean) {
        setBackgroundMode(MetaScreenBackgroundMode.fromLegacyCinematic(enabled))
    }

    fun setBackgroundMode(mode: MetaScreenBackgroundMode) {
        ensureLoaded()
        backgroundMode = mode
        publish()
        persist()
    }

    fun setHeroTrailerPlayback(enabled: Boolean) {
        ensureLoaded()
        heroTrailerPlayback = enabled
        publish()
        persist()
    }

    fun setTabLayout(enabled: Boolean) {
        ensureLoaded()
        tabLayout = enabled
        publish()
        persist()
    }

    fun setEpisodeCardStyle(style: MetaEpisodeCardStyle) {
        ensureLoaded()
        episodeCardStyle = style
        publish()
        persist()
    }

    fun setBlurUnwatchedEpisodes(enabled: Boolean) {
        ensureLoaded()
        blurUnwatchedEpisodes = enabled
        publish()
        persist()
    }

    fun setShowOverallRatings(enabled: Boolean) {
        ensureLoaded()
        showOverallRatings = enabled
        publish()
        persist()
    }

    fun setShowParentsGuide(enabled: Boolean) {
        ensureLoaded()
        showParentsGuide = enabled
        publish()
        persist()
    }

    fun setEpisodeRatingsVisibility(visibility: EpisodeRatingsVisibility) {
        ensureLoaded()
        episodeRatingsVisibility = visibility
        publish()
        persist()
    }

    fun setPosterTransitionEnabled(enabled: Boolean) {
        ensureLoaded()
        posterTransitionEnabled = enabled
        publish()
        persist()
    }

    fun setWatchProviderOrder(order: List<WatchProviderOfferCategory>) {
        ensureLoaded()
        watchProviderOrder = WatchProviderOfferCategory.normalize(order)
        publish()
        persist()
    }

    fun moveWatchProviderOrder(fromIndex: Int, toIndex: Int) {
        ensureLoaded()
        val order = watchProviderOrder.toMutableList()
        if (fromIndex !in order.indices || toIndex !in order.indices || fromIndex == toIndex) return
        order.add(toIndex, order.removeAt(fromIndex))
        watchProviderOrder = order
        publish()
        persist()
    }

    fun resetWatchProviderOrder() {
        ensureLoaded()
        watchProviderOrder = WatchProviderOfferCategory.defaultOrder
        publish()
        persist()
    }

    fun setTabGroup(key: MetaScreenSectionKey, groupId: Int?) {
        ensureLoaded()
        if (!key.canBeTabbed) return
        if (groupId != null) {
            // Enforce max 3 sections per group
            val currentGroupCount = preferences.count { it.value.tabGroup == groupId && it.key != key }
            if (currentGroupCount >= 3) return
        }
        updatePreference(key) { preference ->
            preference.copy(tabGroup = groupId)
        }
    }

    fun clearLocalState() {
        hasLoaded = false
        preferences.clear()
        backgroundMode = MetaScreenBackgroundMode.Normal
        heroTrailerPlayback = false
        tabLayout = false
        episodeCardStyle = MetaEpisodeCardStyle.Horizontal
        blurUnwatchedEpisodes = false
        posterTransitionEnabled = false
        showOverallRatings = true
        showParentsGuide = true
        episodeRatingsVisibility = EpisodeRatingsVisibility.SHOW_ALL
        watchProviderOrder = WatchProviderOfferCategory.defaultOrder
        _uiState.value = MetaScreenSettingsUiState()
    }

    internal fun applyFromSync(
        items: List<MetaScreenSectionItem>,
        cinematicBackground: Boolean,
        heroTrailerPlayback: Boolean = false,
        tabLayout: Boolean,
        episodeCardStyle: MetaEpisodeCardStyle = MetaEpisodeCardStyle.Horizontal,
        blurUnwatchedEpisodes: Boolean = false,
        backgroundMode: MetaScreenBackgroundMode? = null,
        posterTransitionEnabled: Boolean = false,
        showOverallRatings: Boolean = true,
        showParentsGuide: Boolean = true,
        episodeRatingsVisibility: EpisodeRatingsVisibility = EpisodeRatingsVisibility.SHOW_ALL,
        watchProviderOrder: List<WatchProviderOfferCategory> = WatchProviderOfferCategory.defaultOrder,
    ) {
        ensureLoaded()
        this.backgroundMode = backgroundMode ?: MetaScreenBackgroundMode.fromLegacyCinematic(cinematicBackground)
        this.heroTrailerPlayback = heroTrailerPlayback
        this.tabLayout = tabLayout
        this.episodeCardStyle = episodeCardStyle
        this.blurUnwatchedEpisodes = blurUnwatchedEpisodes
        this.posterTransitionEnabled = posterTransitionEnabled
        this.showOverallRatings = showOverallRatings
        this.showParentsGuide = showParentsGuide
        this.episodeRatingsVisibility = episodeRatingsVisibility
        this.watchProviderOrder = WatchProviderOfferCategory.normalize(watchProviderOrder)
        preferences = items.associate { item ->
            item.key to StoredMetaScreenSectionPreference(
                key = item.key.name,
                enabled = item.enabled,
                order = item.order,
                tabGroup = item.tabGroup,
            )
        }.toMutableMap()
        normalizePreferences()
        publish()
        persist()
    }

    fun setEnabled(key: MetaScreenSectionKey, enabled: Boolean) {
        updatePreference(key) { preference ->
            preference.copy(enabled = enabled)
        }
    }

    fun resetToDefaults() {
        ensureLoaded()
        preferences.clear()
        backgroundMode = MetaScreenBackgroundMode.Normal
        heroTrailerPlayback = false
        tabLayout = false
        episodeCardStyle = MetaEpisodeCardStyle.Horizontal
        blurUnwatchedEpisodes = false
        posterTransitionEnabled = false
        showOverallRatings = true
        showParentsGuide = true
        episodeRatingsVisibility = EpisodeRatingsVisibility.SHOW_ALL
        watchProviderOrder = WatchProviderOfferCategory.defaultOrder
        normalizePreferences()
        publish()
        persist()
    }

    fun moveByIndex(fromIndex: Int, toIndex: Int) {
        ensureLoaded()
        val orderedKeys = definitions
            .sortedBy { definition -> preferences[definition.key]?.order ?: Int.MAX_VALUE }
            .map { it.key }
            .toMutableList()
        if (fromIndex !in orderedKeys.indices || toIndex !in orderedKeys.indices) return
        if (fromIndex == toIndex) return
        orderedKeys.add(toIndex, orderedKeys.removeAt(fromIndex))
        orderedKeys.forEachIndexed { newIndex, sectionKey ->
            val current = preferences[sectionKey] ?: return@forEachIndexed
            preferences[sectionKey] = current.copy(order = newIndex)
        }
        publish()
        persist()
    }

    private fun updatePreference(
        key: MetaScreenSectionKey,
        transform: (StoredMetaScreenSectionPreference) -> StoredMetaScreenSectionPreference,
    ) {
        ensureLoaded()
        val current = preferences[key] ?: return
        preferences[key] = transform(current)
        publish()
        persist()
    }

    private fun normalizePreferences() {
        // Payloads saved before this section existed have no entry for it: insert it right after Overview.
        if (preferences.isNotEmpty() && MetaScreenSectionKey.WHERE_TO_WATCH !in preferences) {
            val overviewOrder = preferences[MetaScreenSectionKey.OVERVIEW]?.order
            if (overviewOrder != null) {
                preferences = preferences.mapValues { (_, preference) ->
                    if (preference.order > overviewOrder) {
                        preference.copy(order = preference.order + 1)
                    } else {
                        preference
                    }
                }.toMutableMap()
                preferences[MetaScreenSectionKey.WHERE_TO_WATCH] = StoredMetaScreenSectionPreference(
                    key = MetaScreenSectionKey.WHERE_TO_WATCH.name,
                    enabled = true,
                    order = overviewOrder + 1,
                )
            }
        }

        val normalized = mutableMapOf<MetaScreenSectionKey, StoredMetaScreenSectionPreference>()
        definitions.sortedBy { definition -> preferences[definition.key]?.order ?: Int.MAX_VALUE }
            .forEachIndexed { index, definition ->
                val stored = preferences[definition.key]
                normalized[definition.key] = StoredMetaScreenSectionPreference(
                    key = definition.key.name,
                    enabled = stored?.enabled ?: true,
                    order = index,
                    tabGroup = stored?.tabGroup,
                )
            }
        preferences = normalized
    }

    private fun publish() {
        _uiState.value = MetaScreenSettingsUiState(
            items = definitions
                .sortedBy { definition -> preferences[definition.key]?.order ?: Int.MAX_VALUE }
                .map { definition ->
                    val preference = preferences[definition.key]
                    MetaScreenSectionItem(
                        key = definition.key,
                        title = localizedString(definition.titleRes),
                        description = localizedString(definition.descriptionRes),
                        enabled = preference?.enabled ?: true,
                        order = preference?.order ?: 0,
                        tabGroup = preference?.tabGroup,
                    )
                },
            backgroundMode = backgroundMode,
            cinematicBackground = backgroundMode.usesBackdropBackground,
            heroTrailerPlayback = heroTrailerPlayback,
            tabLayout = tabLayout,
            episodeCardStyle = episodeCardStyle,
            blurUnwatchedEpisodes = blurUnwatchedEpisodes,
            posterTransitionEnabled = posterTransitionEnabled,
            showOverallRatings = showOverallRatings,
            showParentsGuide = showParentsGuide,
            episodeRatingsVisibility = episodeRatingsVisibility,
            watchProviderOrder = watchProviderOrder,
        )
    }

    private fun persist() {
        MetaScreenSettingsStorage.savePayload(
            json.encodeToString(
                StoredMetaScreenSettingsPayload(
                    items = preferences.values.sortedBy { it.order },
                    backgroundMode = MetaScreenBackgroundMode.persist(backgroundMode),
                    cinematicBackground = backgroundMode.usesBackdropBackground,
                    heroTrailerPlayback = heroTrailerPlayback,
                    tabLayout = tabLayout,
                    episodeCardStyle = MetaEpisodeCardStyle.persist(episodeCardStyle),
                    blurUnwatchedEpisodes = blurUnwatchedEpisodes,
                    posterTransitionEnabled = posterTransitionEnabled,
                    showOverallRatings = showOverallRatings,
                    showParentsGuide = showParentsGuide,
                    episodeRatingsVisibility = episodeRatingsVisibility.name,
                    watchProviderOrder = watchProviderOrder.map { it.name },
                ),
            ),
        )
    }
}
