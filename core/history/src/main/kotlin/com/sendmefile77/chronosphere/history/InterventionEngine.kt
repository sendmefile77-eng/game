package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import kotlin.math.roundToLong

enum class InterventionKind {
    HARVEST_AID,
    DROUGHT,
    TECHNOLOGY_BOOST,
    STABILITY_SUPPORT,
    WAR_RAID,
    FESTIVAL,
}

data class InterventionCommand(
    val id: String,
    val kind: InterventionKind,
    val civilizationId: String,
    val strength: Double = 0.5,
    val sourceEventId: String? = null,
    val choiceId: String? = null,
    val choiceLabel: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(civilizationId.isNotBlank())
        require(strength.isFinite() && strength in 0.0..1.0)
        require(sourceEventId == null || sourceEventId.isNotBlank())
        require(choiceId == null || choiceId.isNotBlank())
        require(choiceLabel == null || choiceLabel.isNotBlank())
    }
}

class InterventionEngine {
    fun apply(state: LivingPlanetState, command: InterventionCommand): LivingPlanetState {
        require(state.civilizations.any { it.id == command.civilizationId }) {
            "Unknown civilization: ${command.civilizationId}"
        }
        val event = eventFor(state, command)
        return when (command.kind) {
            InterventionKind.HARVEST_AID -> applyHarvestAid(state, command, event)
            InterventionKind.DROUGHT -> applyDrought(state, command, event)
            InterventionKind.TECHNOLOGY_BOOST -> applyTechnologyBoost(state, command, event)
            InterventionKind.STABILITY_SUPPORT -> applyStabilitySupport(state, command, event)
            InterventionKind.WAR_RAID -> applyWarRaid(state, command, event)
            InterventionKind.FESTIVAL -> applyFestival(state, command, event)
        }
    }

    private fun applyHarvestAid(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
    ): LivingPlanetState {
        val settlements = state.settlements.map { settlement ->
            if (settlement.civilizationId != command.civilizationId) return@map settlement
            val addition = settlement.population * (0.16 + command.strength * 0.42)
            settlement.copy(foodStock = settlement.foodStock + addition)
        }
        return state.copy(settlements = settlements, recentEvents = appendEvent(state, event))
    }

    private fun applyDrought(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
    ): LivingPlanetState {
        val settlements = state.settlements.map { settlement ->
            if (settlement.civilizationId != command.civilizationId) return@map settlement
            val foodFactor = (1.0 - (0.25 + command.strength * 0.55)).coerceIn(0.15, 0.75)
            val populationLoss = (settlement.population * command.strength * 0.006).roundToLong()
            settlement.copy(
                population = (settlement.population - populationLoss).coerceAtLeast(40L),
                foodStock = (settlement.foodStock * foodFactor).coerceAtLeast(0.0),
            )
        }
        return withPopulationTotals(
            state.copy(settlements = settlements, recentEvents = appendEvent(state, event)),
        )
    }

    private fun applyTechnologyBoost(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
    ): LivingPlanetState {
        val civilizations = state.civilizations.map { civilization ->
            if (civilization.id != command.civilizationId) return@map civilization
            civilization.copy(
                technology = (civilization.technology + 0.03 + command.strength * 0.12).coerceAtMost(1.0),
            )
        }
        return state.copy(civilizations = civilizations, recentEvents = appendEvent(state, event))
    }

    private fun applyStabilitySupport(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
    ): LivingPlanetState {
        val civilizations = state.civilizations.map { civilization ->
            if (civilization.id != command.civilizationId) return@map civilization
            civilization.copy(
                stability = (civilization.stability + 0.04 + command.strength * 0.16).coerceAtMost(0.98),
            )
        }
        return state.copy(civilizations = civilizations, recentEvents = appendEvent(state, event))
    }

