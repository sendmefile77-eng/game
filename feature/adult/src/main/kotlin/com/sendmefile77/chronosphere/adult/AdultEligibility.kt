package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest

internal object AdultEligibility {
    fun isEligible(event: AdultEventRule, request: AdultEventRequest): Boolean {
        val count = request.participants.size
        if (count < event.minParticipants || count > event.maxParticipants) return false
        val tags = AdultCulture.normalizedTags(request.context.cultureTags)
        val required = event.requiredTags.map { it.lowercase() }.toSet()
        if (!tags.containsAll(required)) return false
        val forbidden = event.forbiddenTags.map { it.lowercase() }.toSet()
        if (tags.any { it in forbidden }) return false
        return event.numericGates.all { gatePasses(it, request.context.numericContext) }
    }

    fun eligibleEvents(events: List<AdultEventRule>, request: AdultEventRequest): List<AdultEventRule> =
        events.filter { isEligible(it, request) }

    private fun gatePasses(gate: NumericGate, numeric: Map<String, Double>): Boolean {
        val raw = numeric[gate.key]
        if (raw == null) return !gate.required
        if (!raw.isFinite()) return false
        val min = gate.min
        val max = gate.max
        if (min != null && raw < min) return false
        if (max != null && raw > max) return false
        return true
    }
}
