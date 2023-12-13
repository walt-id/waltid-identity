package id.walt.webwallet.service.events

data class EventLogFilter(
    val limit: Int = 0,
    val event: String? = null,
    val action: String? = null,
    val tenant: String? = null,
    val startingAfter: String? = null,
    val sortBy: String?=null,
    val sortOrder: String?=null,
)
