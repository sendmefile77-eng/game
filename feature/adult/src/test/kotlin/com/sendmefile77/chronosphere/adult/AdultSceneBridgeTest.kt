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
    private val recipes = AdultVisualRecipeRegistry.bundled()

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
        assertTrue(first.layerKeys.contains("recipe:card.undressed.baseline"))
        assertFalse(first.fallbackUsed)
    }

    @Test
    fun undressedVisualDescriptorMatchesResolvedRecipe() {
        val request = adult(tags = setOf("open"))
        val scene = bridge.undressedCharacterCard(request)
        val visual = bridge.undressedCharacterVisual(request)

        assertEquals(scene.recipeId, visual.recipeId)
        assertEquals(scene.bodyRigKey, visual.rigLayout)
        assertEquals(scene.poseKey, visual.poseKey)
        assertEquals(scene.backgroundKey, visual.settingKey)
        assertEquals(scene.cameraKey, visual.cameraKey)
        assertEquals(scene.lightingKey, visual.lightingKey)
        assertEquals("character_undress", visual.intent)
        assertEquals("explicit", visual.explicitness)
        assertTrue(visual.participants.all { it.ageYears >= 18 })
        assertTrue("undressed" in visual.effectTags)
    }

    @Test
    fun hybridMorphologyKeepsCompatibleRig() {
        val hybrid = adult(tags = setOf("courtly", "hybrid_lineage", "mixed_ancestry", "lineage:ash"))
        val fingerprint = AdultFingerprint.of(hybrid)
        val selected = recipes.selectCard(hybrid, AdultWardrobeState.UNDRESSED, fingerprint)
        val nude = bridge.undressedCharacterCard(hybrid)
        assertEquals(selected.id, nude.recipeId)
        assertEquals(selected.rigLayout, nude.bodyRigKey)
        assertEquals(WardrobeState.UNDRESSED, nude.wardrobeState)
        assertTrue(selected.rigPlan == RigPlan.HYBRID || selected.rigPlan == RigPlan.BASELINE)
        assertEquals(nude.sceneKey, bridge.undressedCharacterCard(hybrid).sceneKey)
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
        assertFalse(AdultUndressPolicy.allowsAges(listOf(17)))
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
    fun eventVisualDescriptorMatchesDeterministicEventScene() {
        val request = adult(tags = setOf("open", "era_medieval"), participants = 2)
        val scene = bridge.eventScene(request)
        val visual = bridge.eventVisual(request)

        assertEquals(scene.recipeId, visual.recipeId)
        assertEquals(scene.bodyRigKey, visual.rigLayout)
        assertEquals(scene.poseKey, visual.poseKey)
        assertEquals(scene.backgroundKey, visual.settingKey)
        assertEquals(scene.cameraKey, visual.cameraKey)
        assertEquals(scene.lightingKey, visual.lightingKey)
        assertEquals("event", visual.intent)
        assertTrue(visual.eventCode.isNotBlank())
        assertTrue(visual.mediaTags.any { it.startsWith("event:") })
        assertTrue(visual.mediaTags.any { it.startsWith("camera:") })
        assertTrue(visual.participants.all { it.ageYears >= 18 })
    }

    @Test
    fun noArgBridgeIsLoadableByName() {
        val loaded = Class.forName("com.sendmefile77.chronosphere.adult.AdultSceneBridge").getDeclaredConstructor().newInstance()
        assertTrue(loaded is AdultSceneBridge)
    }

    @Test
    fun dressedIntentIsPortrait() {
        val request = adult()
        val recipe = recipes.selectCard(request, AdultWardrobeState.DRESSED, AdultFingerprint.of(request))
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
