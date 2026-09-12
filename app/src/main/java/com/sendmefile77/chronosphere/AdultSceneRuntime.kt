package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultVisualSceneDescriptor
import com.sendmefile77.chronosphere.scene.ResolvedScene
import java.lang.reflect.Method

/**
 * Reflective app-side adapter for the optional feature/adult scene bridge.
 * The app never compiles against the adult implementation itself.
 */
internal class AdultSceneRuntime private constructor(
    private val bridge: Any?,
    private val dressedMethod: Method?,
    private val undressedMethod: Method?,
    private val dressedVisualMethod: Method?,
    private val undressedVisualMethod: Method?,
    private val eventMethod: Method?,
    private val eventVisualMethod: Method?,
) {
    val isActive: Boolean get() = bridge != null && dressedMethod != null && undressedMethod != null
    val hasStructuredVisuals: Boolean get() =
        bridge != null && dressedVisualMethod != null && undressedVisualMethod != null && eventVisualMethod != null

    fun resolveCharacterCard(request: AdultEventRequest, undressed: Boolean): ResolvedScene? {
        val target = bridge ?: return null
        val method = (if (undressed) undressedMethod else dressedMethod) ?: return null
        return runCatching { method.invoke(target, request) as? ResolvedScene }.getOrNull()
    }

    fun resolveCharacterVisual(request: AdultEventRequest, undressed: Boolean): AdultVisualSceneDescriptor? {
        val target = bridge ?: return null
        val method = (if (undressed) undressedVisualMethod else dressedVisualMethod) ?: return null
        return runCatching { method.invoke(target, request) as? AdultVisualSceneDescriptor }.getOrNull()
    }

    fun resolveEventScene(request: AdultEventRequest): ResolvedScene? {
        val target = bridge ?: return null
        val method = eventMethod ?: return null
        return runCatching { method.invoke(target, request) as? ResolvedScene }.getOrNull()
    }

    fun resolveEventVisual(request: AdultEventRequest): AdultVisualSceneDescriptor? {
        val target = bridge ?: return null
        val method = eventVisualMethod ?: return null
        return runCatching { method.invoke(target, request) as? AdultVisualSceneDescriptor }.getOrNull()
    }

    companion object {
        private const val BRIDGE_CLASS = "com.sendmefile77.chronosphere.adult.AdultSceneBridge"

        fun load(): AdultSceneRuntime {
            val loaded = runCatching {
                val clazz = Class.forName(BRIDGE_CLASS)
                val instance = clazz.getDeclaredConstructor().newInstance()
                val dressed = clazz.getMethod("dressedCharacterCard", AdultEventRequest::class.java)
                val undressed = clazz.getMethod("undressedCharacterCard", AdultEventRequest::class.java)
                val dressedVisual = clazz.getMethod("dressedCharacterVisual", AdultEventRequest::class.java)
                val undressedVisual = clazz.getMethod("undressedCharacterVisual", AdultEventRequest::class.java)
                val event = clazz.getMethod("eventScene", AdultEventRequest::class.java)
                val eventVisual = clazz.getMethod("eventVisual", AdultEventRequest::class.java)
                AdultSceneRuntime(
                    bridge = instance,
                    dressedMethod = dressed,
                    undressedMethod = undressed,
                    dressedVisualMethod = dressedVisual,
                    undressedVisualMethod = undressedVisual,
                    eventMethod = event,
                    eventVisualMethod = eventVisual,
                )
            }.getOrNull()
            return loaded ?: AdultSceneRuntime(null, null, null, null, null, null, null)
        }
    }
}
