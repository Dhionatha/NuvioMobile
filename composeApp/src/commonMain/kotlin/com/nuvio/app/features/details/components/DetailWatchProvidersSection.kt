package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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

private val TileShape = RoundedCornerShape(NuvioTokens.Radius.lg)

/**
 * Fixed acquisition type colors so every offer stays distinguishable in any Nuvio theme.
 * The ads variants share one amber, matching the reference layout.
 */
private val OfferSubscriptionColor = Color(0xFF5B9CFF)
private val OfferWithAdsColor = Color(0xFFFFA726)
private val OfferRentColor = Color(0xFFA78BFA)
private val OfferBuyColor = Color(0xFF4ADE80)
private val OfferFreeColor = Color(0xFF2DD4BF)

/**
 * "Where to watch" backed by TMDB watch providers (JustWatch data): a horizontal rail of logo
 * tiles with the acquisition type colored underneath. Attribution to JustWatch is required by the
 * TMDB API terms.
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
                        WatchProviderTile(
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
private fun WatchProviderTile(
    provider: MetaWatchProvider,
    sizing: WatchProviderSectionSizing,
    onClick: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.nuvio
    val shape = TileShape

    Column(
        modifier = Modifier
            .width(sizing.itemWidth)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(sizing.tileSize)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(NuvioTokens.Border.thin, tokens.colors.borderSubtle, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (provider.logo != null) {
                AsyncImage(
                    model = provider.logo,
                    contentDescription = provider.name,
                    modifier = Modifier.size(sizing.logoSize),
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

        OfferTypeLabels(provider = provider, sizing = sizing)
    }
}

@Composable
private fun OfferTypeLabels(
    provider: MetaWatchProvider,
    sizing: WatchProviderSectionSizing,
) {
    val labels = remember(provider.accessTypes) { watchProviderOfferLabels(provider.accessTypes) }
    if (labels.isEmpty()) return

    val texts = labels.map { label -> stringResource(label.labelRes) }
    val annotated = buildAnnotatedString {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                withStyle(SpanStyle(color = Color.White.copy(alpha = 0.35f))) { append("|") }
            }
            withStyle(SpanStyle(color = label.color())) { append(texts[index]) }
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = sizing.labelSize),
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
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
    val tileSize: Dp,
    val labelSize: TextUnit,
) {
    val itemGap: Dp = NuvioTokens.Space.s8
    val itemWidth: Dp = tileSize + NuvioTokens.Space.s8
    val logoSize: Dp = tileSize * 0.66f
}

private fun watchProviderSectionSizing(maxWidthDp: Float): WatchProviderSectionSizing =
    when {
        maxWidthDp >= 1200f -> WatchProviderSectionSizing(tileSize = 96.dp, labelSize = 14.sp)
        maxWidthDp >= 840f -> WatchProviderSectionSizing(tileSize = 88.dp, labelSize = 13.sp)
        maxWidthDp >= 600f -> WatchProviderSectionSizing(tileSize = 84.dp, labelSize = 12.sp)
        else -> WatchProviderSectionSizing(tileSize = 80.dp, labelSize = 12.sp)
    }

private fun WatchProviderOfferLabel.color(): Color = when (this) {
    WatchProviderOfferLabel.SUBSCRIPTION -> OfferSubscriptionColor
    WatchProviderOfferLabel.SUBSCRIPTION_WITH_ADS -> OfferWithAdsColor
    WatchProviderOfferLabel.RENT -> OfferRentColor
    WatchProviderOfferLabel.BUY -> OfferBuyColor
    WatchProviderOfferLabel.FREE -> OfferFreeColor
    WatchProviderOfferLabel.FREE_WITH_ADS -> OfferWithAdsColor
    WatchProviderOfferLabel.ADS_ONLY -> OfferWithAdsColor
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
