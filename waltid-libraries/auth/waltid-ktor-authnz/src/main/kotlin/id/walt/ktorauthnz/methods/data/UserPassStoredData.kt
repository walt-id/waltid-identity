package id.walt.ktorauthnz.methods.data

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.UserPass
import id.walt.ktorauthnz.security.PasswordHashing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("userpass")
data class UserPassStoredData(
    val password: String,
) : AuthMethodStoredData {
    override fun authMethod() = UserPass
    override suspend fun transformSavable(identifier: AccountIdentifier): AuthMethodStoredData {
        return UserPassStoredData(PasswordHashing.hash(password))
    }

    companion object {
        val EXAMPLE = UserPassStoredData("password")
    }
}
