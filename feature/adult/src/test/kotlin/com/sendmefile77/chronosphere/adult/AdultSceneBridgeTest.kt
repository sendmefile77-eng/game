package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import com.sendmefile77.chronosphere.scene.SceneIntent
import com.sendmefile77.chronosphere.scene.WardrobeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultSceneBridgeTest {
    private val bridge = AdultSceneBridge()

    @Test
    fun dressedCardResolvesDeterministically() {
        val request = adult()
        val first = bridge.dressedCharacterCard(request)
        val second = bridge.dressedCharacterCard(request)
        assertEquals(first, second)
        assertEquals(first.sceneKey, second.sceneKey)
        assertEquals(WardrobeState.DRESSED, first.wardrobeState)
        assertFalse(first.fallbackUsed)
        assertEquals("card.dressed.baseline", first.recipeId)
        assertEquals(AdultSceneMapper.PACK_ID, first.packId)
    }

    @Test
    fun undressedCardResolvesDeterministically() {
        val request = adult()
        val first = bridge.undressedCharacterCard(request)
        val second = bridge.undressedCharacterCard(request)
        assertEquals(first, second)
        assertEquals(WardrobeState.UNDRESSED, first.wardrobeState)
        assertEquals("card.undressed.baseline", first.recipeId)
        assertTrue(first.layerKeys.any { it.contains("undressed") || it.contains("nude") || it.contains("recipe:card.undressed.baseline") })
        assertFalse(first.fallbackUsed)
    }

    @Test
    fun hybridMorphologyKeepsCompatibleRig() {
        val hybrid = adult(tags = setOf("courtly", "hybrid_lineage", "mixed_ancestry", "lineage:ash"))
        val dressed = bridge.dressedCharacterCard(hybrid)
        val nude = bridge.undressedCharacterCard(hybrid)
        assertEquals(dressed.sceneKey, bridge.dressedCharacterCard(hybrid).sceneKey)
        assertEquals("card.undressed.hybrid", nude.recipeId)
        assertEquals(WardrobeState.UNDRESSED, nude.wardrobeState)
        assertTrue(nude.bodyRigKey.contains("hybrid") || nude.recipeId.contains("hybrid"))
    }

    @Test
    fun divergentFallbackKeepsUndressedState() {
        val odd = adult(tags = setOf("courtly", "arms:8", "legs:6", "eyes:5", "covering:scales", "tail"))
        val scene = bridge.undressedCharacterCard(odd)
        assertTrue(scene.fallbackUsed)
        assertEquals(WardrobeState.UNDRESSED, scene.wardrobeState)
        assertEquals(SAFE_UNDRESSED_FALLBACK.id, scene.recipeId)
    }

    @Test
    fun under18UndressImpossible() {
        var contractThrew = false
        try {
            AdultEventRequest(
                requestId = "minor",
                participants = listOf(AdultParticipantRef("teen", 17)),
                context = AdultWorldContext(1L, 1L),
            )
        } catch (error: IllegalArgumentException) {
            contractThrew = true
        }
        assertTrue(contractThrew)
        assertFalse(AdultUndressPolicy.allows(listOf(17)))
    }

    @Test
    fun eventRecipeIdSurvivesTranslation() {
        val request = adult(participants = 2)
        val scene = bridge.eventScene(request)
        val again = bridge.eventScene(request)
        assertEquals(scene, again)
        val recipe = AdultSceneMapper.findAdultRecipe(scene.recipeId)
        assertTrue(recipe != null)
        assertEquals(recipe!!.rigLayout, scene.bodyRigKey)
        assertEquals(recipe.poseKey, scene.poseKey)
        assertEquals(recipe.settingKey, scene.backgroundKey)
        assertEquals(recipe.cameraKey, scene.cameraKey)
        assertEquals(recipe.lightingKey, scene.lightingKey)
        assertTrue(scene.layerKeys.contains("recipe:${recipe.id}"))
    }

    @Test
    fun noArgBridgeIsLoadableByName() {
        val loaded = Class.forName("com.sendmefile77.chronosphere.adult.AdultSceneBridge").getDeclaredConstructor().newInstance()
        assertTrue(loaded is AdultSceneBridge)
    }

    @Test
    fun dressedIntentIsPortrait() {
        val request = adult()
        val recipe = AdultVisualRecipeRegistry.bundled().selectCard(request, AdultWardrobeState.DRESSED, AdultFingerprint.of(request))
        val sceneRequest = AdultSceneMapper.sceneRequest(request, recipe, SceneIntent.PORTRAIT, "card:dressed:${recipe.id}")
        assertEquals(SceneIntent.PORTRAIT, sceneRequest.intent)
        assertEquals(WardrobeState.DRESSED, sceneRequest.requestedWardrobeState)
    }

    private fun adult(
        tags: Set<String> = setOf("courtly"),
        participants: Int = 1,
    ): AdultEventRequest = AdultEventRequest(
        requestId = "bridge-alpha",
        participants = (1..participants).map { AdultParticipantRef("person-$it", 24 + it) },
        context = AdultWorldContext(
            worldSeed = 424242L,
            tick = 120L,
            cultureTags = tags,
            numericContext = mapOf(SocialContextKeys.TENSION to 0.1),
        ),
    )
}
