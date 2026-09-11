package com.sendmefile77.chronosphere

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.map.WorldMapView
import com.sendmefile77.chronosphere.simulation.WorldSeed
import com.sendmefile77.chronosphere.worldgen.WorldGenerator
import com.sendmefile77.chronosphere.worldgen.WorldMap

@androidx.compose.runtime.Composable
fun ChronosphereApp() {
    val generator = remember { WorldGenerator() }
    var seedText by remember { mutableStateOf("424242") }
    var world by remember { mutableStateOf<WorldMap?>(generator.generate(WorldSeed(424242L))) }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Chronosphere", style = MaterialTheme.typography.headlineMedium)
                Text("Deterministic world bootstrap — no LLM, no cloud runtime")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = seedText,
                        onValueChange = { seedText = it.filter { c -> c == '-' || c.isDigit() } },
                        label = { Text("Seed") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        val seed = seedText.toLongOrNull() ?: return@Button
                        world = generator.generate(WorldSeed(seed))
                    }) { Text("Generate") }
                }
                world?.let {
                    Text("Fingerprint: ${it.fingerprint}", style = MaterialTheme.typography.bodySmall)
                    Text("Land: ${it.landPercent}% · Ocean: ${100 - it.landPercent}%")
                    WorldMapView(world = it, modifier = Modifier.fillMaxWidth().weight(1f))
                }
            }
        }
    }
}
