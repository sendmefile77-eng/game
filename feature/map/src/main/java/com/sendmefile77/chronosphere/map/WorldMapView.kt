package com.sendmefile77.chronosphere.map

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.worldgen.Biome
import com.sendmefile77.chronosphere.worldgen.TileCoord
import com.sendmefile77.chronosphere.worldgen.WorldMap
import com.sendmefile77.chronosphere.worldgen.WorldTile
import kotlin.math.abs
import kotlin.math.sqrt

data class SettlementMarker(
    val x: Int,
    val y: Int,
    val population: Long,
    val civilizationIndex: Int,
    val name: String = "",
    val civilizationName: String = "",
)

enum class MapConnectionKind { TRADE, WAR }

data class MapConnection(
    val fromX: Int,
    val fromY: Int,
    val toX: Int,
    val toY: Int,
    val kind: MapConnectionKind,
)

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
    connections: List<MapConnection> = emptyList(),
    territoryOwners: IntArray? = null,
    selectedCivilizationIndex: Int? = null,
    onCivilizationSelected: ((Int) -> Unit)? = null,
) {
    var scale by remember(world.seed.value) { mutableFloatStateOf(1f) }
    var translation by remember(world.seed.value) { mutableStateOf(Offset.Zero) }

    fun boundedTranslation(candidate: Offset, targetScale: Float, width: Float, height: Float): Offset {
        if (targetScale <= 1.001f || width <= 0f || height <= 0f) return Offset.Zero
        val minX = width * (1f - targetScale)
        val minY = height * (1f - targetScale)
        return Offset(candidate.x.coerceIn(minX, 0f), candidate.y.coerceIn(minY, 0f))
    }

    val interactiveModifier = modifier
        .clipToBounds()
        .pointerInput(world.seed.value) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                val oldScale = scale
                val nextScale = (oldScale * zoom).coerceIn(1f, 4f)
                val ratio = nextScale / oldScale
                val candidate = Offset(
                    x = centroid.x - (centroid.x - translation.x) * ratio + pan.x,
                    y = centroid.y - (centroid.y - translation.y) * ratio + pan.y,
                )
                scale = nextScale
                translation = boundedTranslation(
                    candidate = candidate,
                    targetScale = nextScale,
                    width = size.width.toFloat(),
                    height = size.height.toFloat(),
                )
            }
        }
        .pointerInput(world.width, world.height, settlements, territoryOwners, onCivilizationSelected) {
            detectTapGestures(
                onDoubleTap = { tap ->
                    val nextScale = if (scale > 1.05f) 1f else 2f
                    scale = nextScale
                    translation = if (nextScale == 1f) {
                        Offset.Zero
                    } else {
                        boundedTranslation(
                            candidate = Offset(-tap.x, -tap.y),
                            targetScale = nextScale,
                            width = size.width.toFloat(),
                            height = size.height.toFloat(),
                        )
                    }
                },
                onTap = selectCivilization@{ position ->
                    val select = onCivilizationSelected ?: return@selectCivilization
                    if (size.width <= 0 || size.height <= 0) return@selectCivilization
                    val contentTap = Offset(
                        x = (position.x - translation.x) / scale,
                        y = (position.y - translation.y) / scale,
                    )
                    val cellW = size.width.toFloat() / world.width.toFloat()
                    val cellH = size.height.toFloat() / world.height.toFloat()

                    if (territoryOwners != null && territoryOwners.size == world.tiles.size) {
                        val tileX = (contentTap.x / cellW).toInt().coerceIn(0, world.width - 1)
                        val tileY = (contentTap.y / cellH).toInt().coerceIn(0, world.height - 1)
                        val owner = territoryOwners[tileY * world.width + tileX]
                        if (owner >= 0) {
                            select(owner)
                            return@selectCivilization
                        }
                    }

                    if (settlements.isEmpty()) return@selectCivilization
                    val nearest = settlements.minByOrNull { settlement ->
                        val center = Offset((settlement.x + 0.5f) * cellW, (settlement.y + 0.5f) * cellH)
                        val dx = contentTap.x - center.x
                        val dy = contentTap.y - center.y
                        dx * dx + dy * dy
                    } ?: return@selectCivilization
                    val center = Offset((nearest.x + 0.5f) * cellW, (nearest.y + 0.5f) * cellH)
                    val dx = contentTap.x - center.x
                    val dy = contentTap.y - center.y
                    val hitRadius = (settlementRadius(nearest.population) * 2.8f).coerceAtLeast(22f / scale)
                    if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                        select(nearest.civilizationIndex)
                    }
                },
            )
        }

    Box(modifier = interactiveModifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = translation.x
                    translationY = translation.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
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

        // Sparse biome marks become visible as the player zooms in. They add relief without
        // covering the political layer or turning the map into a noisy tile grid.
        if (minCell * scale >= 3.0f) {
            world.tiles.forEach { tile ->
                val center = Offset((tile.x + 0.5f) * cellW, (tile.y + 0.5f) * cellH)
                when (tile.biome) {
                    Biome.MOUNTAIN -> {
                        val halfWidth = cellW * 0.34f
                        val peakHeight = cellH * 0.38f
                        val foot = center.y + cellH * 0.24f
                        drawLine(
                            Color(0xFF171A1B).copy(alpha = 0.68f),
                            Offset(center.x - halfWidth, foot),
                            Offset(center.x, foot - peakHeight),
                            (0.9f / scale).coerceAtLeast(0.35f),
                        )
                        drawLine(
                            Color(0xFFE7E2D5).copy(alpha = 0.34f),
                            Offset(center.x, foot - peakHeight),
                            Offset(center.x + halfWidth, foot),
                            (0.75f / scale).coerceAtLeast(0.30f),
                        )
                    }

                    Biome.FOREST, Biome.RAINFOREST, Biome.TAIGA -> {
                        val jitter = (((tile.x * 31 + tile.y * 17) and 7) - 3) * cellW * 0.035f
                        drawCircle(
                            color = Color(0xFF071B16).copy(alpha = 0.42f),
                            radius = (minCell * 0.16f).coerceAtLeast(0.42f),
                            center = Offset(center.x + jitter, center.y),
                        )
                    }

                    Biome.DESERT -> if ((tile.x + tile.y) % 3 == 0) {
                        drawLine(
                            Color(0xFFF0D59A).copy(alpha = 0.16f),
                            Offset(center.x - cellW * 0.22f, center.y),
                            Offset(center.x + cellW * 0.22f, center.y),
                            (0.65f / scale).coerceAtLeast(0.25f),
                        )
                    }

                    else -> Unit
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

        // Current trade and war links make the strategic state readable directly on the map.
        connections.forEach { connection ->
            val from = Offset((connection.fromX + 0.5f) * cellW, (connection.fromY + 0.5f) * cellH)
            val to = Offset((connection.toX + 0.5f) * cellW, (connection.toY + 0.5f) * cellH)
            val hostile = connection.kind == MapConnectionKind.WAR
            val color = if (hostile) Color(0xFFE66F6B) else Color(0xFF73D2C8)
            val pathEffect = if (hostile) {
                PathEffect.dashPathEffect(floatArrayOf(6f / scale, 4f / scale))
            } else null
            drawLine(
                color = Color(0xFF02070B).copy(alpha = 0.78f),
                start = from,
                end = to,
                strokeWidth = (4.0f / scale).coerceAtLeast(1.1f),
                cap = StrokeCap.Round,
                pathEffect = pathEffect,
            )
            drawLine(
                color = color.copy(alpha = if (hostile) 0.94f else 0.72f),
                start = from,
                end = to,
                strokeWidth = (1.45f / scale).coerceAtLeast(0.55f),
                cap = StrokeCap.Round,
                pathEffect = pathEffect,
            )
            if (hostile) {
                drawCircle(
                    color = color.copy(alpha = 0.90f),
                    radius = (2.4f / scale).coerceAtLeast(0.8f),
                    center = Offset((from.x + to.x) * 0.5f, (from.y + to.y) * 0.5f),
                )
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

        val selectedCapital = settlements
            .filter { it.civilizationIndex == selectedCivilizationIndex }
            .maxByOrNull { it.population }
        val labelLimit = if (scale >= 1.65f) 12 else 5
        val labelCandidates = buildList {
            if (selectedCapital != null) add(selectedCapital)
            settlements.asSequence()
                .filter { it !== selectedCapital && it.name.isNotBlank() }
                .sortedByDescending { it.population }
                .take(labelLimit - size)
                .forEach(::add)
        }
        val occupiedLabels = mutableListOf<Rect>()
        labelCandidates.forEach { settlement ->
            val selected = settlement === selectedCapital
            val rawLabel = if (selected && settlement.civilizationName.isNotBlank()) {
                "${settlement.civilizationName} · ${settlement.name}"
            } else settlement.name
            val label = rawLabel.take(30)
            if (label.isBlank()) return@forEach

            val textSize = (11.dp.toPx() / scale).coerceAtLeast(3.5f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(236, 231, 213)
                this.textSize = textSize
                typeface = Typeface.create(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            }
            val paddingX = 5.dp.toPx() / scale
            val paddingY = 3.dp.toPx() / scale
            val labelWidth = paint.measureText(label) + paddingX * 2f
            val labelHeight = textSize + paddingY * 2f
            val radius = settlementRadius(settlement.population)
            val center = Offset((settlement.x + 0.5f) * cellW, (settlement.y + 0.5f) * cellH)
            val left = (center.x + radius + 4.dp.toPx() / scale)
                .coerceAtMost((size.width - labelWidth - 3.dp.toPx() / scale).coerceAtLeast(0f))
            val top = (center.y - labelHeight * 0.5f)
                .coerceIn(2.dp.toPx() / scale, (size.height - labelHeight - 2.dp.toPx() / scale).coerceAtLeast(0f))
            val bounds = Rect(left, top, left + labelWidth, top + labelHeight)
            val overlaps = occupiedLabels.any { other ->
                bounds.left < other.right && bounds.right > other.left && bounds.top < other.bottom && bounds.bottom > other.top
            }
            if (overlaps && !selected) return@forEach
            occupiedLabels += bounds

            val nationColor = civilizationPalette[settlement.civilizationIndex % civilizationPalette.size]
            drawRoundRect(
                color = Color(0xE6091016),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                cornerRadius = CornerRadius(4.dp.toPx() / scale),
            )
            drawRoundRect(
                color = if (selected) nationColor.copy(alpha = 0.74f) else Color(0xFF85939A).copy(alpha = 0.32f),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                cornerRadius = CornerRadius(4.dp.toPx() / scale),
                style = Stroke((0.8f / scale).coerceAtLeast(0.25f)),
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    label,
                    bounds.left + paddingX,
                    bounds.bottom - paddingY - textSize * 0.12f,
                    paint,
                )
            }
        }

        // Frame: black outer cut and warm inner keyline instead of a generic white rectangle.
        drawRect(Color.Black.copy(alpha = 0.72f), style = Stroke(width = 3.5f))
        drawRect(Color(0xFFE0C675).copy(alpha = 0.18f), style = Stroke(width = 1.0f))
        }
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
