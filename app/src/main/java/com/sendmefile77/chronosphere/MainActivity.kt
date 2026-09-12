package com.sendmefile77.chronosphere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.sendmefile77.chronosphere.llm.TellamaRuntime
import com.sendmefile77.chronosphere.llm.TellamaSettingsStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TellamaRuntime.configureApiKey(TellamaSettingsStore.load(this))
        setContent {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                ChronosphereGameApp()
            }
        }
    }
}
