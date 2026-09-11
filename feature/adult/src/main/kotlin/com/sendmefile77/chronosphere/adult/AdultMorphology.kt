package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest

internal object MorphKeys {
    const val HEIGHT = "morph_height"
    const val MASS = "morph_mass"
    const val LIMBS = "morph_limbs"
    const val CRANIAL = "morph_cranial"
    const val PIGMENTATION = "morph_pigmentation"
    const val HAIR = "morph_hair"
    const val EYE_SIZE = "morph_eye_size"
    const val DIMORPHISM = "morph_dimorphism"
    const val DIVERGENCE = "morph_divergence"
    const val ADMIXTURE = "morph_admixture"
    const val PRIMARY_ANCESTRY = "morph_primary_ancestry"
}

internal data class AdultMorphology(
    val signaled: Boolean,
    val height: Double? = null,
    val mass: Double? = null,
    val limbs: Double? = null,
    val cranial: Double? = null,
    val pigmentation: Double? = null,
    val hair: Double? = null,
    val eyeSize: Double? = null,
    val dimorphism: Double? = null,
    val divergence: Double? = null,
    val admixture: Double? = null,
    val primaryAncestry: Double? = null,
    val lineageIds: List<String> = emptyList(),
    val bioRank: String? = null,
    val posture: String = "upright",
    val covering: String = "bare_skin",
    val arms: Int = 2,
    val legs: Int = 2,
    val eyes: Int = 2,
    val tail: Boolean = false,
    val mixedAncestry: Boolean = false,
    val hybridLineage: Boolean = false,
    val majorAncestry: String? = null,
    val minorAncestry: String? = null,
) {
    val structuralBaseline: Boolean =
        arms == 2 && legs == 2 && eyes in 1..2 && posture == "upright" && !tail && covering != "scales"

    val visuallyDivergent: Boolean =
        signaled && (
            !structuralBaseline ||
                (divergence != null && divergence >= 0.45) ||
                hybridLineage ||
                mixedAncestry ||
                (admixture != null && admixture >= 0.35)
        )
}

internal object AdultMorphologyParser {
    fun parse(request: AdultEventRequest): AdultMorphology =
        parse(request.context.cultureTags, request.context.numericContext)

    fun parse(tags: Set<String>, numeric: Map<String, Double>): AdultMorphology {
        val normalized = tags.map { it.lowercase().trim() }.filter { it.isNotBlank() }
        val lineageIds = normalized.filter { it.startsWith("lineage:") }.map { it.removePrefix("lineage:") }.filter { it.isNotBlank() }.sorted()
        val bioRank = normalized.firstOrNull { it.startsWith("bio_rank:") }?.removePrefix("bio_rank:")
        val posture = normalized.firstOrNull { it.startsWith("posture:") }?.removePrefix("posture:") ?: "upright"
        val covering = normalized.firstOrNull { it.startsWith("covering:") }?.removePrefix("covering:") ?: "bare_skin"
        val arms = countTag(normalized, "arms:") ?: 2
        val legs = countTag(normalized, "legs:") ?: 2
        val eyes = countTag(normalized, "eyes:") ?: 2
        val tail = normalized.contains("tail")
        val mixed = normalized.contains("mixed_ancestry")
        val hybrid = normalized.contains("hybrid_lineage")
        val major = normalized.firstOrNull { it.startsWith("ancestry:major:") }?.removePrefix("ancestry:major:")
        val minor = normalized.firstOrNull { it.startsWith("ancestry:minor:") }?.removePrefix("ancestry:minor:")
        val morphNumeric = listOf(
            MorphKeys.HEIGHT, MorphKeys.MASS, MorphKeys.LIMBS, MorphKeys.CRANIAL,
            MorphKeys.PIGMENTATION, MorphKeys.HAIR, MorphKeys.EYE_SIZE, MorphKeys.DIMORPHISM,
            MorphKeys.DIVERGENCE, MorphKeys.ADMIXTURE, MorphKeys.PRIMARY_ANCESTRY,
        ).any { AdultContextSignals.finite(numeric, it) != null }
        val signaled = morphNumeric || lineageIds.isNotEmpty() || bioRank != null ||
            normalized.any { it.startsWith("posture:") || it.startsWith("covering:") || it.startsWith("arms:") || it.startsWith("legs:") || it.startsWith("eyes:") } ||
            tail || mixed || hybrid || major != null || minor != null
        return AdultMorphology(
            signaled = signaled,
            height = AdultContextSignals.finite(numeric, MorphKeys.HEIGHT),
            mass = AdultContextSignals.finite(numeric, MorphKeys.MASS),
            limbs = AdultContextSignals.finite(numeric, MorphKeys.LIMBS),
            cranial = AdultContextSignals.finite(numeric, MorphKeys.CRANIAL),
            pigmentation = AdultContextSignals.finite(numeric, MorphKeys.PIGMENTATION),
            hair = AdultContextSignals.finite(numeric, MorphKeys.HAIR),
            eyeSize = AdultContextSignals.finite(numeric, MorphKeys.EYE_SIZE),
            dimorphism = AdultContextSignals.finite(numeric, MorphKeys.DIMORPHISM),
            divergence = AdultContextSignals.finite(numeric, MorphKeys.DIVERGENCE),
            admixture = AdultContextSignals.finite(numeric, MorphKeys.ADMIXTURE),
            primaryAncestry = AdultContextSignals.finite(numeric, MorphKeys.PRIMARY_ANCESTRY),
            lineageIds = lineageIds,
            bioRank = bioRank,
            posture = posture,
            covering = covering,
            arms = arms,
            legs = legs,
            eyes = eyes,
            tail = tail,
            mixedAncestry = mixed,
            hybridLineage = hybrid,
            majorAncestry = major,
            minorAncestry = minor,
        )
    }

