package com.sendmefile77.chronosphere

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal val ChronosphereCardShape = RoundedCornerShape(18.dp)
internal val ChronosphereSmallShape = RoundedCornerShape(12.dp)

@Composable
internal fun SectionHeader(
    title: String,
    eyebrow: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!trailing.isNullOrBlank()) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun PanelCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ChronosphereCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (accent == null) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f)
            } else {
                accent.copy(alpha = 0.075f)
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = (accent ?: MaterialTheme.colorScheme.outline).copy(alpha = if (accent == null) 0.34f else 0.42f),
        ),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) { content() }
    }
}

@Composable
internal fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(100.dp),
        color = color.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.secondary,
) {
    Surface(
        modifier = modifier,
        shape = ChronosphereSmallShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.30f)
                    .height(2.dp),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = accent.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(100.dp),
                ) {}
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun RowScope.ActionTile(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = ChronosphereSmallShape,
        color = if (enabled) accent.copy(alpha = 0.085f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (enabled) accent.copy(alpha = 0.36f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
