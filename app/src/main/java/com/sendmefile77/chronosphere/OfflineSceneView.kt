package com.sendmefile77.chronosphere

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

@Composable
internal fun OfflineSceneView(
    scene: ResolvedScene,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        SceneAssetRepository(
            assets = context.assets,
            resourceLoader = context.classLoader,
        )
    }
    val layers = remember(scene.sceneKey, repository) {
        repository.layersFor(scene).mapNotNull { entry ->
            repository.bitmap(entry)?.let { bitmap -> entry to bitmap }
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (layers.isEmpty()) {
                DeterministicSceneFallback(scene)
            } else {
                layers.forEach { (_, bitmap) ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

/**
 * Renderer-neutral resilience state shown when no compatible local raster layer exists.
 *
 * Deliberately does not draw a fake body from geometric primitives. A missing production
 * portrait should look like a polished unavailable-media state, not like final character art.
 * The resolved wardrobe/rig state is still preserved so installing a compatible local pack
 * later replaces only presentation, never character identity or simulation state.
 */
@Composable
private fun DeterministicSceneFallback(scene: ResolvedScene) {
    val background = MaterialTheme.colorScheme.surfaceVariant
    val backgroundDeep = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val foreground = MaterialTheme.colorScheme.onSurfaceVariant
    val token = remember(scene.sceneKey) { scene.sceneKey.hashCode() }
    val rigLabel = remember(scene.bodyRigKey) { rigLabel(scene.bodyRigKey) }
    val wardrobeLabel = remember(scene.wardrobeState) { wardrobeLabel(scene.wardrobeState) }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        background,
                        backgroundDeep,
                    ),
                ),
                size = size,
            )

            val center = Offset(size.width * 0.5f, size.height * 0.43f)
            val baseRadius = size.minDimension * 0.20f
            val phase = ((token ushr 4) and 0x0F) / 15f

            drawCircle(
                color = accent.copy(alpha = 0.12f),
                radius = baseRadius * 1.38f,
                center = center,
            )
            drawCircle(
                color = accent.copy(alpha = 0.42f),
                radius = baseRadius,
                center = center,
                style = Stroke(width = 2.2f),
            )
            drawCircle(
                color = secondary.copy(alpha = 0.34f),
                radius = baseRadius * (0.58f + phase * 0.10f),
                center = center,
                style = Stroke(width = 1.3f),
            )

            val spokeCount = 6
            repeat(spokeCount) { index ->
                val angle = (index.toFloat() / spokeCount.toFloat()) * (Math.PI * 2.0) + phase * 0.35
                val inner = baseRadius * 0.72f
                val outer = baseRadius * 1.08f
                val start = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * inner,
                    center.y + kotlin.math.sin(angle).toFloat() * inner,
                )
                val end = Offset(
                    center.x + kotlin.math.cos(angle).toFloat() * outer,
                    center.y + kotlin.math.sin(angle).toFloat() * outer,
                )
                drawLine(
                    color = foreground.copy(alpha = 0.18f),
                    start = start,
                    end = end,
                    strokeWidth = 1.1f,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "Локальний портрет ще не встановлено",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "$rigLabel · $wardrobeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = foreground.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun wardrobeLabel(state: WardrobeState): String = when (state) {
    WardrobeState.DRESSED -> "одягнений стан"
    WardrobeState.PARTIAL -> "частковий одяг"
    WardrobeState.UNDRESSED -> "без одягу"
    WardrobeState.DAMAGED -> "пошкоджений одяг"
}

private fun rigLabel(key: String): String {
    val normalized = key.lowercase()
    return when {
        "tail" in normalized -> "морфологія з хвостом"
        "quad" in normalized -> "чотириногий план тіла"
        "scaled" in normalized || "scale" in normalized -> "луската морфологія"
        "hybrid" in normalized -> "гібридна морфологія"
        "divergent" in normalized || "morph" in normalized -> "дивергентна морфологія"
        else -> "базова морфологія"
    }
}
