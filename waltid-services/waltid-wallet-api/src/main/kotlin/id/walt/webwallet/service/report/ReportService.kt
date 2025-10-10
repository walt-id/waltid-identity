@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.report

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.events.EventService
import id.walt.webwallet.service.events.EventType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


interface ReportService<T> {
    fun frequent(parameter: ReportRequestParameter): List<T>

    @OptIn(ExperimentalUuidApi::class)
    class Credentials(private val credentialService: CredentialsService, private val eventService: EventService) :
        ReportService<WalletCredential> {

        override fun frequent(parameter: ReportRequestParameter): List<WalletCredential> =
            (parameter as? CredentialReportRequestParameter)?.let { param ->
                frequent(param.walletId, EventType.Credential.Present, param.limit).groupBy { it.credentialId }
                    .let { group ->
                        val sorted = group.keys.sortedByDescending {
                            group[it]?.count()
                        }
                        credentialService.get(param.walletId, sorted.filterNotNull())
                    }
            } ?: emptyList()

        private fun frequent(walletId: Uuid, action: EventType.Action, limit: Int?) = eventService.get(
            accountId = Uuid.NIL,
            walletId = walletId,
            limit = limit,
            offset = 0,
            sortOrder = "ASC",
            sortBy = "",
            dataFilter = mapOf(
                "event" to listOf(action.type), "action" to listOf(action.toString())
            )
        )
    }
}

abstract class ReportRequestParameter(
    open val walletId: Uuid,
    open val limit: Int?,
)

data class CredentialReportRequestParameter(
    override val walletId: Uuid, override val limit: Int?,
) : ReportRequestParameter(walletId, limit)
