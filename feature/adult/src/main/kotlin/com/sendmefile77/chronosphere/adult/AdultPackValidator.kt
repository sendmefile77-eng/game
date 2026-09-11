package com.sendmefile77.chronosphere.adult

internal class AdultPackValidationException(val errors: List<String>) :
    IllegalArgumentException(errors.joinToString("; "))

internal object AdultPackValidator {
    fun validate(pack: AdultContentPack): List<String> {
        val errors = mutableListOf<String>()
        if (pack.id.isBlank()) errors += "pack id is blank"
        if (pack.events.isEmpty()) errors += "pack ${pack.id} has no events"
        val seen = mutableSetOf<String>()
        for (event in pack.events) {
            if (event.code.isBlank()) {
                errors += "pack ${pack.id} has a blank event code"
            } else if (!seen.add(event.code)) {
                errors += "pack ${pack.id} duplicate event code ${event.code}"
            }
            if (event.setting.isBlank()) errors += "${event.code} setting is blank"
            if (event.mediaKey.isBlank()) errors += "${event.code} media key is blank"
            if (event.mediaTags.any { it.isBlank() }) errors += "${event.code} has a blank media tag"
            if (event.mediaTags.isEmpty()) errors += "${event.code} has no media tags"
            checkFiniteBound("intimacy", event.intimacy, errors, event.code)
            checkFiniteBound("scandal", event.scandal, errors, event.code)
            checkFiniteBound("fertility", event.fertility, errors, event.code)
            checkFiniteBound("baseWeight", event.baseWeight, errors, event.code, min = 0.0, max = 2.0)
            event.cultureWeights.forEach { (tag, value) ->
                if (tag.isBlank()) errors += "${event.code} has a blank culture weight tag"
                checkFiniteBound("cultureWeight:$tag", value, errors, event.code, min = -2.0, max = 2.0)
            }
            event.numericWeights.forEach { (key, value) ->
                if (key.isBlank()) errors += "${event.code} has a blank numeric weight key"
                checkFiniteBound("numericWeight:$key", value, errors, event.code, min = -2.0, max = 2.0)
            }
            validateEligibility(event, errors)
        }
        return errors
    }

    fun validateOrThrow(pack: AdultContentPack): AdultContentPack {
        val errors = validate(pack)
        if (errors.isNotEmpty()) throw AdultPackValidationException(errors)
        return pack
    }

    fun validateAll(packs: List<AdultContentPack>): List<AdultContentPack> {
        val ids = mutableSetOf<String>()
        return packs.map { pack ->
            if (!ids.add(pack.id)) throw AdultPackValidationException(listOf("duplicate pack id ${pack.id}"))
            validateOrThrow(pack)
        }
    }

    private fun validateEligibility(event: AdultEventRule, errors: MutableList<String>) {
        if (event.minParticipants < 1) errors += "${event.code} minParticipants < 1"
        if (event.maxParticipants < event.minParticipants) {
            errors += "${event.code} maxParticipants < minParticipants"
        }
        event.requiredTags.forEach { tag ->
            if (tag.isBlank()) errors += "${event.code} has a blank required tag"
        }
        event.forbiddenTags.forEach { tag ->
            if (tag.isBlank()) errors += "${event.code} has a blank forbidden tag"
        }
        if (event.requiredTags.map { it.lowercase() }.any { it in event.forbiddenTags.map { tag -> tag.lowercase() } }) {
            errors += "${event.code} required tag also forbidden"
        }
        event.numericGates.forEach { gate ->
            if (gate.key.isBlank()) errors += "${event.code} has a blank numeric gate key"
            val min = gate.min
            val max = gate.max
            if (min != null && !min.isFinite()) errors += "${event.code} gate ${gate.key} min is not finite"
            if (max != null && !max.isFinite()) errors += "${event.code} gate ${gate.key} max is not finite"
            if (min != null && max != null && min > max) errors += "${event.code} gate ${gate.key} min > max"
        }
    }

    private fun checkFiniteBound(
        name: String,
        value: Double,
        errors: MutableList<String>,
        code: String,
        min: Double = -1.0,
        max: Double = 1.0,
    ) {
        if (value.isNaN() || value.isInfinite()) {
            errors += "$code $name is not finite"
        } else if (value < min || value > max) {
            errors += "$code $name=$value outside [$min, $max]"
        }
    }
}
