package com.sendmefile77.chronosphere

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.civilization.Civilization
import com.sendmefile77.chronosphere.horde.HordeFullscreenImageDialog
import com.sendmefile77.chronosphere.horde.ImageGenerationProvider
import com.sendmefile77.chronosphere.simulation.SimulationClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal enum class GalleryImageKind(val displayNameUk: String) {
    PERSON("Персонаж"),
    CHRONICLE("Хроніка"),
}

internal data class GalleryCapture(
    val worldSeed: Long,
    val civilizationIds: List<String>,
    val kind: GalleryImageKind,
    val subject: String,
    val tick: Long,
)

internal data class GalleryImageItem(
    val id: String,
    val worldSeed: Long,
    val civilizationIds: List<String>,
    val kind: GalleryImageKind,
    val subject: String,
    val tick: Long,
    val provider: String,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val imageFile: File,
)

/** Permanent archive: generated variants survive cache replacement and app restarts. */
internal object GeneratedImageGalleryStore {
    private const val DIRECTORY = "generated-gallery-v1"
    private val lock = Any()

    fun save(
        filesDir: File,
        capture: GalleryCapture?,
        bytes: ByteArray,
        provider: ImageGenerationProvider,
        width: Int,
        height: Int,
    ) {
        if (capture == null || bytes.isEmpty()) return
        synchronized(lock) {
            val worldDir = File(File(filesDir, DIRECTORY), capture.worldSeed.toString()).apply { mkdirs() }
            val contentHash = sha256(bytes).take(24)
            val captureHash = sha256(
                buildString {
                    append(capture.civilizationIds.sorted().joinToString(","))
                    append('|').append(capture.kind.name)
                    append('|').append(capture.subject)
                    append('|').append(capture.tick)
                }.toByteArray(Charsets.UTF_8),
            ).take(12)
            val id = "$contentHash-$captureHash"
            val imageFile = File(worldDir, "$id.img")
            val metaFile = File(worldDir, "$id.json")
            if (!imageFile.exists()) imageFile.writeBytes(bytes)
            if (!metaFile.exists()) {
                val json = JSONObject()
                    .put("id", id)
                    .put("worldSeed", capture.worldSeed)
                    .put("civilizationIds", JSONArray(capture.civilizationIds.distinct()))
                    .put("kind", capture.kind.name)
                    .put("subject", capture.subject.take(120))
                    .put("tick", capture.tick)
                    .put("provider", provider.name)
                    .put("width", width)
                    .put("height", height)
                    .put("createdAt", System.currentTimeMillis())
                metaFile.writeText(json.toString(), Charsets.UTF_8)
            }
        }
    }

    fun list(filesDir: File, worldSeed: Long): List<GalleryImageItem> = synchronized(lock) {
        val worldDir = File(File(filesDir, DIRECTORY), worldSeed.toString())
        if (!worldDir.isDirectory) return@synchronized emptyList()
        worldDir.listFiles { file -> file.extension == "json" }
            .orEmpty()
            .mapNotNull(::readItem)
            .sortedByDescending { it.createdAt }
    }

    private fun readItem(metaFile: File): GalleryImageItem? = runCatching {
        val json = JSONObject(metaFile.readText(Charsets.UTF_8))
        val idsJson = json.optJSONArray("civilizationIds") ?: JSONArray()
        val ids = (0 until idsJson.length()).mapNotNull { index ->
            idsJson.optString(index).takeIf { it.isNotBlank() }
        }
        val id = json.getString("id")
        val imageFile = File(metaFile.parentFile, "$id.img")
        if (!imageFile.isFile) return@runCatching null
        GalleryImageItem(
            id = id,
            worldSeed = json.getLong("worldSeed"),
            civilizationIds = ids,
            kind = GalleryImageKind.valueOf(json.getString("kind")),
            subject = json.optString("subject").ifBlank { "Кадр" },
            tick = json.optLong("tick", 0L),
            provider = json.optString("provider"),
            width = json.optInt("width", 0),
            height = json.optInt("height", 0),
            createdAt = json.optLong("createdAt", metaFile.lastModified()),
            imageFile = imageFile,
        )
    }.getOrNull()

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
}

