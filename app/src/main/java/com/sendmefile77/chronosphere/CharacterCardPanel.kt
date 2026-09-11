package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    hasPreviousOrNext: Boolean,
    onNext: () -> Unit,
    onToggleWardrobe: () -> Unit,
) {
    val age = person.ageYearsAt(tick)
    val dynasty = person.dynastyId?.let { dynastyId ->
        people.dynasties.firstOrNull { it.id == dynastyId }?.name
    }
    val descriptor = person.settlementId?.let(evolution::visualDescriptor)
    val lineage = descriptor?.lineageId?.let(evolution::lineage)
    val relationships = people.relationships
        .filter { it.involves(person.id) }
        .take(4)
        .mapNotNull { relationship ->
            val otherId = if (relationship.personA == person.id) relationship.personB else relationship.personA
            val other = people.persons.firstOrNull { it.id == otherId } ?: return@mapNotNull null
            "${relationshipLabel(relationship.kind)}: ${other.name}"
        }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(person.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${roleLabel(person.role)} · $age р. · престиж ${String.format("%.2f", person.prestige)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (hasPreviousOrNext) {
                    OutlinedButton(onClick = onNext) { Text("Наступний") }
                }
            }

            OfflineSceneView(scene = scene)

            Text(
                "Династія: ${dynasty ?: "—"} · поселення: ${person.settlementId ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )

            if (lineage != null && descriptor != null) {
                val ancestry = descriptor.ancestry.entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .joinToString(" · ") { (lineageId, share) ->
                        val label = evolution.lineage(lineageId)?.label ?: lineageId
                        "$label ${String.format("%.0f%%", share * 100.0)}"
                    }
                Text(
                    "Походження: ${lineage.label} · ${rankLabel(lineage.rank.name)}" +
                        if (ancestry.isNotBlank()) " · $ancestry" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    buildString {
                        append("Морфологія: рук ${descriptor.bodyPlan.armPairs * 2}, ніг ${descriptor.bodyPlan.legPairs * 2}, очей ${descriptor.bodyPlan.eyeCount}")
                        if (descriptor.bodyPlan.hasTail) append(" · хвіст")
                        descriptor.bodyPlan.covering.takeIf { it.isNotBlank() && it != "skin" }?.let { append(" · ${coveringLabel(it)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (relationships.isNotEmpty()) {
                Text("Зв’язки: ${relationships.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
            }

            if (age >= 18) {
                Button(onClick = onToggleWardrobe) {
                    Text(if (scene.wardrobeState == WardrobeState.UNDRESSED) "Одягнути" else "Роздягнути")
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
    PersonRole.CLERGY -> "Духовенство"
    PersonRole.NOTABLE -> "Впливова особа"
}

private fun relationshipLabel(kind: RelationshipKind): String = when (kind) {
    RelationshipKind.PARTNER -> "партнер"
    RelationshipKind.LOVER -> "коханець/коханка"
    RelationshipKind.PARENT_CHILD -> "батьки/діти"
    RelationshipKind.SIBLING -> "брат/сестра"
    RelationshipKind.RIVAL -> "суперник"
    RelationshipKind.ALLY -> "союзник"
    RelationshipKind.MENTOR -> "наставник"
}

private fun rankLabel(rank: String): String = when (rank.lowercase()) {
    "baseline_human" -> "людська лінія"
    "morph" -> "морф"
    "subspecies" -> "підвид"
    "species" -> "вид"
    else -> rank.lowercase()
}

private fun coveringLabel(covering: String): String = when (covering.lowercase()) {
    "scales" -> "луска"
    "fur" -> "шерсть"
    "feathers" -> "пір’я"
    else -> covering.lowercase()
}
