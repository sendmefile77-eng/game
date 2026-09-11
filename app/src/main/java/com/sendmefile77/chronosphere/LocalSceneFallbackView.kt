package com.sendmefile77.chronosphere

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.scene.ResolvedScene

/** Local rendering path kept as an always-available fallback when AI Horde is slow or unreachable. */
@Composable
internal fun LocalSceneFallbackView(
    scene: ResolvedScene,
    characterKey: String = scene.sceneKey,
    ageYears: Int = 30,
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
                RasterCharacterPortrait(
                    characterKey = characterKey,
                    ageYears = ageYears,
                    wardrobeState = scene.wardrobeState,
                    modifier = Modifier.fillMaxSize(),
                )
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
