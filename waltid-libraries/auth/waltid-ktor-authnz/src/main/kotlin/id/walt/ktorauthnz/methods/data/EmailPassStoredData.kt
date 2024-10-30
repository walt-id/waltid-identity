package id.walt.ktorauthnz.methods.data

import id.walt.ktorauthnz.accounts.identifiers.methods.AccountIdentifier
import id.walt.ktorauthnz.methods.EmailPass
import id.walt.ktorauthnz.security.PasswordHashing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("email")
data class EmailPassStoredData(
    val password: String,
) : AuthMethodStoredData {
    override fun authMethod() = EmailPass
    override suspend fun transformSavable(identifier: AccountIdentifier): EmailPassStoredData {
        return EmailPassStoredData(PasswordHashing.hash(password))
    }

    companion object {
        val EXAMPLE = EmailPassStoredData("password")
    }
}
