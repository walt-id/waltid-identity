package id.walt.webwallet.service.report

import id.walt.webwallet.db.models.WalletCredential
import kotlinx.uuid.UUID

interface ReportService<T> {
    fun frequent(parameter: ReportRequestParameter): List<T>

    object Credentials : ReportService<WalletCredential> {
        override fun frequent(parameter: ReportRequestParameter): List<WalletCredential> =
            (parameter as? CredentialReportRequestParameter)?.let {
                val limit = it.limit
                val walletId = it.walletId
                val credentialId = it.credentialId
                TODO()
            } ?: emptyList()
    }
}

abstract class ReportRequestParameter(
    open val walletId: UUID,
    open val limit: Int,
)

data class CredentialReportRequestParameter(
    override val limit: Int,
    override val walletId: UUID,
    val credentialId: String,
) : ReportRequestParameter(walletId, limit)