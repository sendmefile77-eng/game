package com.sendmefile77.chronosphere.horde

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.sendmefile77.chronosphere.LocalSceneFallbackView
import com.sendmefile77.chronosphere.StatusPill
import com.sendmefile77.chronosphere.scene.ResolvedScene
import kotlinx.coroutines.CancellationException
import java.io.File

@Composable
internal fun HordeSceneView(
    request: HordeImageRequest,
    fallbackScene: ResolvedScene,
    characterKey: String,
    ageYears: Int,
    fitFullBody: Boolean = false,
    galleryCapture: GalleryCapture? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { HordeImageCache(File(context.filesDir, GENERATED_IMAGE_CACHE_DIRECTORY)) }
    val references = remember(context) { HordeCharacterReferenceStore(File(context.filesDir, "horde-character-references")) }
    var retryNonce by remember(request.cacheKey) { mutableStateOf(0) }
    var showFullscreen by remember(request.cacheKey) { mutableStateOf(false) }
    var state by remember(request.cacheKey) { mutableStateOf<HordeUiState>(HordeUiState.Loading) }
    val jobProgress by remember(request.cacheKey) {
        HordeGenerationCoordinator.observeProgress(request.cacheKey)
    }.collectAsState()

    LaunchedEffect(request.cacheKey, retryNonce, galleryCapture) {
        state = HordeUiState.Loading
        val attemptRequest = if (retryNonce == 0) request else request.copy(seed = "${request.seed}:variant:$retryNonce")
        val timeout = if (request.qualityPriority) 135_000L else 75_000L

        try {
            val prepared = HordeGenerationCoordinator.load(
                filesDir = context.filesDir,
                request = attemptRequest,
                timeoutMillis = timeout,
                pollIntervalMillis = 3_000L,
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
            state = HordeUiState.Ready(
                bytes = prepared.bytes,
                model = prepared.model,
                usedReference = prepared.usedReference,
                provider = prepared.provider,
                width = width,
                height = height,
                fallbackNote = prepared.fallbackNote,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            state = HordeUiState.Failed(error.message ?: "невідома помилка")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        when (val current = state) {
            HordeUiState.Loading -> {
                Box {
                    LocalSceneFallbackView(
                        scene = fallbackScene,
                        characterKey = characterKey,
                        ageYears = ageYears,
                        modifier = modifier,
                    )
                    GenerationProgressPlaque(
                        progress = jobProgress,
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                    )
                }
            }

            is HordeUiState.Ready -> {
                val bitmap = remember(current.bytes) {
                    BitmapFactory.decodeByteArray(current.bytes, 0, current.bytes.size)?.asImageBitmap()
                }
                if (bitmap == null) {
                    LocalSceneFallbackView(
                        scene = fallbackScene,
                        characterKey = characterKey,
                        ageYears = ageYears,
                        modifier = modifier,
                    )
                    FailureRow(
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
                            contentDescription = "Згенерований портрет персонажа",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = if (fitFullBody) ContentScale.Fit else ContentScale.Crop,
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
                                        if (current.usedReference) append(" · ref")
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        cache.remove(request.cacheKey)
                                        retryNonce += 1
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = ChronosphereSmallShape,
                                ) { Text("Інший варіант") }
                                if (request.saveResultAsReference && request.referenceCacheKey != null) {
                                    OutlinedButton(
                                        onClick = {
                                            cache.remove(request.cacheKey)
                                            references.remove(request.referenceCacheKey)
                                            retryNonce += 1
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = ChronosphereSmallShape,
                                    ) { Text("Новий образ") }
                                }
                            }
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
                            contentDescription = "Згенерований портрет персонажа",
                            onDismiss = { showFullscreen = false },
                        )
                    }
                }
            }

            is HordeUiState.Failed -> {
                LocalSceneFallbackView(
                    scene = fallbackScene,
                    characterKey = characterKey,
                    ageYears = ageYears,
                    modifier = modifier,
                )
                FailureRow(
                    message = current.message,
                    onRetry = {
                        cache.remove(request.cacheKey)
                        retryNonce += 1
                    },
                )
            }
        }
    }
}

@Composable
private fun FailureRow(
    message: String,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ChronosphereSmallShape,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.06f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Генерація · ${message.take(110)}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onRetry, shape = ChronosphereSmallShape) { Text("Повторити") }
        }
    }
}

private sealed interface HordeUiState {
    data object Loading : HordeUiState
    data class Ready(
        val bytes: ByteArray,
        val model: String?,
        val usedReference: Boolean,
        val provider: ImageGenerationProvider,
        val width: Int,
        val height: Int,
        val fallbackNote: String?,
    ) : HordeUiState
    data class Failed(val message: String) : HordeUiState
}
