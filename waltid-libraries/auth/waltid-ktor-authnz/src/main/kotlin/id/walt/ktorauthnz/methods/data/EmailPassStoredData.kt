package id.walt.ktorauthnz.methods.data

import id.walt.ktorauthnz.methods.EmailPass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("email")
data class EmailPassStoredData(
    val password: String,
) : AuthMethodStoredData {
    override fun authMethod() = EmailPass
}
