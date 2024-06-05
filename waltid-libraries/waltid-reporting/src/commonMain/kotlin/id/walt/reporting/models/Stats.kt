package id.walt.reporting.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class StatReport(
    val timestamp: Instant,
    val serviceReference: String,
    val serviceType: String,
    val events: List<Event>
)

@Serializable
data class Stat(
    val timestamp: Instant,
    val action: String,
    val originatorReference: String,
    val details: JsonObject? = null
)
