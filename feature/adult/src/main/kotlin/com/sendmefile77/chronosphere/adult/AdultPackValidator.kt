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
            checkFiniteBound("baseWeight", event.baseWeight, errors, event.code, min = 0.0)
            event.cultureWeights.forEach { (tag, value) ->
                if (tag.isBlank()) errors += "${event.code} has a blank culture weight tag"
                checkFiniteBound("cultureWeight:$tag", value, errors, event.code, min = -2.0, max = 2.0)
            }
            event.numericWeights.forEach { (key, value) ->
                if (key.isBlank()) errors += "${event.code} has a blank numeric weight key"
                checkFiniteBound("numericWeight:$key", value, errors, event.code, min = -2.0, max = 2.0)
            }
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
