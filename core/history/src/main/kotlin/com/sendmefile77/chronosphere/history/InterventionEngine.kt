package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.AllianceState
import com.sendmefile77.chronosphere.civilization.DiplomaticRelation
import com.sendmefile77.chronosphere.civilization.InstitutionEngine
import com.sendmefile77.chronosphere.civilization.InternalPoliticsEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.WarState
import com.sendmefile77.chronosphere.civilization.institutionsFor
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import kotlin.math.roundToLong

enum class InterventionKind {
    HARVEST_AID,
    DROUGHT,
    TECHNOLOGY_BOOST,
    STABILITY_SUPPORT,
    WAR_RAID,
    FESTIVAL,
    DECLARE_WAR,
    MAKE_PEACE,
    FORM_ALLIANCE,
    EMBASSY,
    TAX_LOWER,
    TAX_RAISE,
    INSTITUTION_REFORM,
}

data class InterventionCommand(
    val id: String,
    val kind: InterventionKind,
    val civilizationId: String,
    val strength: Double = 0.5,
    val sourceEventId: String? = null,
    val choiceId: String? = null,
    val choiceLabel: String? = null,
    val targetCivilizationId: String? = null,
) {
    init {
        require(id.isNotBlank())
        require(civilizationId.isNotBlank())
        require(strength.isFinite() && strength in 0.0..1.0)
        require(sourceEventId == null || sourceEventId.isNotBlank())
        require(choiceId == null || choiceId.isNotBlank())
        require(choiceLabel == null || choiceLabel.isNotBlank())
        require(targetCivilizationId == null || targetCivilizationId.isNotBlank())
        require(targetCivilizationId == null || targetCivilizationId != civilizationId)
    }
}

class InterventionEngine {
    fun apply(state: LivingPlanetState, command: InterventionCommand): LivingPlanetState {
        require(state.civilizations.any { it.id == command.civilizationId }) {
            "Unknown civilization: ${command.civilizationId}"
        }
        val counterpartId = resolveCounterpart(state, command)
        val event = eventFor(state, command, counterpartId)
        return when (command.kind) {
            InterventionKind.HARVEST_AID -> applyHarvestAid(state, command, event)
            InterventionKind.DROUGHT -> applyDrought(state, command, event)
            InterventionKind.TECHNOLOGY_BOOST -> applyTechnologyBoost(state, command, event)
            InterventionKind.STABILITY_SUPPORT -> applyStabilitySupport(state, command, event)
            InterventionKind.WAR_RAID -> applyWarRaid(state, command, event, counterpartId)
            InterventionKind.FESTIVAL -> applyFestival(state, command, event)
            InterventionKind.DECLARE_WAR -> applyDeclareWar(state, command, event, counterpartId)
            InterventionKind.MAKE_PEACE -> applyMakePeace(state, command, event, counterpartId)
            InterventionKind.FORM_ALLIANCE -> applyFormAlliance(state, command, event, counterpartId)
            InterventionKind.EMBASSY -> applyEmbassy(state, command, event, counterpartId)
            InterventionKind.TAX_LOWER -> applyTaxShift(state, command, event, -1)
            InterventionKind.TAX_RAISE -> applyTaxShift(state, command, event, 1)
            InterventionKind.INSTITUTION_REFORM -> applyInstitutionReform(state, command, event)
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
        counterpartId: String?,
    ): LivingPlanetState {
        val targetId = counterpartId ?: command.civilizationId
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
        val relations = adjustRelation(state.relations, command.civilizationId, targetId, -0.08, state.tick)
        return state.copy(
            settlements = settlements,
            civilizations = civilizations,
            relations = relations,
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

    private fun applyDeclareWar(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
        counterpartId: String?,
    ): LivingPlanetState {
        val targetId = counterpartId ?: return state.copy(recentEvents = appendEvent(state, event))
        if (state.wars.any { it.matches(command.civilizationId, targetId) }) {
            return state.copy(recentEvents = appendEvent(state, event))
        }
        val war = WarState(
            id = "war-${command.id}",
            civilizationA = command.civilizationId,
            civilizationB = targetId,
            startedTick = state.tick,
        )
        val alliances = state.alliances.filterNot { it.matches(command.civilizationId, targetId) }
        val relations = adjustRelation(state.relations, command.civilizationId, targetId, -0.35, state.tick)
        val civilizations = state.civilizations.map { civilization ->
            if (civilization.id != command.civilizationId) return@map civilization
            civilization.copy(
                stability = (civilization.stability - 0.04).coerceAtLeast(0.12),
                treasury = (civilization.treasury - 10.0 - command.strength * 12.0).coerceAtLeast(0.0),
            )
        }
        return state.copy(
            wars = state.wars + war,
            alliances = alliances,
            relations = relations,
            civilizations = civilizations,
            recentEvents = appendEvent(state, event),
        )
    }

    private fun applyMakePeace(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
        counterpartId: String?,
    ): LivingPlanetState {
        val targetId = counterpartId ?: return state.copy(recentEvents = appendEvent(state, event))
        val wars = state.wars.filterNot { it.matches(command.civilizationId, targetId) }
        val relations = setRelation(state.relations, command.civilizationId, targetId, -0.12, state.tick)
        val civilizations = state.civilizations.map { civilization ->
            if (civilization.id != command.civilizationId) return@map civilization
            civilization.copy(
                stability = (civilization.stability + 0.03 + command.strength * 0.06).coerceAtMost(0.98),
                treasury = (civilization.treasury - 4.0).coerceAtLeast(0.0),
            )
        }
        return state.copy(
            wars = wars,
            relations = relations,
            civilizations = civilizations,
            recentEvents = appendEvent(state, event),
        )
    }

    private fun applyFormAlliance(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
        counterpartId: String?,
    ): LivingPlanetState {
        val targetId = counterpartId ?: return state.copy(recentEvents = appendEvent(state, event))
        if (state.wars.any { it.matches(command.civilizationId, targetId) }) {
            return state.copy(recentEvents = appendEvent(state, event))
        }
        val alliances = if (state.alliances.any { it.matches(command.civilizationId, targetId) }) {
            state.alliances
        } else {
            state.alliances + AllianceState(
                id = "alliance-${command.id}",
                civilizationA = command.civilizationId,
                civilizationB = targetId,
                startedTick = state.tick,
            )
        }
        val relations = setRelation(state.relations, command.civilizationId, targetId, 0.74, state.tick)
        val civilizations = state.civilizations.map { civilization ->
            if (civilization.id != command.civilizationId) return@map civilization
            civilization.copy(treasury = (civilization.treasury - 8.0 - command.strength * 8.0).coerceAtLeast(0.0))
        }
        return state.copy(
            alliances = alliances,
            relations = relations,
            civilizations = civilizations,
            recentEvents = appendEvent(state, event),
        )
    }

    private fun applyEmbassy(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
        counterpartId: String?,
    ): LivingPlanetState {
        val targetId = counterpartId ?: return state.copy(recentEvents = appendEvent(state, event))
        val relations = adjustRelation(
            state.relations,
            command.civilizationId,
            targetId,
            0.12 + command.strength * 0.16,
            state.tick,
        )
        val civilizations = state.civilizations.map { civilization ->
            if (civilization.id != command.civilizationId) return@map civilization
            civilization.copy(treasury = (civilization.treasury - 5.0 - command.strength * 7.0).coerceAtLeast(0.0))
        }
        return state.copy(
            relations = relations,
            civilizations = civilizations,
            recentEvents = appendEvent(state, event),
        )
    }

    private fun applyTaxShift(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
        direction: Int,
    ): LivingPlanetState {
        val shifted = InternalPoliticsEngine.shiftTaxPolicy(state, command.civilizationId, direction)
        return shifted.copy(recentEvents = appendEvent(shifted, event))
    }

    private fun applyInstitutionReform(
        state: LivingPlanetState,
        command: InterventionCommand,
        event: SimulationEvent,
    ): LivingPlanetState {
        val reformed = InstitutionEngine.reform(state, command.civilizationId, command.strength)
        return reformed.copy(recentEvents = appendEvent(reformed, event))
    }

    internal fun resolveCounterpart(state: LivingPlanetState, command: InterventionCommand): String? {
        val actorId = command.civilizationId
        val requested = command.targetCivilizationId
        if (requested != null && requested != actorId && state.civilizations.any { it.id == requested }) {
            return requested
        }
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

    private fun adjustRelation(
        relations: List<DiplomaticRelation>,
        a: String,
        b: String,
        delta: Double,
        tick: Long,
    ): List<DiplomaticRelation> {
        if (a == b) return relations
        val existing = relations.firstOrNull { it.matches(a, b) }
        return if (existing == null) {
            relations + DiplomaticRelation(a, b, delta.coerceIn(-1.0, 1.0), tick)
        } else {
            relations.map { relation ->
                if (!relation.matches(a, b)) relation
                else relation.copy(value = (relation.value + delta).coerceIn(-1.0, 1.0), lastUpdatedTick = tick)
            }
        }
    }

    private fun setRelation(
        relations: List<DiplomaticRelation>,
        a: String,
        b: String,
        value: Double,
        tick: Long,
    ): List<DiplomaticRelation> {
        if (a == b) return relations
        val existing = relations.firstOrNull { it.matches(a, b) }
        return if (existing == null) {
            relations + DiplomaticRelation(a, b, value.coerceIn(-1.0, 1.0), tick)
        } else {
            relations.map { relation ->
                if (!relation.matches(a, b)) relation
                else relation.copy(value = value.coerceIn(-1.0, 1.0), lastUpdatedTick = tick)
            }
        }
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

    private fun eventFor(state: LivingPlanetState, command: InterventionCommand, counterpartId: String?): SimulationEvent {
        val civilization = state.civilizations.first { it.id == command.civilizationId }
        val counterpart = counterpartId?.let { id -> state.civilizations.firstOrNull { it.id == id } }
        val weakestInstitution = state.institutionsFor(command.civilizationId)
            .minByOrNull { it.capacity * 0.65 + it.legitimacy * 0.35 }
        val code = when (command.kind) {
            InterventionKind.HARVEST_AID -> "INTERVENTION_HARVEST_AID"
            InterventionKind.DROUGHT -> "INTERVENTION_DROUGHT"
            InterventionKind.TECHNOLOGY_BOOST -> "INTERVENTION_TECH_BOOST"
            InterventionKind.STABILITY_SUPPORT -> "INTERVENTION_STABILITY_SUPPORT"
            InterventionKind.WAR_RAID -> "INTERVENTION_WAR_RAID"
            InterventionKind.FESTIVAL -> "INTERVENTION_FESTIVAL"
            InterventionKind.DECLARE_WAR -> "WAR_STARTED"
            InterventionKind.MAKE_PEACE -> "PEACE_TREATY"
            InterventionKind.FORM_ALLIANCE -> "ALLIANCE_FORMED"
            InterventionKind.EMBASSY -> "INTERVENTION_EMBASSY"
            InterventionKind.TAX_LOWER -> "INTERVENTION_TAX_LOWER"
            InterventionKind.TAX_RAISE -> "INTERVENTION_TAX_RAISE"
            InterventionKind.INSTITUTION_REFORM -> "INTERVENTION_INSTITUTION_REFORM"
        }
        val facts = buildMap {
            put("civilization", civilization.name)
            put("a", civilization.name)
            counterpart?.let { put("b", it.name) }
            counterpartId?.let { put("targetCivilizationId", it) }
            command.sourceEventId?.let { put("sourceEventId", it) }
            command.choiceId?.let { put("choiceId", it) }
            command.choiceLabel?.let { put("choiceLabel", it) }
            if (command.kind == InterventionKind.INSTITUTION_REFORM) {
                weakestInstitution?.let { put("institution", it.kind.name) }
            }
        }
        return SimulationEvent(
            id = command.id,
            tick = state.tick,
            code = code,
            actorIds = listOfNotNull(command.civilizationId, counterpartId),
            facts = facts,
            numbers = mapOf("strength" to command.strength),
        )
    }

    private fun appendEvent(state: LivingPlanetState, event: SimulationEvent): List<SimulationEvent> =
        (state.recentEvents + event).takeLast(96)
}
