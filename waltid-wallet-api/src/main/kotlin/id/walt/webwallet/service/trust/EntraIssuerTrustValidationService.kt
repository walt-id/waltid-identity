package id.walt.webwallet.service.trust

import io.ktor.client.*

class EntraIssuerTrustValidationService(private val http: HttpClient) : TrustValidationService {
    override fun validate(did: String): Boolean? {
        TODO("Not yet implemented")
    }

}