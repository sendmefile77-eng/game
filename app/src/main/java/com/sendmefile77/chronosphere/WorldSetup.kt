package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.evolution.BiologicalRank
import com.sendmefile77.chronosphere.evolution.BodyPlan
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.MorphologyProfile
import com.sendmefile77.chronosphere.evolution.Posture
import com.sendmefile77.chronosphere.evolution.SkinCovering
import com.sendmefile77.chronosphere.people.PeopleState

/** Player-authored starting conditions. These are applied to the real simulation state, not UI-only metadata. */
data class WorldSetup(
    val seed: Long,
    val startSpacing: StartSpacing = StartSpacing.NORMAL,
    val tribes: List<TribeSetup> = defaultTribes(3),
) {
    init {
        require(tribes.size in MIN_TRIBES..MAX_TRIBES)
        require(tribes.map { it.name.trim().lowercase() }.distinct().size == tribes.size) { "Tribe names must be unique" }
    }

    companion object {
        const val MIN_TRIBES = 2
        const val MAX_TRIBES = 6

        fun default(seed: Long = 424242L, tribeCount: Int = 3): WorldSetup =
            WorldSetup(seed = seed, tribes = defaultTribes(tribeCount.coerceIn(MIN_TRIBES, MAX_TRIBES)))
    }
}

enum class StartSpacing(val minimumDistance: Int, val displayNameUk: String) {
    CLOSE(4, "Тісно"),
    NORMAL(8, "Звичайно"),
    FAR(13, "Далеко"),
}

enum class TribeRace(
    val displayNameUk: String,
    val tag: String,
) {
    HUMAN("Люди", "race_human"),
    TALL_SLENDER("Високі й стрункі", "race_tall_slender"),
    ROBUST("Масивні", "race_robust"),
    FURRED("Вкриті хутром", "race_furred"),
    TAILED("Хвостаті", "race_tailed"),
    LARGE_EYED("Великоокі", "race_large_eyed"),
    FOUR_ARMED("Чотирирукі", "race_four_armed"),
    SCALED("Лускаті", "race_scaled"),
}

enum class SexualFeature(
    val displayNameUk: String,
    val cultureTag: String,
) {
    NUDITY("Культура наготи", "nudity_culture"),
    PUBLIC("Публічна сексуальність", "public_sex"),
    RITUAL("Ритуальний секс", "ritual_sex"),
    FERTILITY("Культ родючості", "fertility_cult"),
    DOMINANCE("Домінування", "dominance_culture"),
    SUBMISSION("Підкорення", "submission_culture"),
    BONDAGE("Бондаж", "bondage_culture"),
    GROUP("Групові практики", "group_sex"),
    VOYEURISM("Вуайєризм", "voyeurism_culture"),
    STATUS_BONDS("Статусні зв’язки", "status_bonds"),
    POLYGAMY("Полігамія", "plural_bonding"),
    MONOGAMY("Сувора моногамія", "monogamous"),
}

enum class TribeTrait(
    val displayNameUk: String,
    val cultureTag: String,
) {
    WARLIKE("Войовничі", "warlike"),
    TRADERS("Торговці", "mercantile"),
    ISOLATIONIST("Ізоляціоністи", "isolationist"),
    NOMADIC("Кочівники", "nomadic"),
    MARITIME("Мореплавці", "maritime"),
    TECHNOLOGICAL("Винахідники", "technological"),
    RAPID_MUTATION("Швидко мутують", "rapid_mutation"),
    HYBRID_FRIENDLY("Відкриті до гібридів", "hybrid_friendly"),
    BODY_CULT("Культ тіла", "body_cult"),
    MATRIARCHAL("Матріархальні", "matriarchal"),
    DYNASTIC("Династичні", "dynastic"),
}

enum class TribeWeakness(
    val displayNameUk: String,
    val cultureTag: String,
) {
    INTERNAL_SPLITS("Внутрішні розколи", "weak_internal_splits"),
    LOW_FERTILITY("Низька народжуваність", "weak_low_fertility"),
    POOR_ECONOMY("Слабка економіка", "weak_economy"),
    XENOPHOBIC("Ксенофобія", "weak_xenophobia"),
    GENETIC_BOTTLENECK("Генетична вузькість", "weak_genetic_bottleneck"),
}

