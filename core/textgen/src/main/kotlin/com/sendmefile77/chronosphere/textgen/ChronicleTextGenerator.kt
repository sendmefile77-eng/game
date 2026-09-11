package com.sendmefile77.chronosphere.textgen

import com.sendmefile77.chronosphere.simulation.SimulationEvent

class ChronicleTextGenerator {
    fun describe(event: SimulationEvent): String = when (event.code) {
        "SETTLEMENT_FOUNDED" -> {
            val settlement = event.facts["settlement"] ?: "A settlement"
            val civilization = event.facts["civilization"] ?: "a new people"
            "$settlement was founded by $civilization."
        }
        "SETTLEMENT_GROWTH" -> {
            val settlement = event.facts["settlement"] ?: "The settlement"
            val population = event.numbers["population"]?.toLong()
            if (population != null) "$settlement grew to about $population inhabitants." else "$settlement entered a new period of growth."
        }
        "COLONY_FOUNDED" -> {
            val settlement = event.facts["settlement"] ?: "A new settlement"
            val parent = event.facts["parent"] ?: "an older city"
            "$settlement was established by settlers from $parent."
        }
        "FOOD_SHORTAGE" -> {
            val settlement = event.facts["settlement"] ?: "A settlement"
            "$settlement experienced a food shortage."
        }
        else -> event.code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } + "."
    }
}
