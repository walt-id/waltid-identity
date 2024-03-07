package id.walt.webwallet.service.report

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.events.EventService
import id.walt.webwallet.service.events.EventType
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.uuid.UUID

interface ReportService<T> {
    fun frequent(parameter: ReportRequestParameter): List<T>

    class Credentials(private val credentialService: CredentialsService, private val eventService: EventService) :
        ReportService<WalletCredential> {

        override fun frequent(parameter: ReportRequestParameter): List<WalletCredential> =
            (parameter as? CredentialReportRequestParameter)?.let { param ->
                frequent(param.walletId, EventType.Credential.Present, param.limit).mapNotNull {
                    it.jsonObject["credentialId"]?.jsonPrimitive?.content
                }.groupBy { it }.let { group ->
                    val sorted = group.keys.sortedByDescending {
                        group[it]?.count()
                    }
                    credentialService.get(sorted)
                }
            } ?: emptyList()

        private fun frequent(walletId: UUID, action: EventType.Action, limit: Int) = eventService.get(
            accountId = UUID.NIL,
            walletId = walletId,
            limit = limit,
            offset = 0,
            sortOrder = "ASC",
            sortBy = "",
            dataFilter = mapOf(
                "event" to action.type, "action" to action.toString()
            )
        ).map { it.data }
    }
}

abstract class ReportRequestParameter(
    open val walletId: UUID,
    open val limit: Int,
)

data class CredentialReportRequestParameter(
    override val walletId: UUID, override val limit: Int
) : ReportRequestParameter(walletId, limit)
