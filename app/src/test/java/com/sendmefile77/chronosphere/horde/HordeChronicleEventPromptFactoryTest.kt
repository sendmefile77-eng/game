package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomicGood
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.history.ActiveHistoricalContextRegistry
import com.sendmefile77.chronosphere.history.HistoryBranch
import com.sendmefile77.chronosphere.people.Dynasty
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HordeChronicleEventPromptFactoryTest {
    @Test
    fun selectsLatestSignificantEventInsteadOfRoutineTradeNoise() {
        val significant = SimulationEvent("war", 12, "WAR_STARTED")
        val events = listOf(
            significant,
            SimulationEvent("trade", 13, "TRADE_FLOW"),
        )

        assertEquals(significant, HordeChronicleEventPromptFactory.latestSignificant(events))
    }

    @Test
    fun chronicleEventsNeverBecomeCharacterReferences() {
        val request = HordeChronicleEventPromptFactory.create(
            SimulationEvent("city", 24, "CITY_CAPTURED", facts = mapOf("settlement" to "Port")),
            people(),
        )

        assertFalse(request.saveResultAsReference)
        assertEquals(null, request.referenceCacheKey)
        assertTrue(request.width > request.height)
    }

    @Test
    fun adultSocialChronicleShowsEraCitySexNotAClosedRoom() {
        val request = HordeChronicleEventPromptFactory.create(
            SimulationEvent(
                id = "social",
                tick = 360,
                code = "ADULT_SOCIAL_EVENT",
                actorIds = listOf("adult-a", "adult-b", "civ"),
                facts = mapOf("eventCode" to "PRIVATE_EVENT", "participants" to "A, B", "settlement" to "Korareach"),
            ),
            people(),
            metallurgicEconomy(),
        )

        assertTrue(request.nsfw)
        assertTrue(request.positivePrompt.contains("forge") || request.positivePrompt.contains("furnace"))
        assertTrue(request.positivePrompt.contains("explicit"))
        assertFalse(request.positivePrompt.contains("non-explicit"))
        assertFalse(request.negativePrompt.contains("explicit sex"))
        assertTrue(request.cacheKey.startsWith("$CHRONICLE_EVENT_CACHE_SCHEMA|"))
        assertNotNull(request.cacheKey)
    }

    @Test
    fun tribalAndMetallurgicCityFramesDiverge() {
        val event = SimulationEvent("era", 10, "ERA_ADVANCED", actorIds = listOf("civ"), facts = mapOf("civilization" to "A"))
        val tribal = HordeChronicleEventPromptFactory.create(event, people(), economy(TechnologyEra.TRIBAL))
        val metal = HordeChronicleEventPromptFactory.create(event, people(), economy(TechnologyEra.METALLURGIC))
        assertNotEquals(tribal.positivePrompt, metal.positivePrompt)
        assertTrue(tribal.positivePrompt.contains("hide") || tribal.positivePrompt.contains("hearth"))
        assertTrue(metal.positivePrompt.contains("forge") || metal.positivePrompt.contains("bronze"))
        assertNotEquals(tribal.cacheKey, metal.cacheKey)
    }

    @Test
    fun persistentEraChoicesRemainVisibleInLaterUnrelatedChronicleFrames() {
        ActiveHistoricalContextRegistry.activate(
            HistoryBranch(
                id = "branch-test",
                name = "test",
                parentBranchId = null,
                forkTick = 0L,
                state = taggedWorld(),
                economyState = economy(TechnologyEra.TRIBAL),
            ),
        )
        try {
            val request = HordeChronicleEventPromptFactory.create(
                SimulationEvent(
                    id = "later-war",
                    tick = 360L,
                    code = "WAR_STARTED",
                    actorIds = listOf("civ"),
                    facts = mapOf("civilization" to "A"),
                ),
                people(),
                economy(TechnologyEra.TRIBAL),
            )

            assertTrue(request.positivePrompt.contains("blood-stained"))
            assertTrue(request.positivePrompt.contains("flint knives"))
            assertTrue(request.positivePrompt.contains("persistent historical way of life"))
            assertTrue(request.cacheKey.contains("era-choice:subsistence:predator_hunters"))
        } finally {
            ActiveHistoricalContextRegistry.clear()
        }
    }

    private fun people(): PeopleState = PeopleState(
        worldSeed = 1L,
        tick = 360,
        persons = listOf(
            person("adult-a", 0L),
            person("adult-b", 0L),
        ),
        dynasties = emptyList<Dynasty>(),
        relationships = emptyList(),
        rulerByCivilization = emptyMap(),
        socialProfiles = emptyList(),
    )

    private fun person(id: String, birthTick: Long): NotablePerson = NotablePerson(
        id = id,
        name = id,
        civilizationId = "civ",
        settlementId = null,
        dynastyId = null,
        birthTick = birthTick,
        role = PersonRole.NOTABLE,
        prestige = 0.5,
        aptitude = 0.5,
    )

    private fun taggedWorld(): LivingPlanetState = LivingPlanetState(
        worldSeed = 1L,
        tick = 360L,
        civilizations = listOf(
            Civilization(
                id = "civ",
                name = "A",
                population = 1_000L,
                stability = 0.6,
                technology = 0.1,
                treasury = 50.0,
                cultureTags = setOf(
                    "era-choice:subsistence:predator_hunters",
                    "policy:predator_hunters",
                    "hist:blood_hunt",
                    "era-choice:breakthrough:stone_tools",
                    "foundation:stone_tools",
                ),
            ),
        ),
        settlements = listOf(
            Settlement("city", "A", "civ", 1, 1, 1_000L, 500.0, 10.0, 0L),
        ),
    )

    private fun metallurgicEconomy(): EconomyState = economy(TechnologyEra.METALLURGIC)

    private fun economy(era: TechnologyEra): EconomyState = EconomyState(
        worldSeed = 1L,
        tick = 360L,
        civilizations = listOf(
            CivilizationEconomy(
                civilizationId = "civ",
                era = era,
                stockpiles = EconomicGood.entries.associateWith { 1.0 },
                production = EconomicGood.entries.associateWith { 1.0 },
                demand = EconomicGood.entries.associateWith { 1.0 },
                shortageIndex = 0.0,
                tradeBalance = 0.0,
                grossOutput = 3.0,
            ),
        ),
    )
}
