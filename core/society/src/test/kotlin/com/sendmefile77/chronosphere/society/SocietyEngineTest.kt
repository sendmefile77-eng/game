package com.sendmefile77.chronosphere.society

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult
import com.sendmefile77.chronosphere.adultcontracts.CoreEffectKind
import com.sendmefile77.chronosphere.adultcontracts.CoreEffectProposal
import com.sendmefile77.chronosphere.adultcontracts.MediaCue
import com.sendmefile77.chronosphere.adultcontracts.NoOpAdultModule
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.people.RelationshipKind
import com.sendmefile77.chronosphere.people.SocialProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SocietyEngineTest {
    @Test
    fun deterministicAdultBridgeAppliesValidatedEffectsAndExcludesMinors() {
        val world = sampleWorld(tick = 12L)
        val people = samplePeople(tick = 0L)
        val moduleA = RecordingModule()
        val moduleB = RecordingModule()

        val first = SocietyEngine(moduleA).advance(0L, world, people, null)
        val second = SocietyEngine(moduleB).advance(0L, world, people, null)

        assertEquals(first, second)
        assertEquals(1, moduleA.requests.size)
        assertTrue(moduleA.requests.single().participants.all { it.ageYears >= 18 })
        assertFalse(moduleA.requests.single().participants.any { it.entityId == "minor" })
        assertEquals(1, first.events.size)
        assertEquals("ADULT_SOCIAL_EVENT", first.events.single().code)
        assertEquals("adult://recipe/test", first.events.single().facts["mediaKey"])
        assertTrue(first.people.relationships.any { it.kind == RelationshipKind.LOVER })

        val primaryId = moduleA.requests.single().participants.first().entityId
        val beforePrestige = people.persons.first { it.id == primaryId }.prestige
        val afterPrestige = first.people.persons.first { it.id == primaryId }.prestige
        assertTrue(afterPrestige > beforePrestige)

        assertTrue(first.world.totalPopulation > world.totalPopulation)
        assertTrue(first.people.profile("civ-a")!!.bodyOpenness > people.profile("civ-a")!!.bodyOpenness)
    }

    @Test
    fun noopModuleLeavesStateUntouched() {
        val world = sampleWorld(tick = 12L)
        val people = samplePeople(tick = 0L)
        val result = SocietyEngine(NoOpAdultModule).advance(0L, world, people, null)
        assertEquals(world, result.world)
        assertEquals(people.copy(tick = 12L), result.people)
        assertTrue(result.events.isEmpty())
        assertTrue(result.mediaCues.isEmpty())
    }

    @Test
    fun invalidEffectIsRejectedBeforeCoreMutation() {
        val world = sampleWorld(tick = 12L)
        val people = samplePeople(tick = 0L)
        val result = SocietyEngine(InvalidModule()).advance(0L, world, people, null)
        assertEquals(world, result.world)
        assertEquals(people.copy(tick = 12L), result.people)
        assertTrue(result.events.isEmpty())
    }

    private class RecordingModule : AdultModule {
        override val contractVersion: Int = ADULT_CONTRACT_VERSION
        val requests = mutableListOf<AdultEventRequest>()

        override fun evaluate(request: AdultEventRequest): AdultModuleResult {
            requests += request
            val primary = request.participants.first().entityId
            val partner = request.participants.getOrElse(1) { request.participants.first() }.entityId
            return AdultModuleResult(
                requestId = request.requestId,
                eventCode = "TEST_SOCIAL_EVENT",
                effects = listOf(
                    CoreEffectProposal(CoreEffectKind.RELATIONSHIP, primary, 0.8, "REL_TEST"),
                    CoreEffectProposal(CoreEffectKind.RELATIONSHIP, partner, 0.7, "REL_TEST"),
                    CoreEffectProposal(CoreEffectKind.REPUTATION, primary, 0.5, "REP_TEST"),
                    CoreEffectProposal(CoreEffectKind.DEMOGRAPHY, primary, 0.8, "DEM_TEST"),
                    CoreEffectProposal(CoreEffectKind.CULTURE, "culture", 0.6, "CUL_TEST"),
                ),
                mediaCue = MediaCue("adult://recipe/test", setOf("recipe:test", "era:era_agrarian")),
            )
        }
    }

    private class InvalidModule : AdultModule {
        override val contractVersion: Int = ADULT_CONTRACT_VERSION
        override fun evaluate(request: AdultEventRequest): AdultModuleResult = AdultModuleResult(
            requestId = request.requestId,
            eventCode = "BROKEN",
            effects = listOf(
                CoreEffectProposal(CoreEffectKind.REPUTATION, request.participants.first().entityId, 2.0, "BAD"),
            ),
        )
    }

    private fun sampleWorld(tick: Long): LivingPlanetState = LivingPlanetState(
        worldSeed = 77L,
        tick = tick,
        civilizations = listOf(
            Civilization("civ-a", "Ардан", 2_000L, 0.72, 0.16, 300.0, setOf("courtly")),
        ),
        settlements = listOf(
            Settlement("city-a", "Астра", "civ-a", 3, 4, 2_000L, 1_200.0, 90.0, 0L),
        ),
    )

    private fun samplePeople(tick: Long): PeopleState = PeopleState(
        worldSeed = 77L,
        tick = tick,
        persons = listOf(
            NotablePerson("adult-a", "Ара", "civ-a", "city-a", null, -30L * 12L, null, PersonRole.RULER, 0.50, 0.65),
            NotablePerson("adult-b", "Берен", "civ-a", "city-a", null, -28L * 12L, null, PersonRole.NOTABLE, 0.48, 0.61),
            NotablePerson("minor", "Міра", "civ-a", "city-a", null, -10L * 12L, null, PersonRole.DYNAST, 0.30, 0.55),
        ),
        dynasties = emptyList(),
        relationships = emptyList(),
        rulerByCivilization = mapOf("civ-a" to "adult-a"),
        socialProfiles = listOf(
            SocialProfile(
                civilizationId = "civ-a",
                privacy = 0.35,
                bodyOpenness = 0.65,
                pairBonding = 0.50,
                jealousy = 0.40,
                fertilityNorm = 0.70,
                piety = 0.30,
                statusHierarchy = 0.55,
                socialTension = 0.35,
                tags = setOf("courtly"),
            ),
        ),
    )
}
