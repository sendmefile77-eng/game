package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultUndressTest {
    private val module = DeterministicAdultModule()
    private val recipes = AdultVisualRecipeRegistry.bundled()

    @Test
    fun undressedStateIsDeterministicForAdults() {
        val request = adult()
        val first = module.characterCardCue(request, undressed = true)
        val second = module.characterCardCue(request, undressed = true)
        assertEquals(first, second)
        assertTrue(first.assetKey.startsWith("adult://recipe/"))
        assertTrue(first.tags.contains(AdultUndressPolicy.STATE_TAG))
        assertTrue(first.tags.any { it == "wardrobe:wardrobe.undressed" || it.startsWith("wardrobe:wardrobe.undressed") || it == "wardrobe:wardrobe.undressed" })
        assertTrue(first.tags.contains("wardrobe:wardrobe.undressed") || first.tags.contains("wardrobe:wardrobe.undressed"))
        assertTrue(first.tags.any { it.startsWith("wardrobe:") && it.contains("undressed") })
    }

    @Test
    fun undressedUsesSameMorphologyContinuityAsDressed() {
        val hybrid = adult(tags = setOf("courtly", "hybrid_lineage", "mixed_ancestry", "lineage:ash"))
        val dressed = module.characterCardCue(hybrid, undressed = false)
        val nude = module.characterCardCue(hybrid, undressed = true)
        assertTrue(dressed.tags.contains("hybrid_lineage"))
        assertTrue(nude.tags.contains("hybrid_lineage"))
        assertTrue(nude.tags.contains("lineage:ash"))
        assertTrue(nude.tags.contains(AdultUndressPolicy.STATE_TAG))
        assertFalse(dressed.tags.contains(AdultUndressPolicy.STATE_TAG))
        assertTrue(recipes.compatibleCards(hybrid, AdultWardrobeState.UNDRESSED).any { it.id == "card.undressed.hybrid" })
    }

    @Test
    fun incompatibleBodyPlanDoesNotUseBaselineUndressedRig() {
        val odd = adult(tags = setOf("courtly", "arms:4", "legs:2"))
        val pool = recipes.compatibleCards(odd, AdultWardrobeState.UNDRESSED)
        assertTrue(pool.none { it.rigPlan == RigPlan.BASELINE })
        assertTrue(pool.any { it.id == "card.undressed.quad" })
        val cue = module.characterCardCue(odd, undressed = true)
        assertTrue(cue.tags.contains("arms:4"))
        assertFalse(cue.assetKey.contains("baseline"))
    }

    @Test
    fun noCompatibleUndressedRecipeUsesMorphSafeFallback() {
        val odd = adult(tags = setOf("courtly", "arms:8", "legs:6", "eyes:5", "covering:scales", "tail"))
        val selected = recipes.selectCard(odd, AdultWardrobeState.UNDRESSED, AdultFingerprint.of(odd))
        assertEquals(SAFE_UNDRESSED_FALLBACK.id, selected.id)
    }

    @Test
    fun under18RejectedByContractAndUndressPolicy() {
        assertFalse(AdultUndressPolicy.allows(listOf(17)))
        assertFalse(AdultUndressPolicy.allows(listOf(22, 17)))
        assertTrue(AdultUndressPolicy.allows(listOf(18, 41)))
        var threw = false
        try {
            AdultEventRequest(
                requestId = "minor",
                participants = listOf(AdultParticipantRef("teen", 17)),
                context = AdultWorldContext(1L, 1L),
            )
        } catch (error: IllegalArgumentException) {
            threw = true
            assertTrue(error.message.orEmpty().contains("adults only"))
        }
        assertTrue(threw)
    }

    @Test
    fun sceneEvaluateStillIgnoresCardRecipes() {
        val result = module.evaluate(adult())
        assertTrue(result.eventCode in BundledAdultPacks.classicEvents.map { it.code })
        assertFalse(result.mediaCue!!.assetKey.contains("card."))
    }

    private fun adult(
        tags: Set<String> = setOf("courtly"),
    ): AdultEventRequest = AdultEventRequest(
        requestId = "card-alpha",
        participants = listOf(AdultParticipantRef("person-1", 27)),
        context = AdultWorldContext(worldSeed = 424242L, tick = 120L, cultureTags = tags, numericContext = mapOf(SocialContextKeys.TENSION to 0.1)),
    )
}