data class TribeSetup(
    val name: String,
    val race: TribeRace = TribeRace.HUMAN,
    val sexualFeatures: Set<SexualFeature> = setOf(SexualFeature.NUDITY, SexualFeature.FERTILITY),
    val traits: Set<TribeTrait> = setOf(TribeTrait.BODY_CULT),
    val weakness: TribeWeakness = TribeWeakness.INTERNAL_SPLITS,
) {
    init {
        require(name.isNotBlank())
        require(sexualFeatures.size in 1..4) { "Choose 1..4 sexual culture features" }
        require(traits.size in 1..2) { "Choose 1..2 tribe traits" }
        require(!(SexualFeature.MONOGAMY in sexualFeatures && SexualFeature.POLYGAMY in sexualFeatures)) {
            "Monogamy and polygamy cannot both be dominant"
        }
    }

    val cultureTags: Set<String> get() = buildSet {
        add(race.tag)
        addAll(sexualFeatures.map { it.cultureTag })
        addAll(traits.map { it.cultureTag })
        add(weakness.cultureTag)
    }
}

private fun defaultTribes(count: Int): List<TribeSetup> {
    val names = listOf("Астарі", "Варки", "Нері", "Талари", "Солени", "Мерини")
    val races = listOf(
        TribeRace.HUMAN,
        TribeRace.FOUR_ARMED,
        TribeRace.TAILED,
        TribeRace.FURRED,
        TribeRace.LARGE_EYED,
        TribeRace.SCALED,
    )
    val sexual = listOf(
        setOf(SexualFeature.NUDITY, SexualFeature.FERTILITY),
        setOf(SexualFeature.DOMINANCE, SexualFeature.RITUAL),
        setOf(SexualFeature.MONOGAMY, SexualFeature.VOYEURISM),
        setOf(SexualFeature.GROUP, SexualFeature.PUBLIC),
        setOf(SexualFeature.STATUS_BONDS, SexualFeature.RITUAL),
        setOf(SexualFeature.POLYGAMY, SexualFeature.NUDITY),
    )
    val traits = listOf(
        setOf(TribeTrait.TRADERS, TribeTrait.BODY_CULT),
        setOf(TribeTrait.WARLIKE, TribeTrait.RAPID_MUTATION),
        setOf(TribeTrait.ISOLATIONIST, TribeTrait.RAPID_MUTATION),
        setOf(TribeTrait.MARITIME, TribeTrait.HYBRID_FRIENDLY),
        setOf(TribeTrait.TECHNOLOGICAL, TribeTrait.DYNASTIC),
        setOf(TribeTrait.NOMADIC, TribeTrait.HYBRID_FRIENDLY),
    )
    return (0 until count).map { index ->
        TribeSetup(
            name = names[index],
            race = races[index],
            sexualFeatures = sexual[index],
            traits = traits[index],
            weakness = TribeWeakness.entries[index % TribeWeakness.entries.size],
        )
    }
}

/** Applies the setup to each persistent simulation layer. */
object WorldSetupApplier {
    fun applyWorld(base: LivingPlanetState, setup: WorldSetup): LivingPlanetState {
        val ordered = base.civilizations.sortedBy(::civilizationOrdinal)
        val byId = ordered.mapIndexed { index, civ -> civ.id to setup.tribes[index] }.toMap()
        return base.copy(
            civilizations = base.civilizations.map { civ ->
                val tribe = byId[civ.id] ?: return@map civ
                val technologyBonus = if (TribeTrait.TECHNOLOGICAL in tribe.traits) 0.025 else 0.0
                val treasuryMultiplier = when {
                    TribeTrait.TRADERS in tribe.traits -> 1.25
                    tribe.weakness == TribeWeakness.POOR_ECONOMY -> 0.72
                    else -> 1.0
                }
                val stabilityDelta = when {
                    tribe.weakness == TribeWeakness.INTERNAL_SPLITS -> -0.10
                    TribeTrait.DYNASTIC in tribe.traits -> 0.05
                    else -> 0.0
                }
                civ.copy(
                    name = tribe.name.trim(),
                    stability = (civ.stability + stabilityDelta).coerceIn(0.15, 0.95),
                    technology = (civ.technology + technologyBonus).coerceIn(0.0, 1.0),
                    treasury = (civ.treasury * treasuryMultiplier).coerceAtLeast(0.0),
                    cultureTags = civ.cultureTags + tribe.cultureTags,
                )
            },
            recentEvents = base.recentEvents.map { event ->
                val civId = event.actorIds.firstOrNull { it in byId }
                val tribe = civId?.let(byId::get)
                if (tribe == null) event else event.copy(
                    facts = event.facts + ("civilization" to tribe.name.trim()),
                )
            },
        )
    }

