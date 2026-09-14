package com.sendmefile77.chronosphere

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sendmefile77.chronosphere.civilization.CivilizationEngine
import com.sendmefile77.chronosphere.civilization.LivingPlanetState
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.history.HistoryComparator
import com.sendmefile77.chronosphere.history.HistoryTimeline
import com.sendmefile77.chronosphere.history.HistoryWorkspace
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationClock
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldHydrology
import com.sendmefile77.chronosphere.worldgen.WorldResourceGenerator

@Composable
internal fun ChronosphereTopBar(year: Int, branchName: String, onNewWorld: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ChronosphereVisuals.DeepSpace,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.02f),
                        ),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    "ХРОНОСФЕРА",
                    style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.0.sp),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "$year рік",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("·", color = MaterialTheme.colorScheme.outline)
                    Text(
                        branchName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            TextButton(
                onClick = onNewWorld,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    "Новий світ",
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
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
        color = ChronosphereVisuals.DeepSpace.copy(alpha = 0.93f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "СВІТ · ${compactNumber(totalPopulation)}",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.35.sp),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
            )
            Text(
                "$settlements поселень · $civilizations держав · $wars війн · $tradeRoutes шляхів",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
        color = ChronosphereVisuals.DeepSpace.copy(alpha = 0.94f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)),
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.widthIn(min = 112.dp, max = 166.dp).padding(horizontal = 11.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "ОБРАНА ДЕРЖАВА",
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.55.sp),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Black,
            )
            Text(
                civilizationName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
            )
            Text(
                eraName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
internal fun MapGestureHint(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = ChronosphereVisuals.DeepSpace.copy(alpha = 0.88f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                "МАПА",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.65.sp),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Black,
            )
            Text(
                "щипок · 2× огляд",
                maxLines = 1,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun TimeButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = ChronosphereVisuals.PanelSoft,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp),
    ) {
        Text(
            text.uppercase(),
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.55.sp),
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
internal fun GameTabs(selectedPanel: GamePanel, enabled: Boolean, onSelect: (GamePanel) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        color = ChronosphereVisuals.DeepSpace,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            GameTab("Світ", GamePanel.WORLD, selectedPanel, enabled, onSelect)
            GameTab("Люди", GamePanel.PERSON, selectedPanel, enabled, onSelect)
            GameTab("Час", GamePanel.HISTORY, selectedPanel, enabled, onSelect)
            GameTab("Хроніка", GamePanel.CHRONICLE, selectedPanel, enabled, onSelect)
        }
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
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onSelect(panel) },
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.17f) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.64f)) else null,
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 5.dp)
                        .width(24.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(100.dp)),
                )
            }
        }
    }
}

@Composable
internal fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    MetricTile(label = label, value = value, modifier = modifier)
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.55.sp),
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Black,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun InterventionButton(label: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = ChronosphereSmallShape,
    ) { Text(label, maxLines = 1) }
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
    SectionHeader(
        title = "Машина часу",
        eyebrow = "Часова лінія",
    )
    PanelCard(accent = MaterialTheme.colorScheme.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        branchDisplayName(workspace.activeBranch.name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${workspace.branches.size} ліній · ${workspace.checkpoints.count { it.branchId == workspace.activeBranchId }} збережених моментів",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill("активна", color = MaterialTheme.colorScheme.secondary)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCheckpoint, enabled = !isAdvancing, modifier = Modifier.weight(1f), shape = ChronosphereSmallShape) {
                    Text("Зберегти момент")
                }
                OutlinedButton(onClick = onFork, enabled = !isAdvancing, modifier = Modifier.weight(1f), shape = ChronosphereSmallShape) {
                    Text("Нова гілка")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestore, enabled = !isAdvancing, modifier = Modifier.weight(1f), shape = ChronosphereSmallShape) {
                    Text("Повернутися")
                }
                OutlinedButton(
                    onClick = onNextBranch,
                    enabled = !isAdvancing && workspace.branches.size > 1,
                    modifier = Modifier.weight(1f),
                    shape = ChronosphereSmallShape,
                ) { Text("Інша гілка") }
            }
        }
    }

    val originalState = workspace.branches.firstOrNull { it.id == HistoryTimeline.ROOT_BRANCH_ID }?.state ?: workspace.activeState
    val divergence = HistoryComparator.compare(originalState, session.state)
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Відмінність від початкової історії", fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Населення", signedNumber(divergence.populationDelta), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
                MetricTile("Міста", signedNumber(divergence.settlementDelta), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
            }
            MetricTile(
                "Технологічний зсув",
                String.format("%+.3f", divergence.averageTechnologyDelta),
                Modifier.fillMaxWidth(),
                MaterialTheme.colorScheme.primary,
            )
        }
    }

    PanelCard(accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Збереження світу", fontWeight = FontWeight.SemiBold)
            Text(
                "Зберігаються активний світ і всі часові гілки.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = !isAdvancing, modifier = Modifier.weight(1f), shape = ChronosphereSmallShape) { Text("Зберегти") }
                OutlinedButton(onClick = onLoad, enabled = !isAdvancing, modifier = Modifier.weight(1f), shape = ChronosphereSmallShape) { Text("Завантажити") }
            }
        }
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
    var showFacts by remember(session.state.worldSeed) { mutableStateOf(false) }
    val names = session.state.civilizations.associate { it.id to it.name }

    SectionHeader(
        title = "Хроніка світу",
        eyebrow = "Жива історія",
        trailing = "${session.state.recentEvents.size} подій",
    )

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

    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Літопис фактів", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Технічна довідка, не основна історія.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { showFacts = !showFacts }) { Text(if (showFacts) "Сховати" else "Показати") }
            }
            if (showFacts) {
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
        }
    }
}

@Composable
internal fun EmptyPanel(text: String) {
    PanelCard {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
