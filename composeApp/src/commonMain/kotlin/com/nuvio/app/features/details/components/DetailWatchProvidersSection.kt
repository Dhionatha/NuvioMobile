package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
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

private val CardShape = RoundedCornerShape(18.dp)

/**
 * "Where to watch" backed by TMDB watch providers (JustWatch data), rendered as a single compact
 * dark card. Attribution to JustWatch is required by the TMDB API terms.
 */
@Composable
fun DetailWatchProvidersSection(
    providers: List<MetaWatchProvider>,
    link: String?,
    order: List<WatchProviderOfferCategory> = WatchProviderOfferCategory.defaultOrder,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onOpenExternalUrl: ((String) -> Unit)? = null,
) {
    if (providers.isEmpty()) return

    val orderedProviders = remember(providers, order) { sortWatchProviders(providers, order) }
    val openLink = link?.trim()?.takeIf(String::isNotBlank)

    DetailSection(
        title = stringResource(Res.string.detail_watch_providers_title),
        modifier = modifier,
        showHeader = showHeader,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AttributionLabel(
                openLink = openLink,
                onOpenExternalUrl = onOpenExternalUrl,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                items(items = orderedProviders, key = { provider -> provider.id }) { provider ->
                    WatchProviderItem(
                        provider = provider,
                        onClick = if (onOpenExternalUrl != null && openLink != null) {
                            { onOpenExternalUrl(openLink) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AttributionLabel(
    openLink: String?,
    onOpenExternalUrl: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(Res.string.detail_watch_providers_attribution)
    val clickModifier = if (onOpenExternalUrl != null && openLink != null) {
        Modifier.clickable { onOpenExternalUrl(openLink) }
    } else {
        Modifier
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .then(clickModifier)
            .padding(vertical = 2.dp, horizontal = 2.dp),
    )
}

@Composable
private fun WatchProviderItem(
    provider: MetaWatchProvider,
    onClick: (() -> Unit)?,
) {
    val labels = remember(provider.accessTypes) { watchProviderOfferLabels(provider.accessTypes) }
    val accessLabels = mutableListOf<String>()
    for (label in labels) {
        accessLabels += stringResource(label.labelRes)
    }
    val accessLabel = accessLabels.joinToString(" | ")

    Column(
        modifier = Modifier
            .widthIn(min = 64.dp, max = 136.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (provider.logo != null) {
            AsyncImage(
                model = provider.logo,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit,
            )
        }

        Text(
            text = provider.name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (accessLabel.isNotEmpty()) {
            Text(
                text = accessLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
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
