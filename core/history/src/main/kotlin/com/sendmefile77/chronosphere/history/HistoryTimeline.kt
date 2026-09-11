package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.people.PeopleState

data class HistoryBranch(
    val id: String,
    val name: String,
    val parentBranchId: String?,
    val forkTick: Long,
    val state: LivingPlanetState,
    val peopleState: PeopleState? = null,
    val economyState: EconomyState? = null,
)

data class HistoryCheckpoint(
    val id: String,
    val branchId: String,
    val label: String,
    val tick: Long,
    val state: LivingPlanetState,
    val peopleState: PeopleState? = null,
    val economyState: EconomyState? = null,
)

data class HistoryWorkspace(
    val activeBranchId: String,
    val branches: List<HistoryBranch>,
    val checkpoints: List<HistoryCheckpoint> = emptyList(),
) {
    val activeBranch: HistoryBranch
        get() = branches.first { it.id == activeBranchId }

    val activeState: LivingPlanetState
        get() = activeBranch.state

    val activePeopleState: PeopleState?
        get() = activeBranch.peopleState

    val activeEconomyState: EconomyState?
        get() = activeBranch.economyState
}

class HistoryTimeline {
    fun create(
        initialState: LivingPlanetState,
        initialPeopleState: PeopleState? = null,
        initialEconomyState: EconomyState? = null,
    ): HistoryWorkspace = HistoryWorkspace(
        activeBranchId = ROOT_BRANCH_ID,
        branches = listOf(
            HistoryBranch(
                id = ROOT_BRANCH_ID,
                name = "Original timeline",
                parentBranchId = null,
                forkTick = initialState.tick,
                state = initialState,
                peopleState = initialPeopleState,
                economyState = initialEconomyState,
            ),
        ),
    )

    fun syncActive(
        workspace: HistoryWorkspace,
        state: LivingPlanetState,
        peopleState: PeopleState? = workspace.activePeopleState,
        economyState: EconomyState? = workspace.activeEconomyState,
    ): HistoryWorkspace = workspace.copy(
        branches = workspace.branches.map { branch ->
            if (branch.id == workspace.activeBranchId) {
                branch.copy(state = state, peopleState = peopleState, economyState = economyState)
            } else {
                branch
            }
        },
    )

    fun checkpoint(workspace: HistoryWorkspace, label: String? = null): HistoryWorkspace {
        val branch = workspace.activeBranch
        val sequence = workspace.checkpoints.count { it.branchId == branch.id } + 1
        val checkpoint = HistoryCheckpoint(
            id = "${branch.id}-checkpoint-$sequence",
            branchId = branch.id,
            label = label?.takeIf { it.isNotBlank() } ?: "Checkpoint $sequence",
            tick = branch.state.tick,
            state = branch.state,
            peopleState = branch.peopleState,
            economyState = branch.economyState,
        )
        return workspace.copy(checkpoints = workspace.checkpoints + checkpoint)
    }

    fun fork(workspace: HistoryWorkspace, name: String? = null): HistoryWorkspace {
        val parent = workspace.activeBranch
        val sequence = workspace.branches.size
        val id = "branch-$sequence"
        val branch = HistoryBranch(
            id = id,
            name = name?.takeIf { it.isNotBlank() } ?: "Alternative $sequence",
            parentBranchId = parent.id,
            forkTick = parent.state.tick,
            state = parent.state,
            peopleState = parent.peopleState,
            economyState = parent.economyState,
        )
        return workspace.copy(
            activeBranchId = id,
            branches = workspace.branches + branch,
        )
    }

    fun switchTo(workspace: HistoryWorkspace, branchId: String): HistoryWorkspace {
        require(workspace.branches.any { it.id == branchId }) { "Unknown history branch: $branchId" }
        return workspace.copy(activeBranchId = branchId)
    }

    fun restoreCheckpoint(workspace: HistoryWorkspace, checkpointId: String): HistoryWorkspace {
        val checkpoint = workspace.checkpoints.firstOrNull { it.id == checkpointId }
            ?: error("Unknown history checkpoint: $checkpointId")
        require(checkpoint.branchId == workspace.activeBranchId) {
            "Checkpoint $checkpointId belongs to ${checkpoint.branchId}, not active ${workspace.activeBranchId}"
        }
        return workspace.copy(
            branches = workspace.branches.map { branch ->
                if (branch.id == workspace.activeBranchId) {
                    branch.copy(
                        state = checkpoint.state,
                        peopleState = checkpoint.peopleState,
                        economyState = checkpoint.economyState,
                    )
                } else {
                    branch
                }
            },
        )
    }

    fun restoreLatestCheckpoint(workspace: HistoryWorkspace): HistoryWorkspace {
        val checkpoint = workspace.checkpoints.lastOrNull { it.branchId == workspace.activeBranchId }
            ?: return workspace
        return restoreCheckpoint(workspace, checkpoint.id)
    }

    companion object {
        const val ROOT_BRANCH_ID = "branch-0"
    }
}
