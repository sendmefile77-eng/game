package com.sendmefile77.chronosphere.storage

import com.sendmefile77.chronosphere.people.Dynasty
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRelationship
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.people.RelationshipKind
import com.sendmefile77.chronosphere.people.SocialProfile
import java.nio.charset.StandardCharsets
import java.util.Base64

object PeopleSnapshotV1 {
    private const val HEADER = "CHRONOSPHERE_PEOPLE_V1"

    fun encode(state: PeopleState): String = buildString {
        appendLine(HEADER)
        appendLine("WORLD\t${state.worldSeed}\t${state.tick}")
        state.persons.sortedBy { it.id }.forEach { person ->
            appendLine(
                listOf(
                    "PERSON",
                    pack(person.id),
                    pack(person.name),
                    pack(person.civilizationId),
                    person.settlementId?.let(::pack).orEmpty(),
                    person.dynastyId?.let(::pack).orEmpty(),
                    person.birthTick,
                    person.deathTick?.toString().orEmpty(),
                    person.role.name,
                    person.prestige,
                    person.aptitude,
                    encodeStrings(person.traits),
                ).joinToString("\t"),
            )
        }
        state.dynasties.sortedBy { it.id }.forEach { dynasty ->
            appendLine(
                listOf(
                    "DYNASTY",
                    pack(dynasty.id),
                    pack(dynasty.name),
                    pack(dynasty.civilizationId),
                    pack(dynasty.founderPersonId),
                    dynasty.foundedTick,
                    dynasty.prestige,
                ).joinToString("\t"),
            )
        }
        state.relationships.sortedBy { it.id }.forEach { relationship ->
            appendLine(
                listOf(
                    "REL",
                    pack(relationship.id),
                    pack(relationship.personA),
                    pack(relationship.personB),
                    relationship.kind.name,
                    relationship.strength,
                    relationship.startedTick,
                ).joinToString("\t"),
            )
        }
        state.rulerByCivilization.toSortedMap().forEach { (civilizationId, rulerId) ->
            appendLine("RULER\t${pack(civilizationId)}\t${pack(rulerId)}")
        }
        state.socialProfiles.sortedBy { it.civilizationId }.forEach { profile ->
            appendLine(
                listOf(
                    "PROFILE",
                    pack(profile.civilizationId),
                    profile.privacy,
                    profile.bodyOpenness,
                    profile.pairBonding,
                    profile.jealousy,
                    profile.fertilityNorm,
                    profile.piety,
                    profile.statusHierarchy,
                    profile.socialTension,
                    encodeStrings(profile.tags),
                ).joinToString("\t"),
            )
        }
    }

    fun decode(text: String): PeopleState {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == HEADER) { "Unsupported people save format" }
        val world = lines.getOrNull(1)?.split('\t') ?: error("Missing people WORLD row")
        require(world.size >= 3 && world[0] == "WORLD") { "Malformed people WORLD row" }
        val rows = lines.drop(2)

        val persons = rows.filter { it.startsWith("PERSON\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 12) { "Malformed PERSON row" }
            NotablePerson(
                id = unpack(p[1]),
                name = unpack(p[2]),
                civilizationId = unpack(p[3]),
                settlementId = p[4].takeIf { it.isNotBlank() }?.let(::unpack),
                dynastyId = p[5].takeIf { it.isNotBlank() }?.let(::unpack),
                birthTick = p[6].toLong(),
                deathTick = p[7].takeIf { it.isNotBlank() }?.toLong(),
                role = PersonRole.valueOf(p[8]),
                prestige = p[9].toDouble(),
                aptitude = p[10].toDouble(),
                traits = decodeStrings(p[11]),
            )
        }
        val dynasties = rows.filter { it.startsWith("DYNASTY\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 7) { "Malformed DYNASTY row" }
            Dynasty(
                id = unpack(p[1]),
                name = unpack(p[2]),
                civilizationId = unpack(p[3]),
                founderPersonId = unpack(p[4]),
                foundedTick = p[5].toLong(),
                prestige = p[6].toDouble(),
            )
        }
        val relationships = rows.filter { it.startsWith("REL\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 7) { "Malformed people REL row" }
            PersonRelationship(
                id = unpack(p[1]),
                personA = unpack(p[2]),
                personB = unpack(p[3]),
                kind = RelationshipKind.valueOf(p[4]),
                strength = p[5].toDouble(),
                startedTick = p[6].toLong(),
            )
        }
        val rulers = rows.filter { it.startsWith("RULER\t") }.associate { row ->
            val p = row.split('\t')
            require(p.size >= 3) { "Malformed RULER row" }
            unpack(p[1]) to unpack(p[2])
        }
        val profiles = rows.filter { it.startsWith("PROFILE\t") }.map { row ->
            val p = row.split('\t')
            require(p.size >= 11) { "Malformed PROFILE row" }
            SocialProfile(
                civilizationId = unpack(p[1]),
                privacy = p[2].toDouble(),
                bodyOpenness = p[3].toDouble(),
                pairBonding = p[4].toDouble(),
                jealousy = p[5].toDouble(),
                fertilityNorm = p[6].toDouble(),
                piety = p[7].toDouble(),
                statusHierarchy = p[8].toDouble(),
                socialTension = p[9].toDouble(),
                tags = decodeStrings(p[10]),
            )
        }
        val personIds = persons.mapTo(hashSetOf()) { it.id }
        require(rulers.values.all { it in personIds }) { "Ruler references unknown person" }
        require(relationships.all { it.personA in personIds && it.personB in personIds }) {
            "Relationship references unknown person"
        }
        return PeopleState(
            worldSeed = world[1].toLong(),
            tick = world[2].toLong(),
            persons = persons,
            dynasties = dynasties,
            relationships = relationships,
            rulerByCivilization = rulers,
            socialProfiles = profiles,
        )
    }

    private fun encodeStrings(values: Set<String>): String = values.sorted().joinToString(",") { pack(it) }
    private fun decodeStrings(value: String): Set<String> =
        if (value.isBlank()) emptySet() else value.split(',').map(::unpack).toSet()

    private fun pack(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
    )

    private fun unpack(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}
