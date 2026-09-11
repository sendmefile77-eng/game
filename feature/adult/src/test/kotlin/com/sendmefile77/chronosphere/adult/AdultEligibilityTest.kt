package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultEligibilityTest {
    private val module = DeterministicAdultModule()
    private val registry = AdultPackRegistry.bundled()

    @Test
    fun pairEventsRejectSoloParticipants() {
        val union = BundledAdultPacks.classicEvents.first { it.code == "UNION" }
        assertFalse(AdultEligibility.isEligible(union, sample(participants = 1)))
        assertTrue(AdultEligibility.isEligible(union, sample(participants = 2)))
    }

    @Test
    fun orgyRequiresGroup() {
        val orgy = registry.pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "ORGY" }
        assertFalse(AdultEligibility.isEligible(orgy, openRequest(participants = 2)))
        assertTrue(AdultEligibility.isEligible(orgy, openRequest(participants = 4, numeric = mapOf(SocialContextKeys.BODY_OPENNESS to 0.8))))
    }

    @Test
    fun forbiddenTagsBlockAnalInAustereCulture() {
        val anal = registry.pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "ANAL_UNION" }
        assertFalse(AdultEligibility.isEligible(anal, sample(tags = setOf("puritan", "libertine"), participants = 2)))
        assertTrue(AdultEligibility.isEligible(anal, sample(tags = setOf("libertine"), participants = 2)))
    }

    @Test
    fun numericGateBlocksWhenValueOutOfRange() {
        val publicSex = registry.pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "PUBLIC_SEX" }
        val closed = openRequest(numeric = mapOf(SocialContextKeys.PRIVACY to 0.9, SocialContextKeys.BODY_OPENNESS to 0.8))
        val openPlaza = openRequest(numeric = mapOf(SocialContextKeys.PRIVACY to 0.1, SocialContextKeys.BODY_OPENNESS to 0.8))
        assertFalse(AdultEligibility.isEligible(publicSex, closed))
        assertTrue(AdultEligibility.isEligible(publicSex, openPlaza))
    }

    @Test
    fun missingOptionalGateDoesNotBlock() {
        val publicSex = registry.pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "PUBLIC_SEX" }
        assertTrue(AdultEligibility.isEligible(publicSex, openRequest()))
    }

    @Test
    fun noEligibleEventUsesDeterministicFallback() {
        val onlyOrgy = AdultContentPack(
            id = "only-orgy",
            matchTags = emptySet(),
            priority = 1,
            events = listOf(registry.pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "ORGY" }),
        )
        val isolated = AdultPackRegistry(listOf(onlyOrgy))
        val request = sample(participants = 1)
        val first = isolated.selectEvent(onlyOrgy, request, AdultFingerprint.of(request))
        val second = isolated.selectEvent(onlyOrgy, request, AdultFingerprint.of(request))
        assertEquals("CONTEXT_HOLD", first.code)
        assertEquals(first, second)
    }

    @Test
    fun tagOrderDoesNotChangeEligibilityOrResult() {
        val a = sample(tags = setOf("royal", "devout", "open"), participants = 2)
        val b = sample(tags = setOf("open", "royal", "devout"), participants = 2)
        assertEquals(module.evaluate(a), module.evaluate(b))
    }

    @Test
    fun g002ClassicTwoPersonPathStillUsesClassicCodes() {
        val request = sample(tags = setOf("courtly"), participants = 2)
        val result = module.evaluate(request)
        assertEquals(AdultPackRegistry.PACK_CLASSIC, module.selectedPackId(request))
        assertTrue(result.eventCode in BundledAdultPacks.classicEvents.map { it.code }.toSet())
        assertNotEquals("CONTEXT_HOLD", result.eventCode)
        result.effects.forEach { effect ->
            assertTrue(effect.magnitude.isFinite())
            assertTrue(effect.magnitude in -1.0..1.0)
        }
    }

    @Test
    fun ineligibleRulesHaveZeroWeight() {
        val union = BundledAdultPacks.classicEvents.first { it.code == "UNION" }
        assertEquals(0.0, registry.eventWeight(union, sample(participants = 1)), 0.0)
        assertTrue(registry.eventWeight(union, sample(participants = 2)) > 0.0)
    }

    @Test
    fun eligibilityValidationRejectsBrokenRanges() {
        val broken = AdultEventRule(
            code = "BAD",
            intimacy = 0.1,
            scandal = 0.1,
            fertility = 0.1,
            setting = "x",
            mediaKey = "adult://scene/bad/x",
            mediaTags = setOf("sex"),
            minParticipants = 3,
            maxParticipants = 1,
            requiredTags = setOf("open"),
            forbiddenTags = setOf("open"),
            numericGates = listOf(NumericGate("lust", min = 0.9, max = 0.1)),
        )
        val errors = AdultPackValidator.validate(AdultContentPack("x", emptySet(), 1, listOf(broken)))
        assertTrue(errors.any { it.contains("maxParticipants") })
        assertTrue(errors.any { it.contains("required tag also forbidden") })
        assertTrue(errors.any { it.contains("min > max") })
    }

    private fun openRequest(
        participants: Int = 2,
        numeric: Map<String, Double> = emptyMap(),
    ) = sample(requestId = "open-rite", tags = setOf("libertine", "fertility_cult"), participants = participants, numeric = numeric)

    private fun sample(
        requestId: String = "req-alpha",
        tags: Set<String> = setOf("courtly"),
        participants: Int = 2,
        numeric: Map<String, Double> = mapOf(SocialContextKeys.TENSION to 0.25),
    ): AdultEventRequest = AdultEventRequest(
        requestId = requestId,
        participants = (1..participants).map { AdultParticipantRef("person-$it", 24 + it) },
        context = AdultWorldContext(worldSeed = 424242L, tick = 120L, cultureTags = tags, numericContext = numeric),
    )
}
