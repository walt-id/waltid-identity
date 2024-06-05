package id.walt.webwallet.usecase.event

import id.walt.webwallet.service.events.*
import id.walt.webwallet.usecase.entity.EntityNameResolutionUseCase
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID

class EventFilterUseCase(
    private val service: EventService,
    private val issuerNameResolutionUseCase: EntityNameResolutionUseCase,
    private val verifierNameResolutionUseCase: EntityNameResolutionUseCase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun filter(accountId: UUID, walletId: UUID, filter: EventLogFilter) = runCatching {
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
    }.fold(onSuccess = {
        it.copy(
            //TODO: normally, would build a separate dto out of db data
            items = tryResolveEntityNames(it.items)
        )
    }, onFailure = { EventLogFilterErrorResult(reason = it.localizedMessage) })

    //TODO: move to separate use case
    private fun computeCurrentStartingAfter(afterItemIndex: Long): String? = let {
        afterItemIndex.takeIf { it >= 0 }?.toString()
    }

    //TODO: move to separate use case
    private fun computeNextStartingAfter(afterItemIndex: Long, pageSize: Int, count: Long): String? = let {
        val itemIndex = afterItemIndex + pageSize
        itemIndex.takeIf { it < count }?.toString()
    }

    private suspend fun tryResolveEntityNames(items: List<Event>) = items.map { event ->
        tryDecodeData<CredentialEventData>(event.data)?.let { data ->
            data.organization?.let { event.copy(data = updateEntityName(data, it)) }
        } ?: event
    }

    private inline fun <reified T> tryDecodeData(data: JsonObject) =
        runCatching { json.decodeFromJsonElement<T>(data) }.getOrNull()

    private suspend fun updateEntityName(
        data: CredentialEventData,
        entity: CredentialEventDataActor.Organization
    ) = when (entity) {
        is CredentialEventDataActor.Organization.Issuer -> entity.copy(
            name = issuerNameResolutionUseCase.resolve(entity.did)
        )

        is CredentialEventDataActor.Organization.Verifier -> entity.copy(
            name = verifierNameResolutionUseCase.resolve(entity.did)
        )
    }.let {
        json.encodeToJsonElement(
            data.copy(organization = it)
        )
    }.jsonObject
}