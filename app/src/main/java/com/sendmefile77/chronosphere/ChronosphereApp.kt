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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.AdmixtureEngine
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
import com.sendmefile77.chronosphere.society.MorphologyContextAdultModule
import com.sendmefile77.chronosphere.society.SocietyEngine
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
    val peopleEngine = remember { PeopleEngine() }
    val adultModule = remember { AdultModuleRuntime.load() }
    val adultModuleActive = remember(adultModule) { AdultModuleRuntime.isActive(adultModule) }
    val adultSceneRuntime = remember { AdultSceneRuntime.load() }
    val initialSession = remember { newSession(424242L, generator, hydrology, resourceGenerator) }
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
    var saveStatus by remember {
        mutableStateOf(if (adultModuleActive) "Дорослий модуль активний" else "Базовий режим: дорослий модуль не завантажено")
    }
    var interventionSequence by remember { mutableStateOf(0L) }

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

    fun advanceMonths(months: Int) {
        require(months > 0)
        val civilizationEngine = CivilizationEngine(session.world, session.resources)
        val economyEngine = EconomyEngine(session.world, session.resources)
        val evolutionEngine = EvolutionEngine(session.world)
        val admixtureEngine = AdmixtureEngine(session.world)
        var worldState = session.state
        var people = peopleState
        var economy = economyState
        var evolution = evolutionState
        var remaining = months

        // A year is the causal integration slice: politics/economy/people/evolution/society
        // are resolved in sequence before the next year begins.
        while (remaining > 0) {
            val step = minOf(12, remaining)
            val fromTick = worldState.tick
            val civilizationNext = civilizationEngine.advance(worldState, step)
            val economyResult = economyEngine.advance(economy, civilizationNext)
            val peopleResult = peopleEngine.advance(people, economyResult.world)
            val worldWithPeople = economyResult.world.copy(
                recentEvents = (economyResult.world.recentEvents + peopleResult.events).takeLast(96),
            )
            val peopleAtTick = peopleResult.state.copy(tick = worldWithPeople.tick)
            val economyAtTick = economyResult.state.copy(tick = worldWithPeople.tick)

            val evolutionResult = evolutionEngine.advance(evolution, worldWithPeople)
            val admixtureEvents = mutableListOf<com.sendmefile77.chronosphere.simulation.SimulationEvent>()
            val evolutionAtTick = if (step == 12) {
                admixtureEngine.annualStep(
                    evolutionResult.state,
                    worldWithPeople,
                    worldWithPeople.tick,
                    admixtureEvents,
                )
            } else {
                evolutionResult.state
            }
            val worldWithEvolution = worldWithPeople.copy(
                recentEvents = (
                    worldWithPeople.recentEvents + evolutionResult.events + admixtureEvents
                    ).takeLast(96),
            )

            val morphologyAwareModule = MorphologyContextAdultModule(
                delegate = adultModule,
                people = peopleAtTick,
                evolution = evolutionAtTick,
            )
            val societyResult = SocietyEngine(morphologyAwareModule).advance(
                fromTick = fromTick,
                world = worldWithEvolution,
                people = peopleAtTick,
                economy = economyAtTick,
            )
            worldState = societyResult.world
            people = societyResult.people.copy(tick = worldState.tick)
            economy = economyAtTick
            evolution = evolutionAtTick.copy(tick = worldState.tick)
            remaining -= step
        }

        syncState(worldState, people, economy, evolution)
        if (people.livingPeople(selectedCivilizationId).none { it.id == selectedPersonId }) {
            resetCharacterSelection(selectedCivilizationId, people)
        }
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
        syncState(interventionEngine.apply(session.state, command), peopleState, economyState, evolutionState)
        saveStatus = "Втручання застосовано"
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
                        saveStatus = if (adultModuleActive) "Створено новий світ · дорослий модуль активний" else "Створено новий світ · базовий режим"
                    }) { Text("Новий світ") }
                }

                val time = clock.at(session.state.tick)
                Text("Рік ${time.year}, місяць ${time.month} · населення ${session.state.totalPopulation} · міст ${session.state.settlements.size}")
                Text(
                    "Війн ${session.state.wars.size} · союзів ${session.state.alliances.size} · торгових потоків ${economyState.routes.size} · гілка: ${workspace.activeBranch.name}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { advanceMonths(12) }) { Text("+1 рік") }
                    Button(onClick = { advanceMonths(120) }) { Text("+10 років") }
                    Button(onClick = { advanceMonths(1200) }) { Text("+100 років") }
                }

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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    val selectedCivilization = session.state.civilizations.firstOrNull { it.id == selectedCivilizationId }
                        ?: session.state.civilizations.first()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val civilizations = session.state.civilizations
                            val index = civilizations.indexOfFirst { it.id == selectedCivilization.id }.coerceAtLeast(0)
                            val nextCivilization = civilizations[(index + 1) % civilizations.size]
                            selectedCivilizationId = nextCivilization.id
                            resetCharacterSelection(nextCivilization.id, peopleState)
                        }) { Text("Ціль: ${selectedCivilization.name}") }
                        Text(
                            "техн. ${String.format("%.2f", selectedCivilization.technology)} · стаб. ${String.format("%.2f", selectedCivilization.stability)} · казна ${String.format("%.1f", selectedCivilization.treasury)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    val selectedEconomy = economyState.economy(selectedCivilization.id)
                    if (selectedEconomy != null) {
                        Text(
                            "Епоха: ${selectedEconomy.era.displayNameUk} · дефіцит ${String.format("%.0f%%", selectedEconomy.shortageIndex * 100.0)} · торг. баланс ${String.format("%+.1f", selectedEconomy.tradeBalance)} · випуск ${String.format("%.1f", selectedEconomy.grossOutput)}",
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
                            "Біолінія: ${representativeLineage.label} · ${representativeLineage.rank.name.lowercase()} · домішка ${String.format("%.0f%%", representativePopulation.admixture * 100.0)} · походжень ${representativePopulation.ancestry.size}",
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
                            "Правитель: ${ruler.name}, ${ruler.ageYearsAt(session.state.tick)} р. · $dynastyName · престиж ${String.format("%.2f", ruler.prestige)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (profile != null) {
                        Text(
                            "Культурний профіль: ${profile.tags.sorted().take(6).joinToString(", ")} · напруга ${String.format("%.2f", profile.socialTension)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    val livingCharacters = peopleState.livingPeople(selectedCivilization.id)
                        .sortedByDescending { it.prestige }
                    val selectedPerson = livingCharacters.firstOrNull { it.id == selectedPersonId }
                        ?: ruler
                        ?: livingCharacters.firstOrNull()
                    if (selectedPerson != null) {
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
                            onNext = {
                                val index = livingCharacters.indexOfFirst { it.id == selectedPerson.id }.coerceAtLeast(0)
                                selectedPersonId = livingCharacters[(index + 1) % livingCharacters.size].id
                                characterUndressed = false
                            },
                            onToggleWardrobe = {
                                if (age >= 18) characterUndressed = !effectiveUndressed
                            },
                        )
                    }

                    val latestSocietyEvent = session.state.recentEvents.lastOrNull {
                        it.code == "ADULT_SOCIAL_EVENT" && selectedCivilization.id in it.actorIds
                    }
                    if (latestSocietyEvent != null) {
                        Text(
                            "Соціальна подія: ${latestSocietyEvent.facts["eventCode"] ?: "подія"} · ${latestSocietyEvent.facts["participants"] ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { intervene(InterventionKind.HARVEST_AID) }) { Text("Допомога") }
                        Button(onClick = { intervene(InterventionKind.DROUGHT) }) { Text("Посуха") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { intervene(InterventionKind.TECHNOLOGY_BOOST) }) { Text("Технології") }
                        Button(onClick = { intervene(InterventionKind.STABILITY_SUPPORT) }) { Text("Стабільність") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            workspace = historyTimeline.checkpoint(
                                historyTimeline.syncActive(workspace, session.state, peopleState, economyState, evolutionState),
                                "Рік ${time.year}",
                            )
                            saveStatus = "Створено контрольну точку"
                        }) { Text("Точка") }
                        Button(onClick = {
                            workspace = historyTimeline.fork(
                                historyTimeline.syncActive(workspace, session.state, peopleState, economyState, evolutionState),
                                "Альтернатива ${workspace.branches.size}",
                            )
                            activateWorkspaceState()
                            saveStatus = "Створено альтернативну історію"
                        }) { Text("Відгалуження") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            val before = workspace
                            val hasCheckpoint = before.checkpoints.any { it.branchId == before.activeBranchId }
                            workspace = historyTimeline.restoreLatestCheckpoint(before)
                            activateWorkspaceState()
                            saveStatus = if (hasCheckpoint) "Контрольну точку відновлено" else "У цій гілці немає контрольної точки"
                        }) { Text("Відновити") }
                        if (workspace.branches.size > 1) {
                            Button(onClick = {
                                val currentIndex = workspace.branches.indexOfFirst { it.id == workspace.activeBranchId }.coerceAtLeast(0)
                                val nextBranch = workspace.branches[(currentIndex + 1) % workspace.branches.size]
                                workspace = historyTimeline.switchTo(
                                    historyTimeline.syncActive(workspace, session.state, peopleState, economyState, evolutionState),
                                    nextBranch.id,
                                )
                                activateWorkspaceState()
                                saveStatus = "Активна гілка: ${workspace.activeBranch.name}"
                            }) { Text("Змінити гілку") }
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
                                val syncedWorkspace = historyTimeline.syncActive(workspace, session.state, peopleState, economyState, evolutionState)
                                context.openFileOutput(HISTORY_FILE, Context.MODE_PRIVATE).bufferedWriter().use {
                                    it.write(HistoryWorkspaceSnapshotV1.encode(syncedWorkspace))
                                }
                                context.openFileOutput(SAVE_FILE, Context.MODE_PRIVATE).bufferedWriter().use {
                                    it.write(GameSnapshotV1.encode(session.state))
                                }
                                workspace = syncedWorkspace
                                "Світ, люди, економіка, еволюція й усі гілки збережено"
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
                                if (loadedWorkspace != null) "Світ, люди, економіка, еволюція й гілки завантажено" else "Завантажено старе збереження"
                            }.getOrElse { "Помилка завантаження: ${it.message ?: "немає збереження"}" }
                        }) { Text("Завантажити") }
                    }
                    Text(saveStatus, style = MaterialTheme.typography.bodySmall)

                    val leaders = session.state.civilizations.sortedByDescending { it.population }.take(3)
                    Text(
                        "Провідні держави: " + leaders.joinToString(" · ") { civilization ->
                            val leaderName = peopleState.ruler(civilization.id)?.name ?: "?"
                            val eraName = economyState.economy(civilization.id)?.era?.displayNameUk ?: "?"
                            "${civilization.name} ${civilization.population} ($leaderName, $eraName)"
                        },
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
