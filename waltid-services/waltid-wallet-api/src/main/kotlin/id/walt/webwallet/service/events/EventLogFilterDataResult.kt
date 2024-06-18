package id.walt.webwallet.service.events

import kotlinx.serialization.Serializable

sealed class EventLogFilterResult

@Serializable
data class EventLogFilterDataResult(
    val items: List<Event>,
    val count: Int,
    val currentStartingAfter: String? = null,
    val nextStartingAfter: String? = null,
) : EventLogFilterResult()

@Serializable
data class EventLogFilterErrorResult(
    val reason: String,
    val message: String? = null,
) : EventLogFilterResult()