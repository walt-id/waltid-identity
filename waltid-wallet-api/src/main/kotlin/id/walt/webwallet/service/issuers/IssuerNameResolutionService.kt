package id.walt.webwallet.service.issuers

interface IssuerNameResolutionService {
    suspend fun resolve(did: String): Result<String>
}