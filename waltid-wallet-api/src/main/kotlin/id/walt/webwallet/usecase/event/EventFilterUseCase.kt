package id.walt.webwallet.usecase.event

import id.walt.webwallet.service.events.*
import id.walt.webwallet.service.issuers.IssuerNameResolutionService
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.uuid.UUID

class EventFilterUseCase(
    private val service: EventService,
    private val issuerNameResolutionService: IssuerNameResolutionService,
//    private val verifierNameResolutionService: VerifierNameResolutionService,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val eventFilterByActionCondition: (event: Event, actions: List<EventType.Action>) -> Boolean = { e, a ->
        e.action in a.map { it.toString() } && e.event in a.map { it.type }
    }

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
        val items = resolveEntityNames(it.items.filter {
            eventFilterByActionCondition(it, listOf(EventType.Credential.Receive, EventType.Credential.Present))
        }).plus(it.items.filter {
            !eventFilterByActionCondition(it, listOf(EventType.Credential.Receive, EventType.Credential.Present))
        })

        it.copy(
            items = items
        )
    }, onFailure = { EventLogFilterErrorResult(reason = it.localizedMessage) })

    private fun computeCurrentStartingAfter(afterItemIndex: Long): String? = let {
        afterItemIndex.takeIf { it >= 0 }?.toString()
    }

    private fun computeNextStartingAfter(afterItemIndex: Long, pageSize: Int, count: Long): String? = let {
        val itemIndex = afterItemIndex + pageSize
        itemIndex.takeIf { it < count }?.toString()
    }

    private suspend fun resolveEntityNames(items: List<Event>): List<Event> = items.groupBy {
        JsonUtils.tryGetData(it.data, "organization")
    }.mapNotNull { m ->
        m.key?.let {
            //TODO: parameterize organization type
            json.decodeFromJsonElement<CredentialEventDataActor.Organization.Issuer>(it)
        }?.let { entity ->
            issuerNameResolutionService.resolve(entity.did).getOrNull()?.let { entityName ->
                m.value.map { e ->
                    json.decodeFromJsonElement<CredentialEventData>(e.data).let { d ->
                        e.copy(
                            data = json.encodeToJsonElement(
                                d.copy(
                                    organization = (d.organization as? CredentialEventDataActor.Organization.Issuer)!!.copy(
                                        name = entityName
                                    )
                                )
                            ).jsonObject
                        )
                    }
                }
            }
        } ?: m.value
    }.flatten()
}

//EventType.Credential.Present