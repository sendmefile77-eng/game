package com.sendmefile77.chronosphere.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.TileCoord
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldTile
import kotlin.math.sqrt

data class SettlementMarker(val x: Int, val y: Int, val population: Long, val civilizationIndex: Int)

private val civilizationPalette = listOf(
    Color(0xFFE4B95C), Color(0xFFD96B79), Color(0xFF55C3A8), Color(0xFF73B6D1),
    Color(0xFFA982D6), Color(0xFFD58A45), Color(0xFF84A968), Color(0xFFC98563),
    Color(0xFF4E9F8A), Color(0xFF70869A), Color(0xFFC95B5D), Color(0xFFA6BE6B),
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
                val radius = (2.4f + sqrt(nearest.population.coerceAtLeast(1).toFloat()) / 48f).coerceIn(2.4f, 8.0f)
                val hitRadius = (radius * 2.6f).coerceAtLeast(22f)
                if (distanceSquared <= hitRadius * hitRadius) {
                    onCivilizationSelected(nearest.civilizationIndex)
                }
            }
        }
    }

    Canvas(modifier = interactiveModifier) {
        val cellW = size.width / world.width
        val cellH = size.height / world.height

        drawRect(Color(0xFF07131C))

        world.tiles.forEach { tile ->
            drawRect(
                color = tile.renderColor(),
                topLeft = Offset(tile.x * cellW, tile.y * cellH),
                size = Size(cellW + 0.6f, cellH + 0.6f),
            )
        }

        // Coastline makes continents readable at a glance instead of looking like a raw biome grid.
        world.tiles.forEachIndexed { index, tile ->
            val tileLand = tile.biome.isLand()
            if (tile.x + 1 < world.width) {
                val right = world.tiles[index + 1]
                if (tileLand != right.biome.isLand()) {
                    drawLine(
                        color = Color(0xFFCFD8D5).copy(alpha = 0.48f),
                        start = Offset((tile.x + 1) * cellW, tile.y * cellH),
                        end = Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                        strokeWidth = 0.75f,
                    )
                }
            }
            if (tile.y + 1 < world.height) {
                val down = world.tiles[index + world.width]
                if (tileLand != down.biome.isLand()) {
                    drawLine(
                        color = Color(0xFFCFD8D5).copy(alpha = 0.48f),
                        start = Offset(tile.x * cellW, (tile.y + 1) * cellH),
                        end = Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                        strokeWidth = 0.75f,
                    )
                }
            }
        }

        if (territoryOwners != null && territoryOwners.size == world.tiles.size) {
            world.tiles.forEachIndexed { index, tile ->
                val owner = territoryOwners[index]
                if (owner >= 0 && tile.biome.isLand()) {
                    val selected = owner == selectedCivilizationIndex
                    val alpha = if (selected) 0.36f else 0.18f
                    drawRect(
                        color = civilizationPalette[owner % civilizationPalette.size].copy(alpha = alpha),
                        topLeft = Offset(tile.x * cellW, tile.y * cellH),
                        size = Size(cellW + 0.5f, cellH + 0.5f),
                    )
                    val rightOwner = if (tile.x + 1 < world.width) territoryOwners[index + 1] else owner
                    val downOwner = if (tile.y + 1 < world.height) territoryOwners[index + world.width] else owner
                    if (rightOwner != owner) {
                        drawLine(
                            Color(0xFFE8EEF2).copy(alpha = if (selected) 0.84f else 0.42f),
                            Offset((tile.x + 1) * cellW, tile.y * cellH),
                            Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                            if (selected) 1.45f else 0.75f,
                        )
                    }
                    if (downOwner != owner) {
                        drawLine(
                            Color(0xFFE8EEF2).copy(alpha = if (selected) 0.84f else 0.42f),
                            Offset(tile.x * cellW, (tile.y + 1) * cellH),
                            Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                            if (selected) 1.45f else 0.75f,
                        )
                    }
                }
            }
        }

        // Very subtle geographic grid gives the map structure without reading as debug pixels.
        for (x in 12 until world.width step 12) {
            drawLine(
                Color.White.copy(alpha = 0.035f),
                Offset(x * cellW, 0f),
                Offset(x * cellW, size.height),
                0.6f,
            )
        }
        for (y in 9 until world.height step 9) {
            drawLine(
                Color.White.copy(alpha = 0.035f),
                Offset(0f, y * cellH),
                Offset(size.width, y * cellH),
                0.6f,
            )
        }

        rivers.forEach { river ->
            drawRect(
                Color(0xFF5CB7E8).copy(alpha = 0.86f),
                Offset(river.x * cellW, river.y * cellH),
                Size(cellW.coerceAtLeast(1.1f), cellH.coerceAtLeast(1.1f)),
            )
        }

        settlements.forEach { settlement ->
            val radius = (2.4f + sqrt(settlement.population.coerceAtLeast(1).toFloat()) / 48f).coerceIn(2.4f, 8.0f)
            val center = Offset((settlement.x + 0.5f) * cellW, (settlement.y + 0.5f) * cellH)
            if (settlement.civilizationIndex == selectedCivilizationIndex) {
                drawCircle(Color(0xFFE9F0F4).copy(alpha = 0.92f), radius + 3.5f, center)
                drawCircle(Color(0xFF0A1117).copy(alpha = 0.90f), radius + 2.0f, center)
            }
            drawCircle(civilizationPalette[settlement.civilizationIndex % civilizationPalette.size], radius, center)
            drawCircle(Color.White.copy(alpha = 0.82f), (radius * 0.30f).coerceAtLeast(1f), center)
        }

        drawRect(
            color = Color.White.copy(alpha = 0.12f),
            style = Stroke(width = 1f),
        )
    }
}

private fun WorldTile.renderColor(): Color {
    val base = biome.baseColor()
    val elevationLight = (elevation.coerceIn(-1.0, 1.0) * 0.10).toFloat()
    val moistureShift = ((moisture.coerceIn(0.0, 1.0) - 0.5) * 0.025).toFloat()
    val temperatureShift = ((temperature.coerceIn(-1.0, 1.0)) * 0.018).toFloat()
    return Color(
        red = (base.red + elevationLight + temperatureShift).coerceIn(0f, 1f),
        green = (base.green + elevationLight + moistureShift).coerceIn(0f, 1f),
        blue = (base.blue + elevationLight - temperatureShift).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

private fun Biome.isLand(): Boolean = this != Biome.OCEAN && this != Biome.DEEP_OCEAN

private fun Biome.baseColor(): Color = when (this) {
    Biome.DEEP_OCEAN -> Color(0xFF091F30)
    Biome.OCEAN -> Color(0xFF174A67)
    Biome.COAST -> Color(0xFFC8B77C)
    Biome.DESERT -> Color(0xFFB99454)
    Biome.STEPPE -> Color(0xFF7F8B53)
    Biome.GRASSLAND -> Color(0xFF527D49)
    Biome.FOREST -> Color(0xFF28553D)
    Biome.RAINFOREST -> Color(0xFF173F30)
    Biome.TAIGA -> Color(0xFF3A554A)
    Biome.TUNDRA -> Color(0xFF74847A)
    Biome.MOUNTAIN -> Color(0xFF66645E)
    Biome.ICE -> Color(0xFFD8E6EA)
}
