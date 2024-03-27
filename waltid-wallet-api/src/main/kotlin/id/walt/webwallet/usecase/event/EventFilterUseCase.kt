package id.walt.webwallet.usecase.event

import id.walt.webwallet.service.events.EventLogFilter
import id.walt.webwallet.service.events.EventLogFilterDataResult
import id.walt.webwallet.service.events.EventLogFilterErrorResult
import id.walt.webwallet.service.events.EventService
import kotlinx.uuid.UUID

class EventFilterUseCase(
    private val service: EventService,
) {
    fun filter(accountId: UUID, walletId: UUID, filter: EventLogFilter) = runCatching {
        val startingAfterItemIndex = filter.startingAfter?.toLongOrNull()?.takeIf { it >= 0 } ?: -1L
        val pageSize = filter.limit ?: -1
        val count = service.count(walletId, filter.data)
        val offset = startingAfterItemIndex + 1
        val events = service.get(
            accountId = accountId,
            walletId = walletId,
            limit = filter.limit,
            offset = offset,
            sortOrder = filter.sortOrder ?: "asc",
            sortBy = filter.sortBy ?: "",
            dataFilter = filter.data
        )
        EventLogFilterDataResult(
            items = events,
            count = events.size,
            currentStartingAfter = computeCurrentStartingAfter(startingAfterItemIndex),
            nextStartingAfter = computeNextStartingAfter(startingAfterItemIndex, pageSize, count)
        )
    }.fold(onSuccess = { it }, onFailure = { EventLogFilterErrorResult(reason = it.localizedMessage) })

    private fun computeCurrentStartingAfter(afterItemIndex: Long): String? = let {
        afterItemIndex.takeIf { it >= 0 }?.toString()
    }

    private fun computeNextStartingAfter(afterItemIndex: Long, pageSize: Int, count: Long): String? = let {
        val itemIndex = afterItemIndex + pageSize
        itemIndex.takeIf { it < count }?.toString()
    }
}