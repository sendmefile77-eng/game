package com.sendmefile77.chronosphere

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

private enum class GamePanel {
    WORLD,
    PERSON,
    HISTORY,
    CHRONICLE,
}

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
    val peopleEngine = remember { PeopleEngine() }
    val adultModule = remember { AdultModuleRuntime.load() }
    val adultModuleActive = remember(adultModule) { AdultModuleRuntime.isActive(adultModule) }
    val adultSceneRuntime = remember { AdultSceneRuntime.load() }

    val initialSession = remember { createGameSession(424242L, generator, hydrology, resourceGenerator) }
    val initialPeople = remember(initialSession) { peopleEngine.initialize(initialSession.state) }
    val initialEconomy = remember(initialSession) {
        EconomyEngine(initialSession.world, initialSession.resources).initialize(initialSession.state)
    }
    val initialEvolution = remember(initialSession) {
        EvolutionEngine(initialSession.world).initialize(initialSession.state)
    }

    var seedText by remember { mutableStateOf("424242") }
    var showNewWorldDialog by remember { mutableStateOf(false) }
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
        mutableStateOf(initialPeople.ruler(civilizationId)?.id ?: initialPeople.livingPeople(civilizationId).firstOrNull()?.id)
    }
    var characterUndressed by remember { mutableStateOf(false) }
    var interventionSequence by remember { mutableStateOf(0L) }
    var selectedPanel by remember { mutableStateOf(GamePanel.WORLD) }
    var isAdvancing by remember { mutableStateOf(false) }
    var saveStatus by remember {
        mutableStateOf(if (adultModuleActive) "Локальний режим · розширений модуль активний" else "Локальний режим")
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
            saveStatus = "Seed має бути цілим числом"
            return
        }
        val created = createGameSession(seed, generator, hydrology, resourceGenerator)
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
        selectedPanel = GamePanel.WORLD
        showNewWorldDialog = false
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
                if (result.people.livingPeople(validCivilizationId).none { it.id == targetPersonId }) {
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
            seedText = loadedState.worldSeed.toString()
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
                val mapHeight = (maxHeight * 0.46f).coerceIn(220.dp, 360.dp)
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
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp)),
                        )

                        Surface(
                            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                            color = Color(0xD90A1117),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                                Text(
                                    "${compactNumber(session.state.totalPopulation)} людей · ${session.state.settlements.size} міст",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${civilizations.size} держав · ${session.state.wars.size} війн · ${economyState.routes.size} торгових шляхів",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                            color = Color(0xD90A1117),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                        ) {
                            Column(
                                modifier = Modifier.width(150.dp).padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.End,
                            ) {
                                Text(
                                    selectedCivilization.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    selectedEconomy?.era?.displayNameUk ?: "Епоха формується",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }

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

                    if (isAdvancing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

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
                                )

                                GamePanel.PERSON -> {
                                    val livingCharacters = peopleState.livingPeople(selectedCivilization.id)
                                        .sortedByDescending { it.prestige }
                                    val ruler = peopleState.ruler(selectedCivilization.id)
                                    val selectedPerson = livingCharacters.firstOrNull { it.id == selectedPersonId }
                                        ?: ruler
                                        ?: livingCharacters.firstOrNull()
                                    if (selectedPerson == null) {
                                        EmptyPanel("У цій державі зараз немає живих визначних осіб")
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
                                    peopleState = peopleState,
                                    economyState = economyState,
                                    evolutionState = evolutionState,
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
                                        saveStatus = if (hadCheckpoint) "Повернуто збережений момент" else "У цій гілці ще немає збереженого моменту"
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
            AlertDialog(
                onDismissRequest = { if (!isAdvancing) showNewWorldDialog = false },
                title = { Text("Новий світ") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Seed визначає світ. Однаковий seed створює той самий початковий світ.")
                        OutlinedTextField(
                            value = seedText,
                            onValueChange = { seedText = it.filter { character -> character == '-' || character.isDigit() } },
                            label = { Text("Seed") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = { newWorld() }, enabled = !isAdvancing) { Text("Створити") }
                },
                dismissButton = {
                    TextButton(onClick = { showNewWorldDialog = false }) { Text("Скасувати") }
                },
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
private fun TimeButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
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
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
        ),
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
    civilization: com.sendmefile77.chronosphere.civilization.Civilization,
    civilizationCount: Int,
    economyState: EconomyState,
    peopleState: PeopleState,
    evolutionState: EvolutionState,
    session: GameSession,
    isAdvancing: Boolean,
    onNextCivilization: () -> Unit,
    onIntervene: (InterventionKind) -> Unit,
) {
    val economy = economyState.economy(civilization.id)
    val ruler = peopleState.ruler(civilization.id)
    val profile = peopleState.profile(civilization.id)
    val representativeSettlement = session.state.settlements
        .filter { it.civilizationId == civilization.id }
        .maxByOrNull { it.population }
    val representativePopulation = representativeSettlement?.let { evolutionState.population(it.id) }
    val representativeLineage = representativePopulation?.let { evolutionState.lineage(it.lineageId) }

    Row(verticalAlignment = Alignment.CenterVertically) {
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCard("Населення", compactNumber(civilization.population), modifier = Modifier.weight(1f))
        MetricCard("Стабільність", qualityBand(civilization.stability), modifier = Modifier.weight(1f))
        MetricCard("Розвиток", qualityBand(civilization.technology), modifier = Modifier.weight(1f))
    }

    if (economy != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Казна", compactNumber(civilization.treasury), modifier = Modifier.weight(1f))
            MetricCard("Ресурси", shortageBand(economy.shortageIndex), modifier = Modifier.weight(1f))
            MetricCard("Торгівля", tradeBand(economy.tradeBalance), modifier = Modifier.weight(1f))
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
        val culture = profile.tags.sorted().take(6).joinToString(" · ")(::humanizeTag)
        InfoLine("Культура", culture.ifBlank { "Без виразної домінантної традиції" })
        InfoLine("Суспільство", tensionBand(profile.socialTension))
    }

    Text("Втручання у світ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InterventionButton("Допомога", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.HARVEST_AID) }
        InterventionButton("Посуха", Modifier.weight(1f), !isAdvancing) { onIntervene(InterventionKind.DROUGHT) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    workspace: com.sendmefile77.chronosphere.history.HistoryWorkspace,
    session: GameSession,
    peopleState: PeopleState,
    economyState: EconomyState,
    evolutionState: EvolutionState,
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCheckpoint, enabled = !isAdvancing, modifier = Modifier.weight(1f)) { Text("Зберегти момент") }
        OutlinedButton(onClick = onFork, enabled = !isAdvancing, modifier = Modifier.weight(1f)) { Text("Нова гілка") }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSave, enabled = !isAdvancing, modifier = Modifier.weight(1f)) { Text("Зберегти світ") }
        OutlinedButton(onClick = onLoad, enabled = !isAdvancing, modifier = Modifier.weight(1f)) { Text("Завантажити") }
    }

    // Keep references in this presentation function explicit: these layers are part of a history snapshot.
    Spacer(modifier = Modifier.height(0.dp))
    peopleState.tick
    economyState.tick
    evolutionState.tick
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
