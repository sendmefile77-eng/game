package com.sendmefile77.chronosphere

import android.content.Context
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.history.HistoryWorkspace
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.storage.HistoryWorkspaceSnapshotV1
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldHydrology
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import org.json.JSONObject
import java.io.File

internal data class GameAutoResumeState(
    val session: GameSession,
    val people: PeopleState,
    val economy: EconomyState,
    val evolution: EvolutionState,
    val workspace: HistoryWorkspace,
    val selectedCivilizationId: String,
    val selectedPersonId: String?,
    val selectedPanel: GamePanel,
)

/**
 * Small crash/activity-recreation safety net for the current playable world.
 *
 * Manual save slots remain untouched. This snapshot is refreshed after real state changes and when
 * the Activity stops, so Android may destroy/recreate Chronosphere without sending the player back
 * to the new-world wizard.
 */
internal object GameAutoResume {
    private const val HISTORY_FILE = "chronosphere-autoresume-history-v1.txt"
    private const val UI_FILE = "chronosphere-autoresume-ui-v1.json"

    @Volatile
    private var latest: GameAutoResumeState? = null

    fun publish(state: GameAutoResumeState) {
        latest = state
    }

    fun flush(context: Context) {
        latest?.let { persist(context.applicationContext, it) }
    }

    fun persist(context: Context, state: GameAutoResumeState) {
        val app = context.applicationContext
        runCatching {
            writeAtomic(
                File(app.filesDir, HISTORY_FILE),
                HistoryWorkspaceSnapshotV1.encode(state.workspace),
            )
            val ui = JSONObject()
                .put("civilizationId", state.selectedCivilizationId)
                .put("personId", state.selectedPersonId ?: JSONObject.NULL)
                .put("panel", state.selectedPanel.name)
            writeAtomic(File(app.filesDir, UI_FILE), ui.toString())
            latest = state
        }
    }

    fun restore(
        context: Context,
        generator: WorldGenerator,
        hydrology: WorldHydrology,
        resourceGenerator: WorldResourceGenerator,
        peopleEngine: PeopleEngine,
        historyTimeline: HistoryTimeline,
    ): GameAutoResumeState? {
        val app = context.applicationContext
        return runCatching {
            val historyFile = File(app.filesDir, HISTORY_FILE)
            if (!historyFile.isFile) return null
            val workspace = HistoryWorkspaceSnapshotV1.decode(historyFile.readText(Charsets.UTF_8))
            val state = workspace.activeState
            val session = restoreGameSession(state, generator, hydrology, resourceGenerator)
            val people = workspace.activePeopleState ?: peopleEngine.initialize(state)
            val economy = workspace.activeEconomyState ?: EconomyEngine(session.world, session.resources).initialize(state)
            val evolution = workspace.activeEvolutionState ?: EvolutionEngine(session.world).initialize(state)
            val syncedWorkspace = historyTimeline.syncActive(workspace, state, people, economy, evolution)

            val ui = File(app.filesDir, UI_FILE)
                .takeIf { it.isFile }
                ?.let { JSONObject(it.readText(Charsets.UTF_8)) }
            val requestedCivilizationId = ui?.optString("civilizationId").orEmpty()
            val civilizationId = requestedCivilizationId.takeIf { id -> state.civilizations.any { it.id == id } }
                ?: state.civilizations.first().id
            val requestedPersonId = ui?.optString("personId")?.takeIf { it.isNotBlank() && it != "null" }
            val personId = requestedPersonId?.takeIf { id -> people.persons.any { it.id == id } }
            val panel = ui?.optString("panel")
                ?.let { value -> GamePanel.entries.firstOrNull { it.name == value } }
                ?: GamePanel.WORLD

            GameAutoResumeState(
                session = session,
                people = people,
                economy = economy,
                evolution = evolution,
                workspace = syncedWorkspace,
                selectedCivilizationId = civilizationId,
                selectedPersonId = personId,
                selectedPanel = panel,
            ).also { latest = it }
        }.getOrNull()
    }

    private fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(text, Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }
}
