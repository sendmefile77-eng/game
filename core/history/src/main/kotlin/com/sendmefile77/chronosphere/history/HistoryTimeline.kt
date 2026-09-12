package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.people.PeopleState

data class HistoryBranch(
    val id: String,
    val name: String,
    val parentBranchId: String?,
    val forkTick: Long,
    val state: LivingPlanetState,
    val peopleState: PeopleState? = null,
    val economyState: EconomyState? = null,
    val evolutionState: EvolutionState? = null,
    val historicalMemory: HistoricalMemoryState? = null,
    val pendingInterventions: List<PendingInterventionState> = emptyList(),
)

data class HistoryCheckpoint(
    val id: String,
    val branchId: String,
    val label: String,
    val tick: Long,
    val state: LivingPlanetState,
    val peopleState: PeopleState? = null,
    val economyState: EconomyState? = null,
    val evolutionState: EvolutionState? = null,
    val historicalMemory: HistoricalMemoryState? = null,
    val pendingInterventions: List<PendingInterventionState> = emptyList(),
)

data class HistoryWorkspace(
    val activeBranchId: String,
    val branches: List<HistoryBranch>,
    val checkpoints: List<HistoryCheckpoint> = emptyList(),
) {
    val activeBranch: HistoryBranch get() = branches.first { it.id == activeBranchId }
    val activeState: LivingPlanetState get() = activeBranch.state
    val activePeopleState: PeopleState? get() = activeBranch.peopleState
    val activeEconomyState: EconomyState? get() = activeBranch.economyState
    val activeEvolutionState: EvolutionState? get() = activeBranch.evolutionState
    val activeHistoricalMemory: HistoricalMemoryState? get() = activeBranch.historicalMemory
    val activePendingInterventions: List<PendingInterventionState> get() =
        PendingInterventionRegistry.snapshot(activeState.worldSeed, activeBranchId)
            ?: activeBranch.pendingInterventions
}

