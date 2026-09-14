package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
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

/**
 * Character screen: the person and their scene come first. Biography and diagnostics are secondary.
 * Adult actions remain a separate runtime layer, but are surfaced directly for adult characters.
 */
@Composable
fun CharacterCardPanel(
    person: NotablePerson,
    tick: Long,
    people: PeopleState,
    evolution: EvolutionState,
    scene: ResolvedScene,
    technologyEra: TechnologyEra? = null,
    civilizationVisualTags: Set<String> = emptySet(),
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
    val resolvedActionSequence = maxOf(adultActionSequence, localActionSequence)
    val displayScene = scene
    val dynasty = person.dynastyId?.let { dynastyId -> people.dynasties.firstOrNull { it.id == dynastyId }?.name }
    val descriptor = person.settlementId?.let(evolution::visualDescriptor)
    val lineage = descriptor?.lineageId?.let(evolution::lineage)
    val mergedVisualTags = remember(descriptor?.tags, civilizationVisualTags) {
        (descriptor?.tags.orEmpty() + civilizationVisualTags).toSet()
    }
    val adultSceneRuntime = remember { AdultSceneRuntime.load() }
    val effectiveAdultVisual = remember(
        adultVisual,
        person.id,
        tick,
        people,
        evolution,
        displayScene.wardrobeState,
        adultSceneRuntime.hasStructuredVisuals,
    ) {
        adultVisual ?: if (
            displayScene.wardrobeState == WardrobeState.UNDRESSED &&
            age >= 18 &&
            adultSceneRuntime.hasStructuredVisuals
        ) {
            CharacterSceneFactory.adultRequest(
                person = person,
                tick = tick,
                people = people,
                evolution = evolution,
            )?.let { request -> adultSceneRuntime.resolveCharacterVisual(request, undressed = true) }
        } else null
    }
    val livingProfile = people.profile(person.civilizationId)
    val livingNorms = AdultIntimateNorms.resolve(
        era = technologyEra,
        profile = livingProfile,
        tags = livingProfile?.tags.orEmpty() + civilizationVisualTags,
        role = person.role,
    )
    val actionPlan = remember(person.id, tick, people, resolvedActionSequence, age, chosenActionType, technologyEra, livingProfile) {
        if (age >= 18 && resolvedActionSequence > 0) {
            AdultActionPlanner.plan(
                person = person,
                tick = tick,
                people = people,
                sequence = resolvedActionSequence,
                preferredType = chosenActionType,
                technologyEra = technologyEra,
                profile = livingProfile,
                cultureTags = livingProfile?.tags.orEmpty() + civilizationVisualTags,
            )
        } else null
    }
    val visibleActionPlan = actionPlan.takeIf {
        age >= 18 && displayScene.wardrobeState == WardrobeState.UNDRESSED
    }
    val relationships = people.relationships
        .filter { it.involves(person.id) }
        .sortedByDescending { kotlin.math.abs(it.strength) }
        .take(4)
        .mapNotNull { relationship ->
            val otherId = if (relationship.personA == person.id) relationship.personB else relationship.personA
            val other = people.persons.firstOrNull { it.id == otherId } ?: return@mapNotNull null
            "${relationshipLabel(relationship.kind)} · ${other.name} · ${relationshipStrengthLabel(relationship.strength)}"
        }
    val galleryCapture = remember(
        person.id,
        person.civilizationId,
        people.worldSeed,
        visibleActionPlan?.cacheToken,
        displayScene.wardrobeState,
        technologyEra,
        mergedVisualTags,
    ) {
        GalleryCapture(
            worldSeed = people.worldSeed,
            civilizationIds = listOf(person.civilizationId),
            kind = GalleryImageKind.PERSON,
            subject = person.name,
            tick = tick,
        )
    }

    val ancestry = if (lineage != null && descriptor != null) {
        descriptor.ancestry.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(" · ") { (lineageId, share) ->
                val label = evolution.lineage(lineageId)?.label ?: "невідома лінія"
                "$label ${String.format("%.0f%%", share * 100.0)}"
            }
    } else ""
    val morphology = descriptor?.let {
        val covering = it.bodyPlan.covering.name.lowercase()
        buildString {
            append("рук ${it.bodyPlan.armPairs * 2} · ніг ${it.bodyPlan.legPairs * 2} · очей ${it.bodyPlan.eyeCount}")
            if (it.bodyPlan.hasTail) append(" · хвіст")
            if (covering != "bare_skin") append(" · ${coveringLabel(covering)}")
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("ПЕРСОНАЖ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black)
            Text(person.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusPill(roleLabel(person.role), color = MaterialTheme.colorScheme.primary)
                StatusPill("$age р.", color = MaterialTheme.colorScheme.secondary)
            }
        }
        if (hasPreviousOrNext) {
            OutlinedButton(onClick = onNext, enabled = controlsEnabled, shape = ChronosphereSmallShape) {
                Text("Інша")
            }
        }
    }

    OfflineSceneView(
        scene = displayScene,
        characterKey = person.id,
        ageYears = age,
        visualTags = mergedVisualTags,
        visualNumeric = descriptor?.numeric ?: emptyMap(),
        technologyEra = technologyEra,
        adultVisual = effectiveAdultVisual,
        actionPlan = visibleActionPlan,
        galleryCapture = galleryCapture,
        modifier = Modifier.fillMaxWidth().height(if (age >= 18) 400.dp else 260.dp),
    )

    if (age >= 18) {
        PanelCard(accent = MaterialTheme.colorScheme.secondary) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Інтимна сцена", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            visibleActionPlan?.let(::sceneContextLine)
                                ?: if (displayScene.wardrobeState == WardrobeState.UNDRESSED) livingNorms.setting else "Відкрийте дорослу сцену цього персонажа",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    visibleActionPlan?.let { StatusPill(actionCaption(it), color = MaterialTheme.colorScheme.secondary) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onToggleWardrobe,
                        enabled = controlsEnabled,
                        modifier = Modifier.weight(1f),
                        shape = ChronosphereSmallShape,
                    ) {
                        Text(if (displayScene.wardrobeState == WardrobeState.UNDRESSED) "Одягнути" else "Без одягу")
                    }
                    Button(
                        onClick = {
                            val nextType = visibleActionPlan?.type
                                ?: chosenActionType
                                ?: livingNorms.allowedActs.firstOrNull()
                                ?: AdultActionType.MASTURBATION
                            val stored = AdultActionSelectionStore.remember(person.id, nextType)
                            chosenActionType = stored.type
                            localActionSequence = stored.sequence
                            onAdultAction()
                        },
                        enabled = controlsEnabled,
                        modifier = Modifier.weight(1f),
                        shape = ChronosphereSmallShape,
                    ) {
                        Text("Інша варіація")
                    }
                }

                Text(
                    "Оберіть сцену",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Усі 18+ практики доступні вручну; звичаї епохи визначають контекст і те, що в цьому суспільстві вважають нормою або табу.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                adultActionMenuItems().chunked(2).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowItems.forEach { item ->
                            AdultActionQuickButton(
                                label = item.label,
                                selected = (visibleActionPlan?.type ?: chosenActionType) == item.type,
                                enabled = controlsEnabled,
                                modifier = Modifier.weight(1f),
                            ) {
                                val stored = AdultActionSelectionStore.remember(person.id, item.type)
                                chosenActionType = stored.type
                                localActionSequence = stored.sequence
                                onAdultAction()
                            }
                        }
                        if (rowItems.size == 1) {
                            Column(modifier = Modifier.weight(1f)) {}
                        }
                    }
                }
            }
        }
    }

    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("Вплив", prestigeLabel(person.prestige), Modifier.weight(1f), MaterialTheme.colorScheme.primary)
                MetricTile("Здібності", aptitudeLabel(person.aptitude), Modifier.weight(1f), MaterialTheme.colorScheme.secondary)
            }
            dynasty?.let { InfoLine("Династія", it) }
            if (person.traits.isNotEmpty()) {
                InfoLine("Характер", person.traits.sorted().joinToString(" · ") { traitLabel(it) })
            }
            if (lineage != null && descriptor != null) {
                InfoLine(
                    "Походження",
                    "${lineage.label} · ${rankLabel(lineage.rank.name)}" + if (ancestry.isNotBlank()) " · $ancestry" else "",
                )
                morphology?.let { InfoLine("Тіло", it) }
            }
            if (relationships.isNotEmpty()) {
                Text("Зв’язки", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                relationships.forEach { relationship ->
                    Text(relationship, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class AdultActionMenuItem(val type: AdultActionType, val label: String)

private fun adultActionMenuItems(): List<AdultActionMenuItem> = listOf(
    AdultActionMenuItem(AdultActionType.FOOTJOB, "Футджоб"),
    AdultActionMenuItem(AdultActionType.ORAL, "Орал"),
    AdultActionMenuItem(AdultActionType.VAGINAL, "Вагінал"),
    AdultActionMenuItem(AdultActionType.ANAL, "Анал"),
    AdultActionMenuItem(AdultActionType.BUKKAKE, "Буккаке"),
    AdultActionMenuItem(AdultActionType.MASTURBATION, "Соло"),
    AdultActionMenuItem(AdultActionType.BDSM, "BDSM"),
    AdultActionMenuItem(AdultActionType.FUTANARI_ORGASM, "Футанарі"),
)

@Composable
private fun AdultActionQuickButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = ChronosphereSmallShape,
        ) {
            Text(label, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            shape = ChronosphereSmallShape,
        ) {
            Text(label, maxLines = 1)
        }
    }
}

private fun actionCaption(plan: AdultActionPlan): String {
    val label = adultActionMenuItems().firstOrNull { it.type == plan.type }?.label
        ?: plan.type.name.lowercase().replace('_', ' ')
    return plan.partner?.name?.let { "$label · $it" } ?: label
}

private fun sceneContextLine(plan: AdultActionPlan): String {
    val who = plan.partner?.name ?: "наодинці"
    val bond = when (plan.bond) {
        RelationshipKind.PARTNER -> "партнерство"
        RelationshipKind.LOVER -> "близькість"
        RelationshipKind.ALLY -> "союз"
        else -> null
    }
    return listOfNotNull(who, bond, plan.setting.takeIf { it.isNotBlank() }?.substringBefore(';'))
        .joinToString(" · ")
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

private fun traitLabel(value: String): String = value.replace('_', ' ').replace('-', ' ')

private fun rankLabel(value: String): String = when (value.lowercase()) {
    "population" -> "популяція"
    "morph" -> "морф"
    "subspecies" -> "підвид"
    "species" -> "вид"
    else -> value.lowercase()
}

private fun coveringLabel(value: String): String = when (value) {
    "fur" -> "хутро"
    "feathers" -> "пір’я"
    "scales" -> "луска"
    "plates" -> "пластини"
    else -> value.replace('_', ' ')
}
