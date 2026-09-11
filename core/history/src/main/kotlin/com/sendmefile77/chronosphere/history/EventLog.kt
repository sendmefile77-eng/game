package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.simulation.SimulationEvent

class EventLog(private val maxEntries: Int = 2_000) {
    init { require(maxEntries > 0) }

    fun append(existing: List<SimulationEvent>, events: List<SimulationEvent>): List<SimulationEvent> =
        (existing + events).sortedBy { it.tick }.takeLast(maxEntries)

    fun between(events: List<SimulationEvent>, fromTick: Long, toTick: Long): List<SimulationEvent> {
        require(fromTick <= toTick)
        return events.filter { it.tick in fromTick..toTick }
    }
}
