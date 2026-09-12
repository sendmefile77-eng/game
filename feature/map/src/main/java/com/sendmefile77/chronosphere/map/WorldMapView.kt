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
import kotlin.math.abs
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

        drawRect(Color(0xFF06131D))

        world.tiles.forEach { tile ->
            drawRect(
                color = tile.renderColor(),
                topLeft = Offset(tile.x * cellW, tile.y * cellH),
                size = Size(cellW + 0.7f, cellH + 0.7f),
            )
        }

        world.tiles.forEachIndexed { index, tile ->
            if (!tile.biome.isLand()) return@forEachIndexed
            if (tile.x + 1 < world.width) {
                val right = world.tiles[index + 1]
                if (right.biome.isLand()) {
                    val delta = tile.elevation - right.elevation
                    if (abs(delta) > 0.065) {
                        drawLine(
                            color = if (delta > 0) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.20f),
                            start = Offset((tile.x + 1) * cellW, tile.y * cellH),
                            end = Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                            strokeWidth = 0.65f,
                        )
                    }
                }
            }
            if (tile.y + 1 < world.height) {
                val down = world.tiles[index + world.width]
                if (down.biome.isLand()) {
                    val delta = tile.elevation - down.elevation
                    if (abs(delta) > 0.065) {
                        drawLine(
                            color = if (delta > 0) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.18f),
                            start = Offset(tile.x * cellW, (tile.y + 1) * cellH),
                            end = Offset((tile.x + 1) * cellW, (tile.y + 1) * cellH),
                            strokeWidth = 0.65f,
                        )
                    }
                }
            }
        }

        world.tiles.forEachIndexed { index, tile ->
            val land = tile.biome.isLand()
            fun coastSegment(start: Offset, end: Offset) {
                drawLine(Color(0xFF031019).copy(alpha = 0.82f), start, end, 2.2f)
                drawLine(Color(0xFFDCE6DE).copy(alpha = 0.58f), start, end, 0.85f)
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

        if (territoryOwners != null && territoryOwners.size == world.tiles.size) {
            world.tiles.forEachIndexed { index, tile ->
                val owner = territoryOwners[index]
                if (owner < 0 || !tile.biome.isLand()) return@forEachIndexed
                val selected = owner == selectedCivilizationIndex
                val nationColor = civilizationPalette[owner % civilizationPalette.size]
                drawRect(
                    color = nationColor.copy(alpha = if (selected) 0.30f else 0.115f),
                    topLeft = Offset(tile.x * cellW, tile.y * cellH),
                    size = Size(cellW + 0.5f, cellH + 0.5f),
                )

                fun border(start: Offset, end: Offset) {
                    if (selected) drawLine(Color.Black.copy(alpha = 0.58f), start, end, 2.5f)
                    drawLine(
                        color = if (selected) nationColor.copy(alpha = 0.98f) else Color(0xFFDCE5E8).copy(alpha = 0.46f),
                        start = start,
                        end = end,
                        strokeWidth = if (selected) 1.45f else 0.75f,
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

        for (x in 12 until world.width step 12) {
            drawLine(Color.White.copy(alpha = 0.025f), Offset(x * cellW, 0f), Offset(x * cellW, size.height), 0.55f)
        }
        for (y in 9 until world.height step 9) {
            drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, y * cellH), Offset(size.width, y * cellH), 0.55f)
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
                        drawLine(Color(0xFF082437).copy(alpha = 0.88f), from, to, (minCell * 0.42f).coerceIn(1.8f, 4.5f))
                        drawLine(Color(0xFF63C8F2).copy(alpha = 0.92f), from, to, (minCell * 0.19f).coerceIn(0.9f, 2.2f))
                    }
                }
                if (!connected) {
                    drawCircle(Color(0xFF63C8F2).copy(alpha = 0.90f), (minCell * 0.24f).coerceAtLeast(0.9f), from)
                }
            }
        }

        settlements.sortedBy { it.population }.forEach { settlement ->
            val radius = settlementRadius(settlement.population)
            val center = Offset((settlement.x + 0.5f) * cellW, (settlement.y + 0.5f) * cellH)
            val nationColor = civilizationPalette[settlement.civilizationIndex % civilizationPalette.size]
            val selected = settlement.civilizationIndex == selectedCivilizationIndex

            if (selected) {
                drawCircle(nationColor.copy(alpha = 0.16f), radius + 7.0f, center)
                drawCircle(Color.White.copy(alpha = 0.50f), radius + 4.2f, center, style = Stroke(1.2f))
            }
            drawCircle(Color(0xFF061017).copy(alpha = 0.95f), radius + 2.0f, center)
            drawCircle(nationColor, radius, center)
            drawCircle(Color.White.copy(alpha = 0.90f), (radius * 0.28f).coerceAtLeast(1f), center)

            if (radius >= 5.2f) {
                drawLine(
                    Color.White.copy(alpha = 0.58f),
                    Offset(center.x - radius * 0.55f, center.y),
                    Offset(center.x + radius * 0.55f, center.y),
                    0.75f,
                )
                drawLine(
                    Color.White.copy(alpha = 0.58f),
                    Offset(center.x, center.y - radius * 0.55f),
                    Offset(center.x, center.y + radius * 0.55f),
                    0.75f,
                )
            }
        }

        drawRect(Color.Black.copy(alpha = 0.46f), style = Stroke(width = 3f))
        drawRect(Color.White.copy(alpha = 0.14f), style = Stroke(width = 1f))
    }
}

private fun settlementRadius(population: Long): Float =
    (2.5f + sqrt(population.coerceAtLeast(1).toFloat()) / 48f).coerceIn(2.5f, 8.2f)

private fun WorldTile.renderColor(): Color {
    val base = biome.baseColor()
    val elevationLight = (elevation.coerceIn(-1.0, 1.0) * 0.14).toFloat()
    val moistureShift = ((moisture.coerceIn(0.0, 1.0) - 0.5) * 0.045).toFloat()
    val temperatureShift = (temperature.coerceIn(-1.0, 1.0) * 0.026).toFloat()
    val oceanDepth = if (biome == Biome.DEEP_OCEAN || biome == Biome.OCEAN) {
        (-elevation.coerceAtMost(0.0) * 0.07).toFloat()
    } else 0f
    return Color(
        red = (base.red + elevationLight + temperatureShift - oceanDepth * 0.30f).coerceIn(0f, 1f),
        green = (base.green + elevationLight + moistureShift - oceanDepth * 0.10f).coerceIn(0f, 1f),
        blue = (base.blue + elevationLight - temperatureShift + oceanDepth).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

private fun Biome.isLand(): Boolean = this != Biome.OCEAN && this != Biome.DEEP_OCEAN

private fun Biome.baseColor(): Color = when (this) {
    Biome.DEEP_OCEAN -> Color(0xFF071C2B)
    Biome.OCEAN -> Color(0xFF124762)
    Biome.COAST -> Color(0xFFC6B77C)
    Biome.DESERT -> Color(0xFFB99255)
    Biome.STEPPE -> Color(0xFF7F8952)
    Biome.GRASSLAND -> Color(0xFF4C7848)
    Biome.FOREST -> Color(0xFF24543C)
    Biome.RAINFOREST -> Color(0xFF153E2E)
    Biome.TAIGA -> Color(0xFF38564B)
    Biome.TUNDRA -> Color(0xFF74867E)
    Biome.MOUNTAIN -> Color(0xFF66645F)
    Biome.ICE -> Color(0xFFDCE9EC)
}
