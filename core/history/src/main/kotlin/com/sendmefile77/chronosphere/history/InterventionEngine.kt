package com.sendmefile77.chronosphere.history

import com.sendmefile77.chronosphere.civilization.AllianceState
import com.sendmefile77.chronosphere.civilization.DiplomaticRelation
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.WarState
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
    TRADE_MISSION,
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
            InterventionKind.DECLARE_WAR -> applyDeclareWar(state, command, event)
            InterventionKind.MAKE_PEACE -> applyMakePeace(state, command, event)
            InterventionKind.FORM_ALLIANCE -> applyFormAlliance(state, command, event)
            InterventionKind.TRADE_MISSION -> applyTradeMission(state, command, event)
        }
    }
}
