package id.walt.webwallet.service.events

data class EventLogFilter(
    val limit: Int? = null,
    val startingAfter: String? = null,
    val sortBy: String? = null,
    val sortOrder: String? = null,
    val data: Map<String, String> = emptyMap()
)