    private fun applyWarRaid(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
    ): LivingPlanetState {
        val targetId = raidTargetId(state, command.civilizationId) ?: command.civilizationId
        val hittingSelf = targetId == command.civilizationId
        val settlements = state.settlements.map { settlement ->
            if (settlement.civilizationId != targetId) return@map settlement
            val factor = (1.0 - (0.18 + command.strength * 0.32)).coerceIn(0.40, 0.90)
            settlement.copy(foodStock = (settlement.foodStock * factor).coerceAtLeast(0.0))
        }
        val civilizations = state.civilizations.map { civilization ->
            when {
                civilization.id == command.civilizationId && hittingSelf ->
                    civilization.copy(stability = (civilization.stability - 0.03).coerceAtLeast(0.12))
                civilization.id == command.civilizationId ->
                    civilization.copy(treasury = (civilization.treasury - 8.0 - command.strength * 14.0).coerceAtLeast(0.0))
                else -> civilization
            }
        }
        return state.copy(
            settlements = settlements,
            civilizations = civilizations,
            recentEvents = appendEvent(state, event),
        )
    }

    private fun applyFestival(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
    ): LivingPlanetState {
        val civilizations = state.civilizations.map { civilization ->
            if (civilization.id != command.civilizationId) return@map civilization
            civilization.copy(
                stability = (civilization.stability + 0.05 + command.strength * 0.12).coerceAtMost(0.98),
                treasury = (civilization.treasury - 6.0 - command.strength * 10.0).coerceAtLeast(0.0),
            )
        }
        val settlements = state.settlements.map { settlement ->
            if (settlement.civilizationId != command.civilizationId) return@map settlement
            settlement.copy(foodStock = settlement.foodStock + settlement.population * (0.04 + command.strength * 0.08))
        }
        return state.copy(
            civilizations = civilizations,
            settlements = settlements,
            recentEvents = appendEvent(state, event),
        )
    }

    private fun raidTargetId(state: LivingPlanetState, actorId: String): String? {
        state.wars.firstOrNull { it.civilizationA == actorId || it.civilizationB == actorId }?.let { war ->
            return if (war.civilizationA == actorId) war.civilizationB else war.civilizationA
        }
        val rival = state.relations
            .filter { it.involves(actorId) }
            .minByOrNull { it.value }
        if (rival != null) {
            return if (rival.civilizationA == actorId) rival.civilizationB else rival.civilizationA
        }
        return state.civilizations.firstOrNull { it.id != actorId }?.id
    }

    private fun withPopulationTotals(state: LivingPlanetState): LivingPlanetState {
        val totals = state.settlements.groupBy { it.civilizationId }.mapValues { (_, settlements) ->
            settlements.sumOf { it.population }
        }
        return state.copy(
            civilizations = state.civilizations.map { civilization ->
                civilization.copy(population = totals[civilization.id] ?: 0L)
            },
        )
    }

    private fun eventFor(state: LivingPlanetState, command: InterventionCommand): SimulationEvent {
        val civilization = state.civilizations.first { it.id == command.civilizationId }
        val code = when (command.kind) {
            InterventionKind.HARVEST_AID -> "INTERVENTION_HARVEST_AID"
            InterventionKind.DROUGHT -> "INTERVENTION_DROUGHT"
            InterventionKind.TECHNOLOGY_BOOST -> "INTERVENTION_TECH_BOOST"
            InterventionKind.STABILITY_SUPPORT -> "INTERVENTION_STABILITY_SUPPORT"
            InterventionKind.WAR_RAID -> "INTERVENTION_WAR_RAID"
            InterventionKind.FESTIVAL -> "INTERVENTION_FESTIVAL"
        }
        val facts = buildMap {
            put("civilization", civilization.name)
            command.sourceEventId?.let { put("sourceEventId", it) }
            command.choiceId?.let { put("choiceId", it) }
            command.choiceLabel?.let { put("choiceLabel", it) }
        }
        return SimulationEvent(
            id = command.id,
            tick = state.tick,
            code = code,
            actorIds = listOf(command.civilizationId),
            facts = facts,
            numbers = mapOf("strength" to command.strength),
        )
    }

    private fun appendEvent(state: LivingPlanetState, event: SimulationEvent): List<SimulationEvent> =
        (state.recentEvents + event).takeLast(96)
}
