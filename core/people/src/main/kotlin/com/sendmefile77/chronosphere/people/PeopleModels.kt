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
         * Sex remains derived from the immutable person id so old saves stay compatible.
         * The playable cast is intentionally female-skewed: roughly nine deterministic buckets
         * out of ten resolve to FEMALE and one bucket resolves to MALE.
         */
        fun fromStableKey(key: String): BiologicalSex {
            require(key.isNotBlank())
            var hash = -3750763034362895579L
            key.forEach { char ->
                hash = (hash xor char.code.toLong()) * 1099511628211L
            }
            val bucket = ((hash xor (hash ushr 32)) and Long.MAX_VALUE) % 10L
            return if (bucket == 0L) MALE else FEMALE
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

    /** Active playable cast used by the People screen. */
    fun livingPeople(civilizationId: String): List<NotablePerson> =
        featuredPeople(civilizationId, tick)

    /** Full living roster retained for simulation/history code that needs older people too. */
    fun allLivingPeople(civilizationId: String): List<NotablePerson> =
        persons.filter { it.civilizationId == civilizationId && it.isAlive }

    /**
     * Main playable character pool. Older people remain in the simulation, dynasties and history,
     * but character browsing focuses on adults who are at most 40 years old.
     */
    fun featuredPeople(civilizationId: String, atTick: Long = tick): List<NotablePerson> =
        persons.filter {
            it.civilizationId == civilizationId &&
                it.isAlive &&
                it.ageYearsAt(atTick) in FEATURED_MIN_AGE..FEATURED_MAX_AGE
        }

    companion object {
        const val FEATURED_MIN_AGE = 18
        const val FEATURED_MAX_AGE = 40
    }
}