    fun applyPeople(base: PeopleState, setup: WorldSetup): PeopleState {
        val orderedIds = base.socialProfiles.map { it.civilizationId }.sortedBy(::civilizationOrdinal)
        val tribeByCiv = orderedIds.mapIndexed { index, id -> id to setup.tribes[index] }.toMap()
        return base.copy(
            socialProfiles = base.socialProfiles.map { profile ->
                val tribe = tribeByCiv[profile.civilizationId] ?: return@map profile
                var privacy = profile.privacy
                var openness = profile.bodyOpenness
                var pairBonding = profile.pairBonding
                var jealousy = profile.jealousy
                var fertility = profile.fertilityNorm
                var piety = profile.piety
                var status = profile.statusHierarchy
                var tension = profile.socialTension

                if (SexualFeature.NUDITY in tribe.sexualFeatures) {
                    openness += 0.24; privacy -= 0.14
                }
                if (SexualFeature.PUBLIC in tribe.sexualFeatures) {
                    openness += 0.20; privacy -= 0.28
                }
                if (SexualFeature.RITUAL in tribe.sexualFeatures) piety += 0.18
                if (SexualFeature.FERTILITY in tribe.sexualFeatures) fertility += 0.28
                if (SexualFeature.DOMINANCE in tribe.sexualFeatures) status += 0.18
                if (SexualFeature.SUBMISSION in tribe.sexualFeatures) status += 0.10
                if (SexualFeature.GROUP in tribe.sexualFeatures || SexualFeature.POLYGAMY in tribe.sexualFeatures) {
                    pairBonding -= 0.30; jealousy -= 0.15
                }
                if (SexualFeature.MONOGAMY in tribe.sexualFeatures) {
                    pairBonding += 0.30; jealousy += 0.10
                }
                if (TribeTrait.BODY_CULT in tribe.traits) openness += 0.12
                if (TribeTrait.MATRIARCHAL in tribe.traits) status += 0.08
                if (tribe.weakness == TribeWeakness.LOW_FERTILITY) fertility -= 0.32
                if (tribe.weakness == TribeWeakness.INTERNAL_SPLITS) tension += 0.22
                if (tribe.weakness == TribeWeakness.XENOPHOBIC) {
                    privacy += 0.08; tension += 0.12
                }

                profile.copy(
                    privacy = privacy.coerceIn(0.0, 1.0),
                    bodyOpenness = openness.coerceIn(0.0, 1.0),
                    pairBonding = pairBonding.coerceIn(0.0, 1.0),
                    jealousy = jealousy.coerceIn(0.0, 1.0),
                    fertilityNorm = fertility.coerceIn(0.0, 1.0),
                    piety = piety.coerceIn(0.0, 1.0),
                    statusHierarchy = status.coerceIn(0.0, 1.0),
                    socialTension = tension.coerceIn(0.0, 1.0),
                    tags = profile.tags + tribe.cultureTags,
                )
            },
        )
    }

