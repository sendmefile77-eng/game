package com.sendmefile77.chronosphere.economy

import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.DiplomaticRelation
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.Settlement
import com.sendmefile77.chronosphere.civilization.WarState
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.ResourceDeposit
import com.sendmefile77.chronosphere.worldgen.ResourceKind
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import com.sendmefile77.chronosphere.worldgen.WorldTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyEngineTest {
    @Test
    fun advanceIsDeterministicAndBounded() {
        val map = WorldGenerator().generate(WorldSeed(424242L), 48, 32)
        val resources = WorldResourceGenerator().generate(map)
        val civilizationEngine = CivilizationEngine(map, resources)
        val initialWorld = civilizationEngine.initialize(4)
        val engine = EconomyEngine(map, resources)
        val initialEconomy = engine.initialize(initialWorld)
        val futureWorld = civilizationEngine.advance(initialWorld, 120)

        val first = engine.advance(initialEconomy, futureWorld)
        val second = engine.advance(initialEconomy, futureWorld)
        assertEquals(first, second)
        first.state.civilizations.forEach { economy ->
            assertTrue(economy.shortageIndex.isFinite())
            assertTrue(economy.shortageIndex in 0.0..1.0)
            assertTrue(economy.stockpiles.values.all { it.isFinite() && it >= 0.0 })
            assertTrue(economy.production.values.all { it.isFinite() && it >= 0.0 })
        }
    }

    @Test
    fun warBlocksTradeBetweenBelligerents() {
        val map = flatMap()
        val resources = exporterResources()
        val engine = EconomyEngine(map, resources)
        val peaceful = twoStateWorld(tick = 0L)
        val initial = engine.initialize(peaceful)
        val peacefulFuture = twoStateWorld(tick = 12L)
        val peaceResult = engine.advance(initial, peacefulFuture)
        assertTrue(peaceResult.state.routes.isNotEmpty())

        val wartimeFuture = peacefulFuture.copy(
            wars = listOf(WarState("war-a-b", "a", "b", 1L)),
        )
        val warResult = engine.advance(initial, wartimeFuture)
        assertTrue(warResult.state.routes.none {
            it.exporterId == "a" && it.importerId == "b" || it.exporterId == "b" && it.importerId == "a"
        })
    }

    @Test
    fun advancedCivilizationResolvesToAdvancedEra() {
        val map = flatMap()
        val resources = exporterResources()
        val base = twoStateWorld(tick = 0L)
        val advanced = base.copy(
            civilizations = base.civilizations.map { civilization ->
                if (civilization.id == "a") civilization.copy(population = 300_000L, technology = 0.78)
                else civilization
            },
            settlements = base.settlements.map { settlement ->
                if (settlement.civilizationId == "a") settlement.copy(population = 300_000L) else settlement
            },
        )
        val economy = EconomyEngine(map, resources).initialize(advanced).economy("a")!!
        assertTrue(economy.era.ordinal >= TechnologyEra.ELECTRIC.ordinal)
    }

    private fun flatMap(): WorldMap {
        val width = 16
        val height = 16
        val tiles = buildList {
            for (y in 0 until height) for (x in 0 until width) {
                add(WorldTile(x, y, 0.55, 0.55, 0.62, Biome.GRASSLAND))
            }
        }
        return WorldMap(WorldSeed(99L), width, height, tiles)
    }

    private fun exporterResources(): List<ResourceDeposit> = buildList {
        ResourceKind.entries.forEachIndexed { index, kind ->
            repeat(4) { offset ->
                add(ResourceDeposit(3 + (index + offset) % 3, 3 + offset % 2, kind, 1.0))
            }
        }
    }

    private fun twoStateWorld(tick: Long): LivingPlanetState {
        val a = Civilization("a", "А", 3_000L, 0.75, 0.18, 600.0)
        val b = Civilization("b", "Б", 3_000L, 0.75, 0.06, 600.0)
        return LivingPlanetState(
            worldSeed = 99L,
            tick = tick,
            civilizations = listOf(a, b),
            settlements = listOf(
                Settlement("a-city", "Астра", "a", 4, 4, 3_000L, 2_000.0, 100.0, 0L),
                Settlement("b-city", "Брен", "b", 14, 4, 3_000L, 2_000.0, 100.0, 0L),
            ),
            relations = listOf(DiplomaticRelation("a", "b", 0.8, tick)),
        )
    }
}
