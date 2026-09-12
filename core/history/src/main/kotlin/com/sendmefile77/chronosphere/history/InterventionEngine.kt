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
    EMBASSY,
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
