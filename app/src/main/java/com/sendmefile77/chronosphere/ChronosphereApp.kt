package com.sendmefile77.chronosphere

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.map.SettlementMarker
import com.sendmefile77.chronosphere.map.WorldMapView
import com.sendmefile77.chronosphere.simulation.SimulationClock
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.storage.GameSnapshotV1
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator
import com.sendmefile77.chronosphere.worldgen.ResourceDeposit
import com.sendmefile77.chronosphere.worldgen.TileCoord
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldHydrology
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator

private const val SAVE_FILE = "chronosphere-save-v1.txt"

data class GameSession(
    val world: WorldMap,
    val resources: List<ResourceDeposit>,
    val rivers: Set<TileCoord>,
    val state: LivingPlanetState,
)

@androidx.compose.runtime.Composable
fun ChronosphereApp() {
    val context = LocalContext.current
    val generator = remember { WorldGenerator() }
    val hydrology = remember { WorldHydrology() }
    val resourceGenerator = remember { WorldResourceGenerator() }
    val clock = remember { SimulationClock() }
    val textGenerator = remember { ChronicleTextGenerator() }
    var seedText by remember { mutableStateOf("424242") }
    var session by remember { mutableStateOf(newSession(424242L, generator, hydrology, resourceGenerator)) }
    var saveStatus by remember { mutableStateOf("Local save ready") }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Chronosphere", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { seedText = it.filter { c -> c == '-' || c.isDigit() } },
                        label = { Text("World seed") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        val seed = seedText.toLongOrNull() ?: return@Button
                        session = newSession(seed, generator, hydrology, resourceGenerator)
                        saveStatus = "New world created"
                    }) { Text("New world") }
                }

                val time = clock.at(session.state.tick)
                Text("Year ${time.year}, month ${time.month} · population ${session.state.totalPopulation} · settlements ${session.state.settlements.size}")
                Text("Map ${session.world.fingerprint} · land ${session.world.landPercent}% · rivers ${session.rivers.size} · resources ${session.resources.size}", style = MaterialTheme.typography.bodySmall)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { session = advance(session, 12) }) { Text("+1 year") }
                    Button(onClick = { session = advance(session, 120) }) { Text("+10 years") }
                    Button(onClick = { session = advance(session, 1200) }) { Text("+100 years") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        saveStatus = runCatching {
                            context.openFileOutput(SAVE_FILE, Context.MODE_PRIVATE).bufferedWriter().use {
                                it.write(GameSnapshotV1.encode(session.state))
                            }
                            "Saved locally"
                        }.getOrElse { "Save failed: ${it.message ?: "unknown error"}" }
                    }) { Text("Save") }
                    Button(onClick = {
                        saveStatus = runCatching {
                            val loaded = context.openFileInput(SAVE_FILE).bufferedReader().use {
                                GameSnapshotV1.decode(it.readText())
                            }
                            session = sessionFromState(loaded, generator, hydrology, resourceGenerator)
                            seedText = loaded.worldSeed.toString()
                            "Loaded local save"
                        }.getOrElse { "Load failed: ${it.message ?: "no save"}" }
                    }) { Text("Load") }
                    Text(saveStatus, style = MaterialTheme.typography.bodySmall)
                }

                val civOrder = session.state.civilizations.mapIndexed { index, civ -> civ.id to index }.toMap()
                WorldMapView(
                    world = session.world,
                    rivers = session.rivers,
                    settlements = session.state.settlements.map {
                        SettlementMarker(it.x, it.y, it.population, civOrder[it.civilizationId] ?: 0)
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 132.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Recent chronicle", style = MaterialTheme.typography.titleSmall)
                    session.state.recentEvents.takeLast(4).reversed().forEach { event ->
                        val eventTime = clock.at(event.tick)
                        Text("${eventTime.year}: ${textGenerator.describe(event)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun newSession(
    seed: Long,
    generator: WorldGenerator,
    hydrology: WorldHydrology,
    resourceGenerator: WorldResourceGenerator,
): GameSession {
    val world = generator.generate(WorldSeed(seed))
    val resources = resourceGenerator.generate(world)
    val rivers = hydrology.generateRivers(world)
    val state = CivilizationEngine(world, resources).initialize()
    return GameSession(world, resources, rivers, state)
}

private fun sessionFromState(
    state: LivingPlanetState,
    generator: WorldGenerator,
    hydrology: WorldHydrology,
    resourceGenerator: WorldResourceGenerator,
): GameSession {
    val world = generator.generate(WorldSeed(state.worldSeed))
    val resources = resourceGenerator.generate(world)
    val rivers = hydrology.generateRivers(world)
    return GameSession(world, resources, rivers, state)
}

private fun advance(session: GameSession, months: Int): GameSession {
    val nextState = CivilizationEngine(session.world, session.resources).advance(session.state, months)
    return session.copy(state = nextState)
}
