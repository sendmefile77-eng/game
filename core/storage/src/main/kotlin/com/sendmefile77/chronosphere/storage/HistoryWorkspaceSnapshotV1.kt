package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.history.HistoryBranch
import com.sendmefile77.chronosphere.history.HistoryCheckpoint
import com.sendmefile77.chronosphere.history.HistoryWorkspace
import java.nio.charset.StandardCharsets
import java.util.Base64

object HistoryWorkspaceSnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_HISTORY_V1"

    fun encode(workspace: HistoryWorkspace): String {
        validateWorkspace(workspace)
        return buildString {
            appendLine(HEADER)
            appendLine("ACTIVE\t${esc(workspace.activeBranchId)}")
            workspace.branches.forEach { branch ->
                appendLine(
                    listOf(
                        "BRANCH", esc(branch.id), esc(branch.name), esc(branch.parentBranchId ?: ""), branch.forkTick,
                        pack(GameSnapshotV1.encode(branch.state)),
                        branch.peopleState?.let { pack(PeopleSnapshotV1.encode(it)) }.orEmpty(),
                        branch.economyState?.let { pack(EconomySnapshotV1.encode(it)) }.orEmpty(),
                        branch.evolutionState?.let { pack(EvolutionSnapshotV1.encode(it)) }.orEmpty(),
                    ).joinToString("\t"),
                )
            }
            workspace.checkpoints.forEach { checkpoint ->
                appendLine(
                    listOf(
                        "CHECKPOINT", esc(checkpoint.id), esc(checkpoint.branchId), esc(checkpoint.label), checkpoint.tick,
                        pack(GameSnapshotV1.encode(checkpoint.state)),
                        checkpoint.peopleState?.let { pack(PeopleSnapshotV1.encode(it)) }.orEmpty(),
                        checkpoint.economyState?.let { pack(EconomySnapshotV1.encode(it)) }.orEmpty(),
                        checkpoint.evolutionState?.let { pack(EvolutionSnapshotV1.encode(it)) }.orEmpty(),
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
            HistoryBranch(
                id = unesc(p[1]),
                name = unesc(p[2]),
                parentBranchId = unesc(p[3]).ifBlank { null },
                forkTick = p[4].toLong(),
                state = state,
                peopleState = peopleState,
                economyState = economyState,
                evolutionState = evolutionState,
            )
        }

        val checkpoints = lines.filter { it.startsWith("CHECKPOINT\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 6) { "Malformed CHECKPOINT row" }
            val state = GameSnapshotV1.decode(unpack(p[5]))
            val peopleState = p.getOrNull(6)?.takeIf { it.isNotBlank() }?.let { PeopleSnapshotV1.decode(unpack(it)) }
            val economyState = p.getOrNull(7)?.takeIf { it.isNotBlank() }?.let { EconomySnapshotV1.decode(unpack(it)) }
            val evolutionState = p.getOrNull(8)?.takeIf { it.isNotBlank() }?.let { EvolutionSnapshotV1.decode(unpack(it)) }
            HistoryCheckpoint(
                id = unesc(p[1]),
                branchId = unesc(p[2]),
                label = unesc(p[3]),
                tick = p[4].toLong(),
                state = state,
                peopleState = peopleState,
                economyState = economyState,
                evolutionState = evolutionState,
            )
        }

        return HistoryWorkspace(activeBranchId = activeBranchId, branches = branches, checkpoints = checkpoints)
            .also(::validateWorkspace)
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
            validateLayer("Branch people", branch.state.worldSeed, branch.state.tick, branch.peopleState?.worldSeed, branch.peopleState?.tick)
            validateLayer("Branch economy", branch.state.worldSeed, branch.state.tick, branch.economyState?.worldSeed, branch.economyState?.tick)
            validateLayer("Branch evolution", branch.state.worldSeed, branch.state.tick, branch.evolutionState?.worldSeed, branch.evolutionState?.tick)
        }

        val checkpoints = workspace.checkpoints
        require(checkpoints.map { it.id }.distinct().size == checkpoints.size) { "Duplicate checkpoint id" }
        require(checkpoints.all { it.id.isNotBlank() }) { "Blank checkpoint id" }
        checkpoints.forEach { checkpoint ->
            val branch = branchById[checkpoint.branchId] ?: error("Checkpoint references unknown branch")
            require(checkpoint.tick == checkpoint.state.tick) { "Checkpoint tick/state mismatch" }
            require(checkpoint.state.worldSeed == branch.state.worldSeed) { "Checkpoint/world seed mismatch" }
            require(checkpoint.tick >= branch.forkTick) { "Checkpoint predates its branch" }
            validateLayer("Checkpoint people", checkpoint.state.worldSeed, checkpoint.state.tick, checkpoint.peopleState?.worldSeed, checkpoint.peopleState?.tick)
            validateLayer("Checkpoint economy", checkpoint.state.worldSeed, checkpoint.state.tick, checkpoint.economyState?.worldSeed, checkpoint.economyState?.tick)
            validateLayer("Checkpoint evolution", checkpoint.state.worldSeed, checkpoint.state.tick, checkpoint.evolutionState?.worldSeed, checkpoint.evolutionState?.tick)
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
