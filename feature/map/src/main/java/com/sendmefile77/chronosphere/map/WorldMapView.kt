package com.sendmefile77.chronosphere.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.TileCoord
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldTile
import kotlin.math.abs
import kotlin.math.sqrt

data class SettlementMarker(val x: Int, val y: Int, val population: Long, val civilizationIndex: Int)

private val civilizationPalette = listOf(
    Color(0xFFE0B85E), Color(0xFFD56C72), Color(0xFF58BFA7), Color(0xFF69AFCB),
    Color(0xFF9476C2), Color(0xFFC67B47), Color(0xFF7E9D61), Color(0xFFC49A5A),
    Color(0xFF4C9A87), Color(0xFF6F8190), Color(0xFFB95659), Color(0xFF9DAF64),
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
        modifier.pointerInput(world.width, world.height, settlements, territoryOwners) {
            detectTapGestures { tap ->
                if (size.width <= 0 || size.height <= 0) return@detectTapGestures
                val cellW = size.width.toFloat() / world.width.toFloat()
                val cellH = size.height.toFloat() / world.height.toFloat()

                if (territoryOwners != null && territoryOwners.size == world.tiles.size) {
                    val tileX = (tap.x / cellW).toInt().coerceIn(0, world.width - 1)
                    val tileY = (tap.y / cellH).toInt().coerceIn(0, world.height - 1)
                    val owner = territoryOwners[tileY * world.width + tileX]
                    if (owner >= 0) {
                        onCivilizationSelected(owner)
                        return@detectTapGestures
                    }
                }

                if (settlements.isEmpty()) return@detectTapGestures
                val nearest = settlements.minByOrNull { settlement ->
                    val center = Offset((settlement.x + 0.5f) * cellW, (settlement.y + 0.5f) * cellH)
                    val dx = tap.x - center.x
                    val dy = tap.y - center.y
                    dx * dx + dy * dy
                } ?: return@detectTapGestures
                val center = Offset((nearest.x + 0.5f) * cellW, (nearest.y + 0.5f) * cellH)
                val dx = tap.x - center.x
                val dy = tap.y - center.y
                val radius = settlementRadius(nearest.population)
                val hitRadius = (radius * 2.8f).coerceAtLeast(22f)
                if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                    onCivilizationSelected(nearest.civilizationIndex)
                }
            }
        }
    }

    Canvas(modifier = interactiveModifier) {
        val cellW = size.width / world.width
        val cellH = size.height / world.height
        val minCell = minOf(cellW, cellH)

        drawRect(Color(0xFF061017))

        // Terrain base. Color variation follows elevation, moisture and temperature so the map reads
        // like a physical world first and a political overlay second.
        world.tiles.forEach { tile ->
            drawRect(
                color = tile.renderColor(),
                topLeft = Offset(tile.x * cellW, tile.y * cellH),
                size = Size(cellW + 0.8f, cellH + 0.8f),
            )
        }

        // Relief edges are deliberately subdued; they add depth without turning every tile into a grid cell.
        world.tiles.forEachIndexed { index, tile ->
            if (!tile.biome.isLand()) return@forEachIndexed
            if (tile.x + 1 < world.width) {
                val right = world.tiles[index + 1]
                if (right.biome.isLand()) {
                    val delta = tile.elevation - right.elevation
                    if (abs(delta) > 0.055) {
                        drawLine(
                            color = if (delta > 0) Color(0xFFF1E7C7).copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.22f),
                            start = Offset((tile.x + 1) * cellW, tile.y * cellH),
                            end = Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                            strokeWidth = 0.70f,
                        )
                    }
                }
            }
            if (tile.y + 1 < world.height) {
                val down = world.tiles[index + world.width]
                if (down.biome.isLand()) {
                    val delta = tile.elevation - down.elevation
                    if (abs(delta) > 0.055) {
                        drawLine(
                            color = if (delta > 0) Color(0xFFF1E7C7).copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.20f),
                            start = Offset(tile.x * cellW, (tile.y + 1) * cellH),
                            end = Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                            strokeWidth = 0.70f,
                        )
                    }
                }
            }
        }

        // Coastline: dark ink outside, warm cartographic highlight inside.
        world.tiles.forEachIndexed { index, tile ->
            val land = tile.biome.isLand()
            fun coastSegment(start: Offset, end: Offset) {
                drawLine(Color(0xFF02090E).copy(alpha = 0.92f), start, end, 2.7f)
                drawLine(Color(0xFFE7D8A5).copy(alpha = 0.56f), start, end, 0.95f)
            }
            if (tile.x + 1 < world.width && land != world.tiles[index + 1].biome.isLand()) {
                coastSegment(
                    Offset((tile.x + 1) * cellW, tile.y * cellH),
                    Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                )
            }
            if (tile.y + 1 < world.height && land != world.tiles[index + world.width].biome.isLand()) {
                coastSegment(
                    Offset(tile.x * cellW, (tile.y + 1) * cellH),
                    Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                )
            }
        }

        // Political layer. Unselected states stay translucent; the active one gains its own border glow.
        if (territoryOwners != null && territoryOwners.size == world.tiles.size) {
            world.tiles.forEachIndexed { index, tile ->
                val owner = territoryOwners[index]
                if (owner < 0 || !tile.biome.isLand()) return@forEachIndexed
                val selected = owner == selectedCivilizationIndex
                val nationColor = civilizationPalette[owner % civilizationPalette.size]
                drawRect(
                    color = nationColor.copy(alpha = if (selected) 0.24f else 0.075f),
                    topLeft = Offset(tile.x * cellW, tile.y * cellH),
                    size = Size(cellW + 0.5f, cellH + 0.5f),
                )

                fun border(start: Offset, end: Offset) {
                    if (selected) {
                        drawLine(nationColor.copy(alpha = 0.18f), start, end, 5.2f)
                        drawLine(Color(0xFF02070B).copy(alpha = 0.86f), start, end, 3.0f)
                    }
                    drawLine(
                        color = if (selected) nationColor.copy(alpha = 0.98f) else nationColor.copy(alpha = 0.50f),
                        start = start,
                        end = end,
                        strokeWidth = if (selected) 1.65f else 0.72f,
                    )
                }

                val rightOwner = if (tile.x + 1 < world.width) territoryOwners[index + 1] else -1
                val downOwner = if (tile.y + 1 < world.height) territoryOwners[index + world.width] else -1
                if (rightOwner != owner) {
                    border(
                        Offset((tile.x + 1) * cellW, tile.y * cellH),
                        Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                    )
                }
                if (downOwner != owner) {
                    border(
                        Offset(tile.x * cellW, (tile.y + 1) * cellH),
                        Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                    )
                }
            }
        }

        // Sparse atlas grid, almost invisible until the eye looks for it.
        for (x in 16 until world.width step 16) {
            drawLine(Color(0xFFE8D8A3).copy(alpha = 0.022f), Offset(x * cellW, 0f), Offset(x * cellW, size.height), 0.55f)
        }
        for (y in 12 until world.height step 12) {
            drawLine(Color(0xFFE8D8A3).copy(alpha = 0.022f), Offset(0f, y * cellH), Offset(size.width, y * cellH), 0.55f)
        }

        if (rivers.isNotEmpty()) {
            val riverSet = rivers.toHashSet()
            val directions = listOf(1 to 0, 0 to 1)
            rivers.forEach { river ->
                val from = Offset((river.x + 0.5f) * cellW, (river.y + 0.5f) * cellH)
                var connected = false
                directions.forEach { (dx, dy) ->
                    val neighbour = TileCoord(river.x + dx, river.y + dy)
                    if (neighbour in riverSet) {
                        connected = true
                        val to = Offset((neighbour.x + 0.5f) * cellW, (neighbour.y + 0.5f) * cellH)
                        drawLine(Color(0xFF020C13).copy(alpha = 0.90f), from, to, (minCell * 0.48f).coerceIn(2.0f, 4.8f))
                        drawLine(Color(0xFF59B9D8).copy(alpha = 0.84f), from, to, (minCell * 0.18f).coerceIn(0.9f, 2.0f))
                    }
                }
                if (!connected) {
                    drawCircle(Color(0xFF59B9D8).copy(alpha = 0.82f), (minCell * 0.22f).coerceAtLeast(0.9f), from)
                }
            }
        }

        // Edge vignette ties the tile renderer into the dark game shell and keeps focus near the world center.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0x12000000), Color(0xA6000000)),
                center = Offset(size.width * 0.50f, size.height * 0.46f),
                radius = maxOf(size.width, size.height) * 0.78f,
            ),
        )

        settlements.sortedBy { it.population }.forEach { settlement ->
            val radius = settlementRadius(settlement.population)
            val center = Offset((settlement.x + 0.5f) * cellW, (settlement.y + 0.5f) * cellH)
            val nationColor = civilizationPalette[settlement.civilizationIndex % civilizationPalette.size]
            val selected = settlement.civilizationIndex == selectedCivilizationIndex

            if (selected) {
                drawCircle(nationColor.copy(alpha = 0.12f), radius + 9.0f, center)
                drawCircle(nationColor.copy(alpha = 0.32f), radius + 5.4f, center, style = Stroke(1.25f))
            }
            drawCircle(Color(0xFF02070B).copy(alpha = 0.98f), radius + 2.8f, center)
            drawCircle(nationColor.copy(alpha = if (selected) 1.0f else 0.90f), radius + 0.8f, center)
            drawCircle(Color(0xFFF1E5BE), (radius * 0.46f).coerceAtLeast(1.35f), center)
            drawCircle(Color(0xFF0A1117), (radius * 0.18f).coerceAtLeast(0.75f), center)

            if (radius >= 5.2f) {
                drawCircle(
                    Color(0xFFF1E5BE).copy(alpha = 0.52f),
                    radius + 1.9f,
                    center,
                    style = Stroke(0.8f),
                )
            }
        }

        // Frame: black outer cut and warm inner keyline instead of a generic white rectangle.
        drawRect(Color.Black.copy(alpha = 0.72f), style = Stroke(width = 3.5f))
        drawRect(Color(0xFFE0C675).copy(alpha = 0.18f), style = Stroke(width = 1.0f))
    }
}

