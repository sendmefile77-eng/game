package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.AdultActionParticipant
import com.sendmefile77.chronosphere.AdultActionPlan
import com.sendmefile77.chronosphere.AdultActionType
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HordeAdultActionPromptFactoryTest {
    @Test
    fun oralScenePutsTheActBeforeAnyIdlePortraitLanguage() {
        val request = HordeAdultActionPromptFactory.create(
            scene = scene(),
            plan = plan(AdultActionType.ORAL),
        )
        assertTrue(request.nsfw)
        assertTrue(request.cacheKey.startsWith("horde-adult-action-v6|"))
        assertTrue(request.positivePrompt.contains("blowjob") || request.positivePrompt.contains("cunnilingus"))
        assertTrue(request.positivePrompt.contains("mouth"))
        assertTrue(request.positivePrompt.contains("exact face match"))
        assertTrue(request.positivePrompt.contains("the only sex act"))
        assertFalse(request.positivePrompt.contains("natural standing or seated pose"))
        assertFalse(request.negativePrompt.contains("multiple people"))
        assertTrue(request.negativePrompt.contains("standing idle portrait"))
        assertTrue(request.negativePrompt.contains("footjob"))
        assertTrue(request.preferredModels.first().contains("illustrious", ignoreCase = true))
        assertTrue(request.referenceDenoisingStrength >= 0.70)
        assertFalse(request.saveResultAsReference)
    }

    @Test
    fun tribalAndMedievalFootjobsAreDifferentScenes() {
        val tribal = HordeAdultActionPromptFactory.create(
            scene = scene(),
            plan = plan(AdultActionType.FOOTJOB),
            technologyEra = TechnologyEra.TRIBAL,
        )
        val medieval = HordeAdultActionPromptFactory.create(
            scene = scene(),
            plan = plan(AdultActionType.FOOTJOB),
            technologyEra = TechnologyEra.MEDIEVAL,
        )
        assertTrue(tribal.positivePrompt.contains("footjob"))
        assertTrue(tribal.positivePrompt.contains("hide tent") || tribal.positivePrompt.contains("reed hut"))
        assertTrue(tribal.positivePrompt.contains("ochre") || tribal.positivePrompt.contains("mammoth"))
        assertTrue(medieval.positivePrompt.contains("rope bed") || medieval.positivePrompt.contains("candle"))
        assertTrue(tribal.negativePrompt.contains("modern tiled bathroom"))
        assertNotEquals(tribal.cacheKey, medieval.cacheKey)
        assertNotEquals(tribal.seed, medieval.seed)
    }

    @Test
    fun firstNudeCardPromptForbidsClothes() {
        val request = HordeResolvedScenePromptFactory.create(
            scene = scene(),
            characterKey = "adult-a",
            ageYears = 24,
        )
        assertTrue(request.nsfw)
        assertTrue(request.positivePrompt.contains("completely naked"))
        assertTrue(request.negativePrompt.contains("dress"))
        assertTrue(request.cacheKey.startsWith("$RESOLVED_SCENE_CACHE_SCHEMA|"))
    }

    private fun plan(type: AdultActionType): AdultActionPlan = AdultActionPlan(
        type = type,
        sequence = 1,
        primary = AdultActionParticipant("adult-a", "A", 24, BiologicalSex.FEMALE),
        partner = AdultActionParticipant("adult-b", "B", 26, BiologicalSex.MALE),
    )

    private fun scene(): ResolvedScene = ResolvedScene(
        sceneKey = "card",
        recipeId = "card.undressed.human",
        packId = "base",
        packVersion = 1,
        styleId = "card",
        wardrobeState = WardrobeState.UNDRESSED,
        bodyRigKey = "rig.human.card",
        poseKey = "pose.card.neutral",
        backgroundKey = "bg.card.neutral",
        cameraKey = "cam.card.full",
        lightingKey = "light.card.soft",
        layerKeys = emptyList(),
        fallbackUsed = false,
    )
}
