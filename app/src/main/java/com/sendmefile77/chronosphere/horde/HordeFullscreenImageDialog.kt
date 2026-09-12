package com.sendmefile77.chronosphere.horde

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun HordeFullscreenImageDialog(
    bitmap: ImageBitmap,
    contentDescription: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(
                    text = "Закрити",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Торкніться екрана, щоб закрити",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}
