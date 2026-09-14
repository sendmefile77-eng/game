package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChronicleDecisionCatalogTest {
    private val people = PeopleState(
        worldSeed = 1L,
        tick = 0L,
        persons = emptyList(),
        dynasties = emptyList(),
        relationships = emptyList(),
        rulerByCivilization = emptyMap(),
        socialProfiles = emptyList(),
    )
    private val economy = EconomyState(
        worldSeed = 1L,
        tick = 0L,
        civilizations = listOf(
            CivilizationEconomy("civ-a", TechnologyEra.TRIBAL, emptyMap(), emptyMap(), emptyMap(), 0.0, 0.0, 0.0),
            CivilizationEconomy("civ-b", TechnologyEra.TRIBAL, emptyMap(), emptyMap(), emptyMap(), 0.0, 0.0, 0.0),
        ),
    )

    @Test
    fun setupFoundingDoesNotBlockTheFirstTurn() {
        ChronicleDecisionMailbox.drain()
        val event = SimulationEvent(
            id = "founding-setup",
            tick = 0L,
            code = "SETTLEMENT_FOUNDED",
            actorIds = listOf("civ-a"),
            facts = mapOf("civilization" to "Нері", "settlement" to "Erenreach"),
        )

        assertNull(ChronicleDecisionCatalog.latestUnresolved(listOf(event), people, economy))
    }

    @Test
    fun laterSettlementFoundingProducesThreeRealChoices() {
        ChronicleDecisionMailbox.drain()
        val event = SimulationEvent(
            id = "founding-1",
            tick = 12L,
            code = "SETTLEMENT_FOUNDED",
            actorIds = listOf("civ-a"),
            facts = mapOf("civilization" to "Нері", "settlement" to "Erenreach"),
        )

        val decision = ChronicleDecisionCatalog.latestUnresolved(listOf(event), people, economy)
        assertNotNull(decision)
        assertEquals(3, decision!!.options.size)
        assertTrue(decision.options.all { it.targetCivilizationId == "civ-a" })
    }

    @Test
    fun successorStateGetsItsOwnFoundingDecision() {
        ChronicleDecisionMailbox.drain()
        val event = SimulationEvent(
            id = "state-founded-civ-b-1200",
            tick = 1_200L,
            code = "STATE_FOUNDED",
            actorIds = listOf("civ-b", "civ-a"),
            facts = mapOf(
                "civilization" to "Вільні землі Істри",
                "parent" to "Нері",
                "settlement" to "Істра",
            ),
        )

        val decision = ChronicleDecisionCatalog.forEvent(event, people, economy)

        assertNotNull(decision)
        assertEquals(3, decision!!.options.size)
        assertTrue(decision.options.all { it.targetCivilizationId == "civ-b" })
    }

    @Test
    fun warOffersEnemyPressureWhenSecondCivilizationIsKnown() {
        ChronicleDecisionMailbox.drain()
        val event = SimulationEvent(
            id = "war-1",
            tick = 12L,
            code = "WAR_STARTED",
            actorIds = listOf("civ-a", "civ-b"),
            facts = mapOf("a" to "Нері", "b" to "Варки"),
        )

        val decision = ChronicleDecisionCatalog.forEvent(event, people, economy)
        assertNotNull(decision)
        assertTrue(decision!!.options.any { it.targetCivilizationId == "civ-b" })
    }

    @Test
    fun queuedOrPersistedDecisionDoesNotAppearAgain() {
        ChronicleDecisionMailbox.drain()
        val source = SimulationEvent(
            id = "era-1",
            tick = 24L,
            code = "ERA_ADVANCED",
            actorIds = listOf("civ-a"),
            facts = mapOf("civilization" to "Нері"),
        )
        val decision = ChronicleDecisionCatalog.latestUnresolved(listOf(source), people, economy)!!
        ChronicleDecisionMailbox.enqueue(decision.options.first())
        assertNull(ChronicleDecisionCatalog.latestUnresolved(listOf(source), people, economy))
        ChronicleDecisionMailbox.drain()

        val applied = SimulationEvent(
            id = "chronicle-era-1-era-push",
            tick = 25L,
            code = "INTERVENTION_TECH_BOOST",
            actorIds = listOf("civ-a"),
            facts = mapOf("sourceEventId" to "era-1", "choiceId" to "era-push"),
        )
        assertNull(ChronicleDecisionCatalog.latestUnresolved(listOf(source, applied), people, economy))
    }

    @Test
    fun resolvingNewestForkDoesNotResurrectAnOlderFork() {
        ChronicleDecisionMailbox.drain()
        val old = SimulationEvent(
            id = "old-shortage",
            tick = 12L,
            code = "FOOD_SHORTAGE",
            actorIds = listOf("civ-a"),
            facts = mapOf("civilization" to "Нері"),
        )
        val newest = SimulationEvent(
            id = "new-era",
            tick = 24L,
            code = "ERA_ADVANCED",
            actorIds = listOf("civ-a"),
            facts = mapOf("civilization" to "Нері"),
        )
        val newestDecision = ChronicleDecisionCatalog.latestUnresolved(listOf(old, newest), people, economy)!!
        ChronicleDecisionMailbox.enqueue(newestDecision.options.first())
        assertNull(ChronicleDecisionCatalog.latestUnresolved(listOf(old, newest), people, economy))
        ChronicleDecisionMailbox.drain()

        val appliedNewest = SimulationEvent(
            id = "chronicle-new-era-era-push",
            tick = 25L,
            code = "INTERVENTION_TECH_BOOST",
            actorIds = listOf("civ-a"),
            facts = mapOf("sourceEventId" to "new-era", "choiceId" to "era-push"),
        )
        assertNull(ChronicleDecisionCatalog.latestUnresolved(listOf(old, newest, appliedNewest), people, economy))
    }
}
