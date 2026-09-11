package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.history.HistoryBranch
import com.sendmefile77.chronosphere.history.HistoryCheckpoint
import com.sendmefile77.chronosphere.history.HistoryWorkspace
import java.nio.charset.StandardCharsets
import java.util.Base64

object HistoryWorkspaceSnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_HISTORY_V1"

    fun encode(workspace: HistoryWorkspace): String = buildString {
        appendLine(HEADER)
        appendLine("ACTIVE\t${esc(workspace.activeBranchId)}")
        workspace.branches.forEach { branch ->
            appendLine(
                listOf(
                    "BRANCH",
                    esc(branch.id),
                    esc(branch.name),
                    esc(branch.parentBranchId ?: ""),
                    branch.forkTick,
                    pack(GameSnapshotV1.encode(branch.state)),
                ).joinToString("\t"),
            )
        }
        workspace.checkpoints.forEach { checkpoint ->
            appendLine(
                listOf(
                    "CHECKPOINT",
                    esc(checkpoint.id),
                    esc(checkpoint.branchId),
                    esc(checkpoint.label),
                    checkpoint.tick,
                    pack(GameSnapshotV1.encode(checkpoint.state)),
                ).joinToString("\t"),
            )
        }
    }

    fun decode(text: String): HistoryWorkspace {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported history format" }
        val activeRow = lines.firstOrNull { it.startsWith("ACTIVE\t") }?.split('\t')
            ?: error("Missing ACTIVE row")
        require(activeRow.size >= 2)
        val activeBranchId = unesc(activeRow[1])

        val branches = lines.filter { it.startsWith("BRANCH\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 6) { "Malformed BRANCH row" }
            HistoryBranch(
                id = unesc(p[1]),
                name = unesc(p[2]),
                parentBranchId = unesc(p[3]).ifBlank { null },
                forkTick = p[4].toLong(),
                state = GameSnapshotV1.decode(unpack(p[5])),
            )
        }
        require(branches.isNotEmpty()) { "History contains no branches" }
        require(branches.any { it.id == activeBranchId }) { "Active branch is missing" }

        val checkpoints = lines.filter { it.startsWith("CHECKPOINT\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 6) { "Malformed CHECKPOINT row" }
            HistoryCheckpoint(
                id = unesc(p[1]),
                branchId = unesc(p[2]),
                label = unesc(p[3]),
                tick = p[4].toLong(),
                state = GameSnapshotV1.decode(unpack(p[5])),
            )
        }
        require(checkpoints.all { checkpoint -> branches.any { it.id == checkpoint.branchId } }) {
            "Checkpoint references unknown branch"
        }
        return HistoryWorkspace(
            activeBranchId = activeBranchId,
            branches = branches,
            checkpoints = checkpoints,
        )
    }

    private fun pack(value: String): String = Base64.getEncoder().encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
    )

    private fun unpack(value: String): String = String(
        Base64.getDecoder().decode(value),
        StandardCharsets.UTF_8,
    )

    private fun esc(value: String): String = value
        .replace("%", "%25")
        .replace("\t", "%09")
        .replace("\n", "%0A")

    private fun unesc(value: String): String = value
        .replace("%0A", "\n")
        .replace("%09", "\t")
        .replace("%25", "%")
}
