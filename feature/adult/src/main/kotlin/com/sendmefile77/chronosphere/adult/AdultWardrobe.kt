package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.MediaCue

internal enum class AdultWardrobeState {
    DRESSED,
    UNDRESSED,
}

internal object AdultUndressPolicy {
    const val MIN_ADULT_AGE = 18
    const val REQUEST_TAG = "wardrobe:undressed"
    const val STATE_TAG = "wardrobe-state:undressed"
    const val CARD_EVENT = "CHARACTER_CARD"

    fun allows(ages: List<Int>): Boolean =
        ages.isNotEmpty() && ages.all { it >= MIN_ADULT_AGE }

    fun allows(participants: List<AdultParticipantRef>): Boolean =
        allows(participants.map { it.ageYears })

    fun requested(request: AdultEventRequest): Boolean =
        AdultCulture.normalizedTags(request.context.cultureTags).contains(REQUEST_TAG)
}

internal class AdultCharacterCardVisuals(
    private val recipes: AdultVisualRecipeRegistry = AdultVisualRecipeRegistry.bundled(),
) {
    fun resolve(request: AdultEventRequest, state: AdultWardrobeState): MediaCue {
        require(AdultUndressPolicy.allows(request.participants)) {
            "Adult module accepts adults only"
        }
        val fingerprint = AdultFingerprint.of(request)
        val recipe = recipes.selectCard(request, state, fingerprint)
        val cue = recipes.encode(recipe, cardRule(state), request)
        return MediaCue(
            assetKey = cue.assetKey,
            tags = cue.tags + setOf(
                "card:character",
                if (state == AdultWardrobeState.UNDRESSED) AdultUndressPolicy.STATE_TAG else "wardrobe-state:dressed",
            ),
        )
    }

    private fun cardRule(state: AdultWardrobeState) = AdultEventRule(
        code = AdultUndressPolicy.CARD_EVENT,
        intimacy = if (state == AdultWardrobeState.UNDRESSED) 0.35 else 0.05,
        scandal = if (state == AdultWardrobeState.UNDRESSED) 0.20 else 0.0,
        fertility = 0.0,
        setting = "character-card",
        mediaKey = "adult://card/${state.name.lowercase()}",
        mediaTags = setOf("character-card", state.name.lowercase()),
    )
}
