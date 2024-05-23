package id.walt.webwallet.usecase.issuer

import id.walt.webwallet.service.issuers.IssuerCredentialsDataTransferObject
import id.walt.webwallet.service.issuers.IssuerDataTransferObject
import kotlinx.uuid.UUID

interface IssuerUseCase {
    fun get(wallet: UUID, did: String): Result<IssuerDataTransferObject>
    fun list(wallet: UUID): List<IssuerDataTransferObject>
    fun add(issuer: IssuerDataTransferObject): Result<Boolean>
    fun authorize(wallet: UUID, did: String): Result<Boolean>
    suspend fun credentials(wallet: UUID, did: String): Result<IssuerCredentialsDataTransferObject>
}