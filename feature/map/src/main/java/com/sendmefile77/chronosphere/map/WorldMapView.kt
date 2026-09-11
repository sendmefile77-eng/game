package com.sendmefile77.chronosphere.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.TileCoord
import com.sendmefile77.chronosphere.worldgen.WorldMap
import kotlin.math.sqrt

data class SettlementMarker(val x: Int, val y: Int, val population: Long, val civilizationIndex: Int)

private val civilizationPalette = listOf(
    Color(0xFFFFD166), Color(0xFFEF476F), Color(0xFF06D6A0), Color(0xFF8ECAE6),
    Color(0xFFC77DFF), Color(0xFFFF9F1C), Color(0xFF90BE6D), Color(0xFFF4A261),
    Color(0xFF43AA8B), Color(0xFF577590), Color(0xFFF94144), Color(0xFFB8DE6F),
)

@Composable
fun WorldMapView(
    world: WorldMap,
    modifier: Modifier = Modifier,
    rivers: Set<TileCoord> = emptySet(),
    settlements: List<SettlementMarker> = emptyList(),
    territoryOwners: IntArray? = null,
    selectedCivilizationIndex: Int? = null,
    onCivilizationSelected: ((Int) -> Unit)? = null,
) {
    val interactiveModifier = if (onCivilizationSelected == null) modifier else {
        modifier.pointerInput(world.width, world.height, settlements) {
            detectTapGestures { tap ->
                if (settlements.isEmpty() || size.width <= 0 || size.height <= 0) return@detectTapGestures
                val cellW = size.width.toFloat() / world.width.toFloat()
                val cellH = size.height.toFloat() / world.height.toFloat()
                val nearest = settlements.minByOrNull { settlement ->
                    val cx = (settlement.x + 0.5f) * cellW
                    val cy = (settlement.y + 0.5f) * cellH
                    val dx = tap.x - cx
                    val dy = tap.y - cy
                    dx * dx + dy * dy
                } ?: return@detectTapGestures
                val cx = (nearest.x + 0.5f) * cellW
                val cy = (nearest.y + 0.5f) * cellH
                val dx = tap.x - cx
                val dy = tap.y - cy
                val distanceSquared = dx * dx + dy * dy
                val radius = (2.2f + sqrt(nearest.population.coerceAtLeast(1).toFloat()) / 36f).coerceIn(2.2f, 8.5f)
                val hitRadius = (radius * 2.5f).coerceAtLeast(22f)
                if (distanceSquared <= hitRadius * hitRadius) {
                    onCivilizationSelected(nearest.civilizationIndex)
                }
            }
        }
    }

    Canvas(modifier = interactiveModifier) {
        val cellW = size.width / world.width
        val cellH = size.height / world.height
        world.tiles.forEach { tile ->
            drawRect(tile.biome.color(), Offset(tile.x * cellW, tile.y * cellH), Size(cellW + 0.5f, cellH + 0.5f))
        }

        if (territoryOwners != null && territoryOwners.size == world.tiles.size) {
            world.tiles.forEachIndexed { index, tile ->
                val owner = territoryOwners[index]
                if (owner >= 0 && tile.biome != Biome.OCEAN && tile.biome != Biome.DEEP_OCEAN) {
                    val selected = owner == selectedCivilizationIndex
                    val alpha = if (selected) 0.34f else 0.20f
                    drawRect(civilizationPalette[owner % civilizationPalette.size].copy(alpha = alpha), Offset(tile.x * cellW, tile.y * cellH), Size(cellW + 0.5f, cellH + 0.5f))
                    val rightOwner = if (tile.x + 1 < world.width) territoryOwners[index + 1] else owner
                    val downOwner = if (tile.y + 1 < world.height) territoryOwners[index + world.width] else owner
                    if (rightOwner != owner) drawLine(Color.White.copy(alpha = if (selected) 0.72f else 0.40f), Offset((tile.x + 1) * cellW, tile.y * cellH), Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH), if (selected) 1.25f else 0.8f)
                    if (downOwner != owner) drawLine(Color.White.copy(alpha = if (selected) 0.72f else 0.40f), Offset(tile.x * cellW, (tile.y + 1) * cellH), Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH), if (selected) 1.25f else 0.8f)
                }
            }
        }

        rivers.forEach { river ->
            drawRect(Color(0xFF5CB7E8), Offset(river.x * cellW, river.y * cellH), Size(cellW.coerceAtLeast(1.2f), cellH.coerceAtLeast(1.2f)))
        }
        settlements.forEach { settlement ->
            val radius = (2.2f + sqrt(settlement.population.coerceAtLeast(1).toFloat()) / 36f).coerceIn(2.2f, 8.5f)
            val center = Offset((settlement.x + 0.5f) * cellW, (settlement.y + 0.5f) * cellH)
            if (settlement.civilizationIndex == selectedCivilizationIndex) {
                drawCircle(Color.White.copy(alpha = 0.92f), radius + 3.2f, center)
                drawCircle(Color.Black.copy(alpha = 0.58f), radius + 1.6f, center)
            }
            drawCircle(civilizationPalette[settlement.civilizationIndex % civilizationPalette.size], radius, center)
            drawCircle(Color.White.copy(alpha = 0.8f), (radius * 0.34f).coerceAtLeast(1f), center)
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
