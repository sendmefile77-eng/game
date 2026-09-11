package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult

/**
 * Isolated adult-module engine for contract v1.
 * Pack/catalog data is selected deterministically from the request; the engine stays pack-agnostic.
 */
class DeterministicAdultModule internal constructor(
    private val registry: AdultPackRegistry,
    private val recipes: AdultVisualRecipeRegistry,
) : AdultModule {
    constructor() : this(AdultPackRegistry.bundled(), AdultVisualRecipeRegistry.bundled())

    override val contractVersion: Int = ADULT_CONTRACT_VERSION

    override fun evaluate(request: AdultEventRequest): AdultModuleResult {
        val fingerprint = AdultFingerprint.of(request)
        val pack = registry.selectPack(request, fingerprint)
        val event = registry.selectEvent(pack, request, fingerprint)
        return AdultModuleResult(
            requestId = request.requestId,
            eventCode = event.code,
            effects = AdultEffectResolver.effects(event, request, fingerprint),
            mediaCue = AdultEffectResolver.mediaCue(event, request, fingerprint, recipes),
        )
    }

    internal fun selectedPackId(request: AdultEventRequest): String {
        val fingerprint = AdultFingerprint.of(request)
        return registry.selectPack(request, fingerprint).id
    }
}
