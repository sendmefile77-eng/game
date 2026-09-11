package com.sendmefile77.chronosphere.textgen

import com.sendmefile77.chronosphere.simulation.SimulationEvent

class ChronicleTextGenerator {
    fun describe(event: SimulationEvent): String = when (event.code) {
        "SETTLEMENT_FOUNDED" -> "${event.facts["settlement"] ?: "A settlement"} was founded by ${event.facts["civilization"] ?: "a new people"}."
        "SETTLEMENT_GROWTH" -> {
            val settlement = event.facts["settlement"] ?: "The settlement"
            val population = event.numbers["population"]?.toLong()
            if (population != null) "$settlement grew to about $population inhabitants." else "$settlement entered a new period of growth."
        }
        "COLONY_FOUNDED" -> "${event.facts["settlement"] ?: "A new settlement"} was established by settlers from ${event.facts["parent"] ?: "an older city"}."
        "FOOD_SHORTAGE" -> "${event.facts["settlement"] ?: "A settlement"} experienced a food shortage."
        "MIGRATION" -> "About ${event.numbers["people"]?.toLong() ?: 0L} people moved from ${event.facts["from"] ?: "one settlement"} to ${event.facts["to"] ?: "another"}."
        "WAR_STARTED" -> "${event.facts["a"] ?: "One state"} and ${event.facts["b"] ?: "another state"} entered open war."
        "WAR_CASUALTIES" -> "Fighting near ${event.facts["settlementA"] ?: "the frontier"} and ${event.facts["settlementB"] ?: "the opposing frontier"} caused about ${event.numbers["casualties"]?.toLong() ?: 0L} casualties."
        "CITY_CAPTURED" -> "${event.facts["settlement"] ?: "A frontier city"} changed hands after a successful offensive."
        "PEACE_TREATY" -> {
            val winner = event.facts["winner"] ?: "No clear victor"
            "${event.facts["a"] ?: "One state"} and ${event.facts["b"] ?: "another"} signed peace. Result: $winner. ${event.numbers["captures"]?.toInt() ?: 0} cities changed hands during the war."
        }
        "ALLIANCE_FORMED" -> "${event.facts["a"] ?: "One state"} and ${event.facts["b"] ?: "another"} formed an alliance."
        "ALLIANCE_ENDED" -> "The alliance between ${event.facts["a"] ?: "two states"} and ${event.facts["b"] ?: "their partner"} dissolved."
        "INTERVENTION_HARVEST_AID" -> "An outside intervention supported harvests in ${event.facts["civilization"] ?: "a state"}."
        "INTERVENTION_DROUGHT" -> "A forced drought struck ${event.facts["civilization"] ?: "a state"}, reducing food reserves and population."
        "INTERVENTION_TECH_BOOST" -> "${event.facts["civilization"] ?: "A state"} received an abrupt technological impulse."
        "INTERVENTION_STABILITY_SUPPORT" -> "Political stability in ${event.facts["civilization"] ?: "a state"} was artificially reinforced."
        else -> event.code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() } + "."
    }
}
