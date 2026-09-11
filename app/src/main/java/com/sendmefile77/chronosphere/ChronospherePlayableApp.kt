package com.sendmefile77.chronosphere

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.TerritoryResolver
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.history.HistoryComparator
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.history.InterventionCommand
import com.sendmefile77.chronosphere.history.InterventionEngine
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.map.SettlementMarker
import com.sendmefile77.chronosphere.map.WorldMapView
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationClock
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.storage.GameSnapshotV1
import com.sendmefile77.chronosphere.storage.HistoryWorkspaceSnapshotV1
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldHydrology
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PLAYABLE_SAVE_FILE = "chronosphere-save-v1.txt"
private const val PLAYABLE_HISTORY_FILE = "chronosphere-history-v1.txt"

private enum class PlayablePanel {
    STATE,
    PERSON,
    HISTORY,
    CHRONICLE,
}

@Composable
fun ChronospherePlayableApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val generator = remember { WorldGenerator() }
    val hydrology = remember { WorldHydrology() }
    val resourceGenerator = remember { WorldResourceGenerator() }
    val territoryResolver = remember { TerritoryResolver() }
    val clock = remember { SimulationClock() }
    val textGenerator = remember { ChronicleTextGenerator() }
    val historyTimeline = remember { HistoryTimeline() }
    val interventionEngine = remember { InterventionEngine() }
    val peopleEngine = remember { PeopleEngine() }
    val adultModule = remember { AdultModuleRuntime.load() }
    val adultModuleActive = remember(adultModule) { AdultModuleRuntime.isActive(adultModule) }
    val adultSceneRuntime = remember { AdultSceneRuntime.load() }

    val initialSession = remember { newPlayableSession(424242L, generator, hydrology, resourceGenerator) }
    val initialPeople = remember(initialSession) { peopleEngine.initialize(initialSession.state) }
    val initialEconomy = remember(initialSession) {
        EconomyEngine(initialSession.world, initialSession.resources).initialize(initialSession.state)
    }
    val initialEvolution = remember(initialSession) {
        EvolutionEngine(initialSession.world).initialize(initialSession.state)
    }

    var seedText by remember { mutableStateOf("424242") }
    var session by remember { mutableStateOf(initialSession) }
    var peopleState by remember { mutableStateOf(initialPeople) }
    var economyState by remember { mutableStateOf(initialEconomy) }
    var evolutionState by remember { mutableStateOf(initialEvolution) }
    var workspace by remember {
        mutableStateOf(historyTimeline.create(initialSession.state, initialPeople, initialEconomy, initialEvolution))
    }
    var selectedCivilizationId by remember { mutableStateOf(initialSession.state.civilizations.first().id) }
    var selectedPersonId by remember {
        val civId = initialSession.state.civilizations.first().id
        mutableStateOf(initialPeople.ruler(civId)?.id ?: initialPeople.livingPeople(civId).firstOrNull()?.id)
    }
    var characterUndressed by remember { mutableStateOf(false) }
    var interventionSequence by remember { mutableStateOf(0L) }
    var selectedPanel by remember { mutableStateOf(PlayablePanel.STATE) }
    var isAdvancing by remember { mutableStateOf(false) }
    var saveStatus by remember {
        mutableStateOf(
            if (adultModuleActive) {
                "Локальний режим · дорослий модуль активний"
            } else {
                "Локальний базовий режим"
            },
        )
    }

    fun resetCharacterSelection(civilizationId: String, people: PeopleState) {
        selectedPersonId = people.ruler(civilizationId)?.id
            ?: people.livingPeople(civilizationId).maxByOrNull { it.prestige }?.id
        characterUndressed = false
    }

    fun syncState(
        nextState: LivingPlanetState,
        nextPeople: PeopleState = peopleState,
        nextEconomy: EconomyState = economyState,
        nextEvolution: EvolutionState = evolutionState,
    ) {
        session = session.copy(state = nextState)
        peopleState = nextPeople
        economyState = nextEconomy
        evolutionState = nextEvolution
        workspace = historyTimeline.syncActive(workspace, nextState, nextPeople, nextEconomy, nextEvolution)
    }

    fun selectCivilization(civilizationId: String) {
        if (isAdvancing || session.state.civilizations.none { it.id == civilizationId }) return
        selectedCivilizationId = civilizationId
        resetCharacterSelection(civilizationId, peopleState)
    }

    fun newWorld() {
        if (isAdvancing) return
        val seed = seedText.toLongOrNull() ?: run {
            saveStatus = "Некоректний seed"
            return
        }
        val created = newPlayableSession(seed, generator, hydrology, resourceGenerator)
        val createdPeople = peopleEngine.initialize(created.state)
        val createdEconomy = EconomyEngine(created.world, created.resources).initialize(created.state)
        val createdEvolution = EvolutionEngine(created.world).initialize(created.state)
        session = created
        peopleState = createdPeople
        economyState = createdEconomy
        evolutionState = createdEvolution
        workspace = historyTimeline.create(created.state, createdPeople, createdEconomy, createdEvolution)
        selectedCivilizationId = created.state.civilizations.first().id
        resetCharacterSelection(selectedCivilizationId, createdPeople)
        interventionSequence = 0L
        characterUndressed = false
        saveStatus = "Створено новий світ · seed $seed"
    }

    fun advanceMonths(months: Int) {
        if (isAdvancing) return
        require(months > 0)

        val sourceSession = session
        val sourcePeople = peopleState
        val sourceEconomy = economyState
        val sourceEvolution = evolutionState
        val targetCivilizationId = selectedCivilizationId
        val targetPersonId = selectedPersonId
        val runner = PlayableSimulationRunner(
            worldMap = sourceSession.world,
            resources = sourceSession.resources,
            peopleEngine = peopleEngine,
            adultModule = adultModule,
        )

        isAdvancing = true
        saveStatus = "Моделювання часу…"
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    runner.advance(
                        currentWorld = sourceSession.state,
                        currentPeople = sourcePeople,
                        currentEconomy = sourceEconomy,
                        currentEvolution = sourceEvolution,
                        months = months,
                    )
                }
                syncState(result.world, result.people, result.economy, result.evolution)

                val validCivilizationId = if (result.world.civilizations.any { it.id == targetCivilizationId }) {
                    targetCivilizationId
                } else {
                    result.world.civilizations.first().id
                }
                selectedCivilizationId = validCivilizationId
                val personStillAlive = result.people.livingPeople(validCivilizationId).any { it.id == targetPersonId }
                if (!personStillAlive) {
                    resetCharacterSelection(validCivilizationId, result.people)
                }
                val advancedTime = clock.at(result.world.tick)
                saveStatus = "Час промотано до ${advancedTime.year} року"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                saveStatus = "Помилка моделювання: ${error.message ?: "невідома"}"
            } finally {
                isAdvancing = false
            }
        }
    }

    fun intervene(kind: InterventionKind) {
        if (isAdvancing) return
        val targetId = session.state.civilizations.firstOrNull { it.id == selectedCivilizationId }?.id
            ?: session.state.civilizations.first().id.also { selectedCivilizationId = it }
        interventionSequence += 1L
        val command = InterventionCommand(
            id = "player-${kind.name.lowercase()}-${session.state.tick}-$interventionSequence",
            kind = kind,
            civilizationId = targetId,
            strength = 0.65,
        )
        syncState(interventionEngine.apply(session.state, command), peopleState, economyState, evolutionState)
        val targetName = session.state.civilizations.firstOrNull { it.id == targetId }?.name ?: targetId
        saveStatus = "Втручання застосовано до $targetName"
    }

    fun activateWorkspaceState() {
        val branchState = workspace.activeState
        val nextPeople = workspace.activePeopleState ?: peopleEngine.initialize(branchState)
        val nextEconomy = workspace.activeEconomyState
            ?: EconomyEngine(session.world, session.resources).initialize(branchState)
        val nextEvolution = workspace.activeEvolutionState
            ?: EvolutionEngine(session.world).initialize(branchState)
        session = session.copy(state = branchState)
        peopleState = nextPeople
        economyState = nextEconomy
        evolutionState = nextEvolution
        val nextCivilizationId = if (branchState.civilizations.any { it.id == selectedCivilizationId }) {
            selectedCivilizationId
        } else {
            branchState.civilizations.first().id
        }
        selectedCivilizationId = nextCivilizationId
        resetCharacterSelection(nextCivilizationId, nextPeople)
    }

    fun saveGame() {
        if (isAdvancing) return
        saveStatus = runCatching {
            val syncedWorkspace = historyTimeline.syncActive(
                workspace,
                session.state,
                peopleState,
                economyState,
                evolutionState,
            )
            context.openFileOutput(PLAYABLE_HISTORY_FILE, Context.MODE_PRIVATE).bufferedWriter().use {
                it.write(HistoryWorkspaceSnapshotV1.encode(syncedWorkspace))
            }
            context.openFileOutput(PLAYABLE_SAVE_FILE, Context.MODE_PRIVATE).bufferedWriter().use {
                it.write(GameSnapshotV1.encode(session.state))
            }
            workspace = syncedWorkspace
            "Світ і всі часові гілки збережено локально"
        }.getOrElse { "Помилка збереження: ${it.message ?: "невідома"}" }
    }

    fun loadGame() {
        if (isAdvancing) return
        saveStatus = runCatching {
            val loadedWorkspace = runCatching {
                context.openFileInput(PLAYABLE_HISTORY_FILE).bufferedReader().use {
                    HistoryWorkspaceSnapshotV1.decode(it.readText())
                }
            }.getOrNull()
            val loadedState = loadedWorkspace?.activeState
                ?: context.openFileInput(PLAYABLE_SAVE_FILE).bufferedReader().use {
                    GameSnapshotV1.decode(it.readText())
                }
            val loadedSession = playableSessionFromState(loadedState, generator, hydrology, resourceGenerator)
            val loadedPeople = loadedWorkspace?.activePeopleState ?: peopleEngine.initialize(loadedSession.state)
            val loadedEconomy = loadedWorkspace?.activeEconomyState
                ?: EconomyEngine(loadedSession.world, loadedSession.resources).initialize(loadedSession.state)
            val loadedEvolution = loadedWorkspace?.activeEvolutionState
                ?: EvolutionEngine(loadedSession.world).initialize(loadedSession.state)

            session = loadedSession
            peopleState = loadedPeople
            economyState = loadedEconomy
            evolutionState = loadedEvolution
            workspace = if (loadedWorkspace != null) {
                historyTimeline.syncActive(
                    loadedWorkspace,
                    loadedSession.state,
                    loadedPeople,
                    loadedEconomy,
                    loadedEvolution,
                )
            } else {
                historyTimeline.create(loadedSession.state, loadedPeople, loadedEconomy, loadedEvolution)
            }
            selectedCivilizationId = loadedSession.state.civilizations.first().id
            resetCharacterSelection(selectedCivilizationId, loadedPeople)
            seedText = loadedState.worldSeed.toString()
            interventionSequence = loadedState.recentEvents.asSequence()
                .map { it.id }
                .filter { it.startsWith("player-") }
                .mapNotNull { it.substringAfterLast('-').toLongOrNull() }
                .maxOrNull() ?: 0L
            characterUndressed = false
            if (loadedWorkspace != null) {
                "Світ і часові гілки завантажено"
            } else {
                "Завантажено старе збереження"
            }
        }.getOrElse { "Помилка завантаження: ${it.message ?: "немає збереження"}" }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Хроносфера", style = MaterialTheme.typography.headlineMedium)
                        Text("Офлайн-симулятор історії світу", style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { seedText = it.filter { c -> c == '-' || c.isDigit() } },
                        label = { Text("Seed") },
                        singleLine = true,
                        enabled = !isAdvancing,
                        modifier = Modifier.weight(0.75f),
                    )
                    Button(onClick = { newWorld() }, enabled = !isAdvancing) { Text("Новий") }
                }

                val time = clock.at(session.state.tick)
                Text(
                    "${time.year} рік · місяць ${time.month} · населення ${session.state.totalPopulation} · міст ${session.state.settlements.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Держав ${session.state.civilizations.size} · війн ${session.state.wars.size} · союзів ${session.state.alliances.size} · торгівля ${economyState.routes.size} · ${workspace.activeBranch.name}",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { advanceMonths(12) }, enabled = !isAdvancing) { Text("+1 рік") }
                    Button(onClick = { advanceMonths(120) }, enabled = !isAdvancing) { Text("+10 років") }
                    Button(onClick = { advanceMonths(1200) }, enabled = !isAdvancing) { Text("+100 років") }
                }
                if (isAdvancing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                val civilizations = session.state.civilizations
                val civOrder = civilizations.mapIndexed { index, civ -> civ.id to index }.toMap()
                val territory = remember(session) { territoryResolver.resolve(session.world, session.state) }
                WorldMapView(
                    world = session.world,
                    rivers = session.rivers,
                    territoryOwners = territory,
                    settlements = session.state.settlements.map {
                        SettlementMarker(it.x, it.y, it.population, civOrder[it.civilizationId] ?: 0)
                    },
                    selectedCivilizationIndex = civOrder[selectedCivilizationId],
                    onCivilizationSelected = if (isAdvancing) null else { index ->
                        civilizations.getOrNull(index)?.let { selectCivilization(it.id) }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PanelButton("Держава", selectedPanel == PlayablePanel.STATE, !isAdvancing) {
                        selectedPanel = PlayablePanel.STATE
                    }
                    PanelButton("Персонаж", selectedPanel == PlayablePanel.PERSON, !isAdvancing) {
                        selectedPanel = PlayablePanel.PERSON
                    }
                    PanelButton("Час", selectedPanel == PlayablePanel.HISTORY, !isAdvancing) {
                        selectedPanel = PlayablePanel.HISTORY
                    }
                    PanelButton("Хроніка", selectedPanel == PlayablePanel.CHRONICLE, !isAdvancing) {
                        selectedPanel = PlayablePanel.CHRONICLE
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val selectedCivilization = civilizations.firstOrNull { it.id == selectedCivilizationId }
                        ?: civilizations.first()

                    when (selectedPanel) {
                        PlayablePanel.STATE -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val index = civilizations.indexOfFirst { it.id == selectedCivilization.id }
                                            .coerceAtLeast(0)
                                        selectCivilization(civilizations[(index + 1) % civilizations.size].id)
                                    },
                                    enabled = !isAdvancing,
                                ) { Text("${selectedCivilization.name} →") }
                                Text(
                                    "техн. ${String.format("%.2f", selectedCivilization.technology)} · стаб. ${String.format("%.2f", selectedCivilization.stability)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            val selectedEconomy = economyState.economy(selectedCivilization.id)
                            if (selectedEconomy != null) {
                                Text("Епоха: ${selectedEconomy.era.displayNameUk}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Населення ${selectedCivilization.population} · казна ${String.format("%.1f", selectedCivilization.treasury)} · випуск ${String.format("%.1f", selectedEconomy.grossOutput)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "Дефіцит ${String.format("%.0f%%", selectedEconomy.shortageIndex * 100.0)} · торговий баланс ${String.format("%+.1f", selectedEconomy.tradeBalance)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            val representativeSettlement = session.state.settlements
                                .filter { it.civilizationId == selectedCivilization.id }
                                .maxByOrNull { it.population }
                            val representativePopulation = representativeSettlement?.let { evolutionState.population(it.id) }
                            val representativeLineage = representativePopulation?.let { evolutionState.lineage(it.lineageId) }
                            if (representativeLineage != null && representativePopulation != null) {
                                Text(
                                    "Біолінія: ${representativeLineage.label} · ${representativeLineage.rank.name.lowercase()} · домішка ${String.format("%.0f%%", representativePopulation.admixture * 100.0)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            val ruler = peopleState.ruler(selectedCivilization.id)
                            val profile = peopleState.profile(selectedCivilization.id)
                            if (ruler != null) {
                                val dynastyName = ruler.dynastyId?.let { dynastyId ->
                                    peopleState.dynasties.firstOrNull { it.id == dynastyId }?.name
                                } ?: "без династії"
                                Text(
                                    "Правитель: ${ruler.name}, ${ruler.ageYearsAt(session.state.tick)} р. · $dynastyName",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (profile != null) {
                                Text(
                                    "Культура: ${profile.tags.sorted().take(6).joinToString(", ")} · напруга ${String.format("%.2f", profile.socialTension)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            Text("Втручання", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { intervene(InterventionKind.HARVEST_AID) }, enabled = !isAdvancing) {
                                    Text("Допомога")
                                }
                                Button(onClick = { intervene(InterventionKind.DROUGHT) }, enabled = !isAdvancing) {
                                    Text("Посуха")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { intervene(InterventionKind.TECHNOLOGY_BOOST) }, enabled = !isAdvancing) {
                                    Text("Технології")
                                }
                                Button(onClick = { intervene(InterventionKind.STABILITY_SUPPORT) }, enabled = !isAdvancing) {
                                    Text("Стабільність")
                                }
                            }
                        }

                        PlayablePanel.PERSON -> {
                            val livingCharacters = peopleState.livingPeople(selectedCivilization.id)
                                .sortedByDescending { it.prestige }
                            val ruler = peopleState.ruler(selectedCivilization.id)
                            val selectedPerson = livingCharacters.firstOrNull { it.id == selectedPersonId }
                                ?: ruler
                                ?: livingCharacters.firstOrNull()

                            if (selectedPerson == null) {
                                Text("У цій державі зараз немає живих визначних осіб.")
                            } else {
                                val age = selectedPerson.ageYearsAt(session.state.tick)
                                val effectiveUndressed = characterUndressed && age >= 18
                                val baseScene = remember(
                                    selectedPerson.id,
                                    session.state.tick,
                                    evolutionState,
                                    effectiveUndressed,
                                ) {
                                    CharacterSceneFactory.resolve(
                                        person = selectedPerson,
                                        tick = session.state.tick,
                                        evolution = evolutionState,
                                        undressed = effectiveUndressed,
                                    )
                                }
                                val adultRequest = remember(
                                    selectedPerson.id,
                                    session.state.tick,
                                    peopleState,
                                    evolutionState,
                                ) {
                                    CharacterSceneFactory.adultRequest(
                                        person = selectedPerson,
                                        tick = session.state.tick,
                                        people = peopleState,
                                        evolution = evolutionState,
                                    )
                                }
                                val scene = remember(
                                    baseScene,
                                    adultRequest,
                                    effectiveUndressed,
                                    adultSceneRuntime.isActive,
                                ) {
                                    if (adultRequest != null && adultSceneRuntime.isActive) {
                                        adultSceneRuntime.resolveCharacterCard(adultRequest, effectiveUndressed) ?: baseScene
                                    } else {
                                        baseScene
                                    }
                                }
                                CharacterCardPanel(
                                    person = selectedPerson,
                                    tick = session.state.tick,
                                    people = peopleState,
                                    evolution = evolutionState,
                                    scene = scene,
                                    hasPreviousOrNext = livingCharacters.size > 1,
                                    controlsEnabled = !isAdvancing,
                                    onNext = {
                                        val index = livingCharacters.indexOfFirst { it.id == selectedPerson.id }
                                            .coerceAtLeast(0)
                                        selectedPersonId = livingCharacters[(index + 1) % livingCharacters.size].id
                                        characterUndressed = false
                                    },
                                    onToggleWardrobe = {
                                        if (age >= 18) characterUndressed = !effectiveUndressed
                                    },
                                )
                            }
                        }

                        PlayablePanel.HISTORY -> {
                            Text("Машина часу", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Активна гілка: ${workspace.activeBranch.name} · гілок ${workspace.branches.size} · контрольних точок ${workspace.checkpoints.count { it.branchId == workspace.activeBranchId }}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        workspace = historyTimeline.checkpoint(
                                            historyTimeline.syncActive(
                                                workspace,
                                                session.state,
                                                peopleState,
                                                economyState,
                                                evolutionState,
                                            ),
                                            "Рік ${time.year}",
                                        )
                                        saveStatus = "Створено контрольну точку"
                                    },
                                    enabled = !isAdvancing,
                                ) { Text("Точка") }
                                Button(
                                    onClick = {
                                        workspace = historyTimeline.fork(
                                            historyTimeline.syncActive(
                                                workspace,
                                                session.state,
                                                peopleState,
                                                economyState,
                                                evolutionState,
                                            ),
                                            "Альтернатива ${workspace.branches.size}",
                                        )
                                        activateWorkspaceState()
                                        saveStatus = "Створено альтернативну історію"
                                    },
                                    enabled = !isAdvancing,
                                ) { Text("Відгалуження") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        val before = workspace
                                        val hasCheckpoint = before.checkpoints.any { it.branchId == before.activeBranchId }
                                        workspace = historyTimeline.restoreLatestCheckpoint(before)
                                        activateWorkspaceState()
                                        saveStatus = if (hasCheckpoint) {
                                            "Контрольну точку відновлено"
                                        } else {
                                            "У цій гілці немає контрольної точки"
                                        }
                                    },
                                    enabled = !isAdvancing,
                                ) { Text("Відновити") }
                                if (workspace.branches.size > 1) {
                                    Button(
                                        onClick = {
                                            val currentIndex = workspace.branches.indexOfFirst {
                                                it.id == workspace.activeBranchId
                                            }.coerceAtLeast(0)
                                            val nextBranch = workspace.branches[(currentIndex + 1) % workspace.branches.size]
                                            workspace = historyTimeline.switchTo(
                                                historyTimeline.syncActive(
                                                    workspace,
                                                    session.state,
                                                    peopleState,
                                                    economyState,
                                                    evolutionState,
                                                ),
                                                nextBranch.id,
                                            )
                                            activateWorkspaceState()
                                            saveStatus = "Активна гілка: ${workspace.activeBranch.name}"
                                        },
                                        enabled = !isAdvancing,
                                    ) { Text("Наступна гілка") }
                                }
                            }

                            val originalState = workspace.branches
                                .firstOrNull { it.id == HistoryTimeline.ROOT_BRANCH_ID }
                                ?.state ?: workspace.activeState
                            val divergence = HistoryComparator.compare(originalState, session.state)
                            Text(
                                "Відхилення від кореня: Δнас. ${signedPlayable(divergence.populationDelta)} · Δміст ${signedPlayable(divergence.settlementDelta)} · Δтехн. ${String.format("%+.3f", divergence.averageTechnologyDelta)}",
                                style = MaterialTheme.typography.bodySmall,
                            )

                            Text("Локальне збереження", style = MaterialTheme.typography.titleSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { saveGame() }, enabled = !isAdvancing) { Text("Зберегти") }
                                Button(onClick = { loadGame() }, enabled = !isAdvancing) { Text("Завантажити") }
                            }
                        }

                        PlayablePanel.CHRONICLE -> {
                            val leaders = session.state.civilizations.sortedByDescending { it.population }.take(5)
                            Text("Провідні держави", style = MaterialTheme.typography.titleMedium)
                            leaders.forEachIndexed { index, civilization ->
                                val leaderName = peopleState.ruler(civilization.id)?.name ?: "?"
                                val eraName = economyState.economy(civilization.id)?.era?.displayNameUk ?: "?"
                                Text(
                                    "${index + 1}. ${civilization.name} · ${civilization.population} · $leaderName · $eraName",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                            val names = session.state.civilizations.associate { it.id to it.name }
                            if (session.state.wars.isNotEmpty()) {
                                Text("Активні війни", style = MaterialTheme.typography.titleSmall)
                                session.state.wars.take(5).forEach { war ->
                                    val score = String.format("%.1f:%.1f", war.scoreA, war.scoreB)
                                    Text(
                                        "${names[war.civilizationA] ?: war.civilizationA} — ${names[war.civilizationB] ?: war.civilizationB} [$score]",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }

                            Text("Останні події", style = MaterialTheme.typography.titleSmall)
                            session.state.recentEvents.takeLast(12).reversed().forEach { event ->
                                val eventTime = clock.at(event.tick)
                                Text(
                                    "${eventTime.year}: ${textGenerator.describe(event)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                Text(saveStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RowScope.PanelButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.weight(1f)) { Text(text) }
    }
}

private fun signedPlayable(value: Long): String = if (value >= 0) "+$value" else value.toString()
private fun signedPlayable(value: Int): String = if (value >= 0) "+$value" else value.toString()

private fun newPlayableSession(
    seed: Long,
    generator: WorldGenerator,
    hydrology: WorldHydrology,
    resourceGenerator: WorldResourceGenerator,
): GameSession {
    val world = generator.generate(WorldSeed(seed))
    val resources = resourceGenerator.generate(world)
    return GameSession(
        world = world,
        resources = resources,
        rivers = hydrology.generateRivers(world),
        state = CivilizationEngine(world, resources).initialize(),
    )
}

private fun playableSessionFromState(
    state: LivingPlanetState,
    generator: WorldGenerator,
    hydrology: WorldHydrology,
    resourceGenerator: WorldResourceGenerator,
): GameSession {
    val world = generator.generate(WorldSeed(state.worldSeed))
    val resources = resourceGenerator.generate(world)
    val normalizedState = CivilizationEngine(world, resources).prepareState(state)
    return GameSession(
        world = world,
        resources = resources,
        rivers = hydrology.generateRivers(world),
        state = normalizedState,
    )
}
