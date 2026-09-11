package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.people.PeopleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HistoryWorkspaceSnapshotV1Test {
    @Test
    fun workspaceRoundTripPreservesBranchesCheckpointsWorldAndPeople() {
        val timeline = HistoryTimeline()
        val initial = sampleState(24L)
        val initialPeople = PeopleEngine().initialize(initial)
        var workspace = timeline.create(initial, initialPeople)
        workspace = timeline.checkpoint(workspace, "Before fork")
        workspace = timeline.fork(workspace, "Dry future")

        val futureState = sampleState(96L)
        val futurePeople = PeopleEngine().advance(workspace.activePeopleState!!, futureState).state
        workspace = timeline.syncActive(workspace, futureState, futurePeople)
        workspace = timeline.checkpoint(workspace, "After divergence")

        val encoded = HistoryWorkspaceSnapshotV1.encode(workspace)
        val decoded = HistoryWorkspaceSnapshotV1.decode(encoded)

        assertEquals(workspace, decoded)
        assertEquals("branch-1", decoded.activeBranchId)
        assertEquals(96L, decoded.activeState.tick)
        assertEquals(96L, decoded.activePeopleState!!.tick)
        assertEquals(2, decoded.branches.size)
        assertEquals(2, decoded.checkpoints.size)
    }

    @Test
    fun restoringCheckpointRestoresPeopleTogetherWithWorld() {
        val timeline = HistoryTimeline()
        val initial = sampleState(24L)
        val initialPeople = PeopleEngine().initialize(initial)
        var workspace = timeline.create(initial, initialPeople)
        workspace = timeline.checkpoint(workspace, "Before jump")

        val futureState = sampleState(1_200L)
        val futurePeople = PeopleEngine().advance(initialPeople, futureState).state
        workspace = timeline.syncActive(workspace, futureState, futurePeople)
        assertNotEquals(initialPeople, workspace.activePeopleState)

        workspace = timeline.restoreLatestCheckpoint(workspace)
        assertEquals(initial, workspace.activeState)
        assertEquals(initialPeople, workspace.activePeopleState)
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
}
