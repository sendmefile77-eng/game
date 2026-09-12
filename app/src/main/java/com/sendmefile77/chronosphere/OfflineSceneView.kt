package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.horde.HordeAdultActionPromptFactory
import com.sendmefile77.chronosphere.horde.HordeAdultScenePromptFactory
import com.sendmefile77.chronosphere.horde.HordeResolvedScenePromptFactory
import com.sendmefile77.chronosphere.horde.HordeSceneView
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

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
    technologyEra: TechnologyEra? = null,
    adultVisual: AdultVisualSceneDescriptor? = null,
    actionPlan: AdultActionPlan? = null,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
) {
    val adultFullBody = ageYears >= 18 && (
        actionPlan != null ||
            scene.wardrobeState == WardrobeState.UNDRESSED ||
            adultVisual != null
        )
    val request = remember(
        scene,
        characterKey,
        ageYears,
        visualTags,
        visualNumeric,
        technologyEra,
        adultVisual,
        actionPlan,
    ) {
        when {
            actionPlan != null && ageYears >= 18 -> HordeAdultActionPromptFactory.create(
                scene = scene,
                plan = actionPlan,
                visualTags = visualTags,
                visualNumeric = visualNumeric,
                technologyEra = technologyEra,
                adultVisual = adultVisual,
            )
            ageYears >= 18 && scene.wardrobeState == WardrobeState.UNDRESSED -> HordeResolvedScenePromptFactory.create(
                scene = scene,
                characterKey = characterKey,
                ageYears = ageYears,
                visualTags = visualTags,
                visualNumeric = visualNumeric,
                technologyEra = technologyEra,
            )
            adultVisual != null -> HordeAdultScenePromptFactory.createCharacter(
                scene = scene,
                descriptor = adultVisual,
                characterKey = characterKey,
                ageYears = ageYears,
                visualTags = visualTags,
                visualNumeric = visualNumeric,
                technologyEra = technologyEra,
            )
            else -> HordeResolvedScenePromptFactory.create(
                scene = scene,
                characterKey = characterKey,
                ageYears = ageYears,
                visualTags = visualTags,
                visualNumeric = visualNumeric,
                technologyEra = technologyEra,
            )
        }
    }
    HordeSceneView(
        request = request,
        fallbackScene = scene,
        characterKey = characterKey,
        ageYears = ageYears,
        fitFullBody = adultFullBody,
        modifier = modifier,
    )
}
