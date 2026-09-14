package com.sendmefile77.chronosphere

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ChronosphereCardShape = RoundedCornerShape(20.dp)
internal val ChronosphereSmallShape = RoundedCornerShape(14.dp)

/** Shared visual language for the playable shell: dark metal, warm chronology and cold simulation light. */
internal object ChronosphereVisuals {
    val DeepSpace = Color(0xFF070B10)
    val PanelTop = Color(0xFF151D26)
    val PanelBottom = Color(0xFF0B1118)
    val PanelSoft = Color(0xFF111922)
    val Hairline = Color(0xFF31404D)
    val Gold = Color(0xFFE2C46F)
    val GoldSoft = Color(0xFF9B8142)
    val Cyan = Color(0xFF75D6D2)
    val Danger = Color(0xFFE47B79)
}

@Composable
internal fun SectionHeader(
    title: String,
    eyebrow: String? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 3.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f),
                        ),
                    ),
                    shape = RoundedCornerShape(100.dp),
                ),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
            )
        }
        if (!trailing.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = ChronosphereVisuals.PanelSoft,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)),
            ) {
                Text(
                    trailing,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun PanelCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val lineColor = accent ?: MaterialTheme.colorScheme.outline
    val top = if (accent == null) {
        ChronosphereVisuals.PanelTop
    } else {
        blend(ChronosphereVisuals.PanelTop, accent, 0.09f)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ChronosphereCardShape,
        color = Color.Transparent,
        border = BorderStroke(
            width = 1.dp,
            color = lineColor.copy(alpha = if (accent == null) 0.42f else 0.58f),
        ),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        top,
                        blend(top, ChronosphereVisuals.PanelBottom, 0.48f),
                        ChronosphereVisuals.PanelBottom,
                    ),
                ),
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                lineColor.copy(alpha = if (accent == null) 0.38f else 0.95f),
                                lineColor.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp)) {
                content()
            }
        }
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
        color = blend(ChronosphereVisuals.PanelSoft, color, 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.48f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(5.dp)
                    .background(color, RoundedCornerShape(100.dp)),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        color = Color.Transparent,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            blend(ChronosphereVisuals.PanelTop, accent, 0.08f),
                            ChronosphereVisuals.PanelBottom,
                        ),
                    ),
                )
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(2.dp)
                        .background(accent, RoundedCornerShape(100.dp)),
                )
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.45.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
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
    val top = if (enabled) blend(ChronosphereVisuals.PanelTop, accent, 0.13f) else ChronosphereVisuals.PanelSoft
    Surface(
        modifier = Modifier
            .weight(1f)
            .clickable(enabled = enabled, onClick = onClick),
        shape = ChronosphereSmallShape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (enabled) accent.copy(alpha = 0.48f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
        ),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(top, ChronosphereVisuals.PanelBottom)))
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(28.dp)
                    .height(2.dp)
                    .background(
                        if (enabled) accent else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(100.dp),
                    ),
            )
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
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

private fun blend(base: Color, accent: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = base.red + (accent.red - base.red) * t,
        green = base.green + (accent.green - base.green) * t,
        blue = base.blue + (accent.blue - base.blue) * t,
        alpha = 1f,
    )
}
