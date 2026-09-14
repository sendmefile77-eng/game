package com.sendmefile77.chronosphere

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.civilization.TerritoryResolver
import com.sendmefile77.chronosphere.economy.EconomyEngine
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.EvolutionEngine
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.PlayerEvolutionInterventionEngine
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.map.MapConnection
import com.sendmefile77.chronosphere.map.MapConnectionKind
import com.sendmefile77.chronosphere.map.SettlementMarker
import com.sendmefile77.chronosphere.map.WorldMapView
import com.sendmefile77.chronosphere.people.PeopleEngine
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationClock
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

internal enum class GamePanel { WORLD, PERSON, HISTORY, CHRONICLE }

@Composable
fun ChronosphereGameApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val generator = remember { WorldGenerator() }
    val hydrology = remember { WorldHydrology() }
    val resourceGenerator = remember { WorldResourceGenerator() }
    val territoryResolver = remember { TerritoryResolver() }
    val clock = remember { SimulationClock() }
    val textGenerator = remember { ChronicleTextGenerator() }
    val historyTimeline = remember { HistoryTimeline() }
    val playerEvolutionEngine = remember { PlayerEvolutionInterventionEngine() }
    val peopleEngine = remember { PeopleEngine() }
    val adultModule = remember { AdultModuleRuntime.load() }
    val adultModuleActive = remember(adultModule) { AdultModuleRuntime.isActive(adultModule) }
    val adultSceneRuntime = remember { AdultSceneRuntime.load() }

    val restoredStart = remember(appContext) {
        GameAutoResume.restore(
            context = appContext,
            generator = generator,
            hydrology = hydrology,
            resourceGenerator = resourceGenerator,
            peopleEngine = peopleEngine,
            historyTimeline = historyTimeline,
        )
    }
    val initialSetup = remember(restoredStart) {
        restoredStart?.let { restored ->
            WorldSetup.default(
                seed = restored.session.state.worldSeed,
                tribeCount = restored.session.state.civilizations.size.coerceIn(WorldSetup.MIN_TRIBES, WorldSetup.MAX_TRIBES),
            )
        } ?: WorldSetup.default(seed = 424242L, tribeCount = 1)
    }
    val fallbackStart = remember(restoredStart) {
        if (restoredStart == null) {
            createConfiguredWorldStart(
                setup = initialSetup,
                generator = generator,
                hydrology = hydrology,
                resourceGenerator = resourceGenerator,
                peopleEngine = peopleEngine,
            )
        } else null
    }
    val initialSession = restoredStart?.session ?: fallbackStart!!.session
    val initialPeople = restoredStart?.people ?: fallbackStart!!.people
    val initialEconomy = restoredStart?.economy ?: fallbackStart!!.economy
    val initialEvolution = restoredStart?.evolution ?: fallbackStart!!.evolution

    var worldSetup by remember { mutableStateOf(initialSetup) }
    var showNewWorldDialog by remember { mutableStateOf(restoredStart == null) }
    var session by remember { mutableStateOf(initialSession) }
    var peopleState by remember { mutableStateOf(initialPeople) }
    var economyState by remember { mutableStateOf(initialEconomy) }
    var evolutionState by remember { mutableStateOf(initialEvolution) }
    var workspace by remember {
        mutableStateOf(
            restoredStart?.workspace
                ?: historyTimeline.create(initialSession.state, initialPeople, initialEconomy, initialEvolution),
        )
    }
    var selectedCivilizationId by remember {
        mutableStateOf(restoredStart?.selectedCivilizationId ?: initialSession.state.civilizations.first().id)
    }
    var selectedPersonId by remember {
        val civilizationId = restoredStart?.selectedCivilizationId ?: initialSession.state.civilizations.first().id
        val restoredPersonId = restoredStart?.selectedPersonId?.takeIf { id -> initialPeople.persons.any { it.id == id } }
        val featured = initialPeople.featuredPeople(civilizationId, initialSession.state.tick)
        mutableStateOf(
            restoredPersonId
                ?: featured.maxByOrNull { it.prestige }?.id
                ?: initialPeople.ruler(civilizationId)?.takeIf { it.ageYearsAt(initialSession.state.tick) <= 40 }?.id,
        )
    }
    var characterUndressed by remember { mutableStateOf(false) }
    var selectedPanel by remember { mutableStateOf(restoredStart?.selectedPanel ?: GamePanel.WORLD) }
    var isAdvancing by remember { mutableStateOf(false) }
    var pendingTurnMonths by remember { mutableStateOf<Int?>(null) }
    var turnDecision by remember { mutableStateOf<ChronicleDecision?>(null) }
    var turnReport by remember { mutableStateOf<GameplayTurnReport?>(null) }
    var showDevelopmentDialog by remember { mutableStateOf(false) }
    var developmentAcknowledged by remember { mutableStateOf(true) }
    var saveStatus by remember {
        mutableStateOf(
            if (restoredStart != null) "Світ автоматично відновлено"
            else if (adultModuleActive) "Гібридний режим · AI Horde · розширений модуль активний"
            else "Гібридний режим · AI Horde",
        )
    }

    SideEffect {
        if (!showNewWorldDialog) {
            GameAutoResume.publish(
                GameAutoResumeState(
                    session = session,
                    people = peopleState,
                    economy = economyState,
                    evolution = evolutionState,
                    workspace = workspace,
                    selectedCivilizationId = selectedCivilizationId,
                    selectedPersonId = selectedPersonId,
                    selectedPanel = selectedPanel,
                ),
            )
        }
    }

    LaunchedEffect(
        session.state,
        peopleState,
        economyState,
        evolutionState,
        workspace,
        selectedCivilizationId,
        selectedPersonId,
        selectedPanel,
        showNewWorldDialog,
        isAdvancing,
    ) {
        if (!showNewWorldDialog && !isAdvancing) {
            val snapshot = GameAutoResumeState(
                session = session,
                people = peopleState,
                economy = economyState,
                evolution = evolutionState,
                workspace = historyTimeline.syncActive(workspace, session.state, peopleState, economyState, evolutionState),
                selectedCivilizationId = selectedCivilizationId,
                selectedPersonId = selectedPersonId,
                selectedPanel = selectedPanel,
            )
            GameAutoResume.publish(snapshot)
            withContext(Dispatchers.IO) { GameAutoResume.persist(appContext, snapshot) }
        }
    }

    fun resetCharacterSelection(civilizationId: String, people: PeopleState) {
        selectedPersonId = people.featuredPeople(civilizationId, people.tick).maxByOrNull { it.prestige }?.id
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

    fun chronicleDecision(): ChronicleDecision? = ChronicleDecisionCatalog.latestUnresolved(
        session.state.recentEvents, peopleState, economyState,
    )

    fun composeTurnDecision(): ChronicleDecision = TurnChoiceComposer.compose(
        eraDecision = EraTurnChoiceCatalog.decision(session.state, economyState, selectedCivilizationId),
        historicalDecision = chronicleDecision(),
    )

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
        saveStatus = "Моделювання наступних років…"
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
                } else result.world.civilizations.first().id
                selectedCivilizationId = validCivilizationId
                if (result.people.featuredPeople(validCivilizationId, result.world.tick).none { it.id == targetPersonId }) {
                    resetCharacterSelection(validCivilizationId, result.people)
                }
                turnReport = GameplayTurnReportStore.latestFor(selectedCivilizationId)
                    ?: GameplayTurnReportStore.latestFor(result.world.civilizations.first().id)
                developmentAcknowledged = turnReport == null
                showDevelopmentDialog = turnReport != null
                pendingTurnMonths = null
                turnDecision = null
                saveStatus = "Світ прожив хід і дійшов до ${clock.at(result.world.tick).year} року"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: PendingChronicleDecisionException) {
                pendingTurnMonths = months
                turnDecision = composeTurnDecision()
                saveStatus = error.message ?: "Потрібна відповідь на історичну розвилку"
            } catch (error: Throwable) {
                saveStatus = "Помилка моделювання: ${error.message ?: "невідома"}"
            } finally {
                isAdvancing = false
            }
        }
    }

    fun requestTurn(months: Int = TURN_MONTHS) {
        if (isAdvancing || turnDecision != null || showDevelopmentDialog) return
        if (turnReport != null && !developmentAcknowledged) {
            showDevelopmentDialog = true
            saveStatus = "Спочатку прочитайте наслідки попереднього ходу"
            return
        }
        pendingTurnMonths = months
        turnDecision = composeTurnDecision()
        saveStatus = if (chronicleDecision() != null) {
            "Оберіть відповідь на подію та напрями епохи — потім одразу мине 100 років"
        } else {
            "Оберіть 1–3 напрями епохи — після підтвердження світ одразу проживе 100 років"
        }
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
        workspace = historyTimeline.create(created.session.state, created.people, created.economy, created.evolution)
        selectedCivilizationId = created.session.state.civilizations.first().id
        resetCharacterSelection(selectedCivilizationId, created.people)
        characterUndressed = false
        selectedPanel = GamePanel.WORLD
        showNewWorldDialog = false
        pendingTurnMonths = null
        turnDecision = null
        turnReport = null
        showDevelopmentDialog = false
        developmentAcknowledged = true
        GameplayTurnReportStore.clear()
        saveStatus = "Створено світ · ${setup.tribes.size} племені · seed ${setup.seed}"
    }

    fun interveneEvolution(kind: PlayerEvolutionInterventionEngine.Kind, settlementId: String) {
        if (isAdvancing) return
        val settlement = session.state.settlements.firstOrNull { it.id == settlementId }
        val civilizationId = settlement?.civilizationId ?: run {
            saveStatus = "Поселення для втручання більше не існує"
            return
        }
        val gate = GameplayLoop.evolutionGate(
            state = session.state,
            civilizationId = civilizationId,
            hasPendingDecision = false,
        )
        if (!gate.enabled) {
            saveStatus = gate.reasonUk ?: "Еволюційне втручання зараз недоступне"
            return
        }
        val result = runCatching {
            playerEvolutionEngine.apply(kind, evolutionState, session.state, settlementId)
        }.getOrElse { error ->
            saveStatus = error.message ?: "Еволюційне втручання недоступне"
            return
        }
        val paidWorld = GameplayLoop.chargeEvolutionCost(session.state, civilizationId)
        val nextWorld = paidWorld.copy(recentEvents = (paidWorld.recentEvents + result.event).takeLast(96))
        syncState(nextWorld, nextEvolution = result.state)
        saveStatus = when (kind) {
            PlayerEvolutionInterventionEngine.Kind.DIVERGE -> "Лінію відокремлено · витрачено ${EVOLUTION_ACTION_COST.toInt()} казни"
            PlayerEvolutionInterventionEngine.Kind.MUTATE -> "Створено структурно змінену лінію · витрачено ${EVOLUTION_ACTION_COST.toInt()} казни"
            PlayerEvolutionInterventionEngine.Kind.HYBRIDIZE -> "Створено гібридну лінію · витрачено ${EVOLUTION_ACTION_COST.toInt()} казни"
        }
    }

    fun activateWorkspaceState() {
        val branchState = workspace.activeState
        val nextPeople = workspace.activePeopleState ?: peopleEngine.initialize(branchState)
        val nextEconomy = workspace.activeEconomyState ?: EconomyEngine(session.world, session.resources).initialize(branchState)
        val nextEvolution = workspace.activeEvolutionState ?: EvolutionEngine(session.world).initialize(branchState)
        session = session.copy(state = branchState)
        peopleState = nextPeople
        economyState = nextEconomy
        evolutionState = nextEvolution
        val nextCivilizationId = if (branchState.civilizations.any { it.id == selectedCivilizationId }) {
            selectedCivilizationId
        } else branchState.civilizations.first().id
        selectedCivilizationId = nextCivilizationId
        resetCharacterSelection(nextCivilizationId, nextPeople)
    }

    fun saveGame() {
        if (isAdvancing) return
        saveStatus = runCatching {
            val syncedWorkspace = historyTimeline.syncActive(workspace, session.state, peopleState, economyState, evolutionState)
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
            val historyFile = context.getFileStreamPath(GAME_HISTORY_FILE)
            val loadedWorkspace = if (historyFile.isFile) {
                context.openFileInput(GAME_HISTORY_FILE).bufferedReader().use {
                    HistoryWorkspaceSnapshotV1.decode(it.readText())
                }
            } else null
            val loadedState = loadedWorkspace?.activeState
                ?: context.openFileInput(GAME_SAVE_FILE).bufferedReader().use { GameSnapshotV1.decode(it.readText()) }
            val loadedSession = restoreGameSession(loadedState, generator, hydrology, resourceGenerator)
            val loadedPeople = loadedWorkspace?.activePeopleState ?: peopleEngine.initialize(loadedSession.state)
            val loadedEconomy = loadedWorkspace?.activeEconomyState
                ?: EconomyEngine(loadedSession.world, loadedSession.resources).initialize(loadedSession.state)
            val loadedEvolution = loadedWorkspace?.activeEvolutionState ?: EvolutionEngine(loadedSession.world).initialize(loadedSession.state)
            session = loadedSession
            peopleState = loadedPeople
            economyState = loadedEconomy
            evolutionState = loadedEvolution
            workspace = if (loadedWorkspace != null) {
                historyTimeline.syncActive(loadedWorkspace, loadedSession.state, loadedPeople, loadedEconomy, loadedEvolution)
            } else {
                historyTimeline.create(loadedSession.state, loadedPeople, loadedEconomy, loadedEvolution)
            }
            selectedCivilizationId = selectedCivilizationId.takeIf { selected ->
                loadedSession.state.civilizations.any { it.id == selected }
            } ?: loadedSession.state.civilizations.first().id
            resetCharacterSelection(selectedCivilizationId, loadedPeople)
            worldSetup = WorldSetup.default(
                seed = loadedState.worldSeed,
                tribeCount = loadedState.civilizations.size.coerceIn(WorldSetup.MIN_TRIBES, WorldSetup.MAX_TRIBES),
            )
            characterUndressed = false
            pendingTurnMonths = null
            turnDecision = null
            turnReport = null
            showDevelopmentDialog = false
            developmentAcknowledged = true
            if (loadedWorkspace != null) "Світ і часові гілки завантажено" else "Старе збереження завантажено"
        }.getOrElse { "Помилка завантаження: ${it.message ?: "збереження не знайдено"}" }
    }

    val civilizations = session.state.civilizations
    val selectedCivilization = civilizations.firstOrNull { it.id == selectedCivilizationId } ?: civilizations.first()
    val selectedEconomy = economyState.economy(selectedCivilization.id)
    val time = clock.at(session.state.tick)
    val civilizationOrder = remember(civilizations) {
        civilizations.mapIndexed { index, civilization -> civilization.id to index }.toMap()
    }
    val civilizationNames = remember(civilizations) { civilizations.associate { it.id to it.name } }
    val settlementMarkers = remember(session.state.settlements, civilizationOrder, civilizationNames) {
        session.state.settlements.map { settlement ->
            SettlementMarker(
                x = settlement.x,
                y = settlement.y,
                population = settlement.population,
                civilizationIndex = civilizationOrder[settlement.civilizationId] ?: 0,
                name = settlement.name,
                civilizationName = civilizationNames[settlement.civilizationId].orEmpty(),
            )
        }
    }
    val territory = remember(session.state) { territoryResolver.resolve(session.world, session.state) }
    val mapConnections = remember(session.state.settlements, session.state.wars, economyState.routes) {
        val capitals = session.state.settlements
            .groupBy { it.civilizationId }
            .mapValues { (_, centers) -> centers.maxByOrNull { it.population } }
        buildList {
            val tradePairs = hashSetOf<String>()
            economyState.routes.sortedByDescending { it.value }.forEach tradeRoute@{ route ->
                if (tradePairs.size >= 18) return@tradeRoute
                val pair = listOf(route.exporterId, route.importerId).sorted()
                val pairKey = pair.joinToString("|")
                if (!tradePairs.add(pairKey)) return@tradeRoute
                val from = capitals[route.exporterId] ?: return@tradeRoute
                val to = capitals[route.importerId] ?: return@tradeRoute
                add(MapConnection(from.x, from.y, to.x, to.y, MapConnectionKind.TRADE))
            }
            session.state.wars.forEach warLink@{ war ->
                val from = capitals[war.civilizationA] ?: return@warLink
                val to = capitals[war.civilizationB] ?: return@warLink
                add(MapConnection(from.x, from.y, to.x, to.y, MapConnectionKind.WAR))
            }
        }
    }
    val worldPanelScroll = rememberScrollState()
    val personPanelScroll = rememberScrollState()
    val historyPanelScroll = rememberScrollState()
    val chroniclePanelScroll = rememberScrollState()
    val activePanelScroll = when (selectedPanel) {
        GamePanel.WORLD -> worldPanelScroll
        GamePanel.PERSON -> personPanelScroll
        GamePanel.HISTORY -> historyPanelScroll
        GamePanel.CHRONICLE -> chroniclePanelScroll
    }

    MaterialTheme(colorScheme = ChronosphereColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val worldMapHeight = (maxHeight * 0.40f).coerceIn(205.dp, 315.dp)
                val turnEnabled = !isAdvancing && turnDecision == null && !showDevelopmentDialog
                Column(modifier = Modifier.fillMaxSize()) {
                    ChronosphereTopBar(
                        year = time.year,
                        branchName = branchDisplayName(workspace.activeBranch.name),
                        onNewWorld = { if (!isAdvancing) showNewWorldDialog = true },
                    )
                    if (selectedPanel == GamePanel.WORLD) {
                        Box(modifier = Modifier.fillMaxWidth().height(worldMapHeight).padding(horizontal = 8.dp)) {
                            WorldMapView(
                                world = session.world,
                                rivers = session.rivers,
                                territoryOwners = territory,
                                connections = mapConnections,
                                settlements = settlementMarkers,
                                selectedCivilizationIndex = civilizationOrder[selectedCivilizationId],
                                onCivilizationSelected = if (isAdvancing) null else { index ->
                                    civilizations.getOrNull(index)?.let { selectCivilization(it.id) }
                                },
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                WorldMapSummary(
                                    totalPopulation = session.state.totalPopulation,
                                    settlements = session.state.settlements.size,
                                    civilizations = civilizations.size,
                                    wars = session.state.wars.size,
                                    tradeRoutes = economyState.routes.size,
                                    modifier = Modifier.weight(1f),
                                )
                                SelectedCivilizationBadge(
                                    civilizationName = selectedCivilization.name,
                                    eraName = selectedEconomy?.era?.displayNameUk ?: "Епоха формується",
                                )
                            }
                            MapGestureHint(
                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                            )
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                                color = Color(0xE60A1117),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.44f)),
                            ) {
                                Box(modifier = Modifier.padding(6.dp)) {
                                    TimeButton("Хід · 100 років", turnEnabled) { requestTurn(TURN_MONTHS) }
                                }
                            }
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        selectedCivilization.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${selectedEconomy?.era?.displayNameUk ?: "Епоха формується"} · ${time.year} рік",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                Button(
                                    onClick = { requestTurn(TURN_MONTHS) },
                                    enabled = turnEnabled,
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp),
                                ) {
                                    Text("Хід · 100 р.", maxLines = 1, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (isAdvancing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    GameTabs(selectedPanel = selectedPanel, enabled = !isAdvancing, onSelect = { selectedPanel = it })
                    Surface(modifier = Modifier.fillMaxWidth().weight(1f), color = Color.Transparent) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surface,
                                            MaterialTheme.colorScheme.background,
                                        ),
                                    ),
                                )
                                .verticalScroll(activePanelScroll)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            when (selectedPanel) {
                                GamePanel.WORLD -> WorldPlayPanel(
                                    civilization = selectedCivilization,
                                    civilizationCount = civilizations.size,
                                    economyState = economyState,
                                    peopleState = peopleState,
                                    evolutionState = evolutionState,
                                    session = session,
                                    pendingDecisionTitle = chronicleDecision()?.titleUk,
                                    isAdvancing = isAdvancing,
                                    onOpenChronicle = { selectedPanel = GamePanel.CHRONICLE },
                                    onNextCivilization = {
                                        val index = civilizations.indexOfFirst { it.id == selectedCivilization.id }.coerceAtLeast(0)
                                        selectCivilization(civilizations[(index + 1) % civilizations.size].id)
                                    },
                                    onEvolutionIntervene = ::interveneEvolution,
                                )
                                GamePanel.PERSON -> {
                                    val livingCharacters = peopleState.featuredPeople(selectedCivilization.id, session.state.tick)
                                        .sortedByDescending { it.prestige }
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
                                            selectedCivilization.cultureTags,
                                        ) {
                                            CharacterSceneFactory.resolve(
                                                person = selectedPerson,
                                                tick = session.state.tick,
                                                evolution = evolutionState,
                                                undressed = effectiveUndressed,
                                            ).let { resolved ->
                                                resolved.copy(
                                                    layerKeys = (resolved.layerKeys + selectedCivilization.cultureTags).distinct(),
                                                )
                                            }
                                        }
                                        val adultRequest = remember(selectedPerson.id, session.state.tick, peopleState, evolutionState) {
                                            CharacterSceneFactory.adultRequest(
                                                person = selectedPerson,
                                                tick = session.state.tick,
                                                people = peopleState,
                                                evolution = evolutionState,
                                            )
                                        }
                                        val scene = remember(baseScene, adultRequest, effectiveUndressed, adultSceneRuntime.isActive) {
                                            if (adultRequest != null && adultSceneRuntime.isActive) {
                                                adultSceneRuntime.resolveCharacterCard(adultRequest, effectiveUndressed)?.let { adultScene ->
                                                    adultScene.copy(layerKeys = (adultScene.layerKeys + baseScene.layerKeys).distinct())
                                                } ?: baseScene
                                            } else baseScene
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
                                            onToggleWardrobe = { if (age >= 18) characterUndressed = !effectiveUndressed },
                                            onAdultAction = { if (age >= 18) characterUndressed = true },
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
                                            historyTimeline.syncActive(workspace, session.state, peopleState, economyState, evolutionState),
                                            "Рік ${time.year}",
                                        )
                                        saveStatus = "Момент історії збережено"
                                    },
                                    onFork = {
                                        workspace = historyTimeline.fork(
                                            historyTimeline.syncActive(workspace, session.state, peopleState, economyState, evolutionState),
                                            "Альтернатива ${workspace.branches.size}",
                                        )
                                        activateWorkspaceState()
                                        saveStatus = "Створено альтернативну історію"
                                    },
                                    onRestore = {
                                        val hadCheckpoint = workspace.checkpoints.any { it.branchId == workspace.activeBranchId }
                                        workspace = historyTimeline.restoreLatestCheckpoint(workspace)
                                        activateWorkspaceState()
                                        saveStatus = if (hadCheckpoint) "Повернуто збережений момент" else "У цій гілці ще немає збереженого моменту"
                                    },
                                    onNextBranch = {
                                        if (workspace.branches.size > 1) {
                                            val currentIndex = workspace.branches.indexOfFirst { it.id == workspace.activeBranchId }.coerceAtLeast(0)
                                            val nextBranch = workspace.branches[(currentIndex + 1) % workspace.branches.size]
                                            workspace = historyTimeline.switchTo(
                                                historyTimeline.syncActive(workspace, session.state, peopleState, economyState, evolutionState),
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
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
        turnDecision?.let { decision ->
            TurnDecisionDialog(
                decision = decision,
                onDismiss = {
                    turnDecision = null
                    pendingTurnMonths = null
                    saveStatus = "Хід скасовано — світ не змінено"
                },
                onConfirm = { options ->
                    if (options.isNotEmpty()) {
                        if (EraTurnChoiceCatalog.isEraTurn(decision)) {
                            var nextWorld = session.state
                            options.forEach { option ->
                                nextWorld = EraTurnChoiceCatalog.applyLegacy(
                                    state = nextWorld,
                                    civilizationId = option.targetCivilizationId,
                                    choiceId = option.id,
                                )
                            }
                            syncState(nextWorld)
                        }
                        options.forEach(ChronicleDecisionMailbox::enqueue)
                        turnDecision = null
                        val months = pendingTurnMonths ?: TURN_MONTHS
                        pendingTurnMonths = null
                        saveStatus = "Рішення прийнято · моделюю наслідки одразу"
                        advanceMonths(months)
                    }
                },
            )
        }
        if (showDevelopmentDialog) {
            turnReport?.let { report ->
                TurnConsequenceDialog(
                    report = report,
                    onDismiss = {
                        showDevelopmentDialog = false
                        developmentAcknowledged = true
                        saveStatus = "Хід завершено. Можна починати наступне століття"
                    },
                )
            }
        }
    }
}