@Composable
internal fun GeneratedImageGalleryPanel(
    worldSeed: Long,
    civilizations: List<Civilization>,
    clock: SimulationClock,
) {
    val context = LocalContext.current.applicationContext
    var expanded by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var selectedCivilizationId by remember(worldSeed) { mutableStateOf<String?>(null) }
    var selectedKind by remember(worldSeed) { mutableStateOf<GalleryImageKind?>(null) }
    val items = remember(worldSeed, refreshNonce, expanded) {
        GeneratedImageGalleryStore.list(context.filesDir, worldSeed)
    }
    val filtered = items.filter { item ->
        (selectedCivilizationId == null || selectedCivilizationId in item.civilizationIds) &&
            (selectedKind == null || item.kind == selectedKind)
    }
    var fullscreen by remember { mutableStateOf<GalleryImageItem?>(null) }
    val personCount = items.count { it.kind == GalleryImageKind.PERSON }
    val chronicleCount = items.count { it.kind == GalleryImageKind.CHRONICLE }

    PanelCard(accent = MaterialTheme.colorScheme.secondary) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Галерея світу", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Усі згенеровані варіанти зберігаються окремо від кешу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill("${items.size} кадрів", color = MaterialTheme.colorScheme.secondary)
                TextButton(onClick = { expanded = !expanded; refreshNonce += 1 }) {
                    Text(if (expanded) "Згорнути" else "Відкрити")
                }
            }

            if (!expanded && items.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    StatusPill("Люди · $personCount", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                    StatusPill("Хроніка · $chronicleCount", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
                }
            }

            if (expanded) {
                Text("Тип кадру", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = selectedKind == null,
                        onClick = { selectedKind = null },
                        label = { Text("Усі · ${items.size}") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = selectedKind == GalleryImageKind.PERSON,
                        onClick = { selectedKind = GalleryImageKind.PERSON },
                        label = { Text("Люди · $personCount") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = selectedKind == GalleryImageKind.CHRONICLE,
                        onClick = { selectedKind = GalleryImageKind.CHRONICLE },
                        label = { Text("Хроніка · $chronicleCount") },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (civilizations.size > 1) {
                    Text("Плем’я / держава", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = selectedCivilizationId == null,
                            onClick = { selectedCivilizationId = null },
                            label = { Text("Усі") },
                        )
                        civilizations.forEach { civilization ->
                            val count = items.count { civilization.id in it.civilizationIds }
                            FilterChip(
                                selected = selectedCivilizationId == civilization.id,
                                onClick = { selectedCivilizationId = civilization.id },
                                label = { Text("${civilization.name} · $count") },
                            )
                        }
                    }
                } else {
                    civilizations.firstOrNull()?.let { civilization ->
                        StatusPill(civilization.name, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Показано ${filtered.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { refreshNonce += 1 }, shape = ChronosphereSmallShape) {
                        Text("Оновити")
                    }
                }

                if (filtered.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ChronosphereSmallShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    ) {
                        Text(
                            "За цим фільтром ще немає збережених генерацій.",
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    filtered.take(40).chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            rowItems.forEach { item ->
                                GalleryThumbnail(
                                    item = item,
                                    year = clock.at(item.tick).year,
                                    modifier = Modifier.weight(1f),
                                    onClick = { fullscreen = item },
                                )
                            }
                            if (rowItems.size == 1) {
                                Surface(modifier = Modifier.weight(1f), color = Color.Transparent) {}
                            }
                        }
                    }
                }
            }
        }
    }

    fullscreen?.let { item ->
        val bitmap = remember(item.id) {
            BitmapFactory.decodeFile(item.imageFile.absolutePath)?.asImageBitmap()
        }
        if (bitmap != null) {
            HordeFullscreenImageDialog(
                bitmap = bitmap,
                contentDescription = item.subject,
                onDismiss = { fullscreen = null },
            )
        }
    }
}

@Composable
private fun GalleryThumbnail(
    item: GalleryImageItem,
    year: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bitmap = remember(item.id, item.imageFile.lastModified()) {
        BitmapFactory.decodeFile(
            item.imageFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = 4 },
        )?.asImageBitmap()
    }
    val ratio = when {
        item.width > 0 && item.height > 0 -> (item.width.toFloat() / item.height.toFloat()).coerceIn(0.72f, 1.78f)
        item.kind == GalleryImageKind.CHRONICLE -> 16f / 9f
        else -> 0.78f
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        tonalElevation = 1.dp,
    ) {
        Column {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = item.subject,
                    modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    item.subject,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusPill(item.kind.displayNameUk, color = if (item.kind == GalleryImageKind.PERSON) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    Text(
                        "$year · ${providerLabel(item.provider)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun providerLabel(provider: String): String = when (provider) {
    "LOCAL_DREAM" -> "Local Dream"
    "AI_HORDE" -> "AI Horde"
    "CACHE" -> "кеш"
    else -> provider.replace('_', ' ').lowercase().ifBlank { "генератор" }
}
