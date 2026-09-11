package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.CoreEffectKind
import com.sendmefile77.chronosphere.adultcontracts.CoreEffectProposal
import com.sendmefile77.chronosphere.adultcontracts.MediaCue
import kotlin.math.abs

internal object AdultEffectResolver {
    fun effects(event: AdultEventRule, request: AdultEventRequest, fingerprint: Long): List<CoreEffectProposal> {
        val primary = request.participants.first().entityId
        val partner = request.participants.getOrNull(1)?.entityId ?: primary
        val tone = AdultCulture.tone(request.context.cultureTags)
        val ageBias = request.participants.map { it.ageYears }.average().let { age ->
            ((age - 30.0) / 80.0).coerceIn(-0.2, 0.2)
        }
        val proposals = mutableListOf(
            proposal(
                kind = CoreEffectKind.RELATIONSHIP,
                targetId = primary,
                magnitude = AdultFingerprint.bounded(fingerprint, 11L, event.intimacy * tone.bondScale) + ageBias * 0.1,
                reasonCode = "REL_${event.code}",
            ),
            proposal(
                kind = CoreEffectKind.RELATIONSHIP,
                targetId = partner,
                magnitude = AdultFingerprint.bounded(fingerprint, 13L, event.intimacy * tone.bondScale),
                reasonCode = "REL_${event.code}",
            ),
            proposal(
                kind = CoreEffectKind.REPUTATION,
                targetId = primary,
                magnitude = AdultFingerprint.bounded(fingerprint, 17L, event.scandal * tone.scandalScale),
                reasonCode = "REP_${event.code}",
            ),
        )
        if (abs(event.fertility) > 0.0) {
            proposals += proposal(
                kind = CoreEffectKind.DEMOGRAPHY,
                targetId = primary,
                magnitude = AdultFingerprint.bounded(fingerprint, 19L, event.fertility * tone.fertilityScale),
                reasonCode = "DEM_${event.code}",
            )
        }
        val cultureTarget = request.context.cultureTags.sorted().firstOrNull()?.lowercase() ?: "world"
        proposals += proposal(
            kind = CoreEffectKind.CULTURE,
            targetId = cultureTarget,
            magnitude = AdultFingerprint.bounded(fingerprint, 23L, 0.35 * tone.cultureScale),
            reasonCode = "CUL_${event.code}",
        )
        return proposals
    }

    fun mediaCue(
        event: AdultEventRule,
        request: AdultEventRequest,
        fingerprint: Long,
        recipes: AdultVisualRecipeRegistry = AdultVisualRecipeRegistry.bundled(),
    ): MediaCue = recipes.mediaCue(event, request, fingerprint)

    private fun proposal(
        kind: CoreEffectKind,
        targetId: String,
        magnitude: Double,
        reasonCode: String,
    ): CoreEffectProposal {
        val finite = if (magnitude.isNaN() || magnitude.isInfinite()) 0.0 else magnitude
        return CoreEffectProposal(
            kind = kind,
            targetId = targetId.ifBlank { "unknown" },
            magnitude = finite.coerceIn(-1.0, 1.0),
            reasonCode = reasonCode,
        )
    }
}
