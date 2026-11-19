package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio

/**
 * Shortcut to the IMDb Parents Guide shown next to the certification badge.
 *
 * Matches the RatingChip button on the same row: same shape, container tint, padding, icon size and
 * label style, so it reads as a regular action instead of a metadata badge. The accessible name is
 * [contentDescription], while [label] stays visible next to the icon.
 */
@Composable
internal fun ImdbParentsGuideBadge(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val tokens = MaterialTheme.nuvio
    Row(
        modifier = modifier
            .clip(MetaBadgeShape)
            .background(tokens.colors.textPrimary.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
            .padding(MetaBadgePadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.FamilyRestroom,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
