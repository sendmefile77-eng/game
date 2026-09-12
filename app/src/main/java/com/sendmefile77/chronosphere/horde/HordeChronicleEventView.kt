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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.CancellationException
import java.io.File

@Composable
internal fun HordeChronicleEventView(
    request: HordeImageRequest,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(12f / 7f),
) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { HordeImageCache(File(context.filesDir, GENERATED_IMAGE_CACHE_DIRECTORY)) }
    var retryNonce by remember(request.cacheKey) { mutableStateOf(0) }
    var showFullscreen by remember(request.cacheKey) { mutableStateOf(false) }
    var state by remember(request.cacheKey) { mutableStateOf<ChronicleHordeUiState>(ChronicleHordeUiState.Loading) }

    LaunchedEffect(request.cacheKey, retryNonce) {
        state = ChronicleHordeUiState.Loading
        val attempt = if (retryNonce == 0) request else request.copy(seed = "${request.seed}:variant:$retryNonce")
        val timeout = if (request.qualityPriority) 135_000L else 75_000L
        try {
            val prepared = HordeGenerationCoordinator.load(
                filesDir = context.filesDir,
                request = attempt,
                timeoutMillis = timeout,
                pollIntervalMillis = 3_000L,
            )
            state = ChronicleHordeUiState.Ready(
                bytes = prepared.bytes,
                model = prepared.model,
                provider = prepared.provider,
                width = prepared.actualWidth ?: request.width,
                height = prepared.actualHeight ?: request.height,
                fallbackNote = prepared.fallbackNote,
            )
        } catch (cancelled: CancellationException) {
            // Changing tabs only detaches this observer; the process-level coordinator keeps the
            // Local Dream/Horde job alive and stores its result in cache for the next visit.
            throw cancelled
        } catch (error: Throwable) {
            state = ChronicleHordeUiState.Failed(error.message ?: "невідома помилка")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (val current = state) {
            ChronicleHordeUiState.Loading -> {
                Surface(
                    modifier = modifier,
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 2.dp,
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (request.qualityPriority) {
                                "Local Dream → AI Horde · якісний кадр події генерується у фоні…"
                            } else {
                                "Local Dream → AI Horde · ілюстрація події генерується у фоні…"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Ілюстрація події хроніки",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = buildString {
                                append(current.provider.displayNameUk)
                                append(" · ${current.width}×${current.height}")
                                current.model?.let { append(" · $it") }
                                append(" · торкніться для перегляду")
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Інший кадр",
                            modifier = Modifier.clickable {
                                cache.remove(request.cacheKey)
                                retryNonce += 1
                            }.padding(vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (current.provider == ImageGenerationProvider.AI_HORDE && current.fallbackNote != null) {
                        Text(
                            text = "Local Dream → Horde: ${current.fallbackNote}",
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
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Генерація · ${message.take(100)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Спробувати ще",
                    modifier = Modifier.clickable(onClick = onRetry).padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
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
