package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.history.HistoryBranch
import com.sendmefile77.chronosphere.history.HistoryCheckpoint
import com.sendmefile77.chronosphere.history.HistoryWorkspace
import com.sendmefile77.chronosphere.history.PendingInterventionRegistry
import java.nio.charset.StandardCharsets
import java.util.Base64

object HistoryWorkspaceSnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_HISTORY_V1"

    fun encode(workspace: HistoryWorkspace): String {
        val effective = captureLivePending(workspace)
        validateWorkspace(effective)
        return buildString {
            appendLine(HEADER)
            appendLine("ACTIVE\t${esc(effective.activeBranchId)}")
            effective.branches.forEach { branch ->
                appendLine(
                    listOf(
                        "BRANCH", esc(branch.id), esc(branch.name), esc(branch.parentBranchId ?: ""), branch.forkTick,
                        pack(GameSnapshotV1.encode(branch.state)),
                        branch.peopleState?.let { pack(PeopleSnapshotV1.encode(it)) }.orEmpty(),
                        branch.economyState?.let { pack(EconomySnapshotV1.encode(it)) }.orEmpty(),
                        branch.evolutionState?.let { pack(EvolutionSnapshotV1.encode(it)) }.orEmpty(),
                        branch.historicalMemory?.let { pack(HistoricalMemorySnapshotV1.encode(it)) }.orEmpty(),
                        pack(PendingInterventionSnapshotV1.encode(branch.pendingInterventions)),
                    ).joinToString("\t"),
                )
            }
            effective.checkpoints.forEach { checkpoint ->
                appendLine(
                    listOf(
                        "CHECKPOINT", esc(checkpoint.id), esc(checkpoint.branchId), esc(checkpoint.label), checkpoint.tick,
                        pack(GameSnapshotV1.encode(checkpoint.state)),
                        checkpoint.peopleState?.let { pack(PeopleSnapshotV1.encode(it)) }.orEmpty(),
                        checkpoint.economyState?.let { pack(EconomySnapshotV1.encode(it)) }.orEmpty(),
                        checkpoint.evolutionState?.let { pack(EvolutionSnapshotV1.encode(it)) }.orEmpty(),
                        checkpoint.historicalMemory?.let { pack(HistoricalMemorySnapshotV1.encode(it)) }.orEmpty(),
                        pack(PendingInterventionSnapshotV1.encode(checkpoint.pendingInterventions)),
                    ).joinToString("\t"),
                )
            }
        }
    }

    fun decode(text: String): HistoryWorkspace {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported history format" }
        val activeRow = lines.firstOrNull { it.startsWith("ACTIVE\t") }?.split('\t') ?: error("Missing ACTIVE row")
        require(activeRow.size >= 2)
        val activeBranchId = unesc(activeRow[1])

        val branches = lines.filter { it.startsWith("BRANCH\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 6) { "Malformed BRANCH row" }
            val state = GameSnapshotV1.decode(unpack(p[5]))
            val peopleState = p.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { PeopleSnapshotV1.decode(unpack(it)) }
            val economyState = p.getOrNull(7)?.takeIf { it.isNotBlank() }?.let { EconomySnapshotV1.decode(unpack(it)) }
            val evolutionState = p.getOrNull(8)?.takeIf { it.isNotBlank() }?.let { EvolutionSnapshotV1.decode(unpack(it)) }
            val historicalMemory = p.getOrNull(9)?.takeIf { it.isNotBlank() }?.let { HistoricalMemorySnapshotV1.decode(unpack(it)) }
            val pendingInterventions = p.getOrNull(10)?.takeIf { it.isNotBlank() }
                ?.let { PendingInterventionSnapshotV1.decode(unpack(it)) }
                .orEmpty()
            HistoryBranch(
                id = unesc(p[1]),
                name = unesc(p[2]),
                parentBranchId = unesc(p[3]).ifBlank { null },
                forkTick = p[4].toLong(),
                state = state,
                peopleState = peopleState,
                economyState = economyState,
                evolutionState = evolutionState,
                historicalMemory = historicalMemory,
                pendingInterventions = pendingInterventions,
            )
        }

        val checkpoints = lines.filter { it.startsWith("CHECKPOINT\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 6) { "Malformed CHECKPOINT row" }
            val state = GameSnapshotV1.decode(unpack(p[5]))
            val peopleState = p.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { PeopleSnapshotV1.decode(unpack(it)) }
            val economyState = p.getOrNull(7)?.takeIf { it.isNotBlank() }?.let { EconomySnapshotV1.decode(unpack(it)) }
            val evolutionState = p.getOrNull(8)?.takeIf { it.isNotBlank() }?.let { EvolutionSnapshotV1.decode(unpack(it)) }
            val historicalMemory = p.getOrNull(9)?.takeIf { it.isNotBlank() }?.let { HistoricalMemorySnapshotV1.decode(unpack(it)) }
            val pendingInterventions = p.getOrNull(10)?.takeIf { it.isNotBlank() }
                ?.let { PendingInterventionSnapshotV1.decode(unpack(it)) }
                .orEmpty()
            HistoryCheckpoint(
                id = unesc(p[1]),
                branchId = unesc(p[2]),
                label = unesc(p[3]),
                tick = p[4].toLong(),
                state = state,
                peopleState = peopleState,
                economyState = economyState,
                evolutionState = evolutionState,
                historicalMemory = historicalMemory,
                pendingInterventions = pendingInterventions,
            )
        }

        return HistoryWorkspace(activeBranchId = activeBranchId, branches = branches, checkpoints = checkpoints).also { workspace ->
            validateWorkspace(workspace)
            PendingInterventionRegistry.activate(
                workspace.activeState.worldSeed,
                workspace.activeBranchId,
                workspace.activeBranch.pendingInterventions,
            )
        }
    }

    private fun captureLivePending(workspace: HistoryWorkspace): HistoryWorkspace {
        val branch = workspace.activeBranch
        val pending = PendingInterventionRegistry.snapshot(branch.state.worldSeed, branch.id)
            ?: branch.pendingInterventions
        if (pending == branch.pendingInterventions) return workspace
        return workspace.copy(
            branches = workspace.branches.map { current ->
                if (current.id == branch.id) current.copy(pendingInterventions = pending) else current
            },
        )
    }

    private fun validateWorkspace(workspace: HistoryWorkspace) {
        val branches = workspace.branches
        require(branches.isNotEmpty()) { "History contains no branches" }
        require(workspace.activeBranchId.isNotBlank()) { "Blank active branch id" }
        require(branches.map { it.id }.distinct().size == branches.size) { "Duplicate branch id" }
        require(branches.all { it.id.isNotBlank() }) { "Blank branch id" }
        require(branches.any { it.id == workspace.activeBranchId }) { "Active branch is missing" }

        val branchById = branches.associateBy { it.id }
        val workspaceSeed = branches.first().state.worldSeed
        require(branches.all { it.state.worldSeed == workspaceSeed }) { "History branches use different world seeds" }
        branches.forEach { branch ->
            require(branch.forkTick in 0L..branch.state.tick) { "Branch fork tick is outside branch history" }
            require(branch.parentBranchId == null || branch.parentBranchId in branchById) { "Branch references unknown parent" }
            require(branch.pendingInterventions.map { it.sourceEventId }.distinct().size == branch.pendingInterventions.size) {
                "Branch contains duplicate pending intervention sources"
            }
            validateLayer("Branch people", branch.state.worldSeed, branch.state.tick, branch.peopleState?.worldSeed, branch.peopleState?.tick)
            validateLayer("Branch economy", branch.state.worldSeed, branch.state.tick, branch.economyState?.worldSeed, branch.economyState?.tick)
            validateLayer("Branch evolution", branch.state.worldSeed, branch.state.tick, branch.evolutionState?.worldSeed, branch.evolutionState?.tick)
            validateLayer("Branch historical memory", branch.state.worldSeed, branch.state.tick, branch.historicalMemory?.worldSeed, branch.historicalMemory?.tick)
        }

        val checkpoints = workspace.checkpoints
        require(checkpoints.map { it.id }.distinct().size == checkpoints.size) { "Duplicate checkpoint id" }
        require(checkpoints.all { it.id.isNotBlank() }) { "Blank checkpoint id" }
        checkpoints.forEach { checkpoint ->
            val branch = branchById[checkpoint.branchId] ?: error("Checkpoint references unknown branch")
            require(checkpoint.tick == checkpoint.state.tick) { "Checkpoint tick/state mismatch" }
            require(checkpoint.state.worldSeed == branch.state.worldSeed) { "Checkpoint/world seed mismatch" }
            require(checkpoint.tick >= branch.forkTick) { "Checkpoint predates its branch" }
            require(checkpoint.pendingInterventions.map { it.sourceEventId }.distinct().size == checkpoint.pendingInterventions.size) {
                "Checkpoint contains duplicate pending intervention sources"
            }
            validateLayer("Checkpoint people", checkpoint.state.worldSeed, checkpoint.state.tick, checkpoint.peopleState?.worldSeed, checkpoint.peopleState?.tick)
            validateLayer("Checkpoint economy", checkpoint.state.worldSeed, checkpoint.state.tick, checkpoint.economyState?.worldSeed, checkpoint.economyState?.tick)
            validateLayer("Checkpoint evolution", checkpoint.state.worldSeed, checkpoint.state.tick, checkpoint.evolutionState?.worldSeed, checkpoint.evolutionState?.tick)
            validateLayer("Checkpoint historical memory", checkpoint.state.worldSeed, checkpoint.state.tick, checkpoint.historicalMemory?.worldSeed, checkpoint.historicalMemory?.tick)
        }
    }

    private fun validateLayer(
        label: String,
        worldSeed: Long,
        tick: Long,
        layerSeed: Long?,
        layerTick: Long?,
    ) {
        if (layerSeed == null || layerTick == null) return
        require(layerSeed == worldSeed) { "$label/world seed mismatch" }
        require(layerTick == tick) { "$label/world tick mismatch" }
    }

    private fun pack(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
    private fun unpack(value: String): String = String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
    private fun esc(value: String): String = value.replace("%", "%25").replace("\t", "%09").replace("\n", "%0A")
    private fun unesc(value: String): String = value.replace("%0A", "\n").replace("%09", "\t").replace("%25", "%")
}
