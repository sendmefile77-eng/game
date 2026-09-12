package com.sendmefile77.chronosphere

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.TerritoryResolver
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.PlayerEvolutionInterventionEngine
import com.sendmefile77.chronosphere.history.HistoryComparator
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.history.HistoryWorkspace
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

private const val GAME_SAVE_FILE = "chronosphere-save-v1.txt"
private const val GAME_HISTORY_FILE = "chronosphere-history-v1.txt"

private val ChronosphereColors = darkColorScheme(
    primary = Color(0xFFD8B765),
    onPrimary = Color(0xFF16130A),
    secondary = Color(0xFF6EC8C8),
    onSecondary = Color(0xFF071414),
    background = Color(0xFF080D12),
    onBackground = Color(0xFFE8EDF2),
    surface = Color(0xFF0F171E),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF17232D),
    onSurfaceVariant = Color(0xFFB7C3CD),
    outline = Color(0xFF40515E),
    error = Color(0xFFE07171),
)

private enum class GamePanel { WORLD, PERSON, HISTORY, CHRONICLE }

@Composable
fun ChronosphereGameApp() {
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
    val playerEvolutionEngine = remember { PlayerEvolutionInterventionEngine() }
    val peopleEngine = remember { PeopleEngine() }
    val adultModule = remember { AdultModuleRuntime.load() }
    val adultModuleActive = remember(adultModule) { AdultModuleRuntime.isActive(adultModule) }
    val adultSceneRuntime = remember { AdultSceneRuntime.load() }

    val initialSetup = remember { WorldSetup.default(seed = 424242L, tribeCount = 3) }
    val initialStart = remember {
        createConfiguredWorldStart(
            setup = initialSetup,
            generator = generator,
            hydrology = hydrology,
            resourceGenerator = resourceGenerator,
            peopleEngine = peopleEngine,
        )
    }
    val initialSession = initialStart.session
    val initialPeople = initialStart.people
    val initialEconomy = initialStart.economy
    val initialEvolution = initialStart.evolution

    var worldSetup by remember { mutableStateOf(initialSetup) }
    var showNewWorldDialog by remember { mutableStateOf(true) }
    var session by remember { mutableStateOf(initialSession) }
    var peopleState by remember { mutableStateOf(initialPeople) }
    var economyState by remember { mutableStateOf(initialEconomy) }
    var evolutionState by remember { mutableStateOf(initialEvolution) }
    var workspace by remember {
        mutableStateOf(historyTimeline.create(initialSession.state, initialPeople, initialEconomy, initialEvolution))
    }
    var selectedCivilizationId by remember { mutableStateOf(initialSession.state.civilizations.first().id) }
    var selectedPersonId by remember {
        val civilizationId = initialSession.state.civilizations.first().id
        val featured = initialPeople.featuredPeople(civilizationId, initialSession.state.tick)
        mutableStateOf(
            featured.maxByOrNull { it.prestige }?.id
                ?: initialPeople.ruler(civilizationId)?.takeIf { it.ageYearsAt(initialSession.state.tick) <= 40 }?.id,
        )
    }
    var characterUndressed by remember { mutableStateOf(false) }
    var interventionSequence by remember { mutableStateOf(0L) }
    var selectedPanel by remember { mutableStateOf(GamePanel.WORLD) }
    var isAdvancing by remember { mutableStateOf(false) }
    var saveStatus by remember {
        mutableStateOf(if (adultModuleActive) "Гібридний режим · AI Horde · розширений модуль активний" else "Гібридний режим · AI Horde")
    }

    fun resetCharacterSelection(civilizationId: String, people: PeopleState) {
        selectedPersonId = people.featuredPeople(civilizationId, people.tick)
            .maxByOrNull { it.prestige }
            ?.id
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

    fun newWorld(setup: WorldSetup) {
        if (isAdvancing) return
        val created = runCatching {
            createConfiguredWorldStart(
                setup = setup,
                generator = generator,
                hydrology = hydrology,
                resourceGenerator = resourceGenerator,
                peopleEngine = peopleEngine,
            )
        }.getOrElse { error ->
            saveStatus = "Не вдалося створити світ: ${error.message ?: "невідома помилка"}"
            return
        }
        worldSetup = setup
        session = created.session
        peopleState = created.people
        economyState = created.economy
        evolutionState = created.evolution
        workspace = historyTimeline.create(
            created.session.state,
            created.people,
            created.economy,
            created.evolution,
        )
        selectedCivilizationId = created.session.state.civilizations.first().id
        resetCharacterSelection(selectedCivilizationId, created.people)
        interventionSequence = 0L
        characterUndressed = false
        selectedPanel = GamePanel.WORLD
        showNewWorldDialog = false
        saveStatus = "Створено світ · ${setup.tribes.size} племені · seed ${setup.seed}"
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
        saveStatus = "Моделювання історії…"
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
                if (result.people.featuredPeople(validCivilizationId, result.world.tick).none { it.id == targetPersonId }) {
                    resetCharacterSelection(validCivilizationId, result.people)
                }
                saveStatus = "Світ змодельовано до ${clock.at(result.world.tick).year} року"
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
        val targetName = session.state.civilizations.firstOrNull { it.id == targetId }?.name ?: "держави"
        saveStatus = "Втручання застосовано до $targetName"
    }

    fun interveneEvolution(kind: PlayerEvolutionInterventionEngine.Kind, settlementId: String) {
        if (isAdvancing) return
        val result = runCatching {
            playerEvolutionEngine.apply(kind, evolutionState, session.state, settlementId)
        }.getOrElse { error ->
            saveStatus = error.message ?: "Еволюційне втручання недоступне"
            return
        }
        val nextWorld = session.state.copy(
            recentEvents = (session.state.recentEvents + result.event).takeLast(96),
        )
        syncState(nextWorld, nextEvolution = result.state)
        saveStatus = when (kind) {
            PlayerEvolutionInterventionEngine.Kind.DIVERGE -> "Лінію примусово відокремлено та прискорено її розходження"
            PlayerEvolutionInterventionEngine.Kind.MUTATE -> "Створено нову структурно змінену лінію"
            PlayerEvolutionInterventionEngine.Kind.HYBRIDIZE -> "Створено нову гібридну лінію"
        }
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
            context.openFileOutput(GAME_HISTORY_FILE, Context.MODE_PRIVATE).bufferedWriter().use {
                it.write(HistoryWorkspaceSnapshotV1.encode(syncedWorkspace))
            }
            context.openFileOutput(GAME_SAVE_FILE, Context.MODE_PRIVATE).bufferedWriter().use {
                it.write(GameSnapshotV1.encode(session.state))
            }
            workspace = syncedWorkspace
            "Світ і часові гілки збережено"
        }.getOrElse { "Помилка збереження: ${it.message ?: "невідома"}" }
    }

    fun loadGame() {
        if (isAdvancing) return
        saveStatus = runCatching {
            val loadedWorkspace = runCatching {
                context.openFileInput(GAME_HISTORY_FILE).bufferedReader().use {
                    HistoryWorkspaceSnapshotV1.decode(it.readText())
                }
            }.getOrNull()
            val loadedState = loadedWorkspace?.activeState
                ?: context.openFileInput(GAME_SAVE_FILE).bufferedReader().use {
                    GameSnapshotV1.decode(it.readText())
                }
            val loadedSession = restoreGameSession(loadedState, generator, hydrology, resourceGenerator)
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
            worldSetup = WorldSetup.default(
                seed = loadedState.worldSeed,
                tribeCount = loadedState.civilizations.size.coerceIn(WorldSetup.MIN_TRIBES, WorldSetup.MAX_TRIBES),
            )
            interventionSequence = loadedState.recentEvents.asSequence()
                .map { it.id }
                .filter { it.startsWith("player-") }
                .mapNotNull { it.substringAfterLast('-').toLongOrNull() }
                .maxOrNull() ?: 0L
            characterUndressed = false
            if (loadedWorkspace != null) "Світ і часові гілки завантажено" else "Старе збереження завантажено"
        }.getOrElse { "Помилка завантаження: ${it.message ?: "збереження не знайдено"}" }
    }

    val civilizations = session.state.civilizations
    val selectedCivilization = civilizations.firstOrNull { it.id == selectedCivilizationId } ?: civilizations.first()
    val selectedEconomy = economyState.economy(selectedCivilization.id)
    val time = clock.at(session.state.tick)
    val civilizationOrder = civilizations.mapIndexed { index, civilization -> civilization.id to index }.toMap()
    val territory = remember(session.state) { territoryResolver.resolve(session.world, session.state) }

    MaterialTheme(colorScheme = ChronosphereColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val mapHeight = when (selectedPanel) {
                    GamePanel.WORLD -> (maxHeight * 0.46f).coerceIn(220.dp, 360.dp)
                    GamePanel.PERSON -> (maxHeight * 0.27f).coerceIn(150.dp, 220.dp)
                    GamePanel.HISTORY, GamePanel.CHRONICLE -> (maxHeight * 0.24f).coerceIn(140.dp, 205.dp)
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    ChronosphereTopBar(
                        year = time.year,
                        branchName = branchDisplayName(workspace.activeBranch.name),
                        onNewWorld = { if (!isAdvancing) showNewWorldDialog = true },
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(mapHeight)
                            .padding(horizontal = 10.dp),
                    ) {
                        WorldMapView(
                            world = session.world,
                            rivers = session.rivers,
                            territoryOwners = territory,
                            settlements = session.state.settlements.map {
                                SettlementMarker(
                                    x = it.x,
                                    y = it.y,
                                    population = it.population,
                                    civilizationIndex = civilizationOrder[it.civilizationId] ?: 0,
                                )
                            },
                            selectedCivilizationIndex = civilizationOrder[selectedCivilizationId],
                            onCivilizationSelected = if (isAdvancing) null else { index ->
                                civilizations.getOrNull(index)?.let { selectCivilization(it.id) }
                            },
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                        )

                        WorldMapSummary(
                            totalPopulation = session.state.totalPopulation,
                            settlements = session.state.settlements.size,
                            civilizations = civilizations.size,
                            wars = session.state.wars.size,
                            tradeRoutes = economyState.routes.size,
                            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                        )

                        SelectedCivilizationBadge(
                            civilizationName = selectedCivilization.name,
                            eraName = selectedEconomy?.era?.displayNameUk ?: "Епоха формується",
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        )

                        Surface(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                            color = Color(0xE60A1117),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TimeButton("+1 рік", !isAdvancing) { advanceMonths(12) }
                                TimeButton("+10", !isAdvancing) { advanceMonths(120) }
                                TimeButton("+100", !isAdvancing) { advanceMonths(1200) }
                            }
                        }
                    }

                    if (isAdvancing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                    GameTabs(
                        selectedPanel = selectedPanel,
                        enabled = !isAdvancing,
                        onSelect = { selectedPanel = it },
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            when (selectedPanel) {
                                GamePanel.WORLD -> WorldPanel(
                                    civilization = selectedCivilization,
                                    civilizationCount = civilizations.size,
                                    economyState = economyState,
                                    peopleState = peopleState,
                                    evolutionState = evolutionState,
                                    session = session,
                                    isAdvancing = isAdvancing,
                                    onNextCivilization = {
                                        val index = civilizations.indexOfFirst { it.id == selectedCivilization.id }.coerceAtLeast(0)
                                        selectCivilization(civilizations[(index + 1) % civilizations.size].id)
                                    },
                                    onIntervene = ::intervene,
                                    onEvolutionIntervene = ::interveneEvolution,
                                )

                                GamePanel.PERSON -> {
                                    val livingCharacters = peopleState.featuredPeople(
                                        selectedCivilization.id,
                                        session.state.tick,
                                    ).sortedByDescending { it.prestige }
                                    val selectedPerson = livingCharacters.firstOrNull { it.id == selectedPersonId }
                                        ?: livingCharacters.firstOrNull()
                                    if (selectedPerson == null) {
                                        EmptyPanel("У цій державі зараз немає активних визначних осіб віком 18–40 років")
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
                                            technologyEra = selectedEconomy?.era,
                                            hasPreviousOrNext = livingCharacters.size > 1,
                                            controlsEnabled = !isAdvancing,
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
                                }

                                GamePanel.HISTORY -> HistoryPanel(
                                    workspace = workspace,
                                    session = session,
                                    timeYear = time.year,
                                    isAdvancing = isAdvancing,
                                    onCheckpoint = {
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
                                        saveStatus = "Момент історії збережено"
                                    },
                                    onFork = {
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
                                    onRestore = {
                                        val hadCheckpoint = workspace.checkpoints.any { it.branchId == workspace.activeBranchId }
                                        workspace = historyTimeline.restoreLatestCheckpoint(workspace)
                                        activateWorkspaceState()
                                        saveStatus = if (hadCheckpoint) {
                                            "Повернуто збережений момент"
                                        } else {
                                            "У цій гілці ще немає збереженого моменту"
                                        }
                                    },
                                    onNextBranch = {
                                        if (workspace.branches.size > 1) {
                                            val currentIndex = workspace.branches.indexOfFirst { it.id == workspace.activeBranchId }.coerceAtLeast(0)
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
                                            saveStatus = "Активна лінія: ${branchDisplayName(workspace.activeBranch.name)}"
                                        }
                                    },
                                    onSave = ::saveGame,
                                    onLoad = ::loadGame,
                                )

                                GamePanel.CHRONICLE -> ChroniclePanel(
                                    session = session,
                                    peopleState = peopleState,
                                    economyState = economyState,
                                    clock = clock,
                                    textGenerator = textGenerator,
                                )
                            }
                        }
                    }

                    Text(
                        saveStatus,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (showNewWorldDialog) {
            NewWorldSetupDialog(
                initial = worldSetup,
                enabled = !isAdvancing,
                onDismiss = { showNewWorldDialog = false },
                onCreate = ::newWorld,
            )
        }
    }
}

@Composable
private fun ChronosphereTopBar(year: Int, branchName: String, onNewWorld: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "ХРОНОСФЕРА",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "$year рік · $branchName",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onNewWorld) { Text("Новий світ") }
    }
}

@Composable
private fun WorldMapSummary(
    totalPopulation: Long,
    settlements: Int,
    civilizations: Int,
    wars: Int,
    tradeRoutes: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xD90A1117),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(
                "${compactNumber(totalPopulation)} людей · $settlements міст",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "$civilizations держав · $wars війн · $tradeRoutes торгових шляхів",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SelectedCivilizationBadge(
    civilizationName: String,
    eraName: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = Color(0xD90A1117),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier.widthIn(min = 110.dp, max = 150.dp).padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                civilizationName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                eraName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun TimeButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text, maxLines = 1)
    }
}

