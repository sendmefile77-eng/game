package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
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
) {
    val isActive: Boolean get() = bridge != null && dressedMethod != null && undressedMethod != null

    fun resolveCharacterCard(request: AdultEventRequest, undressed: Boolean): ResolvedScene? {
        val target = bridge ?: return null
        val method = if (undressed) undressedMethod else dressedMethod ?: return null
        return runCatching { method.invoke(target, request) as? ResolvedScene }.getOrNull()
    }

    companion object {
        private const val BRIDGE_CLASS = "com.sendmefile77.chronosphere.adult.AdultSceneBridge"

        fun load(): AdultSceneRuntime {
            val loaded = runCatching {
                val clazz = Class.forName(BRIDGE_CLASS)
                val instance = clazz.getDeclaredConstructor().newInstance()
                val dressed = clazz.getMethod("dressedCharacterCard", AdultEventRequest::class.java)
                val undressed = clazz.getMethod("undressedCharacterCard", AdultEventRequest::class.java)
                AdultSceneRuntime(instance, dressed, undressed)
            }.getOrNull()
            return loaded ?: AdultSceneRuntime(null, null, null)
        }
    }
}