    fun applyEvolution(base: EvolutionState, world: LivingPlanetState, setup: WorldSetup): EvolutionState {
        val orderedCivs = world.civilizations.sortedBy(::civilizationOrdinal)
        val tribeByCiv = orderedCivs.mapIndexed { index, civ -> civ.id to setup.tribes[index] }.toMap()
        val settlementToTribe = world.settlements.associate { settlement ->
            settlement.id to tribeByCiv[settlement.civilizationId]
        }
        val lineageToTribe = base.populations.mapNotNull { population ->
            val tribe = settlementToTribe[population.settlementId] ?: return@mapNotNull null
            population.lineageId to tribe
        }.toMap()

        val lineages = base.lineages.map { lineage ->
            val tribe = lineageToTribe[lineage.id] ?: return@map lineage
            val race = raceProfile(tribe.race)
            lineage.copy(
                label = "${tribe.name}: ${tribe.race.displayNameUk}",
                rank = race.rank,
                morphology = race.morphology,
                bodyPlan = race.bodyPlan,
                divergenceFromOrigin = race.divergence,
                tags = lineage.tags + tribe.cultureTags + race.tags,
            )
        }
        val populations = base.populations.map { population ->
            val tribe = settlementToTribe[population.settlementId] ?: return@map population
            var isolation = population.isolation
            var mutation = population.mutationPressure
            if (TribeTrait.ISOLATIONIST in tribe.traits) isolation += 0.30
            if (TribeTrait.HYBRID_FRIENDLY in tribe.traits) isolation -= 0.24
            if (TribeTrait.RAPID_MUTATION in tribe.traits) mutation = maxOf(mutation, 0.72)
            if (tribe.weakness == TribeWeakness.GENETIC_BOTTLENECK) isolation += 0.16
            isolation = isolation.coerceIn(0.02, 0.98)
            population.copy(
                isolation = isolation,
                geneFlow = (1.0 - isolation).coerceIn(0.0, 1.0),
                mutationPressure = mutation.coerceIn(0.0, 1.0),
            )
        }
        return base.copy(lineages = lineages, populations = populations)
    }

    private data class RaceProfile(
        val morphology: MorphologyProfile,
        val bodyPlan: BodyPlan,
        val rank: BiologicalRank,
        val divergence: Double,
        val tags: Set<String>,
    )

    private fun raceProfile(race: TribeRace): RaceProfile = when (race) {
        TribeRace.HUMAN -> RaceProfile(
            MorphologyProfile.HUMAN_BASELINE, BodyPlan(), BiologicalRank.POPULATION, 0.02,
            setOf("player_race", "human_derived"),
        )
        TribeRace.TALL_SLENDER -> RaceProfile(
            MorphologyProfile(heightScale = 1.20, massScale = 0.84, limbScale = 1.16, eyeSize = 0.56),
            BodyPlan(), BiologicalRank.MORPH, 0.18, setOf("player_race", "tall", "slender"),
        )
        TribeRace.ROBUST -> RaceProfile(
            MorphologyProfile(heightScale = 0.96, massScale = 1.28, limbScale = 0.91, boneDensity = 1.24),
            BodyPlan(), BiologicalRank.MORPH, 0.20, setOf("player_race", "robust"),
        )
        TribeRace.FURRED -> RaceProfile(
            MorphologyProfile(hairCoverage = 0.92, coldAdaptation = 0.76),
            BodyPlan(covering = SkinCovering.FINE_FUR), BiologicalRank.SUBSPECIES, 0.30,
            setOf("player_race", "furred"),
        )
        TribeRace.TAILED -> RaceProfile(
            MorphologyProfile(limbScale = 1.06, boneDensity = 0.96),
            BodyPlan(hasTail = true), BiologicalRank.SUBSPECIES, 0.34,
            setOf("player_race", "tailed"),
        )
        TribeRace.LARGE_EYED -> RaceProfile(
            MorphologyProfile(eyeSize = 0.90, cranialScale = 1.08, pigmentation = 0.42),
            BodyPlan(), BiologicalRank.MORPH, 0.22, setOf("player_race", "large_eyes"),
        )
        TribeRace.FOUR_ARMED -> RaceProfile(
            MorphologyProfile(shoulderHipRatio = 1.18, massScale = 1.08, boneDensity = 1.12),
            BodyPlan(armPairs = 2), BiologicalRank.SPECIES, 0.52,
            setOf("player_race", "four_armed", "structural_divergence"),
        )
        TribeRace.SCALED -> RaceProfile(
            MorphologyProfile(hairCoverage = 0.04, heatAdaptation = 0.76, pigmentation = 0.38),
            BodyPlan(covering = SkinCovering.SCALES, posture = Posture.SEMI_UPRIGHT),
            BiologicalRank.SPECIES, 0.46, setOf("player_race", "scaled", "structural_divergence"),
        )
    }
}

private fun civilizationOrdinal(idOrCiv: Any): Int {
    val id = when (idOrCiv) {
        is String -> idOrCiv
        is com.sendmefile77.chronosphere.civilization.Civilization -> idOrCiv.id
        else -> idOrCiv.toString()
    }
    return id.substringAfterLast('-').toIntOrNull() ?: Int.MAX_VALUE
}
