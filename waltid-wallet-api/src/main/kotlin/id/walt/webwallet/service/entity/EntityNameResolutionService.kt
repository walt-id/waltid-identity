package id.walt.webwallet.service.entity

interface EntityNameResolutionService {
    suspend fun resolve(did: String): Result<String>
}