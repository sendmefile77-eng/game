package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HordeResolvedScenePromptFactoryTest {
    @Test
    fun adultUndressedSceneIsMarkedNsfwAndNeverBecomesReference() {
        val request = HordeResolvedScenePromptFactory.create(
            scene = scene(WardrobeState.UNDRESSED, camera = "cam.card.full"),
            characterKey = "person-adult",
            ageYears = 28,
        )

        assertTrue(request.nsfw)
        assertTrue(request.apiPrompt().contains("###"))
        assertFalse(request.saveResultAsReference)
        assertTrue(request.referenceDenoisingStrength > 0.60)
        assertTrue(request.qualityPriority)
        assertEquals(768, request.width)
        assertEquals(1152, request.height)
        assertEquals(22, request.steps)
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
                scene = scene(WardrobeState.UNDRESSED, camera = "cam.card.full"),
                characterKey = "person-minor",
                ageYears = 16,
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
    }

    @Test
    fun sameCharacterAlwaysGetsSameVisualIdentity() {
        val first = HordeCharacterVisualProfile.from("person-civ-a-ruler-0")
        val second = HordeCharacterVisualProfile.from("person-civ-a-ruler-0")
        assertEquals(first, second)
        assertEquals(BiologicalSex.fromStableKey("person-civ-a-ruler-0"), first.sex)
    }

    @Test
    fun dressedPortraitBecomesStableSafeReference() {
        val characterKey = "person-civ-a-ruler-0"
        val identity = HordeCharacterVisualProfile.from(characterKey)
        val request = HordeResolvedScenePromptFactory.create(
            scene = scene(WardrobeState.DRESSED),
            characterKey = characterKey,
            ageYears = 42,
        )
        val sexWord = if (identity.sex == BiologicalSex.FEMALE) "female" else "male"

        assertTrue(request.positivePrompt.contains(sexWord))
        assertTrue(request.positivePrompt.contains(identity.hairColor))
        assertTrue(request.positivePrompt.contains(identity.eyeColor))
        assertTrue(request.cacheKey.startsWith("horde-resolved-scene-v10|"))
        assertTrue(request.referenceCacheKey?.startsWith("horde-character-reference-v4|") == true)
        assertTrue(request.saveResultAsReference)
        assertFalse(request.nsfw)
        assertTrue(request.qualityPriority)
    }

    @Test
    fun evolvedBodyPlanIsExplicitlyDescribedAndChangesReferenceIdentity() {
        val baseline = HordeResolvedScenePromptFactory.create(
            scene = scene(WardrobeState.DRESSED),
            characterKey = "person-evolved",
            ageYears = 35,
            visualTags = setOf("arms:2", "legs:2", "eyes:2", "posture:upright", "covering:bare_skin"),
        )
        val evolved = HordeResolvedScenePromptFactory.create(
            scene = scene(WardrobeState.DRESSED),
            characterKey = "person-evolved",
            ageYears = 35,
            visualTags = setOf("arms:4", "legs:2", "eyes:4", "tail", "posture:upright", "covering:scales"),
            visualNumeric = mapOf("morph_cranial" to 1.25, "morph_limbs" to 1.30),
        )

        assertTrue(evolved.positivePrompt.contains("exactly 4 arms"))
        assertTrue(evolved.positivePrompt.contains("exactly 4 eyes"))
        assertTrue(evolved.positivePrompt.contains("tail"))
        assertTrue(evolved.positivePrompt.contains("scales"))
        assertTrue(evolved.positivePrompt.contains("enlarged cranium"))
        assertTrue(evolved.positivePrompt.contains("elongated limbs"))
        assertTrue(baseline.referenceCacheKey != evolved.referenceCacheKey)
    }

    private fun scene(
        wardrobeState: WardrobeState,
        camera: String = "cam.card.portrait",
    ): ResolvedScene = ResolvedScene(
        sceneKey = "test:${wardrobeState.name}:$camera",
        recipeId = "test.recipe",
        packId = "test.pack",
        packVersion = 1,
        styleId = "test.style",
        wardrobeState = wardrobeState,
        bodyRigKey = "rig.human.card",
        poseKey = "pose.card.neutral",
        backgroundKey = "bg.card.neutral",
        cameraKey = camera,
        lightingKey = "light.card.soft",
        layerKeys = emptyList(),
        fallbackUsed = false,
    )
}