private fun settlementRadius(population: Long): Float =
    (2.6f + sqrt(population.coerceAtLeast(1).toFloat()) / 48f).coerceIn(2.6f, 8.4f)

private fun WorldTile.renderColor(): Color {
    val base = biome.baseColor()
    val elevationLight = (elevation.coerceIn(-1.0, 1.0) * 0.115).toFloat()
    val moistureShift = ((moisture.coerceIn(0.0, 1.0) - 0.5) * 0.036).toFloat()
    val temperatureShift = (temperature.coerceIn(-1.0, 1.0) * 0.020).toFloat()
    val oceanDepth = if (biome == Biome.DEEP_OCEAN || biome == Biome.OCEAN) {
        (-elevation.coerceAtMost(0.0) * 0.075).toFloat()
    } else 0f
    return Color(
        red = (base.red + elevationLight + temperatureShift - oceanDepth * 0.34f).coerceIn(0f, 1f),
        green = (base.green + elevationLight + moistureShift - oceanDepth * 0.13f).coerceIn(0f, 1f),
        blue = (base.blue + elevationLight - temperatureShift + oceanDepth).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

private fun Biome.isLand(): Boolean = this != Biome.OCEAN && this != Biome.DEEP_OCEAN

private fun Biome.baseColor(): Color = when (this) {
    Biome.DEEP_OCEAN -> Color(0xFF061620)
    Biome.OCEAN -> Color(0xFF0D3345)
    Biome.COAST -> Color(0xFFB9A56D)
    Biome.DESERT -> Color(0xFFA9824D)
    Biome.STEPPE -> Color(0xFF70794A)
    Biome.GRASSLAND -> Color(0xFF416943)
    Biome.FOREST -> Color(0xFF234C38)
    Biome.RAINFOREST -> Color(0xFF15382B)
    Biome.TAIGA -> Color(0xFF344E45)
    Biome.TUNDRA -> Color(0xFF6D7A74)
    Biome.MOUNTAIN -> Color(0xFF5C5A55)
    Biome.ICE -> Color(0xFFC9D8D9)
}