    private fun countTag(tags: List<String>, prefix: String): Int? {
        val raw = tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix) ?: return null
        return raw.toIntOrNull()?.takeIf { it in 0..16 }
    }
}

internal object AdultMorphCompatibility {
    fun matches(recipe: AdultVisualRecipe, morph: AdultMorphology): Boolean {
        if (recipe.rigPlan == RigPlan.SILHOUETTE) return true
        if (recipe.requiredPosture.isNotEmpty() && recipe.requiredPosture != morph.posture) return false
        if (recipe.requiredCovering.isNotEmpty() && recipe.requiredCovering != morph.covering) return false
        if (morph.arms < recipe.minArms || morph.arms > recipe.maxArms) return false
        if (morph.legs < recipe.minLegs || morph.legs > recipe.maxLegs) return false
        if (morph.eyes < recipe.minEyes || morph.eyes > recipe.maxEyes) return false
        if (recipe.requireTail && !morph.tail) return false
        if (recipe.forbidTail && morph.tail) return false
        val effectiveDiv = morph.divergence ?: if (!morph.structuralBaseline) 0.6 else null
        if (recipe.maxDivergence != null && effectiveDiv != null && effectiveDiv > recipe.maxDivergence) return false
        if (recipe.minDivergence != null && effectiveDiv != null && effectiveDiv < recipe.minDivergence) return false
        if (recipe.requireHybrid && !(morph.hybridLineage || morph.mixedAncestry || (morph.admixture != null && morph.admixture >= 0.35))) {
            return false
        }
        if (recipe.rigPlan == RigPlan.BASELINE && !morph.structuralBaseline) return false
        if (recipe.rigPlan == RigPlan.BASELINE && morph.divergence != null && morph.divergence >= 0.45) return false
        return true
    }

    fun weightBump(recipe: AdultVisualRecipe, morph: AdultMorphology): Double {
        var bump = 1.0
        if (recipe.rigPlan == RigPlan.HYBRID) {
            val mix = morph.admixture ?: if (morph.hybridLineage || morph.mixedAncestry) 0.5 else 0.0
            bump *= (1.0 + 0.4 * mix).coerceAtLeast(0.0)
        }
        if (recipe.rigPlan == RigPlan.DIVERGENT) {
            val div = morph.divergence ?: if (!morph.structuralBaseline) 0.6 else 0.0
            bump *= (1.0 + 0.35 * div).coerceAtLeast(0.0)
        }
        return if (bump.isFinite()) bump else 1.0
    }
}

internal enum class RigPlan { BASELINE, HYBRID, DIVERGENT, SILHOUETTE }

internal val SAFE_MORPH_FALLBACK = AdultVisualRecipe(
    id = "fallback.silhouette.morph",
    eventCodes = emptySet(),
    sceneFamily = "symbolic",
    rigLayout = "morph-bust",
    poseKey = "pose.hold",
    wardrobeKey = "wardrobe.opaque",
    settingKey = "set.threshold",
    cameraKey = "cam.portrait",
    lightingKey = "light.low",
    effectTags = setOf("fallback", "silhouette", "morph-safe"),
    minParticipants = 1,
    maxParticipants = 99,
    rigPlan = RigPlan.SILHOUETTE,
    minArms = 0,
    maxArms = 16,
    minLegs = 0,
    maxLegs = 16,
    minEyes = 0,
    maxEyes = 16,
    forbidTail = false,
    maxDivergence = null,
)
