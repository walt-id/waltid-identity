package id.walt.ktorauthnz.methods.storeddata

import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.security.PasswordHash
import id.walt.ktorauthnz.security.PasswordHashing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("email")
data class EmailPassStoredData(
    val password: String? = null,
    override val passwordHash: String? = null,
) : PasswordStoredData {
    override fun authMethod() = EmailPass
    override fun withPasswordHash(hash: PasswordHash) = EmailPassStoredData(passwordHash = hash.toString())
    override suspend fun transformSavable(): EmailPassStoredData =
        when {
            passwordHash != null -> EmailPassStoredData(passwordHash = passwordHash)
            password != null -> EmailPassStoredData(passwordHash = PasswordHashing.hash(password).toString())
            else -> throw IllegalArgumentException("Either password or passwordHash has to be set.")
        }

    companion object {
        val EXAMPLE = EmailPassStoredData("password")
    }
}
