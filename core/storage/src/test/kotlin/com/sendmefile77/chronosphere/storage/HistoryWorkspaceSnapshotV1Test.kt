package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.economy.CivilizationEconomy
import com.sendmefile77.chronosphere.economy.EconomicGood
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.history.PendingInterventionRegistry
import com.sendmefile77.chronosphere.history.PendingInterventionState
import com.sendmefile77.chronosphere.people.PeopleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryWorkspaceSnapshotV1Test {
    @Test
    fun workspaceRoundTripPreservesBranchesWorldPeopleAndEconomy() {
        val timeline = HistoryTimeline()
        val initial = sampleState(24L)
        val initialPeople = PeopleEngine().initialize(initial)
        val initialEconomy = sampleEconomy(24L, TechnologyEra.AGRARIAN, 0.08)
        var workspace = timeline.create(initial, initialPeople, initialEconomy)
        workspace = timeline.checkpoint(workspace, "Before fork")
        workspace = timeline.fork(workspace, "Dry future")

        val futureState = sampleState(96L)
        val futurePeople = PeopleEngine().advance(workspace.activePeopleState!!, futureState).state
        val futureEconomy = sampleEconomy(96L, TechnologyEra.URBAN, 0.22)
        workspace = timeline.syncActive(workspace, futureState, futurePeople, futureEconomy)
        workspace = timeline.checkpoint(workspace, "After divergence")

        val encoded = HistoryWorkspaceSnapshotV1.encode(workspace)
        val decoded = HistoryWorkspaceSnapshotV1.decode(encoded)

        assertEquals(workspace, decoded)
        assertEquals("branch-1", decoded.activeBranchId)
        assertEquals(96L, decoded.activeState.tick)
        assertEquals(96L, decoded.activePeopleState!!.tick)
        assertEquals(96L, decoded.activeEconomyState!!.tick)
        assertEquals(TechnologyEra.URBAN, decoded.activeEconomyState!!.economy("civ-1")!!.era)
        assertEquals(2, decoded.branches.size)
        assertEquals(2, decoded.checkpoints.size)
    }

    @Test
    fun restoringCheckpointRestoresPeopleAndEconomyTogetherWithWorld() {
        val timeline = HistoryTimeline()
        val initial = sampleState(24L)
        val initialPeople = PeopleEngine().initialize(initial)
        val initialEconomy = sampleEconomy(24L, TechnologyEra.AGRARIAN, 0.04)
        var workspace = timeline.create(initial, initialPeople, initialEconomy)
        workspace = timeline.checkpoint(workspace, "Before jump")

        val futureState = sampleState(1_200L)
        val futurePeople = PeopleEngine().advance(initialPeople, futureState).state
        val futureEconomy = sampleEconomy(1_200L, TechnologyEra.MEDIEVAL, 0.35)
        workspace = timeline.syncActive(workspace, futureState, futurePeople, futureEconomy)
        assertNotEquals(initialPeople, workspace.activePeopleState)
        assertNotEquals(initialEconomy, workspace.activeEconomyState)

        workspace = timeline.restoreLatestCheckpoint(workspace)
        assertEquals(initial, workspace.activeState)
        assertEquals(initialPeople, workspace.activePeopleState)
        assertEquals(initialEconomy, workspace.activeEconomyState)
    }

    @Test
    fun queuedInterventionsSurviveWorkspaceSaveAndCheckpointSave() {
        val timeline = HistoryTimeline()
        val initial = sampleState(24L)
        val people = PeopleEngine().initialize(initial)
        var workspace = timeline.create(initial, people)
        val pending = PendingInterventionState(
            commandId = "chronicle-era-era-push",
            sourceEventId = "era-1",
            choiceId = "era-push",
            choiceLabel = "Продовжити ривок",
            effectLabel = "Розвиток",
            riskLabel = "Напруга",
            kind = InterventionKind.TECHNOLOGY_BOOST,
            civilizationId = "civ-1",
            strength = 0.68,
        )
        PendingInterventionRegistry.enqueue(pending)
        workspace = timeline.syncActive(workspace, initial, people)
        workspace = timeline.checkpoint(workspace, "Queued choice")

        val decoded = HistoryWorkspaceSnapshotV1.decode(HistoryWorkspaceSnapshotV1.encode(workspace))
        assertEquals(listOf(pending), decoded.activeBranch.pendingInterventions)
        assertEquals(listOf(pending), decoded.checkpoints.single().pendingInterventions)
    }

    @Test
    fun stage4HistoryWithoutEconomyStillDecodes() {
        val timeline = HistoryTimeline()
        val initial = sampleState(24L)
        val people = PeopleEngine().initialize(initial)
        var workspace = timeline.create(initial, people)
        workspace = timeline.checkpoint(workspace, "Legacy point")

        val stage5Text = HistoryWorkspaceSnapshotV1.encode(workspace)
        val legacyText = stage5Text.lineSequence().joinToString("\n") { line ->
            if (line.startsWith("BRANCH\t") || line.startsWith("CHECKPOINT\t")) {
                val p = line.split('\t').toMutableList()
                while (p.size > 10) p.removeAt(p.lastIndex)
                p.joinToString("\t").trimEnd('\t')
            } else line
        }
        val decoded = HistoryWorkspaceSnapshotV1.decode(legacyText)

        assertEquals(initial, decoded.activeState)
        assertEquals(people, decoded.activePeopleState)
        assertNull(decoded.activeEconomyState)
        assertNull(decoded.checkpoints.single().economyState)
        assertEquals(emptyList<PendingInterventionState>(), decoded.activeBranch.pendingInterventions)
    }

    private fun sampleState(tick: Long): LivingPlanetState {
        val civilization = Civilization("civ-1", "Ардан", 1_100L, 0.68, 0.15, 90.0)
        return LivingPlanetState(
            worldSeed = 123L,
            tick = tick,
            civilizations = listOf(civilization),
            settlements = listOf(
                Settlement("city-1", "Астра", "civ-1", 4, 5, 1_100L, 800.0, 60.0, 0L),
            ),
        )
    }

    private fun sampleEconomy(tick: Long, era: TechnologyEra, shortage: Double): EconomyState {
        val goods = EconomicGood.entries.associateWith { 2.0 + it.ordinal }
        return EconomyState(
            worldSeed = 123L,
            tick = tick,
            civilizations = listOf(
                CivilizationEconomy(
                    civilizationId = "civ-1",
                    era = era,
                    stockpiles = goods,
                    production = goods.mapValues { it.value + 1.0 },
                    demand = goods.mapValues { it.value + 0.5 },
                    shortageIndex = shortage,
                    tradeBalance = 3.0,
                    grossOutput = 44.0,
                ),
            ),
        )
    }
}