@Composable
private fun GameTabs(selectedPanel: GamePanel, enabled: Boolean, onSelect: (GamePanel) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        GameTab("Світ", GamePanel.WORLD, selectedPanel, enabled, onSelect)
        GameTab("Люди", GamePanel.PERSON, selectedPanel, enabled, onSelect)
        GameTab("Час", GamePanel.HISTORY, selectedPanel, enabled, onSelect)
        GameTab("Хроніка", GamePanel.CHRONICLE, selectedPanel, enabled, onSelect)
    }
}

@Composable
private fun RowScope.GameTab(
    label: String,
    panel: GamePanel,
    selectedPanel: GamePanel,
    enabled: Boolean,
    onSelect: (GamePanel) -> Unit,
) {
    val selected = panel == selectedPanel
    Surface(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onSelect(panel) },
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                maxLines = 1,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorldPanel(
    civilization: Civilization,
    civilizationCount: Int,
    economyState: EconomyState,
    peopleState: PeopleState,
    evolutionState: EvolutionState,
    session: GameSession,
    isAdvancing: Boolean,
    onNextCivilization: () -> Unit,
    onIntervene: (InterventionKind) -> Unit,
    onEvolutionIntervene: (PlayerEvolutionInterventionEngine.Kind, String) -> Unit,
) {
    val economy = economyState.economy(civilization.id)
    val ruler = peopleState.ruler(civilization.id)
    val profile = peopleState.profile(civilization.id)
    val representativeSettlement = session.state.settlements
        .filter { it.civilizationId == civilization.id }
        .maxByOrNull { it.population }
    val representativePopulation = representativeSettlement?.let { evolutionState.population(it.id) }
    val representativeLineage = representativePopulation?.let { evolutionState.lineage(it.lineageId) }
    val evolutionInterventionEngine = remember { PlayerEvolutionInterventionEngine() }
    val hybridCandidate = remember(evolutionState, representativeSettlement?.id) {
        representativeSettlement?.let { evolutionInterventionEngine.bestHybridCandidate(evolutionState, it.id) }
    }
    val hybridSettlementName = hybridCandidate?.let { candidate ->
        session.state.settlements.firstOrNull { it.id == candidate.settlementId }?.name
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(civilization.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                economy?.era?.displayNameUk ?: "Епоха формується",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (civilizationCount > 1) {
            OutlinedButton(onClick = onNextCivilization, enabled = !isAdvancing) { Text("Наступна") }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricCard("Населення", compactNumber(civilization.population), Modifier.weight(1f))
        MetricCard("Стабільність", qualityBand(civilization.stability), Modifier.weight(1f))
        MetricCard("Розвиток", qualityBand(civilization.technology), Modifier.weight(1f))
    }

    if (economy != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MetricCard("Казна", compactNumber(civilization.treasury), Modifier.weight(1f))
            MetricCard("Ресурси", shortageBand(economy.shortageIndex), Modifier.weight(1f))
            MetricCard("Торгівля", tradeBand(economy.tradeBalance), Modifier.weight(1f))
        }
    }

    if (representativeLineage != null && representativePopulation != null) {
        InfoLine(
            "Походження",
            "${representativeLineage.label} · ${rankDisplayName(representativeLineage.rank.name)} · домішка ${String.format("%.0f%%", representativePopulation.admixture * 100.0)}",
        )
    }
    if (ruler != null) {
        val dynasty = ruler.dynastyId?.let { dynastyId -> peopleState.dynasties.firstOrNull { it.id == dynastyId }?.name }
        InfoLine(
            "Правитель",
            "${ruler.name}, ${ruler.ageYearsAt(session.state.tick)} р.${dynasty?.let { " · $it" } ?: ""}",
        )
    }
    if (profile != null) {
        val culture = profile.tags.sorted().take(8).joinToString(separator = " · ", transform = ::humanizeTag)
        InfoLine("Культура", culture.ifBlank { "Без виразної домінантної традиції" })
        InfoLine("Суспільство", tensionBand(profile.socialTension))
    }

    if (representativeSettlement != null && representativePopulation != null && representativeLineage != null) {
        val bodyPlan = representativeLineage.bodyPlan
        Text("Керування еволюцією", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        InfoLine(
            "Активна лінія",
            "${representativeLineage.label} · відхилення ${String.format("%.0f%%", representativeLineage.divergenceFromOrigin * 100.0)} · тиск мутацій ${String.format("%.0f%%", representativePopulation.mutationPressure * 100.0)}",
        )
        InfoLine(
            "План тіла",
            "рук ${bodyPlan.armPairs * 2} · ніг ${bodyPlan.legPairs * 2} · очей ${bodyPlan.eyeCount}" +
                if (bodyPlan.hasTail) " · хвіст" else "",
        )
        InfoLine(
            "Гібридизація",
            if (hybridCandidate != null) {
                "${hybridCandidate.lineageLabel}${hybridSettlementName?.let { " ($it)" } ?: ""} · відмінність ${String.format("%.0f%%", hybridCandidate.difference * 100.0)}"
            } else {
                "Поки немає достатньо відмінної другої лінії"
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InterventionButton("Розходження", Modifier.weight(1f), !isAdvancing) {
                onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.DIVERGE, representativeSettlement.id)
            }
            InterventionButton("Мутація", Modifier.weight(1f), !isAdvancing) {
                onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.MUTATE, representativeSettlement.id)
            }
        }
        OutlinedButton(
            onClick = { onEvolutionIntervene(PlayerEvolutionInterventionEngine.Kind.HYBRIDIZE, representativeSettlement.id) },
            enabled = !isAdvancing && hybridCandidate != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Гібридизувати з найвідміннішою доступною лінією")
        }
        if (hybridCandidate == null) {
            Text(
                "Спочатку розведіть лінії: змінюйте різні держави окремо, а потім поверніться до гібридизації.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Text("Втручання у світ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InterventionButton("Допомога", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.HARVEST_AID) }
        InterventionButton("Посуха", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.DROUGHT) }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InterventionButton("Прорив", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.TECHNOLOGY_BOOST) }
        InterventionButton("Підтримка", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.STABILITY_SUPPORT) }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun InterventionButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label, maxLines = 1) }
}

@Composable
private fun HistoryPanel(
    workspace: HistoryWorkspace,
    session: GameSession,
    timeYear: Int,
    isAdvancing: Boolean,
    onCheckpoint: () -> Unit,
    onFork: () -> Unit,
    onRestore: () -> Unit,
    onNextBranch: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
) {
    Text("Машина часу", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(
        "${branchDisplayName(workspace.activeBranch.name)} · ${workspace.branches.size} часових ліній · ${workspace.checkpoints.count { it.branchId == workspace.activeBranchId }} збережених моментів",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onCheckpoint, enabled = !isAdvancing, modifier = Modifier.weight(1f)) { Text("Зберегти момент") }
        OutlinedButton(onClick = onFork, enabled = !isAdvancing, modifier = Modifier.weight(1f)) { Text("Нова гілка") }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onRestore, enabled = !isAdvancing, modifier = Modifier.weight(1f)) { Text("Повернутися") }
        OutlinedButton(
            onClick = onNextBranch,
            enabled = !isAdvancing && workspace.branches.size > 1,
            modifier = Modifier.weight(1f),
        ) { Text("Інша гілка") }
    }

    val originalState = workspace.branches.firstOrNull { it.id == HistoryTimeline.ROOT_BRANCH_ID }?.state ?: workspace.activeState
    val divergence = HistoryComparator.compare(originalState, session.state)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Відмінність від початкової історії", fontWeight = FontWeight.SemiBold)
            Text("Населення ${signedNumber(divergence.populationDelta)} · міста ${signedNumber(divergence.settlementDelta)}")
            Text("Технологічний зсув ${String.format("%+.3f", divergence.averageTechnologyDelta)}")
        }
    }

    Text("Світ на $timeYear рік", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSave, enabled = !isAdvancing, modifier = Modifier.weight(1f)) { Text("Зберегти світ") }
        OutlinedButton(onClick = onLoad, enabled = !isAdvancing, modifier = Modifier.weight(1f)) { Text("Завантажити") }
    }
}

