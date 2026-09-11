package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.history.HistoryTimeline
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryWorkspaceSnapshotV1Test {
    @Test
    fun workspaceRoundTripPreservesBranchesCheckpointsAndActiveState() {
        val timeline = HistoryTimeline()
        val initial = sampleState(24L)
        var workspace = timeline.create(initial)
        workspace = timeline.checkpoint(workspace, "Before fork")
        workspace = timeline.fork(workspace, "Dry future")
        workspace = timeline.syncActive(workspace, sampleState(96L))
        workspace = timeline.checkpoint(workspace, "After divergence")

        val encoded = HistoryWorkspaceSnapshotV1.encode(workspace)
        val decoded = HistoryWorkspaceSnapshotV1.decode(encoded)

        assertEquals(workspace, decoded)
        assertEquals("branch-1", decoded.activeBranchId)
        assertEquals(96L, decoded.activeState.tick)
        assertEquals(2, decoded.branches.size)
        assertEquals(2, decoded.checkpoints.size)
    }

    private fun sampleState(tick: Long): LivingPlanetState {
        val civilization = Civilization("civ-1", "Ardan", 1_100L, 0.68, 0.15, 90.0)
        return LivingPlanetState(
            worldSeed = 123L,
            tick = tick,
            civilizations = listOf(civilization),
            settlements = listOf(
                Settlement("city-1", "Astra", "civ-1", 4, 5, 1_100L, 800.0, 60.0, 0L),
            ),
        )
    }
}
