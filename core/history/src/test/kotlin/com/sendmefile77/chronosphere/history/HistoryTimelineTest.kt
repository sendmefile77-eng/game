package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTimelineTest {
    private val timeline = HistoryTimeline()

    @Test
    fun forkStartsFromSameStateButCanDivergeIndependently() {
        val initial = sampleState(tick = 120L)
        var workspace = timeline.create(initial)
        workspace = timeline.fork(workspace, "Dry future")
        assertEquals(initial, workspace.activeState)

        val diverged = initial.copy(tick = 240L)
        workspace = timeline.syncActive(workspace, diverged)
        assertEquals(240L, workspace.activeState.tick)

        workspace = timeline.switchTo(workspace, HistoryTimeline.ROOT_BRANCH_ID)
        assertEquals(120L, workspace.activeState.tick)
        assertNotEquals(diverged, workspace.activeState)
    }

    @Test
    fun latestCheckpointRestoresExactStateOnActiveBranch() {
        val initial = sampleState(tick = 48L)
        var workspace = timeline.create(initial)
        workspace = timeline.checkpoint(workspace, "Before crisis")
        workspace = timeline.syncActive(workspace, initial.copy(tick = 180L))
        assertEquals(180L, workspace.activeState.tick)

        workspace = timeline.restoreLatestCheckpoint(workspace)
        assertEquals(initial, workspace.activeState)
        assertEquals("Before crisis", workspace.checkpoints.single().label)
    }

    @Test
    fun switchingBranchesPreservesEachBranchState() {
        val initial = sampleState(tick = 12L)
        var workspace = timeline.create(initial)
        workspace = timeline.syncActive(workspace, initial.copy(tick = 24L))
        workspace = timeline.fork(workspace, "Alternative")
        workspace = timeline.syncActive(workspace, initial.copy(tick = 72L))

        workspace = timeline.switchTo(workspace, HistoryTimeline.ROOT_BRANCH_ID)
        assertEquals(24L, workspace.activeState.tick)

        workspace = timeline.switchTo(workspace, "branch-1")
        assertEquals(72L, workspace.activeState.tick)
    }

    @Test
    fun checkpointFromAnotherBranchCannotOverwriteActiveTimeline() {
        val initial = sampleState(tick = 12L)
        var workspace = timeline.create(initial)
        workspace = timeline.checkpoint(workspace, "Root checkpoint")
        val rootCheckpoint = workspace.checkpoints.single().id
        workspace = timeline.fork(workspace, "Alternative")
        workspace = timeline.syncActive(workspace, initial.copy(tick = 84L))

        val result = runCatching { timeline.restoreCheckpoint(workspace, rootCheckpoint) }
        assertTrue(result.isFailure)
        assertEquals(84L, workspace.activeState.tick)
    }

    @Test
    fun queuedInterventionForksWithHistoryButThenDivergesPerBranch() {
        val initial = sampleState(tick = 24L)
        var workspace = timeline.create(initial)
        val pending = PendingInterventionState(
            commandId = "chronicle-era-1-era-push",
            sourceEventId = "era-1",
            choiceId = "era-push",
            choiceLabel = "Продовжити ривок",
            effectLabel = "Технологічний імпульс",
            riskLabel = "Без стабілізації",
            kind = InterventionKind.TECHNOLOGY_BOOST,
            civilizationId = "civ-1",
            strength = 0.68,
        )
        PendingInterventionRegistry.enqueue(pending)
        workspace = timeline.syncActive(workspace, initial)
        workspace = timeline.fork(workspace, "Alternative")

        assertEquals(listOf(pending), workspace.activePendingInterventions)
        assertEquals(listOf(pending), workspace.branches.first { it.id == HistoryTimeline.ROOT_BRANCH_ID }.pendingInterventions)

        PendingInterventionRegistry.drain()
        workspace = timeline.syncActive(workspace, workspace.activeState)
        assertTrue(workspace.activeBranch.pendingInterventions.isEmpty())

        workspace = timeline.switchTo(workspace, HistoryTimeline.ROOT_BRANCH_ID)
        assertEquals(listOf(pending), workspace.activePendingInterventions)
        assertEquals(listOf(pending), PendingInterventionRegistry.activeSnapshot())
    }

    @Test
    fun checkpointRestoresQueuedInterventionTogetherWithWorld() {
        val initial = sampleState(tick = 36L)
        var workspace = timeline.create(initial)
        val pending = PendingInterventionState(
            commandId = "chronicle-shortage-food",
            sourceEventId = "shortage-1",
            choiceId = "emergency-food",
            choiceLabel = "Аварійні запаси",
            effectLabel = "Поповнити продовольство",
            riskLabel = "Причина дефіциту лишається",
            kind = InterventionKind.HARVEST_AID,
            civilizationId = "civ-1",
            strength = 0.72,
        )
        PendingInterventionRegistry.enqueue(pending)
        workspace = timeline.checkpoint(workspace, "Before answer is applied")
        PendingInterventionRegistry.drain()
        workspace = timeline.syncActive(workspace, initial.copy(tick = 48L))
        assertTrue(workspace.activePendingInterventions.isEmpty())

        workspace = timeline.restoreLatestCheckpoint(workspace)
        assertEquals(36L, workspace.activeState.tick)
        assertEquals(listOf(pending), workspace.activePendingInterventions)
    }

    private fun sampleState(tick: Long): LivingPlanetState {
        val civilization = Civilization(
            id = "civ-1",
            name = "Ardan",
            population = 1_000L,
            stability = 0.65,
            technology = 0.12,
            treasury = 80.0,
        )
        val settlement = Settlement(
            id = "city-1",
            name = "Astra",
            civilizationId = civilization.id,
            x = 4,
            y = 5,
            population = 1_000L,
            foodStock = 700.0,
            wealth = 50.0,
            foundedTick = 0L,
        )
        return LivingPlanetState(
            worldSeed = 42L,
            tick = tick,
            civilizations = listOf(civilization),
            settlements = listOf(settlement),
        )
    }
}
