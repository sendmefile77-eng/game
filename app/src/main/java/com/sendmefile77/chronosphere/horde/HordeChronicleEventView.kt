package com.sendmefile77.chronosphere.horde

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.ChronosphereSmallShape
import com.sendmefile77.chronosphere.GalleryCapture
import com.sendmefile77.chronosphere.GeneratedImageGalleryStore
import com.sendmefile77.chronosphere.StatusPill
import kotlinx.coroutines.CancellationException
import java.io.File

@Composable
internal fun HordeChronicleEventView(
    request: HordeImageRequest,
    galleryCapture: GalleryCapture? = null,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { HordeImageCache(File(context.filesDir, GENERATED_IMAGE_CACHE_DIRECTORY)) }
    var retryNonce by remember(request.cacheKey) { mutableStateOf(0) }
    var showFullscreen by remember(request.cacheKey) { mutableStateOf(false) }
    var state by remember(request.cacheKey) { mutableStateOf<ChronicleHordeUiState>(ChronicleHordeUiState.Loading) }
    val jobProgress by remember(request.cacheKey) {
        HordeGenerationCoordinator.observeProgress(request.cacheKey)
    }.collectAsState()

    LaunchedEffect(request.cacheKey, retryNonce, galleryCapture) {
        state = ChronicleHordeUiState.Loading
        val attempt = if (retryNonce == 0) request else request.copy(seed = "${request.seed}:variant:$retryNonce")
        val localDreamAttempt = attempt.copy(
            steps = LOCAL_DREAM_DMD2_STEPS,
            cfgScale = LOCAL_DREAM_DMD2_CFG,
            samplerName = "lcm",
        )
        val timeout = if (request.qualityPriority) 120_000L else 75_000L
        try {
            val prepared = HordeGenerationCoordinator.load(
                filesDir = context.filesDir,
                request = attempt,
                timeoutMillis = timeout,
                pollIntervalMillis = 3_000L,
                localDreamRequest = localDreamAttempt,
                localDreamTimeoutMillis = LOCAL_DREAM_CHRONICLE_TIMEOUT_MS,
            )
            val width = prepared.actualWidth ?: request.width
            val height = prepared.actualHeight ?: request.height
            GeneratedImageGalleryStore.save(
                filesDir = context.filesDir,
                capture = galleryCapture,
                bytes = prepared.bytes,
                provider = prepared.provider,
                width = width,
                height = height,
            )
            state = ChronicleHordeUiState.Ready(
                bytes = prepared.bytes,
                model = prepared.model,
                provider = prepared.provider,
                width = width,
                height = height,
                fallbackNote = prepared.fallbackNote,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            state = ChronicleHordeUiState.Failed(error.message ?: "невідома помилка")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        when (val current = state) {
            ChronicleHordeUiState.Loading -> {
                Surface(
                    modifier = modifier,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        GenerationProgressPlaque(
                            progress = jobProgress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            is ChronicleHordeUiState.Ready -> {
                val bitmap = remember(current.bytes) {
                    BitmapFactory.decodeByteArray(current.bytes, 0, current.bytes.size)?.asImageBitmap()
                }
                if (bitmap == null) {
                    ChronicleFailure(
                        modifier = modifier,
                        message = "отримано пошкоджене зображення",
                        onRetry = {
                            cache.remove(request.cacheKey)
                            retryNonce += 1
                        },
                    )
                } else {
                    Surface(
                        modifier = modifier.clickable { showFullscreen = true },
                        shape = RoundedCornerShape(18.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Ілюстрація події хроніки",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ChronosphereSmallShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StatusPill(
                                    current.provider.displayNameUk,
                                    color = if (current.provider == ImageGenerationProvider.LOCAL_DREAM) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                )
                                Text(
                                    text = buildString {
                                        append("${current.width}×${current.height}")
                                        current.model?.let { append(" · $it") }
                                    },
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "На весь екран",
                                    modifier = Modifier.clickable { showFullscreen = true },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    cache.remove(request.cacheKey)
                                    retryNonce += 1
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ChronosphereSmallShape,
                            ) { Text("Згенерувати інший кадр") }
                        }
                    }
                    if (current.provider == ImageGenerationProvider.AI_HORDE && current.fallbackNote != null) {
                        Text(
                            text = "Local Dream → Horde · ${current.fallbackNote}",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (showFullscreen) {
                        HordeFullscreenImageDialog(
                            bitmap = bitmap,
                            contentDescription = "Ілюстрація події хроніки",
                            onDismiss = { showFullscreen = false },
                        )
                    }
                }
            }

            is ChronicleHordeUiState.Failed -> ChronicleFailure(
                modifier = modifier,
                message = current.message,
                onRetry = {
                    cache.remove(request.cacheKey)
                    retryNonce += 1
                },
            )
        }
    }
}

@Composable
private fun ChronicleFailure(
    modifier: Modifier,
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Генерація · ${message.take(100)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onRetry, shape = ChronosphereSmallShape) { Text("Повторити") }
            }
        }
    }
}

private sealed interface ChronicleHordeUiState {
    data object Loading : ChronicleHordeUiState
    data class Ready(
        val bytes: ByteArray,
        val model: String?,
        val provider: ImageGenerationProvider,
        val width: Int,
        val height: Int,
        val fallbackNote: String?,
    ) : ChronicleHordeUiState
    data class Failed(val message: String) : ChronicleHordeUiState
}

private const val LOCAL_DREAM_DMD2_STEPS = 10
private const val LOCAL_DREAM_DMD2_CFG = 1.5
private const val LOCAL_DREAM_CHRONICLE_TIMEOUT_MS = 60_000L
