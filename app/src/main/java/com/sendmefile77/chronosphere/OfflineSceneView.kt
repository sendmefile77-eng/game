package com.sendmefile77.chronosphere

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.horde.HordeAdultActionPromptFactory
import com.sendmefile77.chronosphere.horde.HordeAdultScenePromptFactory
import com.sendmefile77.chronosphere.horde.HordeResolvedScenePromptFactory
import com.sendmefile77.chronosphere.horde.HordeSceneView
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

/** Character-scene image surface with Local Dream/Horde rendering and local fallback. */
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
    galleryCapture: GalleryCapture? = null,
    modifier: Modifier = Modifier.fillMaxWidth().height(220.dp),
) {
    val adultHistoricalTags = adultVisual?.mediaTags.orEmpty().filterTo(linkedSetOf()) { tag ->
        HISTORICAL_VISUAL_PREFIXES.any(tag::startsWith)
    }
    val sceneHistoricalTags = scene.layerKeys.filterTo(linkedSetOf()) { tag ->
        HISTORICAL_VISUAL_PREFIXES.any(tag::startsWith)
    }
    val mergedVisualTags = remember(visualTags, adultHistoricalTags, sceneHistoricalTags) {
        (visualTags + adultHistoricalTags + sceneHistoricalTags).toSortedSet()
    }
    val fallbackScene = remember(scene, adultHistoricalTags, sceneHistoricalTags) {
        val tags = adultHistoricalTags + sceneHistoricalTags
        if (tags.isEmpty()) scene else scene.copy(layerKeys = (scene.layerKeys + tags).distinct())
    }
    val adultFullBody = ageYears >= 18 && (
        actionPlan != null ||
            scene.wardrobeState == WardrobeState.UNDRESSED ||
            adultVisual != null
        )
    val request = remember(
        scene,
        characterKey,
        ageYears,
        mergedVisualTags,
        visualNumeric,
        technologyEra,
        adultVisual,
        actionPlan,
    ) {
        when {
            actionPlan != null && ageYears >= 18 -> HordeAdultActionPromptFactory.create(
                scene = scene,
                plan = actionPlan,
                visualTags = mergedVisualTags,
                visualNumeric = visualNumeric,
                technologyEra = technologyEra,
                adultVisual = adultVisual,
            )
            ageYears >= 18 && scene.wardrobeState == WardrobeState.UNDRESSED -> HordeResolvedScenePromptFactory.create(
                scene = scene,
                characterKey = characterKey,
                ageYears = ageYears,
                visualTags = mergedVisualTags,
                visualNumeric = visualNumeric,
                technologyEra = technologyEra,
            )
            adultVisual != null -> HordeAdultScenePromptFactory.createCharacter(
                scene = scene,
                descriptor = adultVisual,
                characterKey = characterKey,
                ageYears = ageYears,
                visualTags = mergedVisualTags,
                visualNumeric = visualNumeric,
                technologyEra = technologyEra,
            )
            else -> HordeResolvedScenePromptFactory.create(
                scene = scene,
                characterKey = characterKey,
                ageYears = ageYears,
                visualTags = mergedVisualTags,
                visualNumeric = visualNumeric,
                technologyEra = technologyEra,
            )
        }
    }

    Surface(
        modifier = modifier,
        shape = ChronosphereCardShape,
        color = Color(0xFF03070A),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)),
        shadowElevation = 4.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .clip(RoundedCornerShape(17.dp)),
        ) {
            HordeSceneView(
                request = request,
                fallbackScene = fallbackScene,
                characterKey = characterKey,
                ageYears = ageYears,
                fitFullBody = adultFullBody,
                galleryCapture = galleryCapture,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val HISTORICAL_VISUAL_PREFIXES = listOf(
    "cloth:",
    "jewel:",
    "hair:",
    "body-norm:",
    "arch:",
    "set-bias:",
    "cosmetic:",
    "publicness:",
    "hist:",
    "foundation:",
    "policy:",
    "era-choice:",
    "era:",
    "civ:",
    "branch:",
    "role:",
    "status:",
)
