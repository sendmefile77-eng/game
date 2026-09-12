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