@Composable
private fun ChroniclePanel(
    session: GameSession,
    peopleState: PeopleState,
    economyState: EconomyState,
    clock: SimulationClock,
    textGenerator: ChronicleTextGenerator,
) {
    Text("Хроніка світу", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    val leaders = session.state.civilizations.sortedByDescending { it.population }.take(5)
    Text("Найвпливовіші держави", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    leaders.forEachIndexed { index, civilization ->
        val leaderName = peopleState.ruler(civilization.id)?.name ?: "без відомого правителя"
        val eraName = economyState.economy(civilization.id)?.era?.displayNameUk ?: "невизначена епоха"
        Text(
            "${index + 1}. ${civilization.name} · ${compactNumber(civilization.population)} людей · $leaderName · $eraName",
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    val names = session.state.civilizations.associate { it.id to it.name }
    if (session.state.wars.isNotEmpty()) {
        Text("Війни", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        session.state.wars.take(5).forEach { war ->
            Text("${names[war.civilizationA] ?: "Невідома держава"} — ${names[war.civilizationB] ?: "Невідома держава"}")
        }
    }

    ChronicleHordeEventCard(
        events = session.state.recentEvents,
        peopleState = peopleState,
        economyState = economyState,
        clock = clock,
        textGenerator = textGenerator,
    )

    Text("Останні події", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
    val events = session.state.recentEvents.takeLast(12).reversed()
    if (events.isEmpty()) {
        Text("Світ ще не накопичив значущих подій", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        events.forEach { event ->
            val eventTime = clock.at(event.tick)
            Text("${eventTime.year}: ${textGenerator.describe(event)}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun branchDisplayName(name: String): String = when (name.lowercase()) {
    "original timeline" -> "Основна лінія"
    else -> name.replace("timeline", "лінія", ignoreCase = true)
}

private fun humanizeTag(tag: String): String = when (tag.lowercase()) {
    "dynastic" -> "династична традиція"
    "fertility_cult" -> "культ родючості"
    "highland" -> "гірська культура"
    "sacred" -> "сакральні звичаї"
    "temperate-climate" -> "помірний клімат"
    "maritime" -> "морська культура"
    "agrarian" -> "землеробська культура"
    "urban" -> "міська культура"
    "nomadic" -> "кочова культура"
    "mercantile" -> "торгова культура"
    "nudity_culture" -> "культура наготи"
    "public_sex" -> "публічна сексуальність"
    "ritual_sex" -> "ритуальна сексуальність"
    "dominance_culture" -> "домінування"
    "submission_culture" -> "підкорення"
    "bondage_culture" -> "бондаж"
    "group_sex" -> "групові практики"
    "voyeurism_culture" -> "вуайєризм"
    "status_bonds" -> "статусні зв’язки"
    "plural_bonding" -> "полігамія"
    "monogamous" -> "моногамія"
    "warlike" -> "войовничі"
    "isolationist" -> "ізоляціоністи"
    "technological" -> "винахідники"
    "rapid_mutation" -> "швидка мутація"
    "hybrid_friendly" -> "відкриті до гібридів"
    "body_cult" -> "культ тіла"
    "matriarchal" -> "матріархальні"
    "race_human" -> "люди"
    "race_tall_slender" -> "високі й стрункі"
    "race_robust" -> "масивні"
    "race_furred" -> "хутряні"
    "race_tailed" -> "хвостаті"
    "race_large_eyed" -> "великоокі"
    "race_four_armed" -> "чотирирукі"
    "race_scaled" -> "лускаті"
    else -> tag.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
}

private fun rankDisplayName(rank: String): String = when (rank.lowercase()) {
    "population" -> "популяція"
    "morph" -> "морф"
    "subspecies" -> "підвид"
    "species" -> "вид"
    else -> rank.lowercase()
}

private fun qualityBand(value: Double): String = when {
    value >= 0.82 -> "дуже висока"
    value >= 0.64 -> "висока"
    value >= 0.45 -> "середня"
    value >= 0.25 -> "низька"
    else -> "критична"
}

private fun shortageBand(value: Double): String = when {
    value < 0.08 -> "достатньо"
    value < 0.20 -> "напружено"
    value < 0.40 -> "дефіцит"
    else -> "криза"
}

private fun tradeBand(value: Double): String = when {
    value > 10.0 -> "профіцит"
    value < -10.0 -> "збиткова"
    else -> "збалансована"
}

private fun tensionBand(value: Double): String = when {
    value < 0.20 -> "спокійне"
    value < 0.45 -> "стабільне"
    value < 0.70 -> "напружене"
    else -> "на межі кризи"
}

private fun compactNumber(value: Long): String = when {
    kotlin.math.abs(value) >= 1_000_000_000L -> String.format("%.1f млрд", value / 1_000_000_000.0)
    kotlin.math.abs(value) >= 1_000_000L -> String.format("%.1f млн", value / 1_000_000.0)
    kotlin.math.abs(value) >= 1_000L -> String.format("%.1f тис.", value / 1_000.0)
    else -> value.toString()
}

private fun compactNumber(value: Double): String = when {
    kotlin.math.abs(value) >= 1_000_000_000.0 -> String.format("%.1f млрд", value / 1_000_000_000.0)
    kotlin.math.abs(value) >= 1_000_000.0 -> String.format("%.1f млн", value / 1_000_000.0)
    kotlin.math.abs(value) >= 1_000.0 -> String.format("%.1f тис.", value / 1_000.0)
    else -> String.format("%.1f", value)
}

private fun signedNumber(value: Long): String = if (value >= 0) "+$value" else value.toString()
private fun signedNumber(value: Int): String = if (value >= 0) "+$value" else value.toString()

private fun createGameSession(
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

private fun restoreGameSession(
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
