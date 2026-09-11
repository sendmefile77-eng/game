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
            val capital = world.settlements
                .filter { it.civilizationId == civilization.id }
                .maxByOrNull { it.population }
            val rng = DeterministicRng(WorldSeed(deriveSeed(world.worldSeed, civilization.id)))
            profiles += createSocialProfile(civilization, rng)

            val household = initialHousehold(civilization, capital, rng)
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
        var persons = state.persons.map { person ->
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
            } else person
        }.toMutableList()

        val rulers = state.rulerByCivilization.toMutableMap()
        world.civilizations.forEach { civilization ->
            val rulerId = rulers[civilization.id]
            val rulerAlive = rulerId?.let { id -> persons.firstOrNull { it.id == id }?.isAlive } == true
            if (!rulerAlive) {
                val successor = chooseSuccessor(civilization.id, rulerId, persons, tick)
                if (successor != null) {
                    val index = persons.indexOfFirst { it.id == successor.id }
                    persons[index] = successor.copy(role = PersonRole.RULER)
                    rulers[civilization.id] = successor.id
                    events += SimulationEvent(
                        id = "succession-${civilization.id}-${successor.id}-$tick",
                        tick = tick,
                        code = "RULER_SUCCEEDED",
                        actorIds = listOf(successor.id, civilization.id),
                        locationId = successor.settlementId,
                        facts = mapOf("person" to successor.name, "civilization" to civilization.name),
                    )
                    persons = promoteHeir(civilization.id, successor, persons, tick)
                }
            }
        }

        return state.copy(
            tick = tick,
            persons = persons,
            rulerByCivilization = rulers,
        )
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

        val ruler = person(rng, rulerId, civId, settlementId, dynastyId, 42 + rng.nextInt(18), PersonRole.RULER, 0.78)
        val partner = person(rng, partnerId, civId, settlementId, dynastyId, 33 + rng.nextInt(18), PersonRole.DYNAST, 0.60)
        val heir = person(rng, heirId, civId, settlementId, dynastyId, 18 + rng.nextInt(10), PersonRole.HEIR, 0.54)
        val younger = person(rng, childId, civId, settlementId, dynastyId, 7 + rng.nextInt(10), PersonRole.DYNAST, 0.38)

        val roles = listOf(PersonRole.GENERAL, PersonRole.SCHOLAR, PersonRole.MERCHANT, PersonRole.CLERGY)
        val figures = roles.mapIndexed { index, role ->
            person(
                rng = rng,
                id = "person-$civId-${role.name.lowercase()}-0",
                civilizationId = civId,
                settlementId = settlementId,
                dynastyId = null,
                age = 25 + rng.nextInt(31),
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
    ): NotablePerson = NotablePerson(
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

    private fun chooseSuccessor(
        civilizationId: String,
        oldRulerId: String?,
        persons: List<NotablePerson>,
        tick: Long,
    ): NotablePerson? {
        val oldRuler = oldRulerId?.let { id -> persons.firstOrNull { it.id == id } }
        val dynastyId = oldRuler?.dynastyId
        val adults = persons.filter {
            it.civilizationId == civilizationId && it.isAlive && it.ageYearsAt(tick) >= 18 && it.id != oldRulerId
        }
        return adults.filter { it.role == PersonRole.HEIR }.maxByOrNull { it.prestige }
            ?: adults.filter { dynastyId != null && it.dynastyId == dynastyId }.maxByOrNull { it.prestige }
            ?: adults.maxByOrNull { it.prestige + it.aptitude * 0.25 }
    }

    private fun promoteHeir(
        civilizationId: String,
        ruler: NotablePerson,
        persons: MutableList<NotablePerson>,
        tick: Long,
    ): MutableList<NotablePerson> {
        val candidate = persons.filter {
            it.civilizationId == civilizationId && it.isAlive && it.id != ruler.id &&
                it.ageYearsAt(tick) >= 18 && it.dynastyId == ruler.dynastyId
        }.maxByOrNull { it.prestige } ?: return persons
        val index = persons.indexOfFirst { it.id == candidate.id }
        if (index >= 0) persons[index] = candidate.copy(role = PersonRole.HEIR)
        return persons
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

        val tags = buildSet {
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
            tags = tags,
        )
    }

    private fun annualMortality(age: Int): Double = when {
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

    private fun deriveSeed(seed: Long, value: String): Long {
        var hash = seed xor 0x6A09E667F3BCC909L
        value.forEach { char ->
            hash = hash xor char.code.toLong()
            hash *= 1099511628211L
            hash = hash xor (hash ushr 29)
        }
        return hash
    }

    private fun unit(seed: Long, key: String): Double = DeterministicRng(WorldSeed(deriveSeed(seed, key))).nextDouble()
}
