package com.sendmefile77.chronosphere.society

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult
import com.sendmefile77.chronosphere.evolution.MorphologyVisualDescriptor
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.people.PeopleState
import kotlin.math.roundToInt

/**
 * Adds scene-level morphology context to contract-v1 requests without exposing
 * core:evolution to the optional adult implementation.
 *
 * The first available participant descriptor remains the recipe-selection context for contract v1.
 * Participant-specific body plans are also preserved on the returned media cue so visual backends
 * can render mixed/hybrid couples without pretending every participant has the same morphology.
 */
class MorphologyContextAdultModule(
    private val delegate: AdultModule,
    private val people: PeopleState,
    private val evolution: EvolutionState,
) : AdultModule {
    override val contractVersion: Int get() = delegate.contractVersion

    override fun evaluate(request: AdultEventRequest): AdultModuleResult {
        val participantDescriptors = request.participants.map { participant ->
            val settlementId = people.persons.firstOrNull { it.id == participant.entityId }?.settlementId
            settlementId?.let(evolution::visualDescriptor)
        }
        val primaryDescriptor = participantDescriptors.firstOrNull { it != null }
            ?: return delegate.evaluate(request)

        val numeric = linkedMapOf<String, Double>()
        request.context.numericContext.forEach { (key, value) ->
            if (value.isFinite()) numeric[key] = value
        }
        primaryDescriptor.numeric.forEach { (key, value) ->
            if (value.isFinite()) numeric[key] = value
        }

        val enriched = request.copy(
            context = request.context.copy(
                cultureTags = (request.context.cultureTags + primaryDescriptor.tags).toSortedSet(),
                numericContext = numeric,
            ),
        )
        val result = delegate.evaluate(enriched)
        val participantMorphTags = buildSet {
            participantDescriptors.forEachIndexed { index, descriptor ->
                if (descriptor != null) addAll(encodedParticipantMorphology(index, descriptor))
            }
        }
        if (participantMorphTags.isEmpty()) return result
        val mediaCue = result.mediaCue ?: return result
        return result.copy(
            mediaCue = mediaCue.copy(tags = mediaCue.tags + participantMorphTags),
        )
    }

    private fun encodedParticipantMorphology(
        index: Int,
        descriptor: MorphologyVisualDescriptor,
    ): Set<String> = buildSet {
        val body = descriptor.bodyPlan
        add("pmorph:$index:arms:${body.armPairs * 2}")
        add("pmorph:$index:legs:${body.legPairs * 2}")
        add("pmorph:$index:eyes:${body.eyeCount}")
        add("pmorph:$index:posture:${body.posture.name.lowercase()}")
        add("pmorph:$index:covering:${body.covering.name.lowercase()}")
        if (body.hasTail) add("pmorph:$index:tail:1")
        encodePercent(index, "height", descriptor.numeric["morph_height"])?.let(::add)
        encodePercent(index, "mass", descriptor.numeric["morph_mass"])?.let(::add)
        encodePercent(index, "limbs", descriptor.numeric["morph_limbs"])?.let(::add)
        encodePercent(index, "cranial", descriptor.numeric["morph_cranial"])?.let(::add)
        encodePercent(index, "eye_size", descriptor.numeric["morph_eye_size"])?.let(::add)
        encodePercent(index, "hair", descriptor.numeric["morph_hair"])?.let(::add)
        encodePercent(index, "pigmentation", descriptor.numeric["morph_pigmentation"])?.let(::add)
        encodePercent(index, "dimorphism", descriptor.numeric["morph_dimorphism"])?.let(::add)
    }

    private fun encodePercent(index: Int, key: String, value: Double?): String? {
        if (value == null || !value.isFinite()) return null
        return "pmorph:$index:$key:${(value * 100.0).roundToInt()}"
    }
}
