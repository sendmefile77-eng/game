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
    BUKKAKE,
    MASTURBATION,
    BDSM,
    FUTANARI_ORGASM,
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
        preferredType: AdultActionType? = null,
    ): AdultActionPlan? {
        val age = person.ageYearsAt(tick)
        if (age < 18 || sequence <= 0) return null

        val rawType = preferredType ?: pickType(person.id, tick, sequence)
        val partner = when (rawType) {
            AdultActionType.MASTURBATION -> null
            else -> pickPartner(person, tick, people, sequence)
        }
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

        fun eligible(candidate: NotablePerson?): Boolean =
            candidate != null &&
                candidate.id != person.id &&
                candidate.id !in forbiddenIds &&
                candidate.isAlive &&
                candidate.ageYearsAt(tick) >= 18

        fun choose(candidates: List<NotablePerson>, tier: String): NotablePerson? {
            val valid = candidates.filter(::eligible).distinctBy { it.id }
            if (valid.isEmpty()) return null
            val index = (stableHash("${person.id}:$tick:$sequence:partner:$tier") % valid.size.toLong()).toInt()
            return valid[index]
        }

        val related = people.relationships
            .filter { it.involves(person.id) }
            .sortedByDescending { kotlin.math.abs(it.strength) }

        fun relatedPeople(kind: RelationshipKind): List<NotablePerson> = related
            .asSequence()
            .filter { it.kind == kind }
            .mapNotNull { relationship ->
                people.persons.firstOrNull { it.id == relationship.otherId(person.id) }
            }
            .toList()

        choose(relatedPeople(RelationshipKind.LOVER), "lover")?.let { return it }
        choose(relatedPeople(RelationshipKind.PARTNER), "partner")?.let { return it }
        choose(relatedPeople(RelationshipKind.ALLY), "ally")?.let { return it }
        choose(
            people.featuredPeople(person.civilizationId, tick)
                .filter { it.civilizationId == person.civilizationId }
                .sortedByDescending { it.prestige },
            "featured-local",
        )?.let { return it }
        choose(
            people.allLivingPeople(person.civilizationId)
                .filter { it.civilizationId == person.civilizationId }
                .sortedByDescending { it.prestige },
            "living-local",
        )?.let { return it }
        return choose(
            people.persons.filter { it.civilizationId != person.civilizationId }.sortedByDescending { it.prestige },
            "foreign-fallback",
        )
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
