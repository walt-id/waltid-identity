package id.walt.webwallet.usecase.event

import id.walt.webwallet.service.events.*
import id.walt.webwallet.usecase.issuer.IssuerNameResolutionUseCase
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID

class EventFilterUseCase(
    private val service: EventService,
    private val issuerNameResolutionUseCase: IssuerNameResolutionUseCase,
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
//        val items = resolveEntityNames(it.items.filter {
//            eventFilterByActionCondition(it, listOf(EventType.Credential.Receive, EventType.Credential.Present))
//        }).plus(it.items.filter {
//            !eventFilterByActionCondition(it, listOf(EventType.Credential.Receive, EventType.Credential.Present))
//        })

        it.copy(
            items = tryResolveName(it.items)
        )
    }, onFailure = { EventLogFilterErrorResult(reason = it.localizedMessage) })

    private fun computeCurrentStartingAfter(afterItemIndex: Long): String? = let {
        afterItemIndex.takeIf { it >= 0 }?.toString()
    }

    private fun computeNextStartingAfter(afterItemIndex: Long, pageSize: Int, count: Long): String? = let {
        val itemIndex = afterItemIndex + pageSize
        itemIndex.takeIf { it < count }?.toString()
    }

    private suspend fun tryResolveName(items: List<Event>) = items.map { event ->
        tryDecodeData(event.data)?.let {
            when (it.organization) {
                is CredentialEventDataActor.Organization.Issuer -> event.wallet?.let { w ->
                    event.copy(
                        data = updateIssuerName(w, it, it.organization)
                    )
                }

                is CredentialEventDataActor.Organization.Verifier -> event
                else -> event
            }
        } ?: event
    }

    private fun tryDecodeData(data: JsonObject) =
        runCatching { json.decodeFromJsonElement<CredentialEventData>(data) }.getOrNull()

    private suspend fun updateIssuerName(
        wallet: UUID,
        data: CredentialEventData,
        issuer: CredentialEventDataActor.Organization.Issuer
    ) = json.encodeToJsonElement(
        data.copy(
            organization = issuer.copy(
                name = issuerNameResolutionUseCase.resolve(wallet, issuer.did)
            )
        )
    ).jsonObject

    //TODO: duplicated
//    private suspend fun updateVerifierName(
//        data: CredentialEventData,
//        verifier: CredentialEventDataActor.Organization.Verifier
//    ) = json.encodeToJsonElement(
//        data.copy(
//            organization = verifier.copy(
//                name = verifierNameResolutionService.resolve(verifier.did).getOrNull() ?: verifier.did
//            )
//        )
//    ).jsonObject

    //TODO: solve original order
//    private suspend fun resolveEntityNames(items: List<Event>): List<Event> = items.groupBy {
//        JsonUtils.tryGetData(it.data, "organization")
//    }.mapNotNull { m ->
//        m.key?.let {
//            //TODO: parameterize organization type
//            json.decodeFromJsonElement<CredentialEventDataActor.Organization.Issuer>(it)
//        }?.let { entity ->
//            issuerNameResolutionUseCase.resolve(entity.did).getOrNull()?.let { entityName ->
//                m.value.map { e ->
//                    json.decodeFromJsonElement<CredentialEventData>(e.data).let { d ->
//                        e.copy(
//                            data = json.encodeToJsonElement(
//                                d.copy(
//                                    organization = (d.organization as? CredentialEventDataActor.Organization.Issuer)!!.copy(
//                                        name = entityName
//                                    )
//                                )
//                            ).jsonObject
//                        )
//                    }
//                }
//            }
//        } ?: m.value
//    }.flatten()
}