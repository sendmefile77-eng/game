package com.sendmefile77.chronosphere.society

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.people.PeopleState

/**
 * Adds scene-level morphology context to contract-v1 requests without exposing
 * core:evolution to the optional adult implementation. Participant-specific
 * morphology remains a future contract-v2 concern.
 */
class MorphologyContextAdultModule(
    private val delegate: AdultModule,
    private val people: PeopleState,
    private val evolution: EvolutionState,
) : AdultModule {
    override val contractVersion: Int get() = delegate.contractVersion

    override fun evaluate(request: AdultEventRequest): AdultModuleResult {
        val settlementId = request.participants.asSequence()
            .mapNotNull { participant -> people.persons.firstOrNull { it.id == participant.entityId }?.settlementId }
            .firstOrNull()
            ?: return delegate.evaluate(request)
        val descriptor = evolution.visualDescriptor(settlementId) ?: return delegate.evaluate(request)

        val numeric = linkedMapOf<String, Double>()
        request.context.numericContext.forEach { (key, value) ->
            if (value.isFinite()) numeric[key] = value
        }
        descriptor.numeric.forEach { (key, value) ->
            if (value.isFinite()) numeric[key] = value
        }

        val enriched = request.copy(
            context = request.context.copy(
                cultureTags = (request.context.cultureTags + descriptor.tags).toSortedSet(),
                numericContext = numeric,
            ),
        )
        return delegate.evaluate(enriched)
    }
}
