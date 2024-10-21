package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.VerifiableCredential
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
@SerialName("vc-auth")
data class VerifiableCredentialAuthConfiguration(
    val verification: Map<String, JsonElement>,
    //val claimMappings: Map<String, String>? = null,
    //val redirectUrl: String? = null,
) : AuthMethodConfiguration {
    override fun authMethod() = VerifiableCredential
}
