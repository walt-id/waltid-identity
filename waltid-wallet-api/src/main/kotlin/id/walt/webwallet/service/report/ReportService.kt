package id.walt.webwallet.service.report

import id.walt.webwallet.db.models.Events
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.events.CredentialEventData
import id.walt.webwallet.service.events.EventType
import kotlinx.serialization.json.Json
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

interface ReportService<T> {
    fun frequent(parameter: ReportRequestParameter): List<T>

    object Credentials : ReportService<WalletCredential> {

        private val json = Json { ignoreUnknownKeys = true }
        override fun frequent(parameter: ReportRequestParameter): List<WalletCredential> =
            (parameter as? CredentialReportRequestParameter)?.let { param ->
                frequent(param.walletId, EventType.Credential.Present, param.limit).map {
                    json.decodeFromString<CredentialEventData>(it).credentialId
                }.groupBy { it }.let { group ->
                    val sorted = group.keys.sortedByDescending {
                        group[it]?.count()
                    }
                    CredentialsService.get(sorted)
                }
            } ?: emptyList()

        private fun frequent(walletId: UUID, action: EventType.Action, limit: Int) = transaction {
            Events.select(Events.data)
                .where { Events.wallet eq walletId and (Events.event eq action.type) and (Events.action eq action.toString()) }
                .limit(limit).map {
                    it[Events.data]
                }
        }
    }
}

abstract class ReportRequestParameter(
    open val walletId: UUID,
    open val limit: Int,
)

data class CredentialReportRequestParameter(
    override val walletId: UUID,
    override val limit: Int
) : ReportRequestParameter(walletId, limit)
