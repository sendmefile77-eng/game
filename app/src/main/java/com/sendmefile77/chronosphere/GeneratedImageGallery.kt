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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val items = remember(worldSeed, refreshNonce, expanded) {
        GeneratedImageGalleryStore.list(context.filesDir, worldSeed)
    }
    val filtered = selectedCivilizationId?.let { id -> items.filter { id in it.civilizationIds } } ?: items
    var fullscreen by remember { mutableStateOf<GalleryImageItem?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Галерея світу", fontWeight = FontWeight.Bold)
                    Text(
                        "${items.size} збережених кадрів · окремо від робочого кешу",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { expanded = !expanded; refreshNonce += 1 }) {
                    Text(if (expanded) "Згорнути" else "Відкрити")
                }
            }
            if (expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = selectedCivilizationId == null,
                        onClick = { selectedCivilizationId = null },
                        label = { Text("Усі (${items.size})") },
                    )
                    civilizations.forEach { civilization ->
                        val count = items.count { civilization.id in it.civilizationIds }
                        FilterChip(
                            selected = selectedCivilizationId == civilization.id,
                            onClick = { selectedCivilizationId = civilization.id },
                            label = { Text("${civilization.name} ($count)") },
                        )
                    }
                }
                OutlinedButton(onClick = { refreshNonce += 1 }) { Text("Оновити галерею") }
                if (filtered.isEmpty()) {
                    Text(
                        "Для цього племені ще немає збережених генерацій.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                                Surface(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.surface) {}
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
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
    ) {
        Column {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = item.subject,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.88f),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.subject, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    "${item.kind.displayNameUk} · $year · ${providerLabel(item.provider)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
