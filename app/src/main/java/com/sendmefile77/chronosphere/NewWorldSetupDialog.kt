package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun NewWorldSetupDialog(
    initial: WorldSetup,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onCreate: (WorldSetup) -> Unit,
) {
    var step by remember { mutableIntStateOf(0) }
    var seedText by remember(initial.seed) { mutableStateOf(initial.seed.toString()) }
    var spacing by remember { mutableStateOf(initial.startSpacing) }
    var selectedTribe by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val tribes = remember {
        mutableStateListOf<TribeSetup>().apply { addAll(initial.tribes) }
    }

    fun replaceTribe(index: Int, value: TribeSetup) {
        if (index in tribes.indices) tribes[index] = value
    }

    fun resizeTribes(count: Int) {
        val target = count.coerceIn(WorldSetup.MIN_TRIBES, WorldSetup.MAX_TRIBES)
        if (target > tribes.size) {
            val defaults = WorldSetup.default(seedText.toLongOrNull() ?: initial.seed, target).tribes
            while (tribes.size < target) tribes += defaults[tribes.size]
        } else {
            while (tribes.size > target) tribes.removeAt(tribes.lastIndex)
        }
        selectedTribe = selectedTribe.coerceIn(0, tribes.lastIndex)
    }

    fun randomize() {
        val seed = seedText.toLongOrNull() ?: initial.seed
        val count = tribes.size
        val defaults = WorldSetup.default(seed xor 0x5A17C3L, count).tribes
        tribes.indices.forEach { index ->
            val race = TribeRace.entries[stableIndex(seed, "race-$index", TribeRace.entries.size)]
            val sexPool = SexualFeature.entries
            val first = sexPool[stableIndex(seed, "sex-a-$index", sexPool.size)]
            var second = sexPool[stableIndex(seed, "sex-b-$index", sexPool.size)]
            if ((first == SexualFeature.MONOGAMY && second == SexualFeature.POLYGAMY) ||
                (first == SexualFeature.POLYGAMY && second == SexualFeature.MONOGAMY)
            ) second = SexualFeature.NUDITY
            val traitPool = TribeTrait.entries
            val traitA = traitPool[stableIndex(seed, "trait-a-$index", traitPool.size)]
            var traitB = traitPool[stableIndex(seed, "trait-b-$index", traitPool.size)]
            if (traitA == traitB) traitB = traitPool[(traitPool.indexOf(traitA) + 1) % traitPool.size]
            tribes[index] = defaults[index].copy(
                race = race,
                sexualFeatures = setOf(first, second),
                traits = setOf(traitA, traitB),
                weakness = TribeWeakness.entries[stableIndex(seed, "weak-$index", TribeWeakness.entries.size)],
            )
        }
        spacing = StartSpacing.entries[stableIndex(seed, "spacing", StartSpacing.entries.size)]
        error = null
    }

    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Новий світ", fontWeight = FontWeight.Bold)
                Text(
                    when (step) {
                        0 -> "1/4 · Світ"
                        1 -> "2/4 · Племена і біологія"
                        2 -> "3/4 · Сексуальна культура"
                        else -> "4/4 · Особливості і запуск"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    0 -> WorldStep(
                        seedText = seedText,
                        onSeedChange = { seedText = it.filter { c -> c == '-' || c.isDigit() } },
                        tribeCount = tribes.size,
                        onTribeCountChange = ::resizeTribes,
                        spacing = spacing,
                        onSpacingChange = { spacing = it },
                    )
                    1 -> TribeBiologyStep(
                        tribes = tribes,
                        selectedTribe = selectedTribe,
                        onSelectTribe = { selectedTribe = it },
                        onChange = { replaceTribe(selectedTribe, it) },
                    )
                    2 -> TribeSexualityStep(
                        tribes = tribes,
                        selectedTribe = selectedTribe,
                        onSelectTribe = { selectedTribe = it },
                        onChange = { replaceTribe(selectedTribe, it) },
                    )
                    else -> TribeTraitsStep(
                        tribes = tribes,
                        selectedTribe = selectedTribe,
                        onSelectTribe = { selectedTribe = it },
                        onChange = { replaceTribe(selectedTribe, it) },
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (step < 3) {
                Button(onClick = { step += 1; error = null }, enabled = enabled) { Text("Далі") }
            } else {
                Button(
                    onClick = {
                        val seed = seedText.toLongOrNull()
                        if (seed == null) {
                            error = "Seed має бути цілим числом"
                            return@Button
                        }
                        runCatching {
                            WorldSetup(seed = seed, startSpacing = spacing, tribes = tribes.toList())
                        }.onSuccess(onCreate).onFailure { throwable ->
                            error = throwable.message ?: "Перевірте налаштування племен"
                        }
                    },
                    enabled = enabled,
                ) { Text("Створити світ") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (step > 0) TextButton(onClick = { step -= 1; error = null }, enabled = enabled) { Text("Назад") }
                TextButton(onClick = ::randomize, enabled = enabled) { Text("Випадково") }
                TextButton(onClick = onDismiss, enabled = enabled) { Text("Скасувати") }
            }
        },
    )
}

@Composable
private fun WorldStep(
    seedText: String,
    onSeedChange: (String) -> Unit,
    tribeCount: Int,
    onTribeCountChange: (Int) -> Unit,
    spacing: StartSpacing,
    onSpacingChange: (StartSpacing) -> Unit,
) {
    Text("Світ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(
        value = seedText,
        onValueChange = onSeedChange,
        label = { Text("Seed") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Початкові племена: $tribeCount")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { onTribeCountChange(tribeCount - 1) },
            enabled = tribeCount > WorldSetup.MIN_TRIBES,
        ) { Text("−") }
        OutlinedButton(
            onClick = { onTribeCountChange(tribeCount + 1) },
            enabled = tribeCount < WorldSetup.MAX_TRIBES,
        ) { Text("+") }
    }
    Text("Відстань між стартами")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StartSpacing.entries.forEach { value ->
            FilterChip(
                selected = spacing == value,
                onClick = { onSpacingChange(value) },
                label = { Text(value.displayNameUk) },
            )
        }
    }
    Text(
        "Кількість племен визначає реальну кількість стартових держав. Відстань впливає на те, наскільки рано почнуться контакти, війни й змішування.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TribeBiologyStep(
    tribes: List<TribeSetup>,
    selectedTribe: Int,
    onSelectTribe: (Int) -> Unit,
    onChange: (TribeSetup) -> Unit,
) {
    TribeSelector(tribes, selectedTribe, onSelectTribe)
    val tribe = tribes[selectedTribe]
    OutlinedTextField(
        value = tribe.name,
        onValueChange = { value ->
            val next = value.take(24)
            if (next.isNotBlank()) onChange(tribe.copy(name = next))
        },
        label = { Text("Назва племені") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text("Стартова раса", style = MaterialTheme.typography.titleSmall)
    TribeRace.entries.forEach { race ->
        FilterChip(
            selected = tribe.race == race,
            onClick = { onChange(tribe.copy(race = race)) },
            label = { Text(race.displayNameUk) },
            modifier = Modifier.padding(end = 4.dp),
        )
    }
    Text(
        "Раса змінює справжній план тіла і морфологію стартової еволюційної лінії. Чотирирукі, хвостаті, хутряні та лускаті — не декоративні теги.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TribeSexualityStep(
    tribes: List<TribeSetup>,
    selectedTribe: Int,
    onSelectTribe: (Int) -> Unit,
    onChange: (TribeSetup) -> Unit,
) {
    TribeSelector(tribes, selectedTribe, onSelectTribe)
    val tribe = tribes[selectedTribe]
    Text("Домінуючі сексуальні особливості · ${tribe.sexualFeatures.size}/4", style = MaterialTheme.typography.titleSmall)
    SexualFeature.entries.forEach { feature ->
        val selected = feature in tribe.sexualFeatures
        FilterChip(
            selected = selected,
            onClick = {
                val next = tribe.sexualFeatures.toMutableSet()
                if (selected) {
                    if (next.size > 1) next.remove(feature)
                } else if (next.size < 4) {
                    if (feature == SexualFeature.MONOGAMY) next.remove(SexualFeature.POLYGAMY)
                    if (feature == SexualFeature.POLYGAMY) next.remove(SexualFeature.MONOGAMY)
                    next.add(feature)
                }
                onChange(tribe.copy(sexualFeatures = next))
            },
            label = { Text(feature.displayNameUk) },
            modifier = Modifier.padding(end = 4.dp),
        )
    }
    Text(
        "Ці параметри змінюють приватність, відкритість тіла, ревнощі, парність, родючість, статус і теги, які отримує adult-модуль при виборі подій та сцен.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TribeTraitsStep(
    tribes: List<TribeSetup>,
    selectedTribe: Int,
    onSelectTribe: (Int) -> Unit,
    onChange: (TribeSetup) -> Unit,
) {
    TribeSelector(tribes, selectedTribe, onSelectTribe)
    val tribe = tribes[selectedTribe]
    Text("Сильні риси · ${tribe.traits.size}/2", style = MaterialTheme.typography.titleSmall)
    TribeTrait.entries.forEach { trait ->
        val selected = trait in tribe.traits
        FilterChip(
            selected = selected,
            onClick = {
                val next = tribe.traits.toMutableSet()
                if (selected) {
                    if (next.size > 1) next.remove(trait)
                } else if (next.size < 2) next.add(trait)
                onChange(tribe.copy(traits = next))
            },
            label = { Text(trait.displayNameUk) },
            modifier = Modifier.padding(end = 4.dp),
        )
    }
    Text("Слабкість", style = MaterialTheme.typography.titleSmall)
    TribeWeakness.entries.forEach { weakness ->
        FilterChip(
            selected = tribe.weakness == weakness,
            onClick = { onChange(tribe.copy(weakness = weakness)) },
            label = { Text(weakness.displayNameUk) },
            modifier = Modifier.padding(end = 4.dp),
        )
    }
    Text("Підсумок", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    tribes.forEachIndexed { index, value ->
        Text(
            "${index + 1}. ${value.name} · ${value.race.displayNameUk} · " +
                value.sexualFeatures.joinToString { it.displayNameUk } + " · " +
                value.traits.joinToString { it.displayNameUk } + " · слабкість: ${value.weakness.displayNameUk}",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TribeSelector(
    tribes: List<TribeSetup>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Text("Плем’я ${selected + 1} з ${tribes.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tribes.forEachIndexed { index, tribe ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text("${index + 1}. ${tribe.name}") },
            )
        }
    }
}

private fun stableIndex(seed: Long, key: String, bound: Int): Int {
    require(bound > 0)
    var hash = seed xor -3750763034362895579L
    key.forEach { char -> hash = (hash xor char.code.toLong()) * 1099511628211L }
    return ((hash xor (hash ushr 32)) and Long.MAX_VALUE).rem(bound.toLong()).toInt()
}
