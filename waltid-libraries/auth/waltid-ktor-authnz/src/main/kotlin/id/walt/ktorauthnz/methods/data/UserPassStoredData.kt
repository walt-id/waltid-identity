package id.walt.ktorauthnz.methods.data

import id.walt.ktorauthnz.methods.UserPass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("userpass")
data class UserPassStoredData(
    val password: String,
) : AuthMethodStoredData {
    override fun authMethod() = UserPass
}
