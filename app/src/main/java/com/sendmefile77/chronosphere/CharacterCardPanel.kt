package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    onToggleWardrobe: () -> Unit = {},
    adultActionSequence: Int = 0,
    onAdultAction: () -> Unit = onToggleWardrobe,
    controlsEnabled: Boolean = true,
) {
    val age = person.ageYearsAt(tick)
    val storedAction = remember(person.id) { AdultActionSelectionStore.get(person.id) }
    var localActionSequence by remember(person.id) { mutableStateOf(storedAction?.sequence ?: 0) }
    var chosenActionType by remember(person.id) { mutableStateOf(storedAction?.type) }
    var actionMenuOpen by remember(person.id) { mutableStateOf(false) }
    val resolvedActionSequence = maxOf(adultActionSequence, localActionSequence)
    val displayScene = if (age >= 18) scene.copy(wardrobeState = WardrobeState.UNDRESSED) else scene
    val dynasty = person.dynastyId?.let { dynastyId -> people.dynasties.firstOrNull { it.id == dynastyId }?.name }
    val descriptor = person.settlementId?.let(evolution::visualDescriptor)
    val lineage = descriptor?.lineageId?.let(evolution::lineage)
    val adultSceneRuntime = remember { AdultSceneRuntime.load() }
    val effectiveAdultVisual = remember(adultVisual, person.id, tick, people, evolution, displayScene.wardrobeState, adultSceneRuntime.hasStructuredVisuals) {
        adultVisual ?: if (displayScene.wardrobeState == WardrobeState.UNDRESSED && age >= 18 && adultSceneRuntime.hasStructuredVisuals) {
            CharacterSceneFactory.adultRequest(person = person, tick = tick, people = people, evolution = evolution)?.let { request -> adultSceneRuntime.resolveCharacterVisual(request, undressed = true) }
        } else null
    }
    val actionPlan = remember(person.id, tick, people, resolvedActionSequence, age, chosenActionType) {
        if (age >= 18 && resolvedActionSequence > 0) {
            AdultActionPlanner.plan(person = person, tick = tick, people = people, sequence = resolvedActionSequence, preferredType = chosenActionType)
        } else null
    }
    val relationships = people.relationships.filter { it.involves(person.id) }.sortedByDescending { kotlin.math.abs(it.strength) }.take(6).mapNotNull { relationship ->
        val otherId = if (relationship.personA == person.id) relationship.personB else relationship.personA
        val other = people.persons.firstOrNull { it.id == otherId } ?: return@mapNotNull null
        "${relationshipLabel(relationship.kind)} · ${other.name} · ${relationshipStrengthLabel(relationship.strength)}"
    }
    val galleryCapture = remember(person.id, person.civilizationId, people.worldSeed, actionPlan?.cacheToken, technologyEra) {
        GalleryCapture(worldSeed = people.worldSeed, civilizationIds = listOf(person.civilizationId), kind = GalleryImageKind.PERSON, subject = person.name, tick = tick)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ВИЗНАЧНА ОСОБА", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(person.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(roleLabel(person.role), color = MaterialTheme.colorScheme.primary)
                StatusPill("$age р.", color = MaterialTheme.colorScheme.secondary)
            }
        }
        if (hasPreviousOrNext) {
            OutlinedButton(onClick = onNext, enabled = controlsEnabled, shape = ChronosphereSmallShape) { Text("Інша") }
        }
    }

    OfflineSceneView(scene = displayScene, characterKey = person.id, ageYears = age, visualTags = descriptor?.tags ?: emptySet(), visualNumeric = descriptor?.numeric ?: emptyMap(), technologyEra = technologyEra, adultVisual = effectiveAdultVisual, actionPlan = actionPlan, galleryCapture = galleryCapture, modifier = Modifier.fillMaxWidth().height(if (age >= 18) 400.dp else 240.dp))

    PanelCard(accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Профіль", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Вплив", prestigeLabel(person.prestige), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                MetricTile("Здібності", aptitudeLabel(person.aptitude), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
            }
            dynasty?.let { InfoLine("Династія", it) }
            if (person.traits.isNotEmpty()) {
                InfoLine("Характер", person.traits.sorted().joinToString(" · ") { traitLabel(it) })
            }
        }
    }

    if (lineage != null && descriptor != null) {
        val ancestry = descriptor.ancestry.entries.sortedByDescending { it.value }.take(4).joinToString(" · ") { (lineageId, share) ->
            val label = evolution.lineage(lineageId)?.label ?: "невідома лінія"
            "$label ${String.format("%.0f%%", share * 100.0)}"
        }
        val covering = descriptor.bodyPlan.covering.name.lowercase()
        val morphology = buildString {
            append("рук ${descriptor.bodyPlan.armPairs * 2} · ніг ${descriptor.bodyPlan.legPairs * 2} · очей ${descriptor.bodyPlan.eyeCount}")
            if (descriptor.bodyPlan.hasTail) append(" · хвіст")
            if (covering != "bare_skin") append(" · ${coveringLabel(covering)}")
        }
        PanelCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Біологія", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                InfoLine("Походження", "${lineage.label} · ${rankLabel(lineage.rank.name)}" + if (ancestry.isNotBlank()) " · $ancestry" else "")
                InfoLine("Морфологія", morphology)
            }
        }
    }

    if (relationships.isNotEmpty()) {
        PanelCard(accent = MaterialTheme.colorScheme.secondary) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Зв’язки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                relationships.forEach { relationship ->
                    Text(relationship, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (age >= 18) {
        PanelCard(accent = MaterialTheme.colorScheme.secondary) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Сцена персонажа", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Оберіть дію — кадр зберегається для цієї епохи, поки не натиснете інший варіант. Канонічний портрет не змінюється.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    Button(onClick = { actionMenuOpen = true }, enabled = controlsEnabled, modifier = Modifier.fillMaxWidth(), shape = ChronosphereSmallShape) { Text("Дія") }
                    DropdownMenu(expanded = actionMenuOpen, onDismissRequest = { actionMenuOpen = false }) {
                        adultActionMenuItems().forEach { item ->
                            DropdownMenuItem(text = { Text(item.label) }, onClick = {
                                val stored = AdultActionSelectionStore.remember(person.id, item.type)
                                chosenActionType = stored.type
                                localActionSequence = stored.sequence
                                onAdultAction()
                                actionMenuOpen = false
                            })
                        }
                    }
                }
                if (actionPlan != null) {
                    StatusPill(actionCaption(actionPlan), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

private data class AdultActionMenuItem(val type: AdultActionType, val label: String)

private fun adultActionMenuItems(): List<AdultActionMenuItem> = listOf(
    AdultActionMenuItem(AdultActionType.FOOTJOB, "Футджоб"),
    AdultActionMenuItem(AdultActionType.ORAL, "Мінет"),
    AdultActionMenuItem(AdultActionType.VAGINAL, "Вагінал"),
    AdultActionMenuItem(AdultActionType.ANAL, "Анал"),
    AdultActionMenuItem(AdultActionType.BUKKAKE, "Буккаке"),
    AdultActionMenuItem(AdultActionType.MASTURBATION, "Мастурбація"),
    AdultActionMenuItem(AdultActionType.BDSM, "BDSM"),
    AdultActionMenuItem(AdultActionType.FUTANARI_ORGASM, "Футанарі оргазм"),
)

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
    RelationshipKind.PARTNER -> "Партнерство"
    RelationshipKind.LOVER -> "Близькі стосунки"
    RelationshipKind.PARENT_CHILD -> "Родина"
    RelationshipKind.SIBLING -> "Брат/сестра"
    RelationshipKind.RIVAL -> "Суперництво"
    RelationshipKind.ALLY -> "Союз"
    RelationshipKind.MENTOR -> "Наставництво"
}

private fun relationshipStrengthLabel(strength: Double): String = when {
    strength <= -0.65 -> "ворожі"
    strength < -0.20 -> "напружені"
    strength < 0.20 -> "нейтральні"
    strength < 0.65 -> "міцні"
    else -> "дуже міцні"
}

private fun prestigeLabel(value: Double): String = when {
    value >= 0.80 -> "винятковий"
    value >= 0.60 -> "великий"
    value >= 0.40 -> "помітний"
    else -> "обмежений"
}

private fun aptitudeLabel(value: Double): String = when {
    value >= 0.80 -> "видатні"
    value >= 0.60 -> "сильні"
    value >= 0.40 -> "добрі"
    else -> "звичайні"
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

private fun actionCaption(plan: AdultActionPlan): String {
    val act = when (plan.type) {
        AdultActionType.FOOTJOB -> "футджоб"
        AdultActionType.ORAL -> "мінет"
        AdultActionType.VAGINAL -> "вагінальний секс"
        AdultActionType.ANAL -> "анал"
        AdultActionType.BUKKAKE -> "буккаке"
        AdultActionType.MASTURBATION -> "мастурбація"
        AdultActionType.BDSM -> "BDSM"
        AdultActionType.FUTANARI_ORGASM -> "футанарі оргазм"
    }
    return if (plan.partner == null) act else "$act · з ${plan.partner.name}"
}
