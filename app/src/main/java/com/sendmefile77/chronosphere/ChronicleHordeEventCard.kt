package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.history.HistoricalChronicleNarrator
import com.sendmefile77.chronosphere.horde.HordeChronicleEventPromptFactory
import com.sendmefile77.chronosphere.horde.HordeChronicleEventView
import com.sendmefile77.chronosphere.llm.ChronicleLlmEnricher
import com.sendmefile77.chronosphere.llm.LlmChronicleEnrichment
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationClock
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator
import kotlinx.coroutines.delay

@Composable
internal fun ChronicleHordeEventCard(
    events: List<SimulationEvent>,
    peopleState: PeopleState,
    economyState: EconomyState,
    clock: SimulationClock,
    textGenerator: ChronicleTextGenerator,
    civilizationNames: Map<String, String> = emptyMap(),
) {
    val storyEra = remember(events, economyState) {
        val eraByCivilization = economyState.civilizations.associate { it.civilizationId to it.era }
        events.asReversed()
            .asSequence()
            .flatMap { event -> event.actorIds.asSequence() }
            .mapNotNull { actorId -> eraByCivilization[actorId] }
            .firstOrNull()
            ?: economyState.civilizations.maxByOrNull { it.era.ordinal }?.era
    }
    val story = remember(events, civilizationNames, storyEra) {
        ChronicleStoryComposer.compose(
            events = events,
            civilizationNames = civilizationNames,
            textGenerator = textGenerator,
            era = storyEra,
        )
    }
    val causalBridge = remember(events, civilizationNames) {
        HistoricalChronicleNarrator.fromEvents(events, civilizationNames)
    }
    if (story != null) {
        PanelCard(accent = MaterialTheme.colorScheme.primary) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill("ГОЛОВНА ЛІНІЯ", color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${story.beats.size} поворотів",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(story.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(story.lead, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                story.paragraphs.forEach { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyMedium)
                }
                if (story.beats.isNotEmpty()) {
                    Text(
                        "Ключові повороти",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    story.beats.forEach { beat ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            StatusPill(clock.at(beat.tick).year.toString(), color = MaterialTheme.colorScheme.secondary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(beat.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    beat.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (causalBridge != null) {
        PanelCard(accent = MaterialTheme.colorScheme.secondary) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill("ПРИЧИНИ", color = MaterialTheme.colorScheme.secondary)
                    Text(causalBridge.titleUk, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(causalBridge.bodyUk, style = MaterialTheme.typography.bodyMedium)
                causalBridge.tracesUk.forEach { trace ->
                    Text(
                        "• $trace",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    val event = remember(events) { HordeChronicleEventPromptFactory.latestSignificant(events) } ?: return
    val request = remember(event, peopleState, economyState) {
        HordeChronicleEventPromptFactory.create(event, peopleState, economyState)
    }
    val eventTime = remember(event.tick) { clock.at(event.tick) }
    val baseNarrative = remember(event, storyEra) {
        ChroniclePresentation.narrative(event, textGenerator, storyEra)
    }
    val baseDecision = remember(events, peopleState, economyState) {
        ChronicleDecisionCatalog.latestUnresolved(events, peopleState, economyState)
    }
    val decisionForLlm = baseDecision?.takeIf { it.eventId == event.id }
    val economyCivilizationIds = remember(economyState) { economyState.civilizations.mapTo(hashSetOf()) { it.civilizationId } }
    val eventCivilizationIds = remember(event, economyCivilizationIds) {
        event.actorIds.filter { it in economyCivilizationIds }.distinct()
    }
    val galleryCapture = remember(event.id, event.tick, eventCivilizationIds, peopleState.worldSeed, baseNarrative.title) {
        GalleryCapture(
            worldSeed = peopleState.worldSeed,
            civilizationIds = eventCivilizationIds,
            kind = GalleryImageKind.CHRONICLE,
            subject = baseNarrative.title,
            tick = event.tick,
        )
    }

    var llmEnrichment by remember(event.id, decisionForLlm?.eventId) {
        mutableStateOf<LlmChronicleEnrichment?>(null)
    }
    var llmWorking by remember(event.id, decisionForLlm?.eventId) { mutableStateOf(false) }
    var llmAttempted by remember(event.id, decisionForLlm?.eventId) { mutableStateOf(false) }

    LaunchedEffect(event.id, decisionForLlm?.eventId, request.cacheKey) {
        llmEnrichment = null
        llmWorking = true
        llmAttempted = false
        delay(180L)
        llmEnrichment = ChronicleLlmEnricher.enrich(
            event = event,
            recentEvents = events,
            people = peopleState,
            economy = economyState,
            baseNarrative = baseNarrative,
            baseDecision = decisionForLlm,
        )
        llmAttempted = true
        llmWorking = false
    }

    val narrative = llmEnrichment?.narrative ?: baseNarrative
    val decision = if (decisionForLlm != null) {
        llmEnrichment?.decision ?: baseDecision
    } else {
        baseDecision
    }

    SectionHeader(
        title = narrative.title,
        eyebrow = "Останній важливий кадр",
        trailing = eventTime.year.toString(),
    )
    Text(
        narrative.hook,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    when {
        llmEnrichment != null -> StatusPill(
            "Qwen · ${llmEnrichment!!.model} · ${String.format("%.1f", llmEnrichment!!.elapsedMs / 1000.0)} с",
            color = MaterialTheme.colorScheme.secondary,
        )
        llmWorking -> StatusPill("Qwen пише історію…", color = MaterialTheme.colorScheme.secondary)
        llmAttempted -> StatusPill("Вбудований текст · Tellama не відповіла", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    HordeChronicleEventView(request = request, galleryCapture = galleryCapture)

    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(narrative.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Чому це важливо",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(narrative.significance, style = MaterialTheme.typography.bodyMedium)
            if (narrative.changes.isNotEmpty()) {
                Text(
                    "Що змінилося",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
                narrative.changes.forEach { change ->
                    Text(
                        "• $change",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (decision != null) {
        PanelCard(accent = MaterialTheme.colorScheme.primary) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Майбутня розвилка", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    StatusPill("на наступний хід", color = MaterialTheme.colorScheme.primary)
                }
                Text(decision.titleUk, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    decision.promptUk,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Це попередній перегляд. Натисніть «Хід»: ця розвилка з’явиться разом із напрямами епохи, а «Прожити 100 років» одразу застосує вибір і запустить симуляцію.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                decision.options.forEach { option ->
                    PanelCard {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(option.titleUk, fontWeight = FontWeight.SemiBold)
                            Text("Наслідок · ${option.effectUk}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Ризик · ${option.riskUk}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
