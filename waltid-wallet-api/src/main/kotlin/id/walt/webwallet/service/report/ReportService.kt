package id.walt.webwallet.service.report

import id.walt.webwallet.db.models.Events
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.events.EventType
import kotlinx.uuid.UUID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

interface ReportService<T> {
    fun frequent(parameter: ReportRequestParameter): List<T>

    object Credentials : ReportService<WalletCredential> {

        override fun frequent(parameter: ReportRequestParameter): List<WalletCredential> =
            (parameter as? CredentialReportRequestParameter)?.let { param ->
                frequent(param.walletId, EventType.Credential.Present, param.limit).let {
                    CredentialsService.get(it.filterNotNull())
                }
            } ?: emptyList()

        private fun frequent(walletId: UUID, action: EventType.Action, limit: Int) = transaction {
            Events.slice(Events.credentialId)
                .selectAll().where { Events.wallet eq walletId and (Events.event eq action.type) and (Events.action eq action.toString()) }
                .groupBy(Events.credentialId)
                .having { Events.credentialId neq null }
                .orderBy(Events.credentialId.count(), SortOrder.DESC)
                .limit(limit).map {
                    it[Events.credentialId]
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
