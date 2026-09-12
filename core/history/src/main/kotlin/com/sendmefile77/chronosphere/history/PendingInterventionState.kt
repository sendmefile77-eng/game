package com.sendmefile77.chronosphere.history

/**
 * Player intent selected in the UI but not yet applied to the simulated world.
 *
 * It is part of timeline state: forks copy it, checkpoints retain it and switching branches swaps it
 * together with the rest of history. Presentation strings are retained so a restored save can show
 * exactly what the player chose before time is advanced.
 */
data class PendingInterventionState(
    val commandId: String,
    val sourceEventId: String,
    val choiceId: String,
    val choiceLabel: String,
    val effectLabel: String,
    val riskLabel: String,
    val kind: InterventionKind,
    val civilizationId: String,
    val strength: Double,
    val targetCivilizationId: String? = null,
) {
    init {
        require(commandId.isNotBlank())
        require(sourceEventId.isNotBlank())
        require(choiceId.isNotBlank())
        require(choiceLabel.isNotBlank())
        require(effectLabel.isNotBlank())
        require(riskLabel.isNotBlank())
        require(civilizationId.isNotBlank())
        require(strength.isFinite() && strength in 0.0..1.0)
        require(targetCivilizationId == null || targetCivilizationId.isNotBlank())
        require(targetCivilizationId == null || targetCivilizationId != civilizationId)
    }
}

/**
 * Process-local cache of the active timeline's pending interventions.
 * Persistent ownership remains HistoryBranch/HistoryCheckpoint; this registry only lets the app's
 * existing decision UI keep a tiny mailbox-style API without leaking choices between branches.
 */
object PendingInterventionRegistry {
    private data class TimelineKey(val worldSeed: Long, val branchId: String)

    private val byTimeline = linkedMapOf<TimelineKey, LinkedHashMap<String, PendingInterventionState>>()
    private var activeKey: TimelineKey? = null

    @Synchronized
    fun activate(worldSeed: Long, branchId: String, restored: List<PendingInterventionState>) {
        require(branchId.isNotBlank())
        val key = TimelineKey(worldSeed, branchId)
        val map = linkedMapOf<String, PendingInterventionState>()
        restored.forEach { pending -> map[pending.sourceEventId] = pending }
        byTimeline[key] = map
        activeKey = key
    }

    @Synchronized
    fun isActive(worldSeed: Long, branchId: String): Boolean =
        activeKey == TimelineKey(worldSeed, branchId)

    @Synchronized
    fun activeSnapshot(): List<PendingInterventionState> = activeMap().values.toList()

    @Synchronized
    fun snapshot(worldSeed: Long, branchId: String): List<PendingInterventionState>? {
        val key = TimelineKey(worldSeed, branchId)
        if (activeKey != key) return null
        return byTimeline[key]?.values?.toList().orEmpty()
    }

    @Synchronized
    fun enqueue(pending: PendingInterventionState) {
        activeMap()[pending.sourceEventId] = pending
    }

    @Synchronized
    fun contains(sourceEventId: String): Boolean = sourceEventId in activeMap()

    @Synchronized
    fun pendingFor(sourceEventId: String): PendingInterventionState? = activeMap()[sourceEventId]

    @Synchronized
    fun remove(sourceEventId: String): PendingInterventionState? = activeMap().remove(sourceEventId)

    @Synchronized
    fun drain(): List<PendingInterventionState> = activeMap().values.toList().also { activeMap().clear() }

    @Synchronized
    fun restore(pending: List<PendingInterventionState>) {
        val map = activeMap()
        pending.forEach { map[it.sourceEventId] = it }
    }

    @Synchronized
    fun clearActive() {
        activeMap().clear()
    }

    private fun activeMap(): LinkedHashMap<String, PendingInterventionState> {
        val key = activeKey ?: TimelineKey(Long.MIN_VALUE, "standalone").also {
            activeKey = it
            byTimeline.putIfAbsent(it, linkedMapOf())
        }
        return byTimeline.getOrPut(key) { linkedMapOf() }
    }
}
