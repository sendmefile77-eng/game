package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultHistoricalContextTest {
    private val module = DeterministicAdultModule()
    private val registry = AdultPackRegistry.bundled()

    @Test
    fun missingHistoryDoesNotBreakLegacyPath() {
        val request = sample(tags = setOf("courtly"), numeric = mapOf(SocialContextKeys.TENSION to 0.2))
        val ctx = AdultHistoricalContext.from(request)
        assertFalse(ctx.present)
        val first = module.evaluate(request)
        val second = module.evaluate(request)
        assertEquals(first, second)
        assertTrue(first.mediaCue!!.assetKey.startsWith("adult://recipe/"))
    }

    @Test
    fun unknownHistoryPolicyIsIgnoredSafely() {
        val request = sample(tags = setOf("courtly", "history_policy:unknown_widget_v9"))
        val ctx = AdultHistoricalContext.from(request)
        assertTrue(ctx.present)
        assertEquals(setOf("unknown_widget_v9"), ctx.policies)
        val result = module.evaluate(request)
        assertTrue(result.eventCode.isNotBlank())
        result.effects.forEach { assertTrue(it.magnitude in -1.0..1.0) }
    }

    @Test
    fun sameHistoryYieldsSameAdultResult() {
        val request = warCourt()
        assertEquals(module.evaluate(request), module.evaluate(request))
        assertEquals(AdultHistoricalContext.from(request), AdultHistoricalContext.from(request))
    }

    @Test
    fun differentBranchesYieldDifferentContext() {
        val a = warCourt(branch = "alpha")
        val b = warCourt(branch = "beta")
        assertNotEquals(AdultHistoricalContext.from(a).branchId, AdultHistoricalContext.from(b).branchId)
        val cueA = module.evaluate(a).mediaCue!!.tags
        val cueB = module.evaluate(b).mediaCue!!.tags
        assertTrue(cueA.any { it == "branch:alpha" })
        assertTrue(cueB.any { it == "branch:beta" })
    }

    @Test
    fun eraChangeAppearsInVisualTags() {
        val tribal = module.evaluate(sample(tags = setOf("civ:riverfolk", EraTags.TRIBAL, "foundation:settlement")))
        val industrial = module.evaluate(sample(tags = setOf("civ:riverfolk", EraTags.INDUSTRIAL, "foundation:settlement")))
        assertTrue(tribal.mediaCue!!.tags.contains("era:${EraTags.TRIBAL}"))
        assertTrue(industrial.mediaCue!!.tags.contains("era:${EraTags.INDUSTRIAL}"))
        assertTrue(tribal.mediaCue!!.tags.any { it.startsWith("cloth:") })
        assertNotEquals(
            tribal.mediaCue!!.tags.filter { it.startsWith("cloth:") },
            industrial.mediaCue!!.tags.filter { it.startsWith("cloth:") },
        )
    }

    @Test
    fun warRaisesPowerEventWeightAgainstSameCulture() {
        val event = registry.pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "POWER_FUCK" }
        val peace = registry.eventWeight(event, sample(tags = setOf("martial", "civ:iron", "foundation:authority", EraTags.MEDIEVAL)))
        val war = registry.eventWeight(
            event,
            sample(
                tags = setOf("martial", "civ:iron", "foundation:authority", EraTags.MEDIEVAL, "history_process:war"),
                numeric = mapOf(SocialContextKeys.WAR_PRESSURE to 0.9),
            ),
        )
        assertTrue(war > peace)
    }

    @Test
    fun wealthAndExchangeOpenTheCultureWhileShortageClosesIt() {
        val boomTags = AdultHistoricalCulture.expand(
            setOf("civ:port", "foundation:exchange", "history_process:settlement_expansion", EraTags.URBAN),
            mapOf(SocialContextKeys.WEALTH to 0.8, SocialContextKeys.URBANIZATION to 0.8),
        )
        val crashTags = AdultHistoricalCulture.expand(
            setOf("civ:port", "foundation:exchange", "history_process:shortage", EraTags.URBAN),
            mapOf(SocialContextKeys.SCARCITY to 0.85, SocialContextKeys.WEALTH to 0.1),
        )
        assertTrue(boomTags.contains("libertine") || boomTags.contains("open"))
        assertTrue(crashTags.contains("austere") || crashTags.contains("conservative"))
    }

    @Test
    fun migrationChangesVisualJewelryLanguage() {
        val isolated = AdultHistoricalVisualRules.mediaTags(sample(tags = setOf("civ:vale", "foundation:settlement", EraTags.AGRARIAN)))
        val mixed = AdultHistoricalVisualRules.mediaTags(sample(tags = setOf("civ:vale", "foundation:settlement", EraTags.AGRARIAN, "history_process:migration")))
        assertTrue(mixed.any { it.startsWith("jewel:") && it.contains("foreign") })
        assertFalse(isolated.any { it.contains("foreign") })
    }

    @Test
    fun sameProcessDifferentFoundationsDiverge() {
        val martialAuth = AdultHistoricalCulture.identityTags(
            AdultHistoricalContext.from(sample(tags = setOf("civ:a", "foundation:authority", "history_process:war"), numeric = mapOf(SocialContextKeys.PIETY to 0.8))),
            setOf("civ:a", "foundation:authority", "history_process:war"),
        )
        val openTrade = AdultHistoricalCulture.identityTags(
            AdultHistoricalContext.from(sample(tags = setOf("civ:b", "foundation:exchange", "history_process:war"))),
            setOf("civ:b", "foundation:exchange", "history_process:war"),
        )
        assertTrue(martialAuth.contains("austere"))
        assertTrue(openTrade.contains("open") || openTrade.contains("hedonist"))
        assertNotEquals(martialAuth, openTrade)
    }

    @Test
    fun cultureTagsNeverInventMorphology() {
        val tags = AdultHistoricalCulture.expand(setOf("civ:x", "history_process:population_divergence", "foundation:settlement"), emptyMap())
        assertFalse(tags.any { it.startsWith("arms:") || it.startsWith("tail") || it.contains("morph") })
        val cue = module.evaluate(sample(tags = setOf("civ:x", "history_process:population_divergence", EraTags.AGRARIAN))).mediaCue!!
        assertFalse(cue.tags.contains("tail"))
    }

    @Test
    fun rulerAndClericDoNotSharePreferredCodes() {
        val ruler = AdultHistoricalCulture.preferredEventCodes(AdultHistoricalContext.from(sample(tags = setOf("person_role:ruler", "history_process:dynastic_transition"))))
        val cleric = AdultHistoricalCulture.preferredEventCodes(AdultHistoricalContext.from(sample(tags = setOf("person_role:cleric", "history_process:dynastic_transition"))))
        assertTrue(ruler.contains("DYNASTIC_BOND") || ruler.contains("PATRONAGE_LIAISON"))
        assertTrue(cleric.contains("SACRED_UNION") || cleric.contains("FERTILITY_RITE"))
    }

    @Test
    fun tagOrderDoesNotChangeHistoricalResult() {
        val a = sample(tags = setOf(EraTags.MEDIEVAL, "history_process:war", "civ:iron", "foundation:authority"))
        val b = sample(tags = setOf("foundation:authority", "civ:iron", "history_process:war", EraTags.MEDIEVAL))
        assertEquals(module.evaluate(a), module.evaluate(b))
    }

    private fun warCourt(branch: String = "mainline"): AdultEventRequest = sample(
        tags = setOf("civ:iron", "history_branch:$branch", "foundation:authority", "history_process:war", "person_role:commander", EraTags.MEDIEVAL),
        numeric = mapOf(SocialContextKeys.WAR_PRESSURE to 0.8, SocialContextKeys.PIETY to 0.4),
    )

    private fun sample(tags: Set<String> = setOf("courtly"), numeric: Map<String, Double> = emptyMap()): AdultEventRequest =
        AdultEventRequest(
            requestId = "hist-req",
            participants = listOf(AdultParticipantRef("person-1", 29), AdultParticipantRef("person-2", 33)),
            context = AdultWorldContext(worldSeed = 77L, tick = 240L, cultureTags = tags, numericContext = numeric),
        )
}
