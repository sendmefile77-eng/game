package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultMorphologyTest {
    private val module = DeterministicAdultModule()
    private val recipes = AdultVisualRecipeRegistry.bundled()

    @Test
    fun baselineG005RequestStillWorks() {
        val result = module.evaluate(sample())
        assertTrue(result.eventCode in BundledAdultPacks.classicEvents.map { it.code })
        assertTrue(result.mediaCue!!.assetKey.startsWith("adult://recipe/"))
        assertFalse(result.mediaCue!!.assetKey.contains("morph"))
        result.effects.forEach {
            assertTrue(it.magnitude.isFinite())
            assertTrue(it.magnitude in -1.0..1.0)
        }
    }

    @Test
    fun morphologyParsingIsDeterministic() {
        val tags = setOf("lineage:ash", "hybrid_lineage", "arms:2", "courtly")
        val a = AdultMorphologyParser.parse(tags, mapOf(MorphKeys.DIVERGENCE to 0.2))
        val b = AdultMorphologyParser.parse(setOf("courtly", "arms:2", "hybrid_lineage", "lineage:ash"), mapOf(MorphKeys.DIVERGENCE to 0.2))
        assertEquals(a.lineageIds, b.lineageIds)
        assertEquals(a.hybridLineage, b.hybridLineage)
        assertEquals(a.arms, b.arms)
    }

    @Test
    fun nonFiniteMorphologyIgnored() {
        val morph = AdultMorphologyParser.parse(setOf("courtly"), mapOf(MorphKeys.DIVERGENCE to Double.NaN, MorphKeys.ADMIXTURE to Double.POSITIVE_INFINITY))
        assertFalse(morph.signaled)
        assertEquals(module.evaluate(sample()), module.evaluate(sample(numeric = mapOf(MorphKeys.DIVERGENCE to Double.NaN))))
    }

    @Test
    fun baselineRigRejectedForIncompatibleBodyPlan() {
        val union = BundledAdultPacks.classicEvents.first { it.code == "UNION" }
        val fourArms = sample(tags = setOf("courtly", "arms:4", "legs:2", "posture:upright"))
        val compatible = recipes.compatible(union, fourArms)
        assertTrue(compatible.none { it.rigPlan == RigPlan.BASELINE })
        assertTrue(compatible.any { it.id == "union.divergent.quad" })
    }

    @Test
    fun specializedHybridRecipeEligible() {
        val union = BundledAdultPacks.classicEvents.first { it.code == "UNION" }
        val hybrid = sample(tags = setOf("courtly", "hybrid_lineage", "mixed_ancestry", "lineage:ash", "ancestry:major:ash"))
        val pool = recipes.compatible(union, hybrid)
        assertTrue(pool.any { it.id == "union.hybrid.blend" })
        val cue = recipes.mediaCue(union, hybrid, AdultFingerprint.of(hybrid))
        assertTrue(cue.tags.contains("hybrid_lineage"))
        assertEquals(cue, recipes.mediaCue(union, hybrid, AdultFingerprint.of(hybrid)))
    }

    @Test
    fun missingMorphologyUsesBaseline() {
        val union = BundledAdultPacks.classicEvents.first { it.code == "UNION" }
        val pool = recipes.compatible(union, sample())
        assertTrue(pool.any { it.rigPlan == RigPlan.BASELINE })
        assertTrue(pool.none { it.rigPlan == RigPlan.DIVERGENT })
    }

    @Test
    fun noCompatibleRecipeUsesMorphSafeFallback() {
        val union = BundledAdultPacks.classicEvents.first { it.code == "UNION" }
        val odd = sample(tags = setOf("courtly", "arms:8", "legs:6", "eyes:5", "covering:scales", "tail"))
        val selected = recipes.select(union, odd, AdultFingerprint.of(odd))
        assertEquals(SAFE_MORPH_FALLBACK.id, selected.id)
        val cue = recipes.mediaCue(union, odd, 1L)
        assertEquals("adult://recipe/${SAFE_MORPH_FALLBACK.id}", cue.assetKey)
    }

    @Test
    fun hybridCueStableAcrossTagOrder() {
        val a = module.evaluate(sample(tags = setOf("courtly", "hybrid_lineage", "lineage:ash")))
        val b = module.evaluate(sample(tags = setOf("lineage:ash", "courtly", "hybrid_lineage")))
        assertEquals(a.eventCode, b.eventCode)
        assertEquals(a.mediaCue, b.mediaCue)
    }

    private fun sample(
        tags: Set<String> = setOf("courtly"),
        numeric: Map<String, Double> = mapOf(SocialContextKeys.TENSION to 0.25),
    ): AdultEventRequest = AdultEventRequest(
        requestId = "req-alpha",
        participants = listOf(AdultParticipantRef("person-1", 27), AdultParticipantRef("person-2", 31)),
        context = AdultWorldContext(worldSeed = 424242L, tick = 120L, cultureTags = tags, numericContext = numeric),
    )
}
