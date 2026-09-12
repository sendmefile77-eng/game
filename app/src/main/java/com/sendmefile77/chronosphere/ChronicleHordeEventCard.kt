package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.economy.EconomyState
import com.sendmefile77.chronosphere.horde.HordeAdultScenePromptFactory
import com.sendmefile77.chronosphere.horde.HordeChronicleEventPromptFactory
import com.sendmefile77.chronosphere.horde.HordeChronicleEventView
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.simulation.SimulationClock
import com.sendmefile77.chronosphere.simulation.SimulationEvent
import com.sendmefile77.chronosphere.textgen.ChronicleTextGenerator

@Composable
internal fun ChronicleHordeEventCard(
    events: List<SimulationEvent>,
    peopleState: PeopleState,
    economyState: EconomyState,
    clock: SimulationClock,
    textGenerator: ChronicleTextGenerator,
) {
    val event = remember(events) { HordeChronicleEventPromptFactory.latestSignificant(events) } ?: return
    val request = remember(event, peopleState, economyState) {
        if (event.code == "ADULT_SOCIAL_EVENT") {
            HordeAdultScenePromptFactory.createEvent(
                event = event,
                people = peopleState,
                economy = economyState,
            ) ?: HordeChronicleEventPromptFactory.create(event, peopleState, economyState)
        } else {
            HordeChronicleEventPromptFactory.create(event, peopleState, economyState)
        }
    }
    val eventTime = remember(event.tick) { clock.at(event.tick) }
    val narrative = remember(event) { textGenerator.narrative(event) }

    Text(
        "Останній важливий кадр · ${eventTime.year}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        narrative.title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        narrative.hook,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    HordeChronicleEventView(request = request)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                narrative.body,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Чому це важливо",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                narrative.significance,
                style = MaterialTheme.typography.bodyMedium,
            )
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
}
