package com.sendmefile77.chronosphere

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    Text(
        "Останній важливий кадр · ${eventTime.year}",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    HordeChronicleEventView(request = request)
    Text(
        textGenerator.describe(event),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
