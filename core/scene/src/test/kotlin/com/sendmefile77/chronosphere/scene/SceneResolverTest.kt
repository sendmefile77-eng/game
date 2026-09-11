package com.sendmefile77.chronosphere.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneResolverTest {
    private val pack = ScenePack(
        id = "test-pack",
        version = 1,
        styleId = "painted-2d",
        recipes = listOf(
            SceneRecipe(
                id = "portrait.human",
                packId = "test-pack",
                packVersion = 1,
                styleId = "painted-2d",
                intents = setOf(SceneIntent.PORTRAIT),
                supportedRigFamilies = setOf("human"),
                bodyRigKey = "rig.human",
                poseKey = "pose.portrait",
                backgroundKey = "bg.neutral",
                cameraKey = "cam.bust",
                lightingKey = "light.soft",
            ),
            SceneRecipe(
                id = "undress.human",
                packId = "test-pack",
                packVersion = 1,
                styleId = "painted-2d",
                intents = setOf(SceneIntent.CHARACTER_UNDRESS),
                supportedRigFamilies = setOf("human"),
                wardrobeState = WardrobeState.UNDRESSED,
                bodyRigKey = "rig.human",
                poseKey = "pose.portrait",
                backgroundKey = "bg.neutral",
                cameraKey = "cam.full",
                lightingKey = "light.soft",
            ),
            SceneRecipe(
                id = "undress.morph.safe",
                packId = "test-pack",
                packVersion = 1,
                styleId = "painted-2d",
                intents = setOf(SceneIntent.CHARACTER_UNDRESS),
                wardrobeState = WardrobeState.UNDRESSED,
                bodyRigKey = "rig.silhouette",
                poseKey = "pose.neutral",
                backgroundKey = "bg.neutral",
                cameraKey = "cam.full",
                lightingKey = "light.soft",
                layerKeys = listOf("morph-safe"),
                fallbackPriority = 100,
            ),
            SceneRecipe(
                id = "portrait.fallback",
                packId = "test-pack",
                packVersion = 1,
                styleId = "painted-2d",
                intents = setOf(SceneIntent.PORTRAIT),
                bodyRigKey = "rig.silhouette",
                poseKey = "pose.neutral",
                backgroundKey = "bg.neutral",
                cameraKey = "cam.bust",
                lightingKey = "light.soft",
                fallbackPriority = 100,
            ),
        ),
    )

    @Test
    fun sameRequestAlwaysResolvesSameScene() {
        val resolver = SceneResolver(pack)
        val request = portraitRequest()
        assertEquals(resolver.resolve(request), resolver.resolve(request))
    }

    @Test
    fun differentTokenChangesStableSceneKey() {
        val resolver = SceneResolver(pack)
        val a = resolver.resolve(portraitRequest(token = 1L))
        val b = resolver.resolve(portraitRequest(token = 2L))
        assertNotEquals(a.sceneKey, b.sceneKey)
    }

    @Test
    fun fallbackNeverCompetesWithNormalCompatibleRecipe() {
        val resolved = SceneResolver(pack).resolve(portraitRequest())
        assertEquals("portrait.human", resolved.recipeId)
        assertFalse(resolved.fallbackUsed)
    }

    @Test
    fun incompatibleRigUsesDeterministicFallback() {
        val request = portraitRequest(rig = "four-arm-hybrid")
        val resolved = SceneResolver(pack).resolve(request)
        assertEquals("portrait.fallback", resolved.recipeId)
        assertTrue(resolved.fallbackUsed)
    }

    @Test
    fun adultUndressIntentKeepsUndressedWardrobeState() {
        val request = SceneRequest(
            worldSeed = 42L,
            eventId = "card-person-a-undress",
            rngToken = 9L,
            intent = SceneIntent.CHARACTER_UNDRESS,
            participants = listOf(SceneParticipant("person-a", 31, "human")),
            requestedWardrobeState = WardrobeState.UNDRESSED,
        )
        val resolved = SceneResolver(pack).resolve(request)
        assertEquals("undress.human", resolved.recipeId)
        assertEquals(WardrobeState.UNDRESSED, resolved.wardrobeState)
    }

    @Test(expected = IllegalArgumentException::class)
    fun under18CannotCreateUndressRequest() {
        SceneRequest(
            worldSeed = 42L,
            eventId = "card-minor-undress",
            rngToken = 1L,
            intent = SceneIntent.CHARACTER_UNDRESS,
            participants = listOf(SceneParticipant("minor", 17, "human")),
            requestedWardrobeState = WardrobeState.UNDRESSED,
        )
    }

    @Test
    fun unknownMorphRigUsesUndressedMorphSafeFallbackWithoutChangingState() {
        val request = SceneRequest(
            worldSeed = 42L,
            eventId = "card-hybrid-undress",
            rngToken = 11L,
            intent = SceneIntent.CHARACTER_UNDRESS,
            participants = listOf(
                SceneParticipant(
                    entityId = "hybrid-a",
                    ageYears = 28,
                    rigFamily = "six-arm-scaled",
                    tags = setOf("hybrid_lineage", "arms:6", "covering:scales"),
                    numeric = mapOf("morph_divergence" to 0.8),
                ),
            ),
            requestedWardrobeState = WardrobeState.UNDRESSED,
        )
        val first = SceneResolver(pack).resolve(request)
        val second = SceneResolver(pack).resolve(request)
        assertEquals(first, second)
        assertEquals("undress.morph.safe", first.recipeId)
        assertEquals(WardrobeState.UNDRESSED, first.wardrobeState)
        assertTrue(first.fallbackUsed)
    }

    private fun portraitRequest(token: Long = 7L, rig: String = "human") = SceneRequest(
        worldSeed = 42L,
        eventId = "portrait-person-a",
        rngToken = token,
        intent = SceneIntent.PORTRAIT,
        participants = listOf(SceneParticipant("person-a", 30, rig)),
    )
}
