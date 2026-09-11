package com.sendmefile77.chronosphere.map

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.WorldMap

@Composable
fun WorldMapView(world: WorldMap, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cellW = size.width / world.width
        val cellH = size.height / world.height
        world.tiles.forEach { tile ->
            drawRect(
                color = tile.biome.color(),
                topLeft = Offset(tile.x * cellW, tile.y * cellH),
                size = Size(cellW + 0.5f, cellH + 0.5f),
            )
        }
    }
}

private fun Biome.color(): Color = when (this) {
    Biome.DEEP_OCEAN -> Color(0xFF102A43)
    Biome.OCEAN -> Color(0xFF1F5F8B)
    Biome.COAST -> Color(0xFFD9C98C)
    Biome.DESERT -> Color(0xFFD8B56A)
    Biome.STEPPE -> Color(0xFF9EAA63)
    Biome.GRASSLAND -> Color(0xFF6FA05B)
    Biome.FOREST -> Color(0xFF356B49)
    Biome.RAINFOREST -> Color(0xFF1F5638)
    Biome.TAIGA -> Color(0xFF496A59)
    Biome.TUNDRA -> Color(0xFF87998D)
    Biome.MOUNTAIN -> Color(0xFF77736B)
    Biome.ICE -> Color(0xFFE4F1F5)
}
