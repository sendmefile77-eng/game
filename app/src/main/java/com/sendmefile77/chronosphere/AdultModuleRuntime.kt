package com.sendmefile77.chronosphere

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.NoOpAdultModule

/**
 * Keeps the app compiled only against the stable contract while allowing a full build
 * to package feature/adult as an optional runtime implementation.
 */
internal object AdultModuleRuntime {
    private const val IMPLEMENTATION =
        "com.sendmefile77.chronosphere.adult.DeterministicAdultModule"

    fun load(): AdultModule {
        val loaded = runCatching {
            val clazz = Class.forName(IMPLEMENTATION)
            clazz.getDeclaredConstructor().newInstance() as? AdultModule
        }.getOrNull()
        return loaded?.takeIf { it.contractVersion == ADULT_CONTRACT_VERSION } ?: NoOpAdultModule
    }

    fun isActive(module: AdultModule): Boolean = module !== NoOpAdultModule
}
