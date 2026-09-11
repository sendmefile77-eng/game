package com.sendmefile77.chronosphere.people

enum class PersonRole {
    RULER,
    HEIR,
    DYNAST,
    GENERAL,
    SCHOLAR,
    MERCHANT,
    CLERGY,
    NOTABLE,
}

enum class BiologicalSex {
    FEMALE,
    MALE;

    companion object {
        /**
         * Existing saves predate an explicit sex field. Deriving it from the immutable person id
         * gives every old and new character a stable value without changing the save format.
         */
        fun fromStableKey(key: String): BiologicalSex {
            require(key.isNotBlank())
            var hash = -3750763034362895579L
            key.forEach { char ->
                hash = (hash xor char.code.toLong()) * 1099511628211L
            }
            return if ((hash and 1L) == 0L) FEMALE else MALE
        }
    }
}

enum class RelationshipKind {
    PARTNER,
    LOVER,
    PARENT_CHILD,
    SIBLING,
    RIVAL,
    ALLY,
    MENTOR,
}

data class NotablePerson(
    val id: String,
    val name: String,
    val civilizationId: String,
    val settlementId: String?,
    val dynastyId: String?,
    val birthTick: Long,
    val deathTick: Long? = null,
    val role: PersonRole,
    val prestige: Double,
    val aptitude: Double,
    val traits: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(civilizationId.isNotBlank())
        require(prestige.isFinite() && prestige in 0.0..1.0)
        require(aptitude.isFinite() && aptitude in 0.0..1.0)
        require(deathTick == null || deathTick >= birthTick)
    }

    val isAlive: Boolean get() = deathTick == null
    val biologicalSex: BiologicalSex get() = BiologicalSex.fromStableKey(id)

    fun ageYearsAt(tick: Long): Int = ((tick - birthTick).coerceAtLeast(0L) / 12L).toInt()
}

data class Dynasty(
    val id: String,
    val name: String,
    val civilizationId: String,
    val founderPersonId: String,
    val foundedTick: Long,
    val prestige: Double,
) {
    init {
        require(id.isNotBlank())
        require(name.isNotBlank())
        require(civilizationId.isNotBlank())
        require(founderPersonId.isNotBlank())
        require(prestige.isFinite() && prestige in 0.0..1.0)
    }
}

data class PersonRelationship(
    val id: String,
    val personA: String,
    val personB: String,
    val kind: RelationshipKind,
    val strength: Double,
    val startedTick: Long,
) {
    init {
        require(id.isNotBlank())
        require(personA.isNotBlank() && personB.isNotBlank())
        require(personA != personB)
        require(strength.isFinite() && strength in -1.0..1.0)
    }

    fun involves(personId: String): Boolean = personA == personId || personB == personId
}

data class SocialProfile(
    val civilizationId: String,
    val privacy: Double,
    val bodyOpenness: Double,
    val pairBonding: Double,
    val jealousy: Double,
    val fertilityNorm: Double,
    val piety: Double,
    val statusHierarchy: Double,
    val socialTension: Double,
    val tags: Set<String>,
) {
    init {
        require(civilizationId.isNotBlank())
        listOf(privacy, bodyOpenness, pairBonding, jealousy, fertilityNorm, piety, statusHierarchy, socialTension)
            .forEach { require(it.isFinite() && it in 0.0..1.0) }
    }
}

data class PeopleState(
    val worldSeed: Long,
    val tick: Long,
    val persons: List<NotablePerson>,
    val dynasties: List<Dynasty>,
    val relationships: List<PersonRelationship>,
    val rulerByCivilization: Map<String, String>,
    val socialProfiles: List<SocialProfile>,
) {
    fun ruler(civilizationId: String): NotablePerson? =
        rulerByCivilization[civilizationId]?.let { rulerId -> persons.firstOrNull { it.id == rulerId && it.isAlive } }

    fun profile(civilizationId: String): SocialProfile? = socialProfiles.firstOrNull { it.civilizationId == civilizationId }

    fun livingPeople(civilizationId: String): List<NotablePerson> =
        persons.filter { it.civilizationId == civilizationId && it.isAlive }
}
