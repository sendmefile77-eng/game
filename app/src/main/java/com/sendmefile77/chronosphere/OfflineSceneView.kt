package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.horde.HordeResolvedScenePromptFactory
import com.sendmefile77.chronosphere.horde.HordeSceneView
import com.sendmefile77.chronosphere.scene.ResolvedScene

/**
 * Character-scene image surface.
 *
 * AI Horde is the primary renderer. The previous fully local renderer remains visible while a
 * request is queued and is kept permanently as the failure/offline fallback.
 */
@Composable
internal fun OfflineSceneView(
    scene: ResolvedScene,
    characterKey: String = scene.sceneKey,
    ageYears: Int = 30,
    visualTags: Set<String> = emptySet(),
    visualNumeric: Map<String, Double> = emptyMap(),
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
) {
    val request = remember(scene, characterKey, ageYears, visualTags, visualNumeric) {
        HordeResolvedScenePromptFactory.create(
            scene = scene,
            characterKey = characterKey,
            ageYears = ageYears,
            visualTags = visualTags,
            visualNumeric = visualNumeric,
        )
    }
    HordeSceneView(
        request = request,
        fallbackScene = scene,
        characterKey = characterKey,
        ageYears = ageYears,
        modifier = modifier,
    )
}
