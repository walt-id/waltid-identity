package id.walt.ktorauthnz.methods.storeddata

import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("userpass")
data class UserPassStoredData(
    val password: String? = null,
    override val passwordHash: String? = null
) : PasswordStoredData {
    override fun authMethod() = UserPass
    override fun withPasswordHash(hash: PasswordHash) = UserPassStoredData(passwordHash = hash.toString())
    override suspend fun transformSavable(): UserPassStoredData =
        when {
            passwordHash != null -> UserPassStoredData(passwordHash = passwordHash)
            password != null -> UserPassStoredData(passwordHash = PasswordHashing.hash(password).toString())
            else -> throw IllegalArgumentException("Either password or passwordHash has to be set.")
        }

    companion object {
        val EXAMPLE = UserPassStoredData("password")
    }
}
