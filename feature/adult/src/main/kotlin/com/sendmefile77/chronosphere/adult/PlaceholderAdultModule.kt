package com.sendmefile77.chronosphere.adult

import com.sendmefile77.chronosphere.adultcontracts.ADULT_CONTRACT_VERSION
import com.sendmefile77.chronosphere.adultcontracts.AdultEventRequest
import com.sendmefile77.chronosphere.adultcontracts.AdultModule
import com.sendmefile77.chronosphere.adultcontracts.AdultModuleResult

/** Integration placeholder. Grok owns the eventual implementation behind this contract. */
class PlaceholderAdultModule : AdultModule {
    override val contractVersion: Int = ADULT_CONTRACT_VERSION
    override fun evaluate(request: AdultEventRequest) = AdultModuleResult(request.requestId, "PLACEHOLDER")
}
