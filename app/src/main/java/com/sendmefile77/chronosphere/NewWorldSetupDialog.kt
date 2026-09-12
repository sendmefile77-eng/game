package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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

    fun submit() {
        val seed = seedText.toLongOrNull()
        if (seed == null) {
            error = "Seed має бути цілим числом"
            return
        }
        runCatching {
            WorldSetup(seed = seed, startSpacing = spacing, tribes = tribes.toList())
        }.onSuccess(onCreate).onFailure { throwable ->
            error = throwable.message ?: "Перевірте налаштування племен"
        }
    }

    Dialog(
        onDismissRequest = { if (enabled) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WizardHeader(step = step, tribeCount = tribes.size)

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
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
                        PanelCard(accent = MaterialTheme.colorScheme.error) {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, enabled = enabled) { Text("Скасувати") }
                    TextButton(onClick = ::randomize, enabled = enabled) { Text("Випадково") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step -= 1; error = null },
                            enabled = enabled,
                            modifier = Modifier.weight(1f),
                            shape = ChronosphereSmallShape,
                        ) { Text("Назад") }
                    }
                    Button(
                        onClick = {
                            if (step < 3) {
                                step += 1
                                error = null
                            } else {
                                submit()
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        shape = ChronosphereSmallShape,
                    ) { Text(if (step < 3) "Далі" else "Створити світ") }
                }
            }
        }
    }
}

@Composable
private fun WizardHeader(step: Int, tribeCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Новий світ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    when (step) {
                        0 -> "Світ і старт"
                        1 -> "Племена і біологія"
                        2 -> "Культура і близькість"
                        else -> "Риси і запуск"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill("$tribeCount плем.", color = MaterialTheme.colorScheme.secondary)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { index ->
                Surface(
                    modifier = Modifier.weight(1f).height(4.dp),
                    shape = RoundedCornerShape(100.dp),
                    color = if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ) {}
            }
        }
        Text(
            "Крок ${step + 1} з 4",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
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
    PanelCard(accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Початкові племена", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onTribeCountChange(tribeCount - 1) },
                    enabled = tribeCount > WorldSetup.MIN_TRIBES,
                    shape = ChronosphereSmallShape,
                ) { Text("−") }
                Text("$tribeCount", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                OutlinedButton(
                    onClick = { onTribeCountChange(tribeCount + 1) },
                    enabled = tribeCount < WorldSetup.MAX_TRIBES,
                    shape = ChronosphereSmallShape,
                ) { Text("+") }
            }
            Text(
                "Можна почати навіть з одного племені. Нові держави з’являтимуться вже з історії світу.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Розташування", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ChoiceGrid(StartSpacing.entries.toList()) { value ->
                FilterChip(
                    selected = spacing == value,
                    onClick = { onSpacingChange(value) },
                    label = { Text(value.displayNameUk) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "Відстань визначає, наскільки рано почнуться контакти, війни, торгівля та змішування.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Seed світу", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = seedText,
                onValueChange = onSeedChange,
                label = { Text("Seed") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = ChronosphereSmallShape,
            )
            Text(
                "Однаковий seed дає однакову базову карту.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    PanelCard(accent = MaterialTheme.colorScheme.primary) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = tribe.name,
                onValueChange = { value ->
                    val next = value.take(24)
                    if (next.isNotBlank()) onChange(tribe.copy(name = next))
                },
                label = { Text("Назва племені") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = ChronosphereSmallShape,
            )
            Text("Стартова раса", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ChoiceGrid(TribeRace.entries.toList()) { race ->
                FilterChip(
                    selected = tribe.race == race,
                    onClick = { onChange(tribe.copy(race = race)) },
                    label = { Text(race.displayNameUk) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "Раса змінює справжню морфологію і план тіла стартової еволюційної лінії.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    PanelCard(accent = MaterialTheme.colorScheme.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Культурні особливості", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusPill("${tribe.sexualFeatures.size}/4", color = MaterialTheme.colorScheme.secondary)
            }
            ChoiceGrid(SexualFeature.entries.toList()) { feature ->
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "Оберіть від 1 до 4 домінуючих норм. Вони впливають на соціальну модель і події дорослих персонажів.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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
    PanelCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Сильні риси", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusPill("${tribe.traits.size}/2", color = MaterialTheme.colorScheme.primary)
            }
            ChoiceGrid(TribeTrait.entries.toList()) { trait ->
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    PanelCard(accent = MaterialTheme.colorScheme.error) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Слабкість", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ChoiceGrid(TribeWeakness.entries.toList()) { weakness ->
                FilterChip(
                    selected = tribe.weakness == weakness,
                    onClick = { onChange(tribe.copy(weakness = weakness)) },
                    label = { Text(weakness.displayNameUk) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    Text("Підсумок світу", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    tribes.forEachIndexed { index, value ->
        PanelCard(accent = if (index == selectedTribe) MaterialTheme.colorScheme.primary else null) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(value.name, fontWeight = FontWeight.Bold)
                    StatusPill(value.race.displayNameUk, color = MaterialTheme.colorScheme.secondary)
                }
                Text(
                    value.sexualFeatures.joinToString(" · ") { it.displayNameUk },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    value.traits.joinToString(" · ") { it.displayNameUk } + " · слабкість: ${value.weakness.displayNameUk}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TribeSelector(
    tribes: List<TribeSetup>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Оберіть плем’я", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChoiceGrid(tribes.indices.toList()) { index ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text("${index + 1}. ${tribes[index].name}") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun <T> ChoiceGrid(
    items: List<T>,
    content: @Composable (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pair.forEach { item ->
                    Column(modifier = Modifier.weight(1f)) { content(item) }
                }
                if (pair.size == 1) {
                    Surface(modifier = Modifier.weight(1f), color = Color.Transparent) {}
                }
            }
        }
    }
}

private fun stableIndex(seed: Long, key: String, bound: Int): Int {
    require(bound > 0)
    var hash = seed xor -3750763034362895579L
    key.forEach { char -> hash = (hash xor char.code.toLong()) * 1099511628211L }
    return ((hash xor (hash ushr 32)) and Long.MAX_VALUE).rem(bound.toLong()).toInt()
}
