package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomicGood
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
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
        assertTrue(request.cacheKey.contains("v7"))
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
