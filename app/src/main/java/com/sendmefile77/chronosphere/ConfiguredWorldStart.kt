package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldHydrology
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import com.sendmefile77.chronosphere.worldgen.WorldTile
import kotlin.math.abs

internal data class ConfiguredWorldStart(
    val setup: WorldSetup,
    val session: GameSession,
    val people: PeopleState,
    val economy: EconomyState,
    val evolution: EvolutionState,
)

internal fun createConfiguredWorldStart(
    setup: WorldSetup,
    generator: WorldGenerator,
    hydrology: WorldHydrology,
    resourceGenerator: WorldResourceGenerator,
    peopleEngine: PeopleEngine,
): ConfiguredWorldStart {
    val map = generator.generate(WorldSeed(setup.seed))
    val resources = resourceGenerator.generate(map)
    val rawState = CivilizationEngine(map, resources).initialize(setup.tribes.size)
    require(rawState.civilizations.size == setup.tribes.size) {
        "Світ не зміг розмістити ${setup.tribes.size} стартових племен"
    }

    val positioned = rawState.copy(
        settlements = repositionStartingSettlements(
            settlements = rawState.settlements,
            map = map,
            spacing = setup.startSpacing,
            seed = setup.seed,
        ),
    )
    val configuredWorld = WorldSetupApplier.applyWorld(positioned, setup)
    val session = GameSession(
        world = map,
        resources = resources,
        rivers = hydrology.generateRivers(map),
        state = configuredWorld,
    )
    val people = WorldSetupApplier.applyPeople(peopleEngine.initialize(configuredWorld), setup)
    val economy = EconomyEngine(map, resources).initialize(configuredWorld)
    val configuredEvolution = WorldSetupApplier.applyEvolution(
        EvolutionEngine(map).initialize(configuredWorld),
        configuredWorld,
        setup,
    )
    val evolution = makeDistinctConfiguredRacesIndependent(configuredEvolution, configuredWorld, setup)
    return ConfiguredWorldStart(setup, session, people, economy, evolution)
}

private fun makeDistinctConfiguredRacesIndependent(
    state: EvolutionState,
    world: com.sendmefile77.chronosphere.civilization.LivingPlanetState,
    setup: WorldSetup,
): EvolutionState {
    val civs = world.civilizations.sortedBy { civilizationOrdinal(it.id) }
    val tribeByCiv = civs.mapIndexed { index, civ -> civ.id to setup.tribes[index] }.toMap()
    val independentLineages = state.populations.mapNotNull { population ->
        val settlement = world.settlements.firstOrNull { it.id == population.settlementId } ?: return@mapNotNull null
        val tribe = tribeByCiv[settlement.civilizationId] ?: return@mapNotNull null
        if (tribe.race !in INDEPENDENT_START_RACES) return@mapNotNull null
        population.lineageId
    }.toSet()
    if (independentLineages.isEmpty()) return state
    return state.copy(
        lineages = state.lineages.map { lineage ->
            if (lineage.id !in independentLineages) lineage else lineage.copy(
                parentLineageId = null,
                generation = 0,
                tags = (lineage.tags - "human_derived") + "independent_origin",
            )
        },
    )
}

private val INDEPENDENT_START_RACES = setOf(
    TribeRace.FURRED,
    TribeRace.TAILED,
    TribeRace.FOUR_ARMED,
    TribeRace.SCALED,
)

private fun repositionStartingSettlements(
    settlements: List<Settlement>,
    map: WorldMap,
    spacing: StartSpacing,
    seed: Long,
): List<Settlement> {
    if (settlements.size <= 1) return settlements
    val candidates = map.tiles.asSequence()
        .filter(::isStartHabitable)
        .sortedWith(
            compareByDescending<WorldTile> { startQuality(it) }
                .thenBy { stableTileOrder(seed, it.x, it.y) },
        )
        .toList()
    if (candidates.isEmpty()) return settlements

    val selected = ArrayList<WorldTile>(settlements.size)
    val anchor = candidates.first()
    selected += anchor

    while (selected.size < settlements.size) {
        val threshold = spacing.minimumDistance
        val viable = candidates.asSequence()
            .filter { candidate -> candidate !in selected }
            .filter { candidate -> selected.all { distance(candidate, it) >= threshold } }
            .toList()
        val next = when (spacing) {
            StartSpacing.CLOSE -> viable.minWithOrNull(
                compareBy<WorldTile> { distance(it, anchor) }
                    .thenByDescending { startQuality(it) }
                    .thenBy { stableTileOrder(seed, it.x, it.y) },
            )
            StartSpacing.NORMAL, StartSpacing.FAR -> viable.firstOrNull()
        }
        if (next != null) {
            selected += next
            continue
        }

        // Extremely fragmented maps may not satisfy the requested distance. Relax deterministically
        // instead of silently creating fewer tribes than the player selected.
        val fallback = candidates.asSequence()
            .filter { it !in selected }
            .maxWithOrNull(
                compareBy<WorldTile> { candidate -> selected.minOf { distance(candidate, it) } }
                    .thenBy { startQuality(it) },
            ) ?: break
        selected += fallback
    }

    if (selected.size < settlements.size) return settlements
    val orderedSettlements = settlements.sortedBy { civilizationOrdinal(it.civilizationId) }
    val placementById = orderedSettlements.mapIndexed { index, settlement -> settlement.id to selected[index] }.toMap()
    return settlements.map { settlement ->
        placementById[settlement.id]?.let { tile -> settlement.copy(x = tile.x, y = tile.y) } ?: settlement
    }
}

private fun isStartHabitable(tile: WorldTile): Boolean = tile.biome !in setOf(
    Biome.DEEP_OCEAN,
    Biome.OCEAN,
    Biome.ICE,
)

private fun startQuality(tile: WorldTile): Double {
    val moistureComfort = 1.0 - abs(tile.moisture - 0.58)
    val temperatureComfort = 1.0 - abs(tile.temperature - 0.56)
    val biomeBonus = when (tile.biome) {
        Biome.COAST -> 0.24
        Biome.GRASSLAND -> 0.22
        Biome.FOREST -> 0.18
        Biome.STEPPE -> 0.12
        Biome.RAINFOREST -> 0.09
        Biome.TAIGA -> 0.04
        Biome.DESERT, Biome.TUNDRA, Biome.MOUNTAIN -> -0.08
        else -> -1.0
    }
    return moistureComfort * 0.38 + temperatureComfort * 0.34 + tile.elevation * 0.04 + biomeBonus
}

private fun distance(a: WorldTile, b: WorldTile): Int = abs(a.x - b.x) + abs(a.y - b.y)

private fun stableTileOrder(seed: Long, x: Int, y: Int): Long {
    var value = seed xor (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
    value = (value xor (value ushr 30)) * -4658895280553007687L
    value = (value xor (value ushr 27)) * -7723592293110705685L
    return value xor (value ushr 31)
}

private fun civilizationOrdinal(id: String): Int = id.substringAfterLast('-').toIntOrNull() ?: Int.MAX_VALUE
