package id.walt.ktorauthnz.methods.storeddata

import id.walt.ktorauthnz.methods.AuthenticationMethod
import id.walt.ktorauthnz.security.PasswordHash
import kotlinx.serialization.Serializable

@Serializable
sealed interface AuthMethodStoredData {
    fun authMethod(): AuthenticationMethod

    /**
     * Transform AuthMethodStoredData to a savable variant, with the identifier
     * passed as information (e.g. salting)
     */
    suspend fun transformSavable() = this
}

@Serializable
sealed interface PasswordStoredData : AuthMethodStoredData {
    val passwordHash: String?
    fun withPasswordHash(hash: PasswordHash): PasswordStoredData
}
