package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioHorizontalScrollBleed
import com.nuvio.app.features.details.MetaWatchProvider
import com.nuvio.app.features.details.WatchProviderOfferCategory
import com.nuvio.app.features.details.WatchProviderOfferLabel
import com.nuvio.app.features.details.sortWatchProviders
import com.nuvio.app.features.details.watchProviderOfferLabels
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_watch_provider_ads
import nuvio.composeapp.generated.resources.detail_watch_provider_buy
import nuvio.composeapp.generated.resources.detail_watch_provider_free
import nuvio.composeapp.generated.resources.detail_watch_provider_free_ads
import nuvio.composeapp.generated.resources.detail_watch_provider_rent
import nuvio.composeapp.generated.resources.detail_watch_provider_subscription
import nuvio.composeapp.generated.resources.detail_watch_provider_subscription_ads
import nuvio.composeapp.generated.resources.detail_watch_providers_attribution
import nuvio.composeapp.generated.resources.detail_watch_providers_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Where to watch" backed by TMDB watch providers (JustWatch data): a horizontal rail of provider
 * logos with the acquisition type in a subtle capsule underneath, drawn straight on the page with
 * no card container. Attribution to JustWatch is required by the TMDB API terms.
 */
@Composable
fun DetailWatchProvidersSection(
    providers: List<MetaWatchProvider>,
    link: String?,
    order: List<WatchProviderOfferCategory> = WatchProviderOfferCategory.defaultOrder,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    horizontalScrollPadding: Dp = 0.dp,
    onOpenExternalUrl: ((String) -> Unit)? = null,
) {
    if (providers.isEmpty()) return

    val orderedProviders = remember(providers, order) { sortWatchProviders(providers, order) }
    val openLink = link?.trim()?.takeIf(String::isNotBlank)
    val onOpen: (() -> Unit)? = if (onOpenExternalUrl != null && openLink != null) {
        { onOpenExternalUrl(openLink) }
    } else {
        null
    }

    DetailSection(
        title = stringResource(Res.string.detail_watch_providers_title),
        modifier = modifier,
        showHeader = showHeader,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoxWithConstraints {
                val sizing = watchProviderSectionSizing(maxWidth.value)

                LazyRow(
                    modifier = Modifier
                        .nuvioHorizontalScrollBleed(horizontalScrollPadding)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = horizontalScrollPadding),
                    horizontalArrangement = Arrangement.spacedBy(sizing.itemGap),
                    verticalAlignment = Alignment.Top,
                ) {
                    items(items = orderedProviders, key = { provider -> provider.id }) { provider ->
                        WatchProviderItem(
                            provider = provider,
                            sizing = sizing,
                            onClick = onOpen,
                        )
                    }
                }
            }

            WatchProviderAttribution(onClick = onOpen)
        }
    }
}

@Composable
private fun WatchProviderItem(
    provider: MetaWatchProvider,
    sizing: WatchProviderSectionSizing,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .widthIn(min = sizing.minItemWidth, max = sizing.maxItemWidth)
            .clip(RoundedCornerShape(NuvioTokens.Radius.md))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(sizing.logoSize)
                .clip(RoundedCornerShape(NuvioTokens.Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            if (provider.logo != null) {
                AsyncImage(
                    model = provider.logo,
                    contentDescription = provider.name,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = provider.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        OfferTypeCapsule(provider = provider)
    }
}

@Composable
private fun OfferTypeCapsule(
    provider: MetaWatchProvider,
) {
    val labels = remember(provider.accessTypes) { watchProviderOfferLabels(provider.accessTypes) }
    if (labels.isEmpty()) return

    val texts = labels.map { label -> stringResource(label.labelRes) }

    Text(
        text = texts.joinToString(" | "),
        style = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(NuvioTokens.Radius.lg))
            .background(MaterialTheme.nuvio.colors.overlayHover)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun WatchProviderAttribution(onClick: (() -> Unit)?) {
    Text(
        text = stringResource(Res.string.detail_watch_providers_attribution),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.nuvio.colors.textMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 2.dp, vertical = 2.dp),
    )
}

private data class WatchProviderSectionSizing(
    val logoSize: Dp,
) {
    val itemGap: Dp = NuvioTokens.Space.s8
    val minItemWidth: Dp = logoSize + NuvioTokens.Space.s8
    val maxItemWidth: Dp = 136.dp
}

private fun watchProviderSectionSizing(maxWidthDp: Float): WatchProviderSectionSizing =
    when {
        maxWidthDp >= 1200f -> WatchProviderSectionSizing(logoSize = 63.dp)
        maxWidthDp >= 840f -> WatchProviderSectionSizing(logoSize = 58.dp)
        maxWidthDp >= 600f -> WatchProviderSectionSizing(logoSize = 55.dp)
        else -> WatchProviderSectionSizing(logoSize = 53.dp)
    }

private val WatchProviderOfferLabel.labelRes: StringResource
    get() = when (this) {
        WatchProviderOfferLabel.SUBSCRIPTION -> Res.string.detail_watch_provider_subscription
        WatchProviderOfferLabel.SUBSCRIPTION_WITH_ADS -> Res.string.detail_watch_provider_subscription_ads
        WatchProviderOfferLabel.RENT -> Res.string.detail_watch_provider_rent
        WatchProviderOfferLabel.BUY -> Res.string.detail_watch_provider_buy
        WatchProviderOfferLabel.FREE -> Res.string.detail_watch_provider_free
        WatchProviderOfferLabel.FREE_WITH_ADS -> Res.string.detail_watch_provider_free_ads
        WatchProviderOfferLabel.ADS_ONLY -> Res.string.detail_watch_provider_ads
    }
