package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HordeResolvedScenePromptFactoryTest {
    @Test
    fun adultUndressedSceneIsMarkedNsfw() {
        val request = HordeResolvedScenePromptFactory.create(
            scene = scene(WardrobeState.UNDRESSED),
            characterKey = "person-adult",
            ageYears = 28,
        )

        assertTrue(request.nsfw)
        assertTrue(request.apiPrompt().contains("###"))
    }

    @Test
    fun minorDressedSceneIsStrictlySfw() {
        val request = HordeResolvedScenePromptFactory.create(
            scene = scene(WardrobeState.DRESSED),
            characterKey = "person-minor",
            ageYears = 16,
        )

        assertFalse(request.nsfw)
        assertTrue(request.negativePrompt.contains("nudity"))
        assertTrue(request.positivePrompt.contains("strictly nonsexual"))
    }

    @Test
    fun minorUndressedSceneIsRejectedBeforeNetwork() {
        var rejected = false
        try {
            HordeResolvedScenePromptFactory.create(
                scene = scene(WardrobeState.UNDRESSED),
                characterKey = "person-minor",
                ageYears = 16,
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
    }

    private fun scene(wardrobeState: WardrobeState): ResolvedScene = ResolvedScene(
        sceneKey = "test:${wardrobeState.name}",
        recipeId = "test.recipe",
        packId = "test.pack",
        packVersion = 1,
        styleId = "test.style",
        wardrobeState = wardrobeState,
        bodyRigKey = "rig.human.card",
        poseKey = "pose.card.neutral",
        backgroundKey = "bg.card.neutral",
        cameraKey = "cam.card.full",
        lightingKey = "light.card.soft",
        layerKeys = emptyList(),
        fallbackUsed = false,
    )
}
