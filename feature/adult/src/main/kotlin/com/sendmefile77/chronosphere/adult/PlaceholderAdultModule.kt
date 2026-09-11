package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult

/** Compatibility alias. The isolated engine lives in [DeterministicAdultModule]. */
class PlaceholderAdultModule(
    private val engine: DeterministicAdultModule = DeterministicAdultModule(),
) : AdultModule {
    override val contractVersion: Int = engine.contractVersion
    override fun evaluate(request: AdultEventRequest): AdultModuleResult = engine.evaluate(request)
}
