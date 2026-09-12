package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.DiplomaticRelation
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.people.PeopleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayLoopTest {
    @Test
    fun oneQueuedCommandOccupiesTheWholeTurnUntilCancelledOrAdvanced() {
        ChronicleDecisionMailbox.drain()
        val state = state(relation = 0.0)

        GameplayLoop.queueAction(
            state = state,
            civilizationId = "civ-a",
            kind = InterventionKind.HARVEST_AID,
            titleUk = "Резерви",
            effectUk = "Поповнити їжу",
            riskUk = "Витрати казни",
        )

        assertNotNull(GameplayLoop.queuedAction(state))
        val otherCivilizationGate = GameplayLoop.gate(
            state = state,
            civilizationId = "civ-b",
            kind = InterventionKind.STABILITY_SUPPORT,
            targetCivilizationId = null,
            hasPendingDecision = false,
        )
        assertFalse(otherCivilizationGate.enabled)
        assertTrue(otherCivilizationGate.reasonUk.orEmpty().contains("заплановано"))

        GameplayLoop.cancelQueuedAction(state)
        assertNull(GameplayLoop.queuedAction(state))
        ChronicleDecisionMailbox.drain()
    }

    @Test
    fun allianceRequiresGoodRelationsAndEnoughTreasury() {
        ChronicleDecisionMailbox.drain()
        val hostile = GameplayLoop.gate(
            state = state(relation = -0.20),
            civilizationId = "civ-a",
            kind = InterventionKind.FORM_ALLIANCE,
            targetCivilizationId = "civ-b",
            hasPendingDecision = false,
        )
        assertFalse(hostile.enabled)
        assertTrue(hostile.reasonUk.orEmpty().contains("+30"))

        val friendly = GameplayLoop.gate(
            state = state(relation = 0.45),
            civilizationId = "civ-a",
            kind = InterventionKind.FORM_ALLIANCE,
            targetCivilizationId = "civ-b",
            hasPendingDecision = false,
        )
        assertTrue(friendly.enabled)
        assertTrue(friendly.treasuryCost > 0.0)
    }

    @Test
    fun domesticSupportActuallyConsumesTreasury() {
        ChronicleDecisionMailbox.drain()
        val before = state(relation = 0.0)
        val cost = GameplayLoop.treasuryCost(InterventionKind.HARVEST_AID)
        val after = GameplayLoop.chargeDomesticCost(before, "civ-a", InterventionKind.HARVEST_AID)

        assertEquals(
            before.civilizations.first { it.id == "civ-a" }.treasury - cost,
            after.civilizations.first { it.id == "civ-a" }.treasury,
            0.0001,
        )
    }

    @Test
    fun reportShowsConcreteChangesInsteadOfOnlyAStatusMessage() {
        ChronicleDecisionMailbox.drain()
        val beforeState = state(relation = 0.0)
        val snapshot = GameplayLoop.snapshot(beforeState, "civ-a")!!
        val after = beforeState.copy(
            tick = beforeState.tick + 12,
            civilizations = beforeState.civilizations.map { civilization ->
                if (civilization.id == "civ-a") civilization.copy(
                    population = civilization.population + 120,
                    stability = civilization.stability + 0.08,
                    technology = civilization.technology + 0.05,
                    treasury = civilization.treasury - 10.0,
                ) else civilization
            },
            settlements = beforeState.settlements.map { settlement ->
                if (settlement.civilizationId == "civ-a") settlement.copy(
                    population = settlement.population + 120,
                    foodStock = settlement.foodStock + 200.0,
                ) else settlement
            },
        )

        val report = GameplayLoop.report(snapshot, after, monthsAdvanced = 12)
        assertEquals(120L, report.populationDelta)
        assertTrue(report.stabilityDelta > 0.0)
        assertTrue(report.technologyDelta > 0.0)
        assertEquals(-10.0, report.treasuryDelta, 0.0001)
        assertEquals(200.0, report.foodDelta, 0.0001)
    }

    @Test
    fun centuryDecisionAlwaysOffersThreeDomesticPaths() {
        ChronicleDecisionMailbox.drain()
        val decision = GameplayLoop.centuryDecision(state(relation = 0.0), "civ-a")
        assertEquals(3, decision.options.size)
        assertTrue(decision.options.any { it.kind == InterventionKind.HARVEST_AID })
        assertTrue(decision.options.any { it.kind == InterventionKind.STABILITY_SUPPORT })
        assertTrue(decision.options.any { it.kind == InterventionKind.TECHNOLOGY_BOOST })
        assertTrue(decision.options.all { it.sourceEventId == GameplayLoop.centurySourceId(24L, "civ-a") })
    }

    @Test
    fun playDecisionFallsBackToCenturyWhenChronicleIsQuiet() {
        ChronicleDecisionMailbox.drain()
        val decision = GameplayLoop.playDecision(
            state = state(relation = 0.0),
            people = emptyPeople(),
            economy = emptyEconomy(),
            civilizationId = "civ-a",
        )
        assertTrue(decision.eventId.startsWith("player-century-"))
        assertEquals(3, decision.options.size)
    }

    private fun emptyPeople(): PeopleState = PeopleState(
        worldSeed = 7L,
        tick = 24L,
        persons = emptyList(),
        dynasties = emptyList(),
        relationships = emptyList(),
        rulerByCivilization = emptyMap(),
        socialProfiles = emptyList(),
    )

    private fun emptyEconomy(): EconomyState = EconomyState(
        worldSeed = 7L,
        tick = 24L,
        civilizations = listOf(
            CivilizationEconomy("civ-a", TechnologyEra.TRIBAL, emptyMap(), emptyMap(), emptyMap(), 0.0, 0.0, 0.0),
            CivilizationEconomy("civ-b", TechnologyEra.TRIBAL, emptyMap(), emptyMap(), emptyMap(), 0.0, 0.0, 0.0),
        ),
    )

    private fun state(relation: Double): LivingPlanetState {
        val a = Civilization("civ-a", "Ardan", 1_000L, 0.62, 0.20, 80.0)
        val b = Civilization("civ-b", "Velor", 900L, 0.70, 0.20, 80.0)
        return LivingPlanetState(
            worldSeed = 7L,
            tick = 24L,
            civilizations = listOf(a, b),
            settlements = listOf(
                Settlement("city-a", "Astra", "civ-a", 3, 4, 1_000L, 650.0, 20.0, 0L),
                Settlement("city-b", "Bren", "civ-b", 8, 6, 900L, 600.0, 20.0, 0L),
            ),
            relations = listOf(DiplomaticRelation("civ-a", "civ-b", relation, 24L)),
        )
    }
}
