package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.people.BiologicalSex
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRelationship
import com.sendmefile77.chronosphere.people.RelationshipKind

enum class AdultActionType {
    FOOTJOB,
    ORAL,
    VAGINAL,
    ANAL,
}

data class AdultActionParticipant(
    val personId: String,
    val name: String,
    val ageYears: Int,
    val sex: BiologicalSex,
) {
    init {
        require(personId.isNotBlank())
        require(name.isNotBlank())
        require(ageYears >= 18) { "Adult action participants must be 18+" }
    }
}

data class AdultActionPlan(
    val type: AdultActionType,
    val sequence: Int,
    val primary: AdultActionParticipant,
    val partner: AdultActionParticipant?,
) {
    init {
        require(sequence > 0)
        require(partner == null || partner.personId != primary.personId)
    }

    val solo: Boolean get() = partner == null

    val cacheToken: String
        get() = listOf(
            type.name,
            sequence.toString(),
            primary.personId,
            partner?.personId ?: "solo",
        ).joinToString(":")
}

/**
 * Deterministic adult-action selection kept out of Compose.
 * Sequence + person + tick must change the chosen act; partners come only from real PeopleState adults.
 */
object AdultActionPlanner {
    private val forbiddenPartnerKinds = setOf(
        RelationshipKind.PARENT_CHILD,
        RelationshipKind.SIBLING,
    )

    fun plan(
        person: NotablePerson,
        tick: Long,
        people: PeopleState,
        sequence: Int,
    ): AdultActionPlan? {
        val age = person.ageYearsAt(tick)
        if (age < 18 || sequence <= 0) return null

        val rawType = pickType(person.id, tick, sequence)
        val partner = pickPartner(person, tick, people, sequence)
        val type = normalizeType(rawType, person.biologicalSex, partner?.biologicalSex)

        return AdultActionPlan(
            type = type,
            sequence = sequence,
            primary = AdultActionParticipant(
                personId = person.id,
                name = person.name,
                ageYears = age,
                sex = person.biologicalSex,
            ),
            partner = partner?.let { other ->
                AdultActionParticipant(
                    personId = other.id,
                    name = other.name,
                    ageYears = other.ageYearsAt(tick),
                    sex = other.biologicalSex,
                )
            },
        )
    }

    internal fun pickType(personId: String, tick: Long, sequence: Int): AdultActionType {
        val values = AdultActionType.entries
        val index = (stableHash("$personId:$tick:$sequence:action-type") % values.size.toLong()).toInt()
        return values[index]
    }

    internal fun pickPartner(
        person: NotablePerson,
        tick: Long,
        people: PeopleState,
        sequence: Int,
    ): NotablePerson? {
        val forbiddenIds = people.relationships
            .filter { it.involves(person.id) && it.kind in forbiddenPartnerKinds }
            .flatMap { listOf(it.personA, it.personB) }
            .filter { it != person.id }
            .toSet()

        val ranked = linkedMapOf<String, NotablePerson>()

        fun consider(candidate: NotablePerson?, prioritySalt: String) {
            if (candidate == null) return
            if (candidate.id == person.id) return
            if (candidate.id in forbiddenIds) return
            if (!candidate.isAlive) return
            if (candidate.ageYearsAt(tick) < 18) return
            ranked.putIfAbsent("${prioritySalt}:${candidate.id}", candidate)
        }

        val related = people.relationships
            .filter { it.involves(person.id) }
            .sortedByDescending { kotlin.math.abs(it.strength) }

        related.filter { it.kind == RelationshipKind.LOVER }.forEach { relationship ->
            consider(people.persons.firstOrNull { it.id == relationship.otherId(person.id) }, "01-lover")
        }
        related.filter { it.kind == RelationshipKind.PARTNER }.forEach { relationship ->
            consider(people.persons.firstOrNull { it.id == relationship.otherId(person.id) }, "02-partner")
        }
        related.filter { it.kind == RelationshipKind.ALLY }.forEach { relationship ->
            consider(people.persons.firstOrNull { it.id == relationship.otherId(person.id) }, "03-ally")
        }

        people.featuredPeople(person.civilizationId, tick)
            .sortedByDescending { it.prestige }
            .forEach { consider(it, "04-featured") }

        people.allLivingPeople(person.civilizationId)
            .filter { it.ageYearsAt(tick) >= 18 }
            .sortedByDescending { it.prestige }
            .forEach { consider(it, "05-living") }

        people.persons
            .filter { it.isAlive && it.ageYearsAt(tick) >= 18 && it.civilizationId != person.civilizationId }
            .sortedByDescending { it.prestige }
            .forEach { consider(it, "06-foreign-adult") }

        val candidates = ranked.values.distinctBy { it.id }
        if (candidates.isEmpty()) return null
        val index = (stableHash("${person.id}:$tick:$sequence:partner") % candidates.size.toLong()).toInt()
        return candidates[index]
    }

    internal fun normalizeType(
        type: AdultActionType,
        primarySex: BiologicalSex,
        partnerSex: BiologicalSex?,
    ): AdultActionType {
        if (type != AdultActionType.VAGINAL) return type
        if (partnerSex == null) {
            return if (primarySex == BiologicalSex.FEMALE) AdultActionType.VAGINAL else AdultActionType.ANAL
        }
        val hasVagina = primarySex == BiologicalSex.FEMALE || partnerSex == BiologicalSex.FEMALE
        return if (hasVagina) AdultActionType.VAGINAL else AdultActionType.ANAL
    }

    private fun stableHash(key: String): Long {
        var hash = -3750763034362895579L
        key.forEach { char ->
            hash = (hash xor char.code.toLong()) * 1099511628211L
        }
        return hash and Long.MAX_VALUE
    }

    private fun PersonRelationship.otherId(personId: String): String =
        if (personA == personId) personB else personA
}
