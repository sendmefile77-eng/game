package com.sendmefile77.chronosphere.textgen

import com.sendmefile77.chronosphere.history.HistoricalEvent

class TemplateTextEngine {
    fun short(event: HistoricalEvent): String = when (event.type) {
        "STATE_FOUNDED" -> "A new state was founded."
        "WAR_STARTED" -> "A war began between rival powers."
        "CITY_FOUNDED" -> "A new settlement was founded."
        else -> "An event of type ${event.type} occurred."
    }
}
