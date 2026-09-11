package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.LivingPlanetState

data class HistoryBranch(
    val id: String,
    val name: String,
    val parentBranchId: String?,
    val forkTick: Long,
    val state: LivingPlanetState,
)

data class HistoryCheckpoint(
    val id: String,
    val branchId: String,
    val label: String,
    val tick: Long,
    val state: LivingPlanetState,
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
}

class HistoryTimeline {
    fun create(initialState: LivingPlanetState): HistoryWorkspace = HistoryWorkspace(
        activeBranchId = ROOT_BRANCH_ID,
        branches = listOf(
            HistoryBranch(
                id = ROOT_BRANCH_ID,
                name = "Original timeline",
                parentBranchId = null,
                forkTick = initialState.tick,
                state = initialState,
            ),
        ),
    )

    fun syncActive(workspace: HistoryWorkspace, state: LivingPlanetState): HistoryWorkspace = workspace.copy(
        branches = workspace.branches.map { branch ->
            if (branch.id == workspace.activeBranchId) branch.copy(state = state) else branch
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
        val targetBranchId = if (checkpoint.branchId == workspace.activeBranchId) {
            workspace.activeBranchId
        } else {
            workspace.activeBranchId
        }
        return workspace.copy(
            branches = workspace.branches.map { branch ->
                if (branch.id == targetBranchId) branch.copy(state = checkpoint.state) else branch
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
