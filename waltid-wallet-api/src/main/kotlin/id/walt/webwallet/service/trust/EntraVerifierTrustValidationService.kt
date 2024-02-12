package id.walt.webwallet.service.trust

import io.ktor.client.*

class EntraVerifierTrustValidationService(private val http: HttpClient) : TrustValidationService {
    override suspend fun validate(did: String, type: String): Boolean {
        TODO("Not yet implemented")
    }
}