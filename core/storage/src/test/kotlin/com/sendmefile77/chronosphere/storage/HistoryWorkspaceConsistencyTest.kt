package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.history.HistoryBranch
import com.sendmefile77.chronosphere.history.HistoryWorkspace
import com.sendmefile77.chronosphere.people.PeopleEngine
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryWorkspaceConsistencyTest {
    @Test
    fun encoderRejectsWorldAndPeopleFromDifferentTicks() {
        val state = LivingPlanetState(
            worldSeed = 55L,
            tick = 120L,
            civilizations = listOf(Civilization("civ-1", "Ардан", 1_000L, 0.7, 0.2, 50.0)),
            settlements = listOf(Settlement("city-1", "Астра", "civ-1", 3, 4, 1_000L, 700.0, 40.0, 0L)),
        )
        val people = PeopleEngine().initialize(state).copy(tick = 132L)
        val workspace = HistoryWorkspace(
            activeBranchId = "main",
            branches = listOf(
                HistoryBranch(
                    id = "main",
                    name = "Основна",
                    parentBranchId = null,
                    forkTick = 0L,
                    state = state,
                    peopleState = people,
                ),
            ),
            checkpoints = emptyList(),
        )

        val result = runCatching { HistoryWorkspaceSnapshotV1.encode(workspace) }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("tick mismatch") == true)
    }
}
