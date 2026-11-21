package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.Menu
import com.nuvio.app.core.ui.MenuItem
import com.nuvio.app.features.details.MetaWatchProvider
import com.nuvio.app.features.details.WatchProviderAccessType
import com.nuvio.app.features.details.sortWatchProviders
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.detail_watch_provider_ads
import nuvio.composeapp.generated.resources.detail_watch_provider_buy
import nuvio.composeapp.generated.resources.detail_watch_provider_free
import nuvio.composeapp.generated.resources.detail_watch_provider_rent
import nuvio.composeapp.generated.resources.detail_watch_provider_subscription
import nuvio.composeapp.generated.resources.detail_watch_providers_attribution
import nuvio.composeapp.generated.resources.detail_watch_providers_sort
import nuvio.composeapp.generated.resources.detail_watch_providers_sort_default
import nuvio.composeapp.generated.resources.detail_watch_providers_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private val CardShape = RoundedCornerShape(18.dp)
private val SortOptions = listOf(
    WatchProviderAccessType.SUBSCRIPTION,
    WatchProviderAccessType.FREE,
    WatchProviderAccessType.ADS,
    WatchProviderAccessType.RENT,
    WatchProviderAccessType.BUY,
)

/**
 * "Where to watch" backed by TMDB watch providers (JustWatch data), rendered as a single compact
 * dark card. Attribution to JustWatch is required by the TMDB API terms.
 */
@Composable
fun DetailWatchProvidersSection(
    providers: List<MetaWatchProvider>,
    link: String?,
    sort: WatchProviderAccessType? = null,
    onSortChange: ((WatchProviderAccessType?) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    onOpenExternalUrl: ((String) -> Unit)? = null,
) {
    if (providers.isEmpty()) return

    val orderedProviders = remember(providers, sort) { sortWatchProviders(providers, sort) }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttributionLabel(
                    openLink = openLink,
                    onOpenExternalUrl = onOpenExternalUrl,
                )
                if (onSortChange != null) {
                    SortCapsule(sort = sort, onSortChange = onSortChange)
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
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
) {
    val text = stringResource(Res.string.detail_watch_providers_attribution)
    val clickModifier = if (onOpenExternalUrl != null && openLink != null) {
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { onOpenExternalUrl(openLink) }
    } else {
        Modifier
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = clickModifier.padding(vertical = 2.dp, horizontal = 2.dp),
    )
}

@Composable
private fun SortCapsule(
    sort: WatchProviderAccessType?,
    onSortChange: (WatchProviderAccessType?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val isActive = sort != null
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(containerColor)
                .clickable { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Sort,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = stringResource(Res.string.detail_watch_providers_sort),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }

        Menu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MenuItem(
                text = stringResource(Res.string.detail_watch_providers_sort_default),
                selected = sort == null,
                onClick = {
                    expanded = false
                    onSortChange(null)
                },
            )
            SortOptions.forEach { accessType ->
                MenuItem(
                    text = stringResource(accessType.labelRes),
                    selected = sort == accessType,
                    onClick = {
                        expanded = false
                        onSortChange(accessType)
                    },
                )
            }
        }
    }
}

@Composable
private fun WatchProviderItem(
    provider: MetaWatchProvider,
    onClick: (() -> Unit)?,
) {
    val accessLabel = provider.accessTypes
        .map { accessType -> stringResource(accessType.labelRes) }
        .joinToString(" | ")

    Row(
        modifier = Modifier
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 2.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (provider.logo != null) {
            AsyncImage(
                model = provider.logo,
                contentDescription = provider.name,
                modifier = Modifier.size(32.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = provider.name,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 88.dp),
            )
        }

        if (accessLabel.isNotEmpty()) {
            Text(
                text = accessLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
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
