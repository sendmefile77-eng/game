package com.sendmefile77.chronosphere

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun TurnDecisionDialog(
    decision: ChronicleDecision,
    onDismiss: () -> Unit,
    onConfirm: (List<ChronicleDecisionOption>) -> Unit,
) {
    val multi = EraTurnChoiceCatalog.isEraTurn(decision)
    val era = remember(decision) { EraExperience.eraFromDecision(decision) }
    val chapter = remember(era) { era?.let { EraExperience.chapter(it) } }
    var selectedIds by remember(decision.eventId) { mutableStateOf(emptySet<String>()) }
    val selected = decision.options.filter { it.id in selectedIds }
    val selectedEra = selected.filter(TurnChoiceComposer::isEraOption)
    val requiredEventSources = remember(decision) { TurnChoiceComposer.requiredHistoricalSources(decision) }
    val resolvedEventSources = selected.asSequence()
        .filterNot(TurnChoiceComposer::isEraOption)
        .map { it.sourceEventId }
        .toSet()
    val historicalReady = requiredEventSources.all { it in resolvedEventSources }

    fun toggle(option: ChronicleDecisionOption) {
        if (!multi) {
            onConfirm(listOf(option))
            return
        }
        selectedIds = TurnChoiceComposer.toggleSelection(decision, selectedIds, option.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = ChronosphereVisuals.DeepSpace,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(42.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(100.dp)),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (multi) "СТРАТЕГІЯ СТОЛІТТЯ" else "ІСТОРИЧНА РОЗВИЛКА",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.0.sp),
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            decision.titleUk,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    if (multi) {
                        StatusPill(
                            "${selectedEra.size}/${TurnChoiceComposer.MAX_ERA_CHOICES}",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                ) {
                    Text(
                        if (multi) "Одна дилема + 1–3 напрями. Потім світ проживе століття без додаткового підтвердження."
                        else "Це рішення одразу змінить подальший хід історії.",
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    decision.promptUk,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (multi && chapter != null) {
                    EraChapterCard(chapter)
                    Text(
                        "Відкриття накопичуються назавжди. Спосіб життя, суспільний курс і мобільність замінюють попередній вибір тієї ж сім’ї — тому держава справді набуває власної історичної траєкторії.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                var shownHistoricalHeader = false
                var shownEraHeader = false
                decision.options.forEach { option ->
                    val eraOption = TurnChoiceComposer.isEraOption(option)
                    if (!eraOption && !shownHistoricalHeader) {
                        DecisionSectionHeader(
                            title = "Дилема століття",
                            subtitle = "оберіть одну відповідь",
                            color = MaterialTheme.colorScheme.error,
                        )
                        shownHistoricalHeader = true
                    }
                    if (eraOption && !shownEraHeader) {
                        DecisionSectionHeader(
                            title = "Напрями епохи",
                            subtitle = "оберіть 1–${TurnChoiceComposer.MAX_ERA_CHOICES}",
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        shownEraHeader = true
                    }
                    DecisionOptionCard(
                        option = option,
                        selected = option.id in selectedIds,
                        historical = !eraOption,
                        onClick = { toggle(option) },
                    )
                }

                if (multi) {
                    StrategyFocusCard(selectedEra.size)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = ChronosphereVisuals.PanelSoft,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Напрямів ${selectedEra.size}/${TurnChoiceComposer.MAX_ERA_CHOICES}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                            )
                            if (requiredEventSources.isNotEmpty()) {
                                Text(
                                    if (historicalReady) "Дилему вирішено" else "Потрібна відповідь",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (historicalReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multi) {
                Button(
                    onClick = { onConfirm(EraStrategyBalance.apply(selected)) },
                    enabled = selectedEra.isNotEmpty() && historicalReady,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        "ПРОЖИТИ 100 РОКІВ",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Повернутися до світу")
            }
        },
    )
}

@Composable
private fun EraChapterCard(chapter: EraChapter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.30f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                chapter.subtitle.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Black,
            )
            Text(
                chapter.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                chapter.everyday,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "РИЗИК ДОБИ · ${chapter.danger}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
            )
            Text(
                "НАСТУПНИЙ ПОРІГ · ${chapter.horizon}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StrategyFocusCard(eraCount: Int) {
    val accent = when (eraCount) {
        1 -> MaterialTheme.colorScheme.primary
        2 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.075f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                EraStrategyBalance.title(eraCount).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.65.sp),
                color = accent,
                fontWeight = FontWeight.Black,
            )
            Text(
                EraStrategyBalance.detail(eraCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DecisionSectionHeader(title: String, subtitle: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(2.dp)
                .background(color.copy(alpha = 0.74f), RoundedCornerShape(100.dp)),
        )
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
            color = color,
            fontWeight = FontWeight.Black,
        )
        Text(
            "· $subtitle",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DecisionOptionCard(
    option: ChronicleDecisionOption,
    selected: Boolean,
    historical: Boolean,
    onClick: () -> Unit,
) {
    val accent = if (historical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent.copy(alpha = 0.15f) else ChronosphereVisuals.PanelSoft,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accent.copy(alpha = 0.88f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.34f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, accent.copy(alpha = if (selected) 0.90f else 0.35f)),
                ) {
                    Text(
                        if (selected) "✓" else "○",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (selected) Color(0xFF111318) else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    option.titleUk,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "НАСЛІДОК",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Black,
            )
            Text(
                option.effectUk,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "РИЗИК · ${option.riskUk}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
            )
        }
    }
}

@Composable
internal fun TurnConsequenceDialog(
    report: GameplayTurnReport,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = ChronosphereVisuals.DeepSpace,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    "ПІДСУМОК СТОЛІТТЯ · ${report.yearsAdvanced} РОКІВ",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    report.headlineUk,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                ) {
                    Text(
                        EraExperience.centuryOutcome(report),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        "Населення",
                        GameplayLoop.signedLong(report.populationDelta),
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.secondary,
                    )
                    MetricTile(
                        "Стабільність",
                        GameplayLoop.signedDouble(report.stabilityDelta),
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.primary,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricTile(
                        "Розвиток",
                        GameplayLoop.signedDouble(report.technologyDelta),
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.primary,
                    )
                    MetricTile(
                        "Казна",
                        GameplayLoop.signedDouble(report.treasuryDelta),
                        Modifier.weight(1f),
                        MaterialTheme.colorScheme.secondary,
                    )
                }
                Text(
                    "Їжа ${GameplayLoop.signedDouble(report.foodDelta)} · війни ${report.warsBefore} → ${report.warsAfter}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (report.highlights.isNotEmpty()) {
                    Text(
                        "ЩО ЗМІНИЛОСЯ",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.7.sp),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Black,
                    )
                    report.highlights.forEach { highlight ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = ChronosphereVisuals.PanelSoft,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
                        ) {
                            Text(
                                highlight,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("ПРОДОВЖИТИ ІСТОРІЮ", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
            }
        },
    )
}
