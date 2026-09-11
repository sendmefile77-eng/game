package com.sendmefile77.chronosphere

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.scene.ResolvedScene

@Composable
internal fun OfflineSceneView(
    scene: ResolvedScene,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { SceneAssetRepository(context.assets) }
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
 * Built-in drawable fallback. It is intentionally generic and never changes the resolved
 * wardrobe state or body rig; real packs replace it with local aligned raster layers.
 */
@Composable
private fun DeterministicSceneFallback(scene: ResolvedScene) {
    val background = MaterialTheme.colorScheme.surfaceVariant
    val backgroundDeep = MaterialTheme.colorScheme.surface
    val figure = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
    val isMorph = scene.bodyRigKey.contains("morph", ignoreCase = true) ||
        scene.bodyRigKey.contains("divergent", ignoreCase = true) ||
        scene.bodyRigKey.contains("hybrid", ignoreCase = true)
    val hasTail = scene.bodyRigKey.contains("tail", ignoreCase = true)

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(listOf(background, backgroundDeep)),
            size = size,
        )
        val cx = size.width * 0.5f
        val headY = size.height * 0.24f
        val headR = size.minDimension * 0.075f
        val shoulderY = size.height * 0.38f
        val hipY = size.height * 0.68f
        val torsoWidth = size.width * if (isMorph) 0.29f else 0.25f

        drawCircle(accent, radius = headR * 2.5f, center = Offset(cx, headY))
        drawCircle(figure, radius = headR, center = Offset(cx, headY))
        drawRoundRect(
            color = figure,
            topLeft = Offset(cx - torsoWidth / 2f, shoulderY),
            size = Size(torsoWidth, hipY - shoulderY),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(torsoWidth * 0.28f),
        )

        val limbStroke = size.minDimension * 0.035f
        fun limb(from: Offset, to: Offset) {
            drawLine(figure, from, to, strokeWidth = limbStroke)
        }
        limb(Offset(cx - torsoWidth * 0.42f, shoulderY + 8f), Offset(cx - size.width * 0.24f, size.height * 0.58f))
        limb(Offset(cx + torsoWidth * 0.42f, shoulderY + 8f), Offset(cx + size.width * 0.24f, size.height * 0.58f))
        if (isMorph) {
            limb(Offset(cx - torsoWidth * 0.38f, shoulderY + size.height * 0.08f), Offset(cx - size.width * 0.30f, size.height * 0.48f))
            limb(Offset(cx + torsoWidth * 0.38f, shoulderY + size.height * 0.08f), Offset(cx + size.width * 0.30f, size.height * 0.48f))
        }
        limb(Offset(cx - torsoWidth * 0.24f, hipY), Offset(cx - size.width * 0.13f, size.height * 0.91f))
        limb(Offset(cx + torsoWidth * 0.24f, hipY), Offset(cx + size.width * 0.13f, size.height * 0.91f))

        if (hasTail) {
            val tail = Path().apply {
                moveTo(cx + torsoWidth * 0.35f, hipY - 6f)
                cubicTo(
                    cx + size.width * 0.32f,
                    size.height * 0.72f,
                    cx + size.width * 0.36f,
                    size.height * 0.86f,
                    cx + size.width * 0.23f,
                    size.height * 0.90f,
                )
            }
            drawPath(tail, figure, style = Stroke(width = limbStroke * 0.65f))
        }
    }
}
