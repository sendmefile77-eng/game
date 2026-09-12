package com.sendmefile77.chronosphere.horde

import com.sendmefile77.chronosphere.people.Dynasty
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertFalse(request.nsfw)
        assertFalse(request.saveResultAsReference)
        assertEquals(null, request.referenceCacheKey)
        assertTrue(request.width > request.height)
    }

    @Test
    fun adultSocialChroniclePreviewStaysNonExplicit() {
        val request = HordeChronicleEventPromptFactory.create(
            SimulationEvent(
                id = "social",
                tick = 360,
                code = "ADULT_SOCIAL_EVENT",
                actorIds = listOf("adult-a", "adult-b", "civ"),
                facts = mapOf("eventCode" to "PRIVATE_EVENT", "participants" to "A, B"),
            ),
            people(),
        )

        assertFalse(request.nsfw)
        assertTrue(request.positivePrompt.contains("non-explicit"))
        assertTrue(request.negativePrompt.contains("explicit sex"))
        assertNotNull(request.cacheKey)
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
}
