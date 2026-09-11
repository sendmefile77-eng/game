package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultContextSignalsTest {
    private val module = DeterministicAdultModule()
    private val registry = AdultPackRegistry.bundled()
    private val recipes = AdultVisualRecipeRegistry.bundled()

    @Test
    fun warPressureRaisesPowerFuckWeight() {
        val event = registry.pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "POWER_FUCK" }
        val calm = registry.eventWeight(event, sample(tags = setOf("martial"), numeric = mapOf(SocialContextKeys.WAR_PRESSURE to 0.1)))
        val war = registry.eventWeight(event, sample(tags = setOf("martial"), numeric = mapOf(SocialContextKeys.WAR_PRESSURE to 0.9)))
        assertTrue(war > calm)
    }

    @Test
    fun wealthRaisesPatronageWeightAndScarcityLowersIt() {
        val event = BundledAdultPacks.classicEvents.first { it.code == "PATRONAGE_LIAISON" }
        val rich = registry.eventWeight(event, sample(numeric = mapOf(SocialContextKeys.WEALTH to 0.9, SocialContextKeys.SCARCITY to 0.0)))
        val poor = registry.eventWeight(event, sample(numeric = mapOf(SocialContextKeys.WEALTH to 0.1, SocialContextKeys.SCARCITY to 0.9)))
        assertTrue(rich > poor)
    }

    @Test
    fun industrialEraBoostsIndustrialRecipeOverGenericWhenPresent() {
        val union = BundledAdultPacks.classicEvents.first { it.code == "UNION" }
        val industrial = recipes.compatible(union, sample(tags = setOf("courtly", EraTags.INDUSTRIAL)))
        val agrarian = recipes.compatible(union, sample(tags = setOf("courtly", EraTags.AGRARIAN)))
        assertTrue(industrial.any { it.id == "union.loft.industrial" })
        assertTrue(agrarian.none { it.id == "union.loft.industrial" })
        assertTrue(agrarian.any { it.id.startsWith("union.chamber") })
    }

    @Test
    fun missingOptionalKeysStayCompatibleWithG004() {
        val result = module.evaluate(sample())
        assertTrue(result.eventCode in BundledAdultPacks.classicEvents.map { it.code })
        assertTrue(result.mediaCue!!.assetKey.startsWith("adult://recipe/"))
        result.effects.forEach {
            assertTrue(it.magnitude.isFinite())
            assertTrue(it.magnitude in -1.0..1.0)
        }
    }

    @Test
    fun nonFiniteNumericInputsDoNotBreakSelection() {
        val request = sample(numeric = mapOf(SocialContextKeys.WEALTH to Double.NaN, SocialContextKeys.WAR_PRESSURE to Double.POSITIVE_INFINITY))
        val first = module.evaluate(request)
        val second = module.evaluate(request)
        assertEquals(first, second)
        first.effects.forEach { assertTrue(it.magnitude.isFinite()) }
    }

    @Test
    fun selectionRemainsDeterministicWithEraTags() {
        val request = sample(tags = setOf("libertine", EraTags.INDUSTRIAL), numeric = mapOf(SocialContextKeys.WEALTH to 0.6))
        assertEquals(module.evaluate(request), module.evaluate(request))
    }

    @Test
    fun eraTagOrderDoesNotChangeResult() {
        val a = module.evaluate(sample(tags = setOf(EraTags.INDUSTRIAL, "courtly")))
        val b = module.evaluate(sample(tags = setOf("courtly", EraTags.INDUSTRIAL)))
        assertEquals(a, b)
    }

    @Test
    fun g004MediaCueStillNamespaced() {
        val cue = module.evaluate(sample()).mediaCue!!
        assertTrue(cue.assetKey.startsWith("adult://recipe/"))
        assertTrue(cue.tags.any { it.startsWith("pose:") })
        assertFalse(cue.tags.any { it.isBlank() })
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
