package com.sendmefile77.chronosphere.simulation

data class SimulationTime(val tick: Long, val year: Int, val month: Int)

class SimulationClock(
    private val startYear: Int = -10000,
    private val ticksPerMonth: Int = 1,
) {
    init { require(ticksPerMonth > 0) }

    fun at(tick: Long): SimulationTime {
        require(tick >= 0)
        val months = tick / ticksPerMonth
        val year = startYear + (months / 12).toInt()
        val month = (months % 12).toInt() + 1
        return SimulationTime(tick, year, month)
    }
}
