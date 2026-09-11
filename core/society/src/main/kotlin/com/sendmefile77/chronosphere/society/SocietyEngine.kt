package com.sendmefile77.chronosphere.society

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult
import com.sendmefile77.chronosphere.adultcontracts.AdultParticipantRef
import com.sendmefile77.chronosphere.adultcontracts.AdultWorldContext
import com.sendmefile77.chronosphere.adultcontracts.CoreEffectKind
import com.sendmefile77.chronosphere.adultcontracts.MediaCue
import com.sendmefile77.chronosphere.adultcontracts.NoOpAdultModule
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRelationship
import com.sendmefile77.chronosphere.people.RelationshipKind
import com.sendmefile77.chronosphere.people.SocialProfile
import com.sendmefile77.chronosphere.simulation.DeterministicRng
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.simulation.WorldSeed
import kotlin.math.abs
import kotlin.math.roundToLong

data class SocietyAdvanceResult(
    val world: LivingPlanetState,
    val people: PeopleState,
    val events: List<SimulationEvent>,
    val mediaCues: List<MediaCue>,
)

/**
 * Stable integration boundary between the deterministic core and an optional adult module.
 * The module never receives mutable core objects and can only propose bounded effects.
 */
class SocietyEngine(
    private val adultModule: AdultModule = NoOpAdultModule,
) {
    fun advance(
        fromTick: Long,
        world: LivingPlanetState,
        people: PeopleState,
        economy: EconomyState?,
    ): SocietyAdvanceResult {
        require(world.worldSeed == people.worldSeed) { "Society world/people seed mismatch" }
        require(economy == null || economy.worldSeed == world.worldSeed) { "Society world/economy seed mismatch" }
        require(world.tick >= fromTick) { "Cannot move society simulation backward" }
        if (adultModule.contractVersion != ADULT_CONTRACT_VERSION || world.tick <= fromTick) {
            return SocietyAdvanceResult(world, people, emptyList(), emptyList())
        }

        val annualTicks = annualBoundaries(fromTick, world.tick)
        if (annualTicks.isEmpty()) return SocietyAdvanceResult(world, people, emptyList(), emptyList())

        var currentWorld = world
        var currentPeople = people
        val events = mutableListOf<SimulationEvent>()
        val media = mutableListOf<MediaCue>()

        for (annualTick in annualTicks) {
            for (civilization in currentWorld.civilizations.sortedBy { it.id }) {
                val profile = currentPeople.profile(civilization.id) ?: continue
                val participants = selectParticipants(
                    worldSeed = currentWorld.worldSeed,
                    civilizationId = civilization.id,
                    tick = annualTick,
                    people = currentPeople,
                    profile = profile,
                )
                if (participants.isEmpty()) continue

                val request = buildRequest(
                    tick = annualTick,
                    civilizationId = civilization.id,
                    world = currentWorld,
                    economy = economy,
                    profile = profile,
                    participants = participants,
                )
                val result = runCatching { adultModule.evaluate(request) }.getOrNull() ?: continue
                if (result.eventCode == "NO_OP" || !validResult(request, result)) continue

                val applied = applyEffects(
                    tick = annualTick,
                    civilizationId = civilization.id,
                    world = currentWorld,
                    people = currentPeople,
                    participants = participants,
                    result = result,
                )
                currentWorld = applied.first
                currentPeople = applied.second
                result.mediaCue?.let(media::add)

                events += SimulationEvent(
                    id = "society-${civilization.id}-$annualTick-${stableToken(result.eventCode)}",
                    tick = annualTick,
                    code = "ADULT_SOCIAL_EVENT",
                    actorIds = participants.map { it.id } + civilization.id,
                    locationId = participants.firstOrNull()?.settlementId,
                    numbers = mapOf("effects" to result.effects.size.toDouble()),
                    facts = buildMap {
                        put("civilization", civilization.name)
                        put("eventCode", result.eventCode)
                        put("participants", participants.joinToString(", ") { it.name })
                        result.mediaCue?.let { cue ->
                            put("mediaKey", cue.assetKey)
                            put("mediaTags", cue.tags.sorted().joinToString("|"))
                        }
                    },
                )
            }
        }

        return SocietyAdvanceResult(
            world = currentWorld.copy(recentEvents = (currentWorld.recentEvents + events).takeLast(96)),
            people = currentPeople.copy(tick = world.tick),
            events = events,
            mediaCues = media,
        )
    }

    private fun buildRequest(
        tick: Long,
        civilizationId: String,
        world: LivingPlanetState,
        economy: EconomyState?,
        profile: SocialProfile,
        participants: List<NotablePerson>,
    ): AdultEventRequest {
        val civilization = world.civilizations.first { it.id == civilizationId }
        val economic = economy?.economy(civilizationId)
        val eraTag = economic?.era?.name?.lowercase()?.let { "era_$it" }
        val activeWars = world.wars.count { it.civilizationA == civilizationId || it.civilizationB == civilizationId }
        val routeCount = economy?.routes?.count { it.exporterId == civilizationId || it.importerId == civilizationId } ?: 0
        val settlements = world.settlements.filter { it.civilizationId == civilizationId }
        val urbanPopulation = settlements.filter { it.population >= 5_000L }.sumOf { it.population }
        val urbanization = if (civilization.population <= 0L) 0.0 else {
            (urbanPopulation.toDouble() / civilization.population.toDouble()).coerceIn(0.0, 1.0)
        }
        val wealthBase = (civilization.population / 1000.0).coerceAtLeast(0.2) * 120.0
        val wealth = (civilization.treasury / wealthBase).coerceIn(0.0, 1.0)
        val scarcity = (economic?.shortageIndex ?: 0.0).coerceIn(0.0, 1.0)
        val tradeOpenness = (routeCount / 6.0).coerceIn(0.0, 1.0)
        val warPressure = (activeWars / 2.0).coerceIn(0.0, 1.0)
        val lust = (
            profile.bodyOpenness * 0.34 +
                profile.fertilityNorm * 0.24 +
                (1.0 - profile.privacy) * 0.20 +
                profile.socialTension * 0.22
            ).coerceIn(0.0, 1.0)

        val tags = buildSet {
            addAll(civilization.cultureTags)
            addAll(profile.tags)
            eraTag?.let(::add)
            if (activeWars > 0) add("at_war")
            if (tradeOpenness >= 0.45) add("trade_open")
            if (urbanization >= 0.55) add("urbanized")
        }.map { it.lowercase() }.toSortedSet()

        return AdultEventRequest(
            requestId = "society-$civilizationId-$tick-${participants.joinToString("-") { it.id }}",
            participants = participants.map {
                AdultParticipantRef(entityId = it.id, ageYears = it.ageYearsAt(tick))
            },
            context = AdultWorldContext(
                worldSeed = world.worldSeed,
                tick = tick,
                cultureTags = tags,
                numericContext = linkedMapOf(
                    "technology" to civilization.technology.coerceIn(0.0, 1.0),
                    "wealth" to wealth,
                    "scarcity" to scarcity,
                    "urbanization" to urbanization,
                    "trade_openness" to tradeOpenness,
                    "war_pressure" to warPressure,
                    "social_tension" to profile.socialTension,
                    "tension" to profile.socialTension,
                    "status" to profile.statusHierarchy,
                    "privacy" to profile.privacy,
                    "body_openness" to profile.bodyOpenness,
                    "fertility" to profile.fertilityNorm,
                    "piety" to profile.piety,
                    "jealousy" to profile.jealousy,
                    "monogamy" to profile.pairBonding,
                    "lust" to lust,
                ),
            ),
        )
    }

    private fun validResult(request: AdultEventRequest, result: AdultModuleResult): Boolean {
        if (result.requestId != request.requestId || result.eventCode.isBlank()) return false
        val participantIds = request.participants.mapTo(hashSetOf()) { it.entityId }
        if (result.effects.any { effect ->
                effect.targetId.isBlank() ||
                    effect.reasonCode.isBlank() ||
                    !effect.magnitude.isFinite() ||
                    effect.magnitude !in -1.0..1.0 ||
                    (effect.kind != CoreEffectKind.CULTURE && effect.targetId !in participantIds)
            }) return false
        val cue = result.mediaCue
        if (cue != null && (cue.assetKey.isBlank() || cue.tags.any { it.isBlank() })) return false
        return true
    }

    private fun applyEffects(
        tick: Long,
        civilizationId: String,
        world: LivingPlanetState,
        people: PeopleState,
        participants: List<NotablePerson>,
        result: AdultModuleResult,
    ): Pair<LivingPlanetState, PeopleState> {
        var nextPeople = people
        var nextWorld = world
        val participantIds = participants.mapTo(hashSetOf()) { it.id }

        result.effects
            .filter { it.kind == CoreEffectKind.REPUTATION && it.targetId in participantIds }
            .forEach { effect ->
                nextPeople = nextPeople.copy(
                    persons = nextPeople.persons.map { person ->
                        if (person.id == effect.targetId) {
                            person.copy(prestige = (person.prestige + effect.magnitude * 0.035).coerceIn(0.0, 1.0))
                        } else person
                    },
                )
            }

        val relationshipEffects = result.effects.filter {
            it.kind == CoreEffectKind.RELATIONSHIP && it.targetId in participantIds
        }
        if (participants.size >= 2 && relationshipEffects.isNotEmpty()) {
            val a = participants[0].id
            val b = participants[1].id
            val magnitude = relationshipEffects.map { it.magnitude }.average().coerceIn(-1.0, 1.0)
            val relationshipIndex = nextPeople.relationships.indexOfFirst {
                connects(it, a, b) && (it.kind == RelationshipKind.PARTNER || it.kind == RelationshipKind.LOVER)
            }
            nextPeople = when {
                relationshipIndex >= 0 -> nextPeople.copy(
                    relationships = nextPeople.relationships.mapIndexed { index, relation ->
                        if (index == relationshipIndex) {
                            relation.copy(strength = (relation.strength + magnitude * 0.08).coerceIn(-1.0, 1.0))
                        } else relation
                    },
                )
                magnitude > 0.15 -> nextPeople.copy(
                    relationships = nextPeople.relationships + PersonRelationship(
                        id = "rel-lover-$a-$b-$tick",
                        personA = a,
                        personB = b,
                        kind = RelationshipKind.LOVER,
                        strength = (0.20 + magnitude * 0.45).coerceIn(-1.0, 1.0),
                        startedTick = tick,
                    ),
                )
                else -> nextPeople
            }
        }

        val demography = result.effects
            .filter { it.kind == CoreEffectKind.DEMOGRAPHY && it.targetId in participantIds }
            .sumOf { it.magnitude }
            .coerceIn(-1.0, 1.0)
        if (abs(demography) > 0.0001) {
            val settlements = nextWorld.settlements.toMutableList()
            val targetIndex = settlements.indices
                .filter { settlements[it].civilizationId == civilizationId }
                .maxByOrNull { settlements[it].population }
            if (targetIndex != null) {
                val settlement = settlements[targetIndex]
                val delta = (settlement.population * demography * 0.0008).roundToLong()
                settlements[targetIndex] = settlement.copy(
                    population = (settlement.population + delta).coerceAtLeast(40L),
                )
                val populations = settlements.groupBy { it.civilizationId }
                    .mapValues { (_, values) -> values.sumOf { it.population } }
                nextWorld = nextWorld.copy(
                    settlements = settlements,
                    civilizations = nextWorld.civilizations.map { civilization ->
                        civilization.copy(population = populations[civilization.id] ?: civilization.population)
                    },
                )
            }
        }

        val culture = result.effects
            .filter { it.kind == CoreEffectKind.CULTURE }
            .sumOf { it.magnitude }
            .coerceIn(-1.0, 1.0)
        if (abs(culture) > 0.0001) {
            nextPeople = nextPeople.copy(
                socialProfiles = nextPeople.socialProfiles.map { profile ->
                    if (profile.civilizationId != civilizationId) profile
                    else profile.copy(
                        bodyOpenness = (profile.bodyOpenness + culture * 0.018).coerceIn(0.0, 1.0),
                        privacy = (profile.privacy - culture * 0.012).coerceIn(0.0, 1.0),
                        fertilityNorm = (profile.fertilityNorm + culture * 0.010).coerceIn(0.0, 1.0),
                        socialTension = (profile.socialTension - culture * 0.006).coerceIn(0.0, 1.0),
                    )
                },
            )
        }

        return nextWorld to nextPeople
    }

    private fun selectParticipants(
        worldSeed: Long,
        civilizationId: String,
        tick: Long,
        people: PeopleState,
        profile: SocialProfile,
    ): List<NotablePerson> {
        val adults = people.persons.asSequence()
            .filter { it.civilizationId == civilizationId }
            .filter { it.birthTick <= tick - 18L * 12L }
            .filter { it.deathTick == null || it.deathTick > tick }
            .sortedBy { it.id }
            .toList()
        if (adults.isEmpty()) return emptyList()
        if (adults.size == 1) return adults

        val adultById = adults.associateBy { it.id }
        val rng = DeterministicRng(WorldSeed(deriveSeed(worldSeed, "$civilizationId:$tick")))
        val selected = mutableListOf<NotablePerson>()

        val partnerRelations = people.relationships.asSequence()
            .filter { it.kind == RelationshipKind.PARTNER || it.kind == RelationshipKind.LOVER }
            .filter { it.personA in adultById && it.personB in adultById }
            .filter { !closeFamily(it.personA, it.personB, people) }
            .sortedBy { it.id }
            .toList()
        if (partnerRelations.isNotEmpty()) {
            val relation = partnerRelations[rng.nextInt(partnerRelations.size)]
            selected += adultById.getValue(relation.personA)
            selected += adultById.getValue(relation.personB)
        } else {
            val first = adults[rng.nextInt(adults.size)]
            selected += first
            val compatible = adults.filter { candidate ->
                candidate.id != first.id && !closeFamily(first.id, candidate.id, people)
            }
            if (compatible.isNotEmpty()) selected += compatible[rng.nextInt(compatible.size)]
        }

        if (selected.size < 2) return selected

        val groupChance = (
            profile.bodyOpenness * 0.42 +
                profile.fertilityNorm * 0.20 +
                (1.0 - profile.privacy) * 0.18
            ).coerceIn(0.0, 0.75)
        val desired = when {
            adults.size >= 4 && rng.nextDouble() < groupChance * 0.35 -> 4
            adults.size >= 3 && rng.nextDouble() < groupChance * 0.55 -> 3
            else -> 2
        }
        val selectedIds = selected.mapTo(hashSetOf()) { it.id }
        val remaining = adults.filter { candidate ->
            candidate.id !in selectedIds && selected.none { closeFamily(it.id, candidate.id, people) }
        }.toMutableList()
        while (selected.size < desired && remaining.isNotEmpty()) {
            selected += remaining.removeAt(rng.nextInt(remaining.size))
        }
        return selected
    }

    private fun closeFamily(a: String, b: String, people: PeopleState): Boolean {
        if (a == b) return true
        return people.relationships.any { relation ->
            connects(relation, a, b) &&
                (relation.kind == RelationshipKind.PARENT_CHILD || relation.kind == RelationshipKind.SIBLING)
        }
    }

    private fun connects(relation: PersonRelationship, a: String, b: String): Boolean =
        (relation.personA == a && relation.personB == b) ||
            (relation.personA == b && relation.personB == a)

    private fun annualBoundaries(fromTick: Long, toTick: Long): List<Long> {
        if (toTick <= fromTick) return emptyList()
        val first = ((fromTick / 12L) + 1L) * 12L
        if (first > toTick) return emptyList()
        return buildList {
            var tick = first
            while (tick <= toTick) {
                add(tick)
                tick += 12L
            }
        }
    }

    private fun deriveSeed(seed: Long, key: String): Long {
        var hash = seed xor -3750763034362895579L
        key.forEach { char ->
            hash = (hash xor char.code.toLong()) * 1099511628211L
        }
        return hash
    }

    private fun stableToken(value: String): String {
        var hash = -3750763034362895579L
        value.forEach { char -> hash = (hash xor char.code.toLong()) * 1099511628211L }
        return java.lang.Long.toUnsignedString(hash, 16)
    }
}
