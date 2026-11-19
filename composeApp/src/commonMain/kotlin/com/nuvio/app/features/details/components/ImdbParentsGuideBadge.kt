package com.nuvio.app.features.details.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Compact, icon-only shortcut to the IMDb Parents Guide shown next to the certification badge.
 *
 * Reuses the metadata badge border and shape so it reads as part of the same row, and keeps the
 * visible content to a single icon. The accessible name lives in [contentDescription].
 */
@Composable
internal fun ImdbParentsGuideBadge(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .size(ImdbParentsGuideBadgeSize)
            .clip(MetaBadgeShape)
            .border(BorderStroke(1.dp, contentColor.copy(alpha = 0.55f)), MetaBadgeShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.FamilyRestroom,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(ImdbParentsGuideBadgeIconSize),
        )
    }
}

private val ImdbParentsGuideBadgeSize = 24.dp
private val ImdbParentsGuideBadgeIconSize = 16.dp
