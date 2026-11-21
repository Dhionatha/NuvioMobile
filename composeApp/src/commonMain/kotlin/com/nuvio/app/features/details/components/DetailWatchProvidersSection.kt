package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.nuvioHorizontalScrollBleed
import com.nuvio.app.core.ui.rememberPosterCardStyleUiState
import com.nuvio.app.features.details.MetaWatchProvider
import com.nuvio.app.features.details.WatchProviderAccessType
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_watch_provider_ads
import nuvio.composeapp.generated.resources.detail_watch_provider_buy
import nuvio.composeapp.generated.resources.detail_watch_provider_free
import nuvio.composeapp.generated.resources.detail_watch_provider_rent
import nuvio.composeapp.generated.resources.detail_watch_provider_subscription
import nuvio.composeapp.generated.resources.detail_watch_providers_attribution
import nuvio.composeapp.generated.resources.detail_watch_providers_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Where to watch" rail backed by TMDB watch providers (JustWatch data).
 * Attribution to JustWatch is required by the TMDB API terms.
 */
@Composable
fun DetailWatchProvidersSection(
    providers: List<MetaWatchProvider>,
    link: String?,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    horizontalScrollPadding: Dp = 0.dp,
    onOpenExternalUrl: ((String) -> Unit)? = null,
) {
    if (providers.isEmpty()) return

    val cornerRadius = rememberPosterCardStyleUiState().cornerRadiusDp.dp
    val attribution = stringResource(Res.string.detail_watch_providers_attribution)
    val openLink = link?.trim()?.takeIf(String::isNotBlank)

    DetailSection(
        title = stringResource(Res.string.detail_watch_providers_title),
        modifier = modifier,
        showHeader = showHeader,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(
                modifier = Modifier
                    .nuvioHorizontalScrollBleed(horizontalScrollPadding)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = horizontalScrollPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items = providers, key = { provider -> provider.id }) { provider ->
                    WatchProviderCard(
                        provider = provider,
                        cornerRadius = cornerRadius,
                        onClick = if (onOpenExternalUrl != null && openLink != null) {
                            { onOpenExternalUrl(openLink) }
                        } else {
                            null
                        },
                    )
                }
            }

            if (onOpenExternalUrl != null && openLink != null) {
                Text(
                    text = attribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenExternalUrl(openLink) }
                        .padding(vertical = 2.dp),
                )
            } else {
                Text(
                    text = attribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchProviderCard(
    provider: MetaWatchProvider,
    cornerRadius: Dp,
    onClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(RoundedCornerShape(cornerRadius))
                .background(ProviderLogoPlateColor),
            contentAlignment = Alignment.Center,
        ) {
            if (provider.logo != null) {
                AsyncImage(
                    model = provider.logo,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .padding(4.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = provider.name.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black.copy(alpha = 0.72f),
                )
            }
        }

        Text(
            text = provider.name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            provider.accessTypes.forEach { accessType ->
                WatchProviderAccessChip(accessType = accessType)
            }
        }
    }
}

@Composable
private fun WatchProviderAccessChip(accessType: WatchProviderAccessType) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = stringResource(accessType.labelRes),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private val WatchProviderAccessType.labelRes: StringResource
    get() = when (this) {
        WatchProviderAccessType.FREE -> Res.string.detail_watch_provider_free
        WatchProviderAccessType.ADS -> Res.string.detail_watch_provider_ads
        WatchProviderAccessType.SUBSCRIPTION -> Res.string.detail_watch_provider_subscription
        WatchProviderAccessType.RENT -> Res.string.detail_watch_provider_rent
        WatchProviderAccessType.BUY -> Res.string.detail_watch_provider_buy
    }

/** Providers ship logos designed for light surfaces; a white plate keeps dark logos visible. */
private val ProviderLogoPlateColor = Color.White.copy(alpha = 0.94f)
