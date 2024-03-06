package id.walt.webwallet.service.trust

interface TrustValidationService {
    suspend fun validate(did: String, type: String, egfUri: String): Boolean
}