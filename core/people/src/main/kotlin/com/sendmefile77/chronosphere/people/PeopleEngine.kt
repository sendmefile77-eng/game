package com.sendmefile77.chronosphere.people

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.simulation.DeterministicRng
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.simulation.WorldSeed

data class PeopleAdvanceResult(
    val state: PeopleState,
    val events: List<SimulationEvent>,
)

class PeopleEngine {
    fun initialize(world: LivingPlanetState): PeopleState {
        val persons = mutableListOf<NotablePerson>()
        val dynasties = mutableListOf<Dynasty>()
        val relationships = mutableListOf<PersonRelationship>()
        val rulers = linkedMapOf<String, String>()
        val profiles = mutableListOf<SocialProfile>()

        world.civilizations.forEach { civilization ->
            val capital = capital(world, civilization.id)
            val rng = DeterministicRng(WorldSeed(deriveSeed(world.worldSeed, civilization.id)))
            profiles += createSocialProfile(civilization, rng)

            val baseHousehold = initialHousehold(civilization, capital, rng)
            val household = if (world.tick == 0L) {
                baseHousehold
            } else {
                baseHousehold.copy(
                    persons = baseHousehold.persons.map { person ->
                        person.copy(birthTick = person.birthTick + world.tick)
                    },
                    dynasty = baseHousehold.dynasty.copy(foundedTick = world.tick),
                    relationships = baseHousehold.relationships.map { relationship ->
                        relationship.copy(startedTick = world.tick)
                    },
                )
            }
            persons += household.persons
            dynasties += household.dynasty
            relationships += household.relationships
            rulers[civilization.id] = household.rulerId
        }

        return PeopleState(
            worldSeed = world.worldSeed,
            tick = world.tick,
            persons = persons,
            dynasties = dynasties,
            relationships = relationships,
            rulerByCivilization = rulers,
            socialProfiles = profiles,
        )
    }

    fun advance(state: PeopleState, world: LivingPlanetState): PeopleAdvanceResult {
        require(state.worldSeed == world.worldSeed) { "People/world seed mismatch" }
        require(world.tick >= state.tick) { "Cannot move people simulation backward" }
        if (world.tick == state.tick) return PeopleAdvanceResult(state, emptyList())

        var current = state
        val events = mutableListOf<SimulationEvent>()
        var annualTick = ((state.tick / 12L) + 1L) * 12L
        while (annualTick <= world.tick) {
            current = annualStep(current, world, annualTick, events)
            annualTick += 12L
        }
        return PeopleAdvanceResult(current.copy(tick = world.tick), events)
    }

    private fun annualStep(
        state: PeopleState,
        world: LivingPlanetState,
        tick: Long,
        events: MutableList<SimulationEvent>,
    ): PeopleState {
        var persons = applyMortality(state, tick, events).toMutableList()
        var relationships = state.relationships.toMutableList()
        val dynasties = state.dynasties.toMutableList()
        val rulers = state.rulerByCivilization.toMutableMap()

        world.civilizations.forEach { civilization ->
            val rulerId = rulers[civilization.id]
            val currentRuler = rulerId?.let { id -> persons.firstOrNull { it.id == id } }
            val rulerActive = currentRuler?.isAlive == true &&
                currentRuler.ageYearsAt(tick) <= PeopleState.FEATURED_MAX_AGE
            if (!rulerActive) {
                val retiringRuler = currentRuler?.takeIf {
                    it.isAlive && it.ageYearsAt(tick) > PeopleState.FEATURED_MAX_AGE
                }
                var successor = chooseSuccessor(civilization.id, rulerId, persons, tick)
                if (successor == null) {
                    successor = emergencySuccessor(state.worldSeed, civilization, capital(world, civilization.id), tick)
                    persons += successor
                }

                if (retiringRuler != null) {
                    val retiringIndex = persons.indexOfFirst { it.id == retiringRuler.id }
                    if (retiringIndex >= 0) {
                        persons[retiringIndex] = retiringRuler.copy(role = PersonRole.DYNAST)
                    }
                }

                var successorDynastyId = successor.dynastyId
                if (successorDynastyId == null) {
                    successorDynastyId = "dynasty-${civilization.id}-$tick"
                    dynasties += Dynasty(
                        id = successorDynastyId,
                        name = "Дім ${successor.name}",
                        civilizationId = civilization.id,
                        founderPersonId = successor.id,
                        foundedTick = tick,
                        prestige = successor.prestige,
                    )
                    events += SimulationEvent(
                        id = "dynasty-founded-${civilization.id}-${successor.id}-$tick",
                        tick = tick,
                        code = "DYNASTY_FOUNDED",
                        actorIds = listOf(successor.id, civilization.id),
                        locationId = successor.settlementId,
                        facts = mapOf("person" to successor.name, "civilization" to civilization.name),
                    )
                }

                val index = persons.indexOfFirst { it.id == successor.id }
                val crowned = successor.copy(role = PersonRole.RULER, dynastyId = successorDynastyId)
                if (index >= 0) persons[index] = crowned else persons += crowned
                rulers[civilization.id] = crowned.id
                events += SimulationEvent(
                    id = "succession-${civilization.id}-${crowned.id}-$tick",
                    tick = tick,
                    code = "RULER_SUCCEEDED",
                    actorIds = listOf(crowned.id, civilization.id),
                    locationId = crowned.settlementId,
                    facts = buildMap {
                        put("person", crowned.name)
                        put("civilization", civilization.name)
                        if (retiringRuler != null) put("reason", "age_transition")
                    },
                )
            }

            val ruler = rulers[civilization.id]?.let { id -> persons.firstOrNull { it.id == id && it.isAlive } }
            if (ruler != null) {
                val partnerResult = ensureRulerPartner(
                    seed = state.worldSeed,
                    civilization = civilization,
                    ruler = ruler,
                    capital = capital(world, civilization.id),
                    tick = tick,
                    persons = persons,
                    relationships = relationships,
                    events = events,
                )
                persons = partnerResult.persons
                relationships = partnerResult.relationships

                val profile = state.socialProfiles.firstOrNull { it.civilizationId == civilization.id }
                    ?: createSocialProfile(civilization, DeterministicRng(WorldSeed(deriveSeed(state.worldSeed, civilization.id))))
                val birthResult = maybeAddDynasticChild(
                    seed = state.worldSeed,
                    civilization = civilization,
                    ruler = persons.first { it.id == ruler.id },
                    profile = profile,
                    tick = tick,
                    persons = persons,
                    relationships = relationships,
                    events = events,
                )
                persons = birthResult.persons
                relationships = birthResult.relationships
                persons = ensureHeir(civilization.id, rulers[civilization.id], persons, tick)
            }
        }

        val profiles = world.civilizations.map { civilization ->
            val existing = state.socialProfiles.firstOrNull { it.civilizationId == civilization.id }
                ?: createSocialProfile(civilization, DeterministicRng(WorldSeed(deriveSeed(state.worldSeed, civilization.id))))
            driftSocialProfile(existing, civilization, world, tick)
        }

        val pruned = pruneHistoricalPeople(persons, relationships)
        return state.copy(
            tick = tick,
            persons = pruned.first,
            dynasties = dynasties.distinctBy { it.id },
            relationships = pruned.second,
            rulerByCivilization = rulers,
            socialProfiles = profiles,
        )
    }

    private fun applyMortality(
        state: PeopleState,
        tick: Long,
        events: MutableList<SimulationEvent>,
    ): List<NotablePerson> = state.persons.map { person ->
        if (!person.isAlive) return@map person
        val age = person.ageYearsAt(tick)
        val mortality = annualMortality(age)
        if (unit(state.worldSeed, "death:${person.id}:$tick") < mortality) {
            events += SimulationEvent(
                id = "person-death-${person.id}-$tick",
                tick = tick,
                code = "PERSON_DIED",
                actorIds = listOf(person.id, person.civilizationId),
                locationId = person.settlementId,
                numbers = mapOf("age" to age.toDouble()),
                facts = mapOf("person" to person.name),
            )
            person.copy(deathTick = tick)
        } else {
            person
        }
    }

    private data class MutablePeopleResult(
        val persons: MutableList<NotablePerson>,
        val relationships: MutableList<PersonRelationship>,
    )

    private fun ensureRulerPartner(
        seed: Long,
        civilization: Civilization,
        ruler: NotablePerson,
        capital: Settlement?,
        tick: Long,
        persons: MutableList<NotablePerson>,
        relationships: MutableList<PersonRelationship>,
        events: MutableList<SimulationEvent>,
    ): MutablePeopleResult {
        val livePartner = relationships.asSequence()
            .filter { it.kind == RelationshipKind.PARTNER && it.involves(ruler.id) }
            .mapNotNull { relation ->
                val otherId = if (relation.personA == ruler.id) relation.personB else relation.personA
                persons.firstOrNull { it.id == otherId && it.isAlive }
            }
            .firstOrNull()
        if (livePartner != null || ruler.ageYearsAt(tick) >= 76) return MutablePeopleResult(persons, relationships)
        if (unit(seed, "partner:${ruler.id}:$tick") >= 0.38) return MutablePeopleResult(persons, relationships)

        val existing = persons.asSequence()
            .filter { it.civilizationId == civilization.id && it.isAlive && it.id != ruler.id }
            .filter { it.ageYearsAt(tick) in PeopleState.FEATURED_MIN_AGE..PeopleState.FEATURED_MAX_AGE }
            .filter { candidate -> relationships.none { it.kind == RelationshipKind.PARTNER && it.involves(candidate.id) } }
            .maxByOrNull { it.prestige + it.aptitude * 0.25 }

        val partnerAge = 22 + deterministicInt(seed, "consort-age:${civilization.id}:$tick", 19)
        val partner = existing ?: notableFromSeed(
            seed = seed,
            id = "person-${civilization.id}-consort-$tick",
            civilizationId = civilization.id,
            settlementId = capital?.id ?: ruler.settlementId,
            dynastyId = ruler.dynastyId,
            birthTick = tick - partnerAge * 12L,
            role = PersonRole.DYNAST,
            prestigeBase = 0.48,
        ).also { persons += it }

        val relation = PersonRelationship(
            id = "rel-partner-${ruler.id}-${partner.id}-$tick",
            personA = ruler.id,
            personB = partner.id,
            kind = RelationshipKind.PARTNER,
            strength = (0.56 + unit(seed, "partner-strength:${ruler.id}:${partner.id}:$tick") * 0.34).coerceAtMost(0.95),
            startedTick = tick,
        )
        relationships += relation
        events += SimulationEvent(
            id = "partnership-${ruler.id}-${partner.id}-$tick",
            tick = tick,
            code = "RULER_PARTNERSHIP_FORMED",
            actorIds = listOf(ruler.id, partner.id, civilization.id),
            locationId = ruler.settlementId,
            facts = mapOf("ruler" to ruler.name, "partner" to partner.name, "civilization" to civilization.name),
        )
        return MutablePeopleResult(persons, relationships)
    }

    private fun maybeAddDynasticChild(
        seed: Long,
        civilization: Civilization,
        ruler: NotablePerson,
        profile: SocialProfile,
        tick: Long,
        persons: MutableList<NotablePerson>,
        relationships: MutableList<PersonRelationship>,
        events: MutableList<SimulationEvent>,
    ): MutablePeopleResult {
        val dynastyId = ruler.dynastyId ?: return MutablePeopleResult(persons, relationships)
        val livingDynasts = persons.count { it.civilizationId == civilization.id && it.dynastyId == dynastyId && it.isAlive }
        if (livingDynasts >= MAX_LIVING_DYNASTS_PER_CIV) return MutablePeopleResult(persons, relationships)
        val rulerAge = ruler.ageYearsAt(tick)
        if (rulerAge !in 18..72) return MutablePeopleResult(persons, relationships)

        val partner = relationships.asSequence()
            .filter { it.kind == RelationshipKind.PARTNER && it.involves(ruler.id) }
            .mapNotNull { relation ->
                val otherId = if (relation.personA == ruler.id) relation.personB else relation.personA
                persons.firstOrNull { it.id == otherId && it.isAlive }
            }
            .firstOrNull() ?: return MutablePeopleResult(persons, relationships)
        if (partner.ageYearsAt(tick) !in 18..68) return MutablePeopleResult(persons, relationships)

        val chance = (0.06 + profile.fertilityNorm * 0.16 + (1.0 - profile.socialTension) * 0.04).coerceIn(0.04, 0.28)
        if (unit(seed, "dynastic-birth:${civilization.id}:$tick") >= chance) return MutablePeopleResult(persons, relationships)

        val childId = "person-${civilization.id}-dynast-$tick"
        if (persons.any { it.id == childId }) return MutablePeopleResult(persons, relationships)
        val child = notableFromSeed(
            seed = seed,
            id = childId,
            civilizationId = civilization.id,
            settlementId = ruler.settlementId,
            dynastyId = dynastyId,
            birthTick = tick,
            role = PersonRole.DYNAST,
            prestigeBase = 0.34 + ruler.prestige * 0.18,
        )
        persons += child
        relationships += PersonRelationship(
            id = "rel-parent-${ruler.id}-${child.id}",
            personA = ruler.id,
            personB = child.id,
            kind = RelationshipKind.PARENT_CHILD,
            strength = 0.78,
            startedTick = tick,
        )
        relationships += PersonRelationship(
            id = "rel-parent-${partner.id}-${child.id}",
            personA = partner.id,
            personB = child.id,
            kind = RelationshipKind.PARENT_CHILD,
            strength = 0.78,
            startedTick = tick,
        )
        persons.filter { it.civilizationId == civilization.id && it.dynastyId == dynastyId && it.isAlive && it.id != child.id }
            .filter { candidate -> relationships.any { relation -> relation.kind == RelationshipKind.PARENT_CHILD && relation.involves(candidate.id) && relation.involves(ruler.id) } }
            .take(3)
            .forEach { sibling ->
                relationships += PersonRelationship(
                    id = "rel-sibling-${sibling.id}-${child.id}",
                    personA = sibling.id,
                    personB = child.id,
                    kind = RelationshipKind.SIBLING,
                    strength = 0.58,
                    startedTick = tick,
                )
            }
        events += SimulationEvent(
            id = "dynastic-birth-${civilization.id}-${child.id}",
            tick = tick,
            code = "DYNASTIC_BIRTH",
            actorIds = listOf(child.id, ruler.id, partner.id, civilization.id),
            locationId = child.settlementId,
            facts = mapOf(
                "person" to child.name,
                "ruler" to ruler.name,
                "partner" to partner.name,
                "civilization" to civilization.name,
            ),
        )
        return MutablePeopleResult(persons, relationships)
    }

    private fun ensureHeir(
        civilizationId: String,
        rulerId: String?,
        persons: MutableList<NotablePerson>,
        tick: Long,
    ): MutableList<NotablePerson> {
        val ruler = rulerId?.let { id -> persons.firstOrNull { it.id == id && it.isAlive } } ?: return persons
        val existingHeir = persons.firstOrNull {
            it.civilizationId == civilizationId && it.isAlive && it.role == PersonRole.HEIR && it.id != ruler.id
        }
        if (existingHeir != null) return persons

        val candidate = persons.asSequence()
            .filter { it.civilizationId == civilizationId && it.isAlive && it.id != ruler.id }
            .filter { it.dynastyId != null && it.dynastyId == ruler.dynastyId }
            .sortedWith(
                compareByDescending<NotablePerson> {
                    if (it.ageYearsAt(tick) in PeopleState.FEATURED_MIN_AGE..PeopleState.FEATURED_MAX_AGE) 2
                    else if (it.ageYearsAt(tick) >= PeopleState.FEATURED_MIN_AGE) 1
                    else 0
                }
                    .thenByDescending { it.prestige }
                    .thenBy { it.id },
            )
            .firstOrNull() ?: return persons
        val index = persons.indexOfFirst { it.id == candidate.id }
        if (index >= 0) persons[index] = candidate.copy(role = PersonRole.HEIR)
        return persons
    }

    private data class InitialHousehold(
        val persons: List<NotablePerson>,
        val dynasty: Dynasty,
        val relationships: List<PersonRelationship>,
        val rulerId: String,
    )

    private fun initialHousehold(
        civilization: Civilization,
        capital: Settlement?,
        rng: DeterministicRng,
    ): InitialHousehold {
        val civId = civilization.id
        val dynastyId = "dynasty-$civId-0"
        val rulerId = "person-$civId-ruler-0"
        val partnerId = "person-$civId-partner-0"
        val heirId = "person-$civId-heir-0"
        val childId = "person-$civId-child-0"
        val settlementId = capital?.id

        val ruler = person(rng, rulerId, civId, settlementId, dynastyId, 28 + rng.nextInt(13), PersonRole.RULER, 0.78)
        val partner = person(rng, partnerId, civId, settlementId, dynastyId, 22 + rng.nextInt(17), PersonRole.DYNAST, 0.60)
        val heir = person(rng, heirId, civId, settlementId, dynastyId, 18 + rng.nextInt(10), PersonRole.HEIR, 0.54)
        val younger = person(rng, childId, civId, settlementId, dynastyId, 8 + rng.nextInt(10), PersonRole.DYNAST, 0.38)

        val roles = listOf(PersonRole.GENERAL, PersonRole.SCHOLAR, PersonRole.MERCHANT, PersonRole.CLERGY)
        val figures = roles.mapIndexed { index, role ->
            person(
                rng = rng,
                id = "person-$civId-${role.name.lowercase()}-0",
                civilizationId = civId,
                settlementId = settlementId,
                dynastyId = null,
                age = 21 + rng.nextInt(20),
                role = role,
                prestigeBase = 0.36 + index * 0.02,
            )
        }

        val dynasty = Dynasty(
            id = dynastyId,
            name = "Дім ${ruler.name}",
            civilizationId = civId,
            founderPersonId = rulerId,
            foundedTick = 0L,
            prestige = ruler.prestige,
        )
        val relationships = listOf(
            relation("partner", rulerId, partnerId, RelationshipKind.PARTNER, 0.76),
            relation("parent-a", rulerId, heirId, RelationshipKind.PARENT_CHILD, 0.82),
            relation("parent-b", partnerId, heirId, RelationshipKind.PARENT_CHILD, 0.82),
            relation("parent-c", rulerId, childId, RelationshipKind.PARENT_CHILD, 0.82),
            relation("parent-d", partnerId, childId, RelationshipKind.PARENT_CHILD, 0.82),
            relation("siblings", heirId, childId, RelationshipKind.SIBLING, 0.68),
        )
        return InitialHousehold(
            persons = listOf(ruler, partner, heir, younger) + figures,
            dynasty = dynasty,
            relationships = relationships,
            rulerId = rulerId,
        )
    }

    private fun person(
        rng: DeterministicRng,
        id: String,
        civilizationId: String,
        settlementId: String?,
        dynastyId: String?,
        age: Int,
        role: PersonRole,
        prestigeBase: Double,
    ): NotablePerson {
        require(age in 0..PeopleState.FEATURED_MAX_AGE) { "New notable characters must be 40 or younger" }
        return NotablePerson(
            id = id,
            name = personName(rng.nextLong()),
            civilizationId = civilizationId,
            settlementId = settlementId,
            dynastyId = dynastyId,
            birthTick = -age * 12L,
            role = role,
            prestige = (prestigeBase + rng.nextDouble() * 0.18).coerceIn(0.0, 1.0),
            aptitude = 0.35 + rng.nextDouble() * 0.55,
            traits = traits(rng, 2),
        )
    }

    private fun notableFromSeed(
        seed: Long,
        id: String,
        civilizationId: String,
        settlementId: String?,
        dynastyId: String?,
        birthTick: Long,
        role: PersonRole,
        prestigeBase: Double,
    ): NotablePerson {
        val rng = DeterministicRng(WorldSeed(deriveSeed(seed, id)))
        return NotablePerson(
            id = id,
            name = personName(rng.nextLong()),
            civilizationId = civilizationId,
            settlementId = settlementId,
            dynastyId = dynastyId,
            birthTick = birthTick,
            role = role,
            prestige = (prestigeBase + rng.nextDouble() * 0.20).coerceIn(0.0, 1.0),
            aptitude = 0.32 + rng.nextDouble() * 0.60,
            traits = traits(rng, 2),
        )
    }

    private fun emergencySuccessor(
        seed: Long,
        civilization: Civilization,
        capital: Settlement?,
        tick: Long,
    ): NotablePerson {
        val age = 24 + deterministicInt(seed, "emergency-age:${civilization.id}:$tick", 17)
        return notableFromSeed(
            seed = seed,
            id = "person-${civilization.id}-emergency-ruler-$tick",
            civilizationId = civilization.id,
            settlementId = capital?.id,
            dynastyId = null,
            birthTick = tick - age * 12L,
            role = PersonRole.RULER,
            prestigeBase = 0.62,
        )
    }

    private fun chooseSuccessor(
        civilizationId: String,
        oldRulerId: String?,
        persons: List<NotablePerson>,
        tick: Long,
    ): NotablePerson? {
        val oldRuler = oldRulerId?.let { id -> persons.firstOrNull { it.id == id } }
        val dynastyId = oldRuler?.dynastyId
        val preferred = persons.filter {
            it.civilizationId == civilizationId &&
                it.isAlive &&
                it.ageYearsAt(tick) in PeopleState.FEATURED_MIN_AGE..PeopleState.FEATURED_MAX_AGE &&
                it.id != oldRulerId
        }
        fun choose(pool: List<NotablePerson>): NotablePerson? =
            pool.filter { it.role == PersonRole.HEIR }.maxByOrNull { it.prestige }
                ?: pool.filter { dynastyId != null && it.dynastyId == dynastyId }.maxByOrNull { it.prestige }
                ?: pool.maxByOrNull { it.prestige + it.aptitude * 0.25 }
        return choose(preferred)
    }

    private fun createSocialProfile(civilization: Civilization, rng: DeterministicRng): SocialProfile {
        fun draw(center: Double, span: Double): Double = (center + (rng.nextDouble() - 0.5) * span).coerceIn(0.0, 1.0)
        var privacy = draw(0.52, 0.62)
        var openness = draw(0.48, 0.68)
        var fertility = draw(0.55, 0.55)
        var piety = draw(0.50, 0.70)
        var status = draw(0.58, 0.52)
        val pairBonding = draw(0.60, 0.55)
        val jealousy = (0.25 + pairBonding * 0.55 + (rng.nextDouble() - 0.5) * 0.24).coerceIn(0.0, 1.0)
        val tension = draw(0.36, 0.42)

        if ("coastal" in civilization.cultureTags) openness = (openness + 0.07).coerceAtMost(1.0)
        if ("warm-climate" in civilization.cultureTags) openness = (openness + 0.05).coerceAtMost(1.0)
        if ("river-and-rain" in civilization.cultureTags) fertility = (fertility + 0.07).coerceAtMost(1.0)
        if ("highland" in civilization.cultureTags) {
            privacy = (privacy + 0.06).coerceAtMost(1.0)
            status = (status + 0.06).coerceAtMost(1.0)
            piety = (piety + 0.04).coerceAtMost(1.0)
        }

        return SocialProfile(
            civilizationId = civilization.id,
            privacy = privacy,
            bodyOpenness = openness,
            pairBonding = pairBonding,
            jealousy = jealousy,
            fertilityNorm = fertility,
            piety = piety,
            statusHierarchy = status,
            socialTension = tension,
            tags = profileTags(civilization, privacy, openness, pairBonding, fertility, piety, status),
        )
    }

    private fun driftSocialProfile(
        profile: SocialProfile,
        civilization: Civilization,
        world: LivingPlanetState,
        tick: Long,
    ): SocialProfile {
        val atWar = world.wars.any { it.civilizationA == civilization.id || it.civilizationB == civilization.id }
        val noise = (unit(world.worldSeed, "culture-noise:${civilization.id}:$tick") - 0.5) * 0.010
        val instability = (0.55 - civilization.stability).coerceAtLeast(0.0)
        val privacy = (profile.privacy + noise * 0.30 + if (atWar) 0.002 else -0.0004).coerceIn(0.0, 1.0)
        val openness = (profile.bodyOpenness + noise + (civilization.technology - 0.35) * 0.0015 - if (atWar) 0.001 else 0.0)
            .coerceIn(0.0, 1.0)
        val pairBonding = (profile.pairBonding + noise * 0.24).coerceIn(0.0, 1.0)
        val jealousy = (profile.jealousy + noise * 0.20 + instability * 0.002).coerceIn(0.0, 1.0)
        val fertility = (profile.fertilityNorm + noise * 0.25 - civilization.technology * 0.0006).coerceIn(0.0, 1.0)
        val piety = (profile.piety + noise * 0.18 + if (atWar) 0.001 else -0.0002).coerceIn(0.0, 1.0)
        val status = (profile.statusHierarchy + noise * 0.16 + if (atWar) 0.0015 else -civilization.technology * 0.0004)
            .coerceIn(0.0, 1.0)
        val tension = (profile.socialTension * 0.985 + instability * 0.020 + if (atWar) 0.018 else 0.0 + noise * 0.20)
            .coerceIn(0.0, 1.0)
        return profile.copy(
            privacy = privacy,
            bodyOpenness = openness,
            pairBonding = pairBonding,
            jealousy = jealousy,
            fertilityNorm = fertility,
            piety = piety,
            statusHierarchy = status,
            socialTension = tension,
            tags = profileTags(civilization, privacy, openness, pairBonding, fertility, piety, status),
        )
    }

    private fun profileTags(
        civilization: Civilization,
        privacy: Double,
        openness: Double,
        pairBonding: Double,
        fertility: Double,
        piety: Double,
        status: Double,
    ): Set<String> = buildSet {
        addAll(civilization.cultureTags)
        if (openness >= 0.68) add("open")
        if (privacy >= 0.70) add("privacy_high")
        if (privacy <= 0.30) add("privacy_low")
        if (pairBonding >= 0.70) add("monogamous")
        if (pairBonding <= 0.38) add("plural_bonding")
        if (fertility >= 0.72) add("fertility_cult")
        if (piety >= 0.72) add("sacred")
        if (status >= 0.72) add("dynastic")
        if (openness <= 0.30 && privacy >= 0.66) add("austere")
    }

    private fun pruneHistoricalPeople(
        persons: List<NotablePerson>,
        relationships: List<PersonRelationship>,
    ): Pair<List<NotablePerson>, List<PersonRelationship>> {
        val living = persons.filter { it.isAlive }
        val recentDead = persons.filterNot { it.isAlive }
            .groupBy { it.civilizationId }
            .values
            .flatMap { dead -> dead.sortedByDescending { it.deathTick ?: Long.MIN_VALUE }.take(MAX_REMEMBERED_DEAD_PER_CIV) }
        val kept = (living + recentDead).distinctBy { it.id }.sortedWith(compareBy<NotablePerson> { it.civilizationId }.thenBy { it.id })
        val ids = kept.mapTo(hashSetOf()) { it.id }
        val keptRelationships = relationships.filter { it.personA in ids && it.personB in ids }.distinctBy { it.id }
        return kept to keptRelationships
    }

    private fun annualMortality(age: Int): Double = when {
        age < 18 -> 0.0010
        age < 45 -> 0.0015
        age < 55 -> 0.004
        age < 65 -> 0.012
        age < 75 -> 0.032
        age < 85 -> 0.085
        else -> 0.22
    }

    private fun relation(label: String, a: String, b: String, kind: RelationshipKind, strength: Double) =
        PersonRelationship("rel-$label-$a-$b", a, b, kind, strength, 0L)

    private fun traits(rng: DeterministicRng, count: Int): Set<String> {
        val pool = mutableListOf(
            "ambitious", "cautious", "charismatic", "scholarly", "martial",
            "pious", "commercial", "traditionalist", "reformer", "diplomatic",
        )
        val result = linkedSetOf<String>()
        repeat(count.coerceAtMost(pool.size)) {
            result += pool.removeAt(rng.nextInt(pool.size))
        }
        return result
    }

    private fun personName(seed: Long): String {
        val rng = DeterministicRng(WorldSeed(seed))
        val starts = listOf("Ар", "Вел", "Тор", "Мер", "Ка", "Сол", "Нер", "Іл", "Вар", "Тал")
        val middles = listOf("а", "е", "і", "о", "у", "ан", "ен", "ар", "ор")
        val ends = listOf("н", "р", "с", "м", "т", "й", "на", "ра", "ла", "ен")
        return starts[rng.nextInt(starts.size)] + middles[rng.nextInt(middles.size)] + ends[rng.nextInt(ends.size)]
    }

    private fun capital(world: LivingPlanetState, civilizationId: String): Settlement? = world.settlements
        .filter { it.civilizationId == civilizationId }
        .maxByOrNull { it.population }

    private fun deriveSeed(seed: Long, value: String): Long {
        var hash = seed xor 0x6A09E667F3BCC909L
        value.forEach { char ->
            hash = hash xor char.code.toLong()
            hash *= 1099511628211L
            hash = hash xor (hash ushr 29)
        }
        return hash
    }

    private fun deterministicInt(seed: Long, key: String, bound: Int): Int =
        DeterministicRng(WorldSeed(deriveSeed(seed, key))).nextInt(bound)

    private fun unit(seed: Long, key: String): Double = DeterministicRng(WorldSeed(deriveSeed(seed, key))).nextDouble()

    companion object {
        private const val MAX_LIVING_DYNASTS_PER_CIV = 8
        private const val MAX_REMEMBERED_DEAD_PER_CIV = 64
    }
}
