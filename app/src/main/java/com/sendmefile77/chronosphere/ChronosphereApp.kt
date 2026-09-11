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
import com.sendmefile77.chronosphere.civilization.TerritoryResolver
import com.sendmefile77.chronosphere.history.HistoryComparator
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.history.InterventionCommand
import com.sendmefile77.chronosphere.history.InterventionEngine
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.map.SettlementMarker
import com.sendmefile77.chronosphere.map.WorldMapView
import com.sendmefile77.chronosphere.simulation.SimulationClock
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.storage.GameSnapshotV1
import com.sendmefile77.chronosphere.storage.HistoryWorkspaceSnapshotV1
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator
import com.sendmefile77.chronosphere.worldgen.ResourceDeposit
import com.sendmefile77.chronosphere.worldgen.TileCoord
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldHydrology
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator

private const val SAVE_FILE = "chronosphere-save-v1.txt"
private const val HISTORY_FILE = "chronosphere-history-v1.txt"

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
    val territoryResolver = remember { TerritoryResolver() }
    val clock = remember { SimulationClock() }
    val textGenerator = remember { ChronicleTextGenerator() }
    val historyTimeline = remember { HistoryTimeline() }
    val interventionEngine = remember { InterventionEngine() }
    val initialSession = remember { newSession(424242L, generator, hydrology, resourceGenerator) }

    var seedText by remember { mutableStateOf("424242") }
    var session by remember { mutableStateOf(initialSession) }
    var workspace by remember { mutableStateOf(historyTimeline.create(initialSession.state)) }
    var selectedCivilizationId by remember { mutableStateOf(initialSession.state.civilizations.first().id) }
    var saveStatus by remember { mutableStateOf("Локальне збереження готове") }
    var interventionSequence by remember { mutableStateOf(0L) }

    fun syncState(nextState: LivingPlanetState) {
        session = session.copy(state = nextState)
        workspace = historyTimeline.syncActive(workspace, nextState)
    }

    fun advanceMonths(months: Int) {
        val next = CivilizationEngine(session.world, session.resources).advance(session.state, months)
        syncState(next)
    }

    fun intervene(kind: InterventionKind) {
        val targetId = session.state.civilizations.firstOrNull { it.id == selectedCivilizationId }?.id
            ?: session.state.civilizations.first().id.also { selectedCivilizationId = it }
        interventionSequence += 1L
        val command = InterventionCommand(
            id = "player-${kind.name.lowercase()}-${session.state.tick}-$interventionSequence",
            kind = kind,
            civilizationId = targetId,
            strength = 0.65,
        )
        syncState(interventionEngine.apply(session.state, command))
        saveStatus = "Втручання застосовано"
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("Хроносфера", style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { seedText = it.filter { c -> c == '-' || c.isDigit() } },
                        label = { Text("Seed світу") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        val seed = seedText.toLongOrNull() ?: return@Button
                        val created = newSession(seed, generator, hydrology, resourceGenerator)
                        session = created
                        workspace = historyTimeline.create(created.state)
                        selectedCivilizationId = created.state.civilizations.first().id
                        interventionSequence = 0L
                        saveStatus = "Створено новий світ"
                    }) { Text("Новий світ") }
                }

                val time = clock.at(session.state.tick)
                Text("Рік ${time.year}, місяць ${time.month} · населення ${session.state.totalPopulation} · міст ${session.state.settlements.size}")
                Text(
                    "Війн ${session.state.wars.size} · союзів ${session.state.alliances.size} · гілка: ${workspace.activeBranch.name}",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { advanceMonths(12) }) { Text("+1 рік") }
                    Button(onClick = { advanceMonths(120) }) { Text("+10 років") }
                    Button(onClick = { advanceMonths(1200) }) { Text("+100 років") }
                }

                val selectedCivilization = session.state.civilizations.firstOrNull { it.id == selectedCivilizationId }
                    ?: session.state.civilizations.first()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val civilizations = session.state.civilizations
                        val index = civilizations.indexOfFirst { it.id == selectedCivilization.id }.coerceAtLeast(0)
                        selectedCivilizationId = civilizations[(index + 1) % civilizations.size].id
                    }) { Text("Ціль: ${selectedCivilization.name}") }
                    Text(
                        "техн. ${String.format("%.2f", selectedCivilization.technology)} · стаб. ${String.format("%.2f", selectedCivilization.stability)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { intervene(InterventionKind.HARVEST_AID) }) { Text("Допомога") }
                    Button(onClick = { intervene(InterventionKind.DROUGHT) }) { Text("Посуха") }
                    Button(onClick = { intervene(InterventionKind.TECHNOLOGY_BOOST) }) { Text("Технології") }
                    Button(onClick = { intervene(InterventionKind.STABILITY_SUPPORT) }) { Text("Стабільність") }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        workspace = historyTimeline.checkpoint(
                            historyTimeline.syncActive(workspace, session.state),
                            "Рік ${time.year}",
                        )
                        saveStatus = "Створено контрольну точку"
                    }) { Text("Точка") }
                    Button(onClick = {
                        val forked = historyTimeline.fork(
                            historyTimeline.syncActive(workspace, session.state),
                            "Альтернатива ${workspace.branches.size}",
                        )
                        workspace = forked
                        session = session.copy(state = forked.activeState)
                        saveStatus = "Створено альтернативну історію"
                    }) { Text("Відгалуження") }
                    Button(onClick = {
                        val before = workspace
                        val hasCheckpoint = before.checkpoints.any { it.branchId == before.activeBranchId }
                        val restored = historyTimeline.restoreLatestCheckpoint(before)
                        workspace = restored
                        session = session.copy(state = restored.activeState)
                        saveStatus = if (hasCheckpoint) "Контрольну точку відновлено" else "У цій гілці немає контрольної точки"
                    }) { Text("Відновити") }
                    if (workspace.branches.size > 1) {
                        Button(onClick = {
                            val currentIndex = workspace.branches.indexOfFirst { it.id == workspace.activeBranchId }.coerceAtLeast(0)
                            val nextBranch = workspace.branches[(currentIndex + 1) % workspace.branches.size]
                            val switched = historyTimeline.switchTo(
                                historyTimeline.syncActive(workspace, session.state),
                                nextBranch.id,
                            )
                            workspace = switched
                            session = session.copy(state = switched.activeState)
                            selectedCivilizationId = switched.activeState.civilizations.firstOrNull { it.id == selectedCivilizationId }?.id
                                ?: switched.activeState.civilizations.first().id
                            saveStatus = "Активна гілка: ${switched.activeBranch.name}"
                        }) { Text("Гілка") }
                    }
                }

                val originalState = workspace.branches.firstOrNull { it.id == HistoryTimeline.ROOT_BRANCH_ID }?.state
                    ?: workspace.activeState
                val divergence = HistoryComparator.compare(originalState, session.state)
                Text(
                    "Гілок ${workspace.branches.size} · точок ${workspace.checkpoints.count { it.branchId == workspace.activeBranchId }} · Δнас. ${signed(divergence.populationDelta)} · Δміст ${signed(divergence.settlementDelta)} · Δтехн. ${String.format("%+.3f", divergence.averageTechnologyDelta)}",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        saveStatus = runCatching {
                            val syncedWorkspace = historyTimeline.syncActive(workspace, session.state)
                            context.openFileOutput(HISTORY_FILE, Context.MODE_PRIVATE).bufferedWriter().use {
                                it.write(HistoryWorkspaceSnapshotV1.encode(syncedWorkspace))
                            }
                            context.openFileOutput(SAVE_FILE, Context.MODE_PRIVATE).bufferedWriter().use {
                                it.write(GameSnapshotV1.encode(session.state))
                            }
                            workspace = syncedWorkspace
                            "Світ і всі гілки збережено"
                        }.getOrElse { "Помилка збереження: ${it.message ?: "невідома"}" }
                    }) { Text("Зберегти") }
                    Button(onClick = {
                        saveStatus = runCatching {
                            val loadedWorkspace = runCatching {
                                context.openFileInput(HISTORY_FILE).bufferedReader().use {
                                    HistoryWorkspaceSnapshotV1.decode(it.readText())
                                }
                            }.getOrNull()
                            val loadedState = loadedWorkspace?.activeState ?: context.openFileInput(SAVE_FILE).bufferedReader().use {
                                GameSnapshotV1.decode(it.readText())
                            }
                            val loadedSession = sessionFromState(loadedState, generator, hydrology, resourceGenerator)
                            session = loadedSession
                            workspace = if (loadedWorkspace != null) {
                                historyTimeline.syncActive(loadedWorkspace, loadedSession.state)
                            } else {
                                historyTimeline.create(loadedSession.state)
                            }
                            selectedCivilizationId = loadedSession.state.civilizations.first().id
                            seedText = loadedState.worldSeed.toString()
                            interventionSequence = loadedState.recentEvents.asSequence()
                                .map { it.id }
                                .filter { it.startsWith("player-") }
                                .mapNotNull { it.substringAfterLast('-').toLongOrNull() }
                                .maxOrNull() ?: 0L
                            if (loadedWorkspace != null) "Світ і гілки завантажено" else "Завантажено старе збереження"
                        }.getOrElse { "Помилка завантаження: ${it.message ?: "немає збереження"}" }
                    }) { Text("Завантажити") }
                }
                Text(saveStatus, style = MaterialTheme.typography.bodySmall)

                val civOrder = session.state.civilizations.mapIndexed { index, civ -> civ.id to index }.toMap()
                val territory = remember(session) { territoryResolver.resolve(session.world, session.state) }
                WorldMapView(
                    world = session.world,
                    rivers = session.rivers,
                    territoryOwners = territory,
                    settlements = session.state.settlements.map {
                        SettlementMarker(it.x, it.y, it.population, civOrder[it.civilizationId] ?: 0)
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                val leaders = session.state.civilizations.sortedByDescending { it.population }.take(3)
                Text(
                    "Провідні держави: " + leaders.joinToString(" · ") { "${it.name} ${it.population}" },
                    style = MaterialTheme.typography.bodySmall,
                )
                val names = session.state.civilizations.associate { it.id to it.name }
                if (session.state.wars.isNotEmpty()) {
                    Text(
                        "Активні війни: " + session.state.wars.take(2).joinToString(" · ") {
                            val score = String.format("%.1f:%.1f", it.scoreA, it.scoreB)
                            "${names[it.civilizationA] ?: it.civilizationA}–${names[it.civilizationB] ?: it.civilizationB} [$score]"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 108.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Останні події", style = MaterialTheme.typography.titleSmall)
                    session.state.recentEvents.takeLast(3).reversed().forEach { event ->
                        val eventTime = clock.at(event.tick)
                        Text("${eventTime.year}: ${textGenerator.describe(event)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun signed(value: Long): String = if (value >= 0) "+$value" else value.toString()
private fun signed(value: Int): String = if (value >= 0) "+$value" else value.toString()

private fun newSession(
    seed: Long,
    generator: WorldGenerator,
    hydrology: WorldHydrology,
    resourceGenerator: WorldResourceGenerator,
): GameSession {
    val world = generator.generate(WorldSeed(seed))
    val resources = resourceGenerator.generate(world)
    return GameSession(
        world,
        resources,
        hydrology.generateRivers(world),
        CivilizationEngine(world, resources).initialize(),
    )
}

private fun sessionFromState(
    state: LivingPlanetState,
    generator: WorldGenerator,
    hydrology: WorldHydrology,
    resourceGenerator: WorldResourceGenerator,
): GameSession {
    val world = generator.generate(WorldSeed(state.worldSeed))
    val resources = resourceGenerator.generate(world)
    val normalizedState = CivilizationEngine(world, resources).prepareState(state)
    return GameSession(world, resources, hydrology.generateRivers(world), normalizedState)
}
