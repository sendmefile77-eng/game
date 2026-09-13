package com.sendmefile77.chronosphere.history

/**
 * Process-local read-only pointer to the currently active timeline branch.
 *
 * Authoritative data remains inside HistoryWorkspace/HistoryBranch. This registry exists only so
 * presentation adapters (character cards, chronicle frames, optional adult visuals, etc.) can query
 * the same active branch without threading HistoryWorkspace through every Compose layer.
 */
data class ActiveHistoricalContext(
    val worldSeed: Long,
    val branchId: String,
    val historicalMemory: HistoricalMemoryState?,
    val eraByCivilization: Map<String, String>,
    val cultureTagsByCivilization: Map<String, Set<String>>,
)

object ActiveHistoricalContextRegistry {
    @Volatile
    private var active: ActiveHistoricalContext? = null

    @Synchronized
    fun activate(branch: HistoryBranch) {
        active = ActiveHistoricalContext(
            worldSeed = branch.state.worldSeed,
            branchId = branch.id,
            historicalMemory = branch.historicalMemory,
            eraByCivilization = branch.economyState?.civilizations
                ?.associate { economy -> economy.civilizationId to economy.era.name.lowercase() }
                .orEmpty(),
            cultureTagsByCivilization = branch.state.civilizations
                .associate { civilization -> civilization.id to civilization.cultureTags.toSet() },
        )
    }

    fun snapshot(worldSeed: Long): ActiveHistoricalContext? = active?.takeIf { it.worldSeed == worldSeed }

    @Synchronized
    fun clear() {
        active = null
    }
}