class HistoryTimeline {
    fun create(
        initialState: LivingPlanetState,
        initialPeopleState: PeopleState? = null,
        initialEconomyState: EconomyState? = null,
        initialEvolutionState: EvolutionState? = null,
    ): HistoryWorkspace {
        val initialMemory = HistoricalMemoryEngine.reconcile(
            previous = null,
            world = initialState,
            people = initialPeopleState,
            economy = initialEconomyState,
        )
        PendingInterventionRegistry.activate(initialState.worldSeed, ROOT_BRANCH_ID, emptyList())
        return HistoryWorkspace(
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
                    evolutionState = initialEvolutionState,
                    historicalMemory = initialMemory,
                ),
            ),
        ).also(::publishActiveContext)
    }

    fun syncActive(
        workspace: HistoryWorkspace,
        state: LivingPlanetState,
        peopleState: PeopleState? = workspace.activePeopleState,
        economyState: EconomyState? = workspace.activeEconomyState,
        evolutionState: EvolutionState? = workspace.activeEvolutionState,
    ): HistoryWorkspace {
        val active = workspace.activeBranch
        val pending = if (PendingInterventionRegistry.isActive(state.worldSeed, workspace.activeBranchId)) {
            PendingInterventionRegistry.activeSnapshot()
        } else {
            active.pendingInterventions.also {
                PendingInterventionRegistry.activate(state.worldSeed, workspace.activeBranchId, it)
            }
        }
        return workspace.copy(
            branches = workspace.branches.map { branch ->
                if (branch.id == workspace.activeBranchId) {
                    branch.copy(
                        state = state,
                        peopleState = peopleState,
                        economyState = economyState,
                        evolutionState = evolutionState,
                        historicalMemory = HistoricalMemoryEngine.reconcile(
                            previous = branch.historicalMemory,
                            world = state,
                            people = peopleState,
                            economy = economyState,
                        ),
                        pendingInterventions = pending,
                    )
                } else branch
            },
        ).also(::publishActiveContext)
    }

    fun checkpoint(workspace: HistoryWorkspace, label: String? = null): HistoryWorkspace {
        val synced = capturePending(workspace)
        val branch = synced.activeBranch
        val sequence = synced.checkpoints.count { it.branchId == branch.id } + 1
        val checkpoint = HistoryCheckpoint(
            id = "${branch.id}-checkpoint-$sequence",
            branchId = branch.id,
            label = label?.takeIf { it.isNotBlank() } ?: "Checkpoint $sequence",
            tick = branch.state.tick,
            state = branch.state,
            peopleState = branch.peopleState,
            economyState = branch.economyState,
            evolutionState = branch.evolutionState,
            historicalMemory = branch.historicalMemory,
            pendingInterventions = branch.pendingInterventions,
        )
        return synced.copy(checkpoints = synced.checkpoints + checkpoint).also(::publishActiveContext)
    }

    fun fork(workspace: HistoryWorkspace, name: String? = null): HistoryWorkspace {
        val synced = capturePending(workspace)
        val parent = synced.activeBranch
        val sequence = synced.branches.size
        val id = "branch-$sequence"
        val branch = HistoryBranch(
            id = id,
            name = name?.takeIf { it.isNotBlank() } ?: "Alternative $sequence",
            parentBranchId = parent.id,
            forkTick = parent.state.tick,
            state = parent.state,
            peopleState = parent.peopleState,
            economyState = parent.economyState,
            evolutionState = parent.evolutionState,
            historicalMemory = parent.historicalMemory,
            pendingInterventions = parent.pendingInterventions,
        )
        PendingInterventionRegistry.activate(branch.state.worldSeed, id, branch.pendingInterventions)
        return synced.copy(activeBranchId = id, branches = synced.branches + branch).also(::publishActiveContext)
    }

    fun switchTo(workspace: HistoryWorkspace, branchId: String): HistoryWorkspace {
        require(workspace.branches.any { it.id == branchId }) { "Unknown history branch: $branchId" }
        val synced = capturePending(workspace)
        val target = synced.branches.first { it.id == branchId }
        PendingInterventionRegistry.activate(target.state.worldSeed, target.id, target.pendingInterventions)
        return synced.copy(activeBranchId = branchId).also(::publishActiveContext)
    }

    fun restoreCheckpoint(workspace: HistoryWorkspace, checkpointId: String): HistoryWorkspace {
        val synced = capturePending(workspace)
        val checkpoint = synced.checkpoints.firstOrNull { it.id == checkpointId }
            ?: error("Unknown history checkpoint: $checkpointId")
        require(checkpoint.branchId == synced.activeBranchId) {
            "Checkpoint $checkpointId belongs to ${checkpoint.branchId}, not active ${synced.activeBranchId}"
        }
        val restored = synced.copy(
            branches = synced.branches.map { branch ->
                if (branch.id == synced.activeBranchId) {
                    branch.copy(
                        state = checkpoint.state,
                        peopleState = checkpoint.peopleState,
                        economyState = checkpoint.economyState,
                        evolutionState = checkpoint.evolutionState,
                        historicalMemory = checkpoint.historicalMemory,
                        pendingInterventions = checkpoint.pendingInterventions,
                    )
                } else branch
            },
        )
        PendingInterventionRegistry.activate(
            checkpoint.state.worldSeed,
            synced.activeBranchId,
            checkpoint.pendingInterventions,
        )
        publishActiveContext(restored)
        return restored
    }

    fun restoreLatestCheckpoint(workspace: HistoryWorkspace): HistoryWorkspace {
        val checkpoint = workspace.checkpoints.lastOrNull { it.branchId == workspace.activeBranchId } ?: return workspace
        return restoreCheckpoint(workspace, checkpoint.id)
    }

    private fun capturePending(workspace: HistoryWorkspace): HistoryWorkspace {
        val branch = workspace.activeBranch
        val pending = if (PendingInterventionRegistry.isActive(branch.state.worldSeed, branch.id)) {
            PendingInterventionRegistry.activeSnapshot()
        } else {
            branch.pendingInterventions
        }
        return if (pending == branch.pendingInterventions) workspace else workspace.copy(
            branches = workspace.branches.map { current ->
                if (current.id == branch.id) current.copy(pendingInterventions = pending) else current
            },
        )
    }

    private fun publishActiveContext(workspace: HistoryWorkspace) {
        ActiveHistoricalContextRegistry.activate(workspace.activeBranch)
    }

    companion object { const val ROOT_BRANCH_ID = "branch-0" }
}
