package com.sendmefile77.chronosphere

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.evolution.PlayerEvolutionInterventionEngine
import com.sendmefile77.chronosphere.history.HistoryComparator
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.history.HistoryWorkspace
import com.sendmefile77.chronosphere.history.InterventionKind
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationClock
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldHydrology
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator
import com.sendmefile77.chronosphere.simulation.WorldSeed

@Composable
internal fun ChronosphereTopBar(year: Int, branchName: String, onNewWorld: () -> Unit) {
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
internal fun WorldMapSummary(
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
internal fun SelectedCivilizationBadge(
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
internal fun TimeButton(text: String, enabled: Boolean, onClick: () -> Unit) {
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
internal fun GameTabs(selectedPanel: GamePanel, enabled: Boolean, onSelect: (GamePanel) -> Unit) {
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
internal fun RowScope.GameTab(
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
internal fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
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
internal fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun InterventionButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) { Text(label, maxLines = 1) }
}

@Composable
internal fun HistoryPanel(
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
internal fun ChroniclePanel(
    session: GameSession,
    peopleState: PeopleState,
    economyState: EconomyState,
    clock: SimulationClock,
    textGenerator: ChronicleTextGenerator,
) {
    Text("Хроніка світу", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    val names = session.state.civilizations.associate { it.id to it.name }

    // The story is the primary content now. Rankings and raw facts are context below it.
    ChronicleHordeEventCard(
        events = session.state.recentEvents,
        peopleState = peopleState,
        economyState = economyState,
        clock = clock,
        textGenerator = textGenerator,
        civilizationNames = names,
    )

    GeneratedImageGalleryPanel(
        worldSeed = session.state.worldSeed,
        civilizations = session.state.civilizations,
        clock = clock,
    )

    Text("Світ зараз", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    val leaders = session.state.civilizations.sortedByDescending { it.population }.take(5)
    leaders.forEachIndexed { index, civilization ->
        val leaderName = peopleState.ruler(civilization.id)?.name ?: "без відомого правителя"
        val eraName = economyState.economy(civilization.id)?.era?.displayNameUk ?: "невизначена епоха"
        Text(
            "${index + 1}. ${civilization.name} · ${compactNumber(civilization.population)} людей · $leaderName · $eraName",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (session.state.wars.isNotEmpty()) {
        Text("Активні війни", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        session.state.wars.take(5).forEach { war ->
            Text("${names[war.civilizationA] ?: "Невідома держава"} — ${names[war.civilizationB] ?: "Невідома держава"}")
        }
    }

    Text("Літопис фактів", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
    Text(
        "Короткий журнал лишається лише як довідка. Основний текст вище показує послідовність подій і наслідків.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val events = session.state.recentEvents.takeLast(8).reversed()
    if (events.isEmpty()) {
        Text("Світ ще не накопичив значущих подій", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        events.forEach { event ->
            val eventTime = clock.at(event.tick)
            val narrative = ChroniclePresentation.narrative(event, textGenerator)
            Text(
                "${eventTime.year} · ${narrative.title} — ${narrative.hook}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EmptyPanel(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun branchDisplayName(name: String): String = when (name.lowercase()) {
    "original timeline" -> "Основна лінія"
    else -> name.replace("timeline", "лінія", ignoreCase = true)
}

internal fun compactNumber(value: Long): String = when {
    kotlin.math.abs(value) >= 1_000_000_000L -> String.format("%.1f млрд", value / 1_000_000_000.0)
    kotlin.math.abs(value) >= 1_000_000L -> String.format("%.1f млн", value / 1_000_000.0)
    kotlin.math.abs(value) >= 1_000L -> String.format("%.1f тис.", value / 1_000.0)
    else -> value.toString()
}

internal fun compactNumber(value: Double): String = when {
    kotlin.math.abs(value) >= 1_000_000_000.0 -> String.format("%.1f млрд", value / 1_000_000_000.0)
    kotlin.math.abs(value) >= 1_000_000.0 -> String.format("%.1f млн", value / 1_000_000.0)
    kotlin.math.abs(value) >= 1_000.0 -> String.format("%.1f тис.", value / 1_000.0)
    else -> String.format("%.1f", value)
}

internal fun signedNumber(value: Long): String = if (value >= 0) "+$value" else value.toString()
internal fun signedNumber(value: Int): String = if (value >= 0) "+$value" else value.toString()

internal fun createGameSession(
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

internal fun restoreGameSession(
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