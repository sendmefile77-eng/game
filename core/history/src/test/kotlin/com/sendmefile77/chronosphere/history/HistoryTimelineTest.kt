package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
