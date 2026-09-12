package com.sendmefile77.chronosphere

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.economy.TechnologyEra
import com.sendmefile77.chronosphere.evolution.EvolutionState
import com.sendmefile77.chronosphere.people.NotablePerson
import com.sendmefile77.chronosphere.people.PeopleState
import com.sendmefile77.chronosphere.people.PersonRole
import com.sendmefile77.chronosphere.people.RelationshipKind
import com.sendmefile77.chronosphere.scene.ResolvedScene
import com.sendmefile77.chronosphere.scene.WardrobeState

@Composable
fun CharacterCardPanel(
    person: NotablePerson,
    tick: Long,
    people: PeopleState,
    evolution: EvolutionState,
    scene: ResolvedScene,
    technologyEra: TechnologyEra? = null,
    adultVisual: AdultVisualSceneDescriptor? = null,
    hasPreviousOrNext: Boolean,
    onNext: () -> Unit,
    onToggleWardrobe: () -> Unit,
    controlsEnabled: Boolean = true,
) {
    val age = person.ageYearsAt(tick)
    val dynasty = person.dynastyId?.let { dynastyId ->
        people.dynasties.firstOrNull { it.id == dynastyId }?.name
    }
    val descriptor = person.settlementId?.let(evolution::visualDescriptor)
    val lineage = descriptor?.lineageId?.let(evolution::lineage)
    val adultSceneRuntime = remember { AdultSceneRuntime.load() }
    val effectiveAdultVisual = remember(
        adultVisual,
        person.id,
        tick,
        people,
        evolution,
        scene.wardrobeState,
        adultSceneRuntime.hasStructuredVisuals,
    ) {
        adultVisual ?: if (
            scene.wardrobeState == WardrobeState.UNDRESSED &&
            age >= 18 &&
            adultSceneRuntime.hasStructuredVisuals
        ) {
            CharacterSceneFactory.adultRequest(
                person = person,
                tick = tick,
                people = people,
                evolution = evolution,
            )?.let { request ->
                adultSceneRuntime.resolveCharacterVisual(request, undressed = true)
            }
        } else {
            null
        }
    }
    val relationships = people.relationships
        .filter { it.involves(person.id) }
        .sortedByDescending { kotlin.math.abs(it.strength) }
        .take(6)
        .mapNotNull { relationship ->
            val otherId = if (relationship.personA == person.id) relationship.personB else relationship.personA
            val other = people.persons.firstOrNull { it.id == otherId } ?: return@mapNotNull null
            "${relationshipLabel(relationship.kind)} · ${other.name} · ${relationshipStrengthLabel(relationship.strength)}"
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${roleLabel(person.role)} · $age р.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${prestigeLabel(person.prestige)} · ${aptitudeLabel(person.aptitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (hasPreviousOrNext) {
                    OutlinedButton(onClick = onNext, enabled = controlsEnabled) { Text("Інша особа") }
                }
            }

            OfflineSceneView(
                scene = scene,
                characterKey = person.id,
                ageYears = age,
                visualTags = descriptor?.tags ?: emptySet(),
                visualNumeric = descriptor?.numeric ?: emptyMap(),
                technologyEra = technologyEra,
                adultVisual = effectiveAdultVisual,
            )

            if (dynasty != null) {
                Text(
                    "Династія · $dynasty",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (person.traits.isNotEmpty()) {
                Text("Характер", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    person.traits.sorted().joinToString(" · ") { traitLabel(it) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (lineage != null && descriptor != null) {
                val ancestry = descriptor.ancestry.entries
                    .sortedByDescending { it.value }
                    .take(4)
                    .joinToString(" · ") { (lineageId, share) ->
                        val label = evolution.lineage(lineageId)?.label ?: "невідома лінія"
                        "$label ${String.format("%.0f%%", share * 100.0)}"
                    }
                Text("Походження", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(
                    "${lineage.label} · ${rankLabel(lineage.rank.name)}" +
                        if (ancestry.isNotBlank()) " · $ancestry" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val covering = descriptor.bodyPlan.covering.name.lowercase()
                val morphology = buildString {
                    append("рук ${descriptor.bodyPlan.armPairs * 2} · ніг ${descriptor.bodyPlan.legPairs * 2} · очей ${descriptor.bodyPlan.eyeCount}")
                    if (descriptor.bodyPlan.hasTail) append(" · хвіст")
                    if (covering != "bare_skin") append(" · ${coveringLabel(covering)}")
                }
                Text(
                    "Морфологія · $morphology",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (relationships.isNotEmpty()) {
                Text("Зв’язки", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                relationships.forEach { relationship ->
                    Text("• $relationship", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (age >= 18) {
                Button(onClick = onToggleWardrobe, enabled = controlsEnabled) {
                    Text(if (scene.wardrobeState == WardrobeState.UNDRESSED) "Одягнути" else "Змінити вигляд")
                }
            }
        }
    }
}

private fun roleLabel(role: PersonRole): String = when (role) {
    PersonRole.RULER -> "Правитель"
    PersonRole.HEIR -> "Спадкоємець"
    PersonRole.DYNAST -> "Династ"
    PersonRole.GENERAL -> "Воєначальник"
    PersonRole.SCHOLAR -> "Дослідник"
    PersonRole.MERCHANT -> "Купець"
    PersonRole.CLERGY -> "Духовна особа"
    PersonRole.NOTABLE -> "Впливова особа"
}

private fun relationshipLabel(kind: RelationshipKind): String = when (kind) {
    RelationshipKind.PARTNER -> "партнерство"
    RelationshipKind.LOVER -> "близькі стосунки"
    RelationshipKind.PARENT_CHILD -> "родина"
    RelationshipKind.SIBLING -> "брат/сестра"
    RelationshipKind.RIVAL -> "суперництво"
    RelationshipKind.ALLY -> "союз"
    RelationshipKind.MENTOR -> "наставництво"
}

private fun relationshipStrengthLabel(strength: Double): String = when {
    strength <= -0.65 -> "ворожі"
    strength < -0.20 -> "напружені"
    strength < 0.20 -> "нейтральні"
    strength < 0.65 -> "міцні"
    else -> "дуже міцні"
}

private fun prestigeLabel(value: Double): String = when {
    value >= 0.80 -> "винятковий вплив"
    value >= 0.60 -> "великий вплив"
    value >= 0.40 -> "помітний вплив"
    else -> "обмежений вплив"
}

private fun aptitudeLabel(value: Double): String = when {
    value >= 0.80 -> "видатні здібності"
    value >= 0.60 -> "сильні здібності"
    value >= 0.40 -> "добрі здібності"
    else -> "звичайні здібності"
}

private fun traitLabel(trait: String): String = when (trait.lowercase()) {
    "ambitious" -> "амбітний"
    "martial" -> "войовничий"
    "scholarly" -> "допитливий"
    "pious" -> "набожний"
    "charismatic" -> "харизматичний"
    "mercantile" -> "підприємливий"
    "cautious" -> "обережний"
    "bold" -> "сміливий"
    "diplomatic" -> "дипломатичний"
    "ruthless" -> "безжальний"
    else -> trait.replace('_', ' ').replace('-', ' ').replaceFirstChar { it.uppercase() }
}

private fun rankLabel(rank: String): String = when (rank.lowercase()) {
    "population" -> "популяція"
    "morph" -> "морф"
    "subspecies" -> "підвид"
    "species" -> "вид"
    else -> rank.lowercase()
}

private fun coveringLabel(covering: String): String = when (covering.lowercase()) {
    "dense_hair" -> "густе волосся"
    "fine_fur" -> "шерсть"
    "scales" -> "луска"
    else -> covering.replace('_', ' ').lowercase()
}
