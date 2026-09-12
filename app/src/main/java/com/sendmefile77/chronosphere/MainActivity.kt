package com.sendmefile77.chronosphere

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.sendmefile77.chronosphere.horde.LocalDreamModelPackRuntime
import com.sendmefile77.chronosphere.horde.LocalDreamModelPackStore
import com.sendmefile77.chronosphere.llm.TellamaRuntime
import com.sendmefile77.chronosphere.llm.TellamaSettingsStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.rgb(8, 13, 18)
            window.navigationBarColor = Color.rgb(8, 13, 18)
        }
        TellamaRuntime.configureApiKey(TellamaSettingsStore.load(this))
        LocalDreamModelPackRuntime.select(LocalDreamModelPackStore.load(this))
        setContent {
            Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                ChronosphereGameApp()
            }
        }
    }

    override fun onStop() {
        // Oppo/ColorOS and other aggressive Android builds may destroy the Activity immediately
        // after it goes to background. Persist the last coherent world before that can happen.
        GameAutoResume.flush(applicationContext)
        super.onStop()
    }
}
