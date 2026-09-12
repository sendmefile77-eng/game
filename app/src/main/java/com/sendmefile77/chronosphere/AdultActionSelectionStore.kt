package com.sendmefile77.chronosphere

import java.util.concurrent.ConcurrentHashMap

/** Survives Compose disposal when the person tab is left. */
internal data class AdultActionSelection(
    val type: AdultActionType,
    val sequence: Int,
) {
    init {
        require(sequence > 0)
    }
}

internal object AdultActionSelectionStore {
    private val byPersonId = ConcurrentHashMap<String, AdultActionSelection>()

    fun get(personId: String): AdultActionSelection? {
        require(personId.isNotBlank())
        return byPersonId[personId]
    }

    fun remember(personId: String, type: AdultActionType): AdultActionSelection {
        require(personId.isNotBlank())
        val next = AdultActionSelection(
            type = type,
            sequence = (byPersonId[personId]?.sequence ?: 0) + 1,
        )
        byPersonId[personId] = next
        return next
    }

    fun clear(personId: String) {
        require(personId.isNotBlank())
        byPersonId.remove(personId)
    }
}
