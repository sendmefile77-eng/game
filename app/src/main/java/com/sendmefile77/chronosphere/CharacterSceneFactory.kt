package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.SceneIntent
import com.sendmefile77.chronosphere.scene.ScenePack
import com.sendmefile77.chronosphere.scene.SceneParticipant
import com.sendmefile77.chronosphere.scene.SceneRecipe
import com.sendmefile77.chronosphere.scene.SceneRequest
import com.sendmefile77.chronosphere.scene.SceneResolver
import com.sendmefile77.chronosphere.scene.WardrobeState

/**
 * Base/offline card scenes. Full builds may replace these logical recipes with an optional
 * content-pack bridge, but the card UI never depends on that implementation.
 */
object CharacterSceneFactory {
    private const val PACK_ID = "chronosphere-base-cards"
    private const val STYLE_ID = "chronosphere-card-v1"

    private val resolver = SceneResolver(
        ScenePack(
            id = PACK_ID,
            version = 1,
            styleId = STYLE_ID,
            recipes = listOf(
                SceneRecipe(
                    id = "card.portrait.human",
                    packId = PACK_ID,
                    packVersion = 1,
                    styleId = STYLE_ID,
                    intents = setOf(SceneIntent.PORTRAIT),
                    supportedRigFamilies = setOf("human"),
                    wardrobeState = WardrobeState.DRESSED,
                    bodyRigKey = "rig.human.card",
                    poseKey = "pose.card.neutral",
                    backgroundKey = "bg.card.neutral",
                    cameraKey = "cam.card.portrait",
                    lightingKey = "light.card.soft",
                ),
                SceneRecipe(
                    id = "card.portrait.morph-fallback",
                    packId = PACK_ID,
                    packVersion = 1,
                    styleId = STYLE_ID,
                    intents = setOf(SceneIntent.PORTRAIT),
                    wardrobeState = WardrobeState.DRESSED,
                    bodyRigKey = "rig.morph.silhouette",
                    poseKey = "pose.card.neutral",
                    backgroundKey = "bg.card.neutral",
                    cameraKey = "cam.card.portrait",
                    lightingKey = "light.card.soft",
                    layerKeys = listOf("morph-safe"),
                    fallbackPriority = 100,
                ),
                SceneRecipe(
                    id = "card.undressed.human",
                    packId = PACK_ID,
                    packVersion = 1,
                    styleId = STYLE_ID,
                    intents = setOf(SceneIntent.CHARACTER_UNDRESS),
                    supportedRigFamilies = setOf("human"),
                    wardrobeState = WardrobeState.UNDRESSED,
                    bodyRigKey = "rig.human.card",
                    poseKey = "pose.card.neutral",
                    backgroundKey = "bg.card.neutral",
                    cameraKey = "cam.card.full",
                    lightingKey = "light.card.soft",
                ),
                SceneRecipe(
                    id = "card.undressed.morph-fallback",
                    packId = PACK_ID,
                    packVersion = 1,
                    styleId = STYLE_ID,
                    intents = setOf(SceneIntent.CHARACTER_UNDRESS),
                    wardrobeState = WardrobeState.UNDRESSED,
                    bodyRigKey = "rig.morph.silhouette",
                    poseKey = "pose.card.neutral",
                    backgroundKey = "bg.card.neutral",
                    cameraKey = "cam.card.full",
                    lightingKey = "light.card.soft",
                    layerKeys = listOf("morph-safe"),
                    fallbackPriority = 100,
                ),
            ),
        ),
    )

    fun resolve(
        person: NotablePerson,
        tick: Long,
        evolution: EvolutionState,
        undressed: Boolean,
    ): ResolvedScene {
        val descriptor = person.settlementId?.let(evolution::visualDescriptor)
        val baseline = descriptor == null || (
            descriptor.bodyPlan.armPairs == 1 &&
                descriptor.bodyPlan.legPairs == 1 &&
                descriptor.bodyPlan.eyeCount == 2 &&
                !descriptor.bodyPlan.hasTail &&
                (descriptor.numeric["morph_divergence"] ?: 0.0) < 0.20
            )
        val participant = SceneParticipant(
            entityId = person.id,
            ageYears = person.ageYearsAt(tick),
            rigFamily = if (baseline) "human" else "lineage:${descriptor!!.lineageId}",
            tags = descriptor?.tags ?: emptySet(),
            numeric = descriptor?.numeric ?: emptyMap(),
        )
        val wardrobe = if (undressed) WardrobeState.UNDRESSED else WardrobeState.DRESSED
        return resolver.resolve(
            SceneRequest(
                worldSeed = evolution.worldSeed,
                eventId = "character-card:${person.id}:${wardrobe.name.lowercase()}",
                rngToken = stableToken(person.id, tick, wardrobe),
                intent = if (undressed) SceneIntent.CHARACTER_UNDRESS else SceneIntent.PORTRAIT,
                participants = listOf(participant),
                sceneTags = descriptor?.tags ?: emptySet(),
                numericContext = descriptor?.numeric ?: emptyMap(),
                requestedWardrobeState = wardrobe,
            ),
        )
    }

    /**
     * Contract-v1 request used only when the optional adult visual bridge is available.
     * Minors never enter that bridge: their ordinary portrait remains a generic core:scene request.
     */
    fun adultRequest(
        person: NotablePerson,
        tick: Long,
        people: PeopleState,
        evolution: EvolutionState,
    ): AdultEventRequest? {
        val age = person.ageYearsAt(tick)
        if (age < 18) return null

        val descriptor = person.settlementId?.let(evolution::visualDescriptor)
        val profile = people.profile(person.civilizationId)
        val tags = buildSet {
            addAll(profile?.tags ?: emptySet())
            addAll(descriptor?.tags ?: emptySet())
        }
        val numeric = linkedMapOf<String, Double>().apply {
            descriptor?.numeric?.let(::putAll)
            if (profile != null) {
                put("privacy", profile.privacy)
                put("body_openness", profile.bodyOpenness)
                put("pair_bonding", profile.pairBonding)
                put("jealousy", profile.jealousy)
                put("fertility_norm", profile.fertilityNorm)
                put("piety", profile.piety)
                put("status_hierarchy", profile.statusHierarchy)
                put("social_tension", profile.socialTension)
            }
        }
        return AdultEventRequest(
            requestId = "character-card:${person.id}",
            participants = listOf(AdultParticipantRef(person.id, age)),
            context = AdultWorldContext(
                worldSeed = evolution.worldSeed,
                tick = tick,
                cultureTags = tags,
                numericContext = numeric,
            ),
        )
    }

    private fun stableToken(personId: String, tick: Long, wardrobe: WardrobeState): Long {
        var hash = tick xor -3750763034362895579L
        "$personId:${wardrobe.name}".forEach { char ->
            hash = (hash xor char.code.toLong()) * 1099511628211L
        }
        return hash
    }
}
