package com.sendmefile77.chronosphere.horde

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sendmefile77.chronosphere.LocalSceneFallbackView
import com.sendmefile77.chronosphere.scene.ResolvedScene
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun HordeSceneView(
    request: HordeImageRequest,
    fallbackScene: ResolvedScene,
    characterKey: String,
    ageYears: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val context = LocalContext.current.applicationContext
    val cache = remember(context) { HordeImageCache(File(context.filesDir, "horde-images")) }
    val client = remember { HordeClient() }
    var retryNonce by remember(request.cacheKey) { mutableStateOf(0) }
    var state by remember(request.cacheKey) { mutableStateOf<HordeUiState>(HordeUiState.Loading) }

    LaunchedEffect(request.cacheKey, retryNonce) {
        state = HordeUiState.Loading
        val cached = withContext(Dispatchers.IO) { cache.read(request.cacheKey) }
        if (cached != null) {
            state = HordeUiState.Ready(cached, null)
            return@LaunchedEffect
        }

        try {
            val result = client.generate(
                request = request,
                timeoutMillis = 75_000L,
                pollIntervalMillis = 3_000L,
            )
            withContext(Dispatchers.IO) { cache.write(request.cacheKey, result.imageBytes) }
            state = HordeUiState.Ready(result.imageBytes, result.model)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            state = HordeUiState.Failed(error.message ?: "невідома помилка")
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    Text(
                        text = "AI Horde · генерується…",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                                shape = RoundedCornerShape(topEnd = 8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
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
                        modifier = modifier,
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 2.dp,
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Згенерований портрет персонажа",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    current.model?.let { model ->
                        Text(
                            text = "AI Horde · $model",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    onRetry = { retryNonce += 1 },
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AI Horde · ${message.take(110)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onRetry) { Text("Спробувати ще") }
    }
}

private sealed interface HordeUiState {
    data object Loading : HordeUiState
    data class Ready(val bytes: ByteArray, val model: String?) : HordeUiState
    data class Failed(val message: String) : HordeUiState
}
