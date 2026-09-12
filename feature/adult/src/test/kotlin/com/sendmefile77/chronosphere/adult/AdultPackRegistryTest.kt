package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdultPackRegistryTest {
    private val registry = AdultPackRegistry.bundled()
    private val module = DeterministicAdultModule()

    @Test
    fun bundledPacksPassValidation() {
        AdultPackRegistry.bundled().packs.forEach { pack ->
            assertTrue(AdultPackValidator.validate(pack).isEmpty())
        }
    }

    @Test
    fun classicRequestSelectsClassicPack() {
        val request = sample(tags = setOf("courtly"))
        assertEquals(AdultPackRegistry.PACK_CLASSIC, module.selectedPackId(request))
    }

    @Test
    fun openCultSelectsHardcorePackDeterministically() {
        val request = sample(requestId = "open-rite", tags = setOf("libertine", "fertility_cult"), numeric = mapOf("fertility" to 0.9))
        assertEquals(AdultPackRegistry.PACK_HARDCORE, module.selectedPackId(request))
        assertEquals(module.selectedPackId(request), module.selectedPackId(request))
    }

    @Test
    fun worldSetupPublicSexTagSelectsOpenAdultPack() {
        val request = sample(
            requestId = "setup-public",
            tags = setOf("public_sex"),
            numeric = mapOf("privacy" to 0.2, "body_openness" to 0.85, "lust" to 0.75),
        )
        assertEquals(AdultPackRegistry.PACK_HARDCORE, module.selectedPackId(request))
    }

    @Test
    fun worldSetupBondageTagRaisesBondageRiteWeight() {
        val event = registry.pack(AdultPackRegistry.PACK_HARDCORE).events.first { it.code == "BONDAGE_RITE" }
        val ordinary = registry.eventWeight(event, sample(tags = setOf("open"), numeric = mapOf("lust" to 0.6)))
        val configured = registry.eventWeight(event, sample(tags = setOf("open", "bondage_culture"), numeric = mapOf("lust" to 0.6)))
        assertTrue(configured > ordinary)
    }

    @Test
    fun worldSetupStatusBondsSelectDynasticPack() {
        val request = sample(requestId = "setup-status", tags = setOf("status_bonds"))
        assertEquals(AdultPackRegistry.PACK_DYNASTIC, module.selectedPackId(request))
    }

    @Test
    fun packSelectionIgnoresCultureTagOrder() {
        val a = sample(tags = setOf("royal", "devout", "open"))
        val b = sample(tags = setOf("open", "royal", "devout"))
        assertEquals(module.selectedPackId(a), module.selectedPackId(b))
        assertEquals(module.evaluate(a), module.evaluate(b))
    }

    @Test
    fun numericContextChangesHardcoreWeights() {
        val pack = registry.pack(AdultPackRegistry.PACK_HARDCORE)
        val event = pack.events.first { it.code == "CUM_RITE" }
        val low = registry.eventWeight(event, sample(tags = setOf("fertility_cult"), numeric = mapOf("fertility" to 0.1)))
        val high = registry.eventWeight(event, sample(tags = setOf("fertility_cult"), numeric = mapOf("fertility" to 0.9)))
        assertTrue(high > low)
    }

    @Test
    fun g001ClassicCodesRemainAvailable() {
        val codes = BundledAdultPacks.classicEvents.map { it.code }.toSet()
        assertEquals(
            setOf(
                "COURTSHIP", "UNION", "AFFAIR", "SCANDAL", "DYNASTIC_BOND",
                "FERTILITY_RITE", "PATRONAGE_LIAISON", "CONCUBINAGE", "SACRED_UNION", "TABOO_BREAK",
            ),
            codes,
        )
        val result = module.evaluate(sample())
        assertTrue(result.eventCode in codes)
    }

    @Test
    fun hardcorePackIsNotASanitizedCopyOfClassic() {
        val hardcore = registry.pack(AdultPackRegistry.PACK_HARDCORE).events.map { it.code }.toSet()
        assertTrue(hardcore.contains("ROUGH_COUPLING"))
        assertTrue(hardcore.contains("ANAL_UNION"))
        assertTrue(hardcore.contains("ORGY"))
        assertNotEquals(registry.pack(AdultPackRegistry.PACK_CLASSIC).events.map { it.code }.toSet(), hardcore)
    }

    private fun sample(
        requestId: String = "req-alpha",
        tags: Set<String> = setOf("courtly"),
        numeric: Map<String, Double> = mapOf("tension" to 0.25),
    ): AdultEventRequest = AdultEventRequest(
        requestId = requestId,
        participants = listOf(AdultParticipantRef("person-a", 27), AdultParticipantRef("person-b", 31)),
        context = AdultWorldContext(worldSeed = 424242L, tick = 120L, cultureTags = tags, numericContext = numeric),
    )
}
