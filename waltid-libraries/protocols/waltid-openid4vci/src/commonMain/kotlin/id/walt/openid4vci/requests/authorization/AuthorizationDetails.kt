package id.walt.openid4vci.requests.authorization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

const val OPENID_CREDENTIAL_AUTHORIZATION_DETAIL_TYPE = "openid_credential"

@Serializable
data class AuthorizationDetail(
    val type: String,
    @SerialName("credential_configuration_id")
    val credentialConfigurationId: String,
    val claims: JsonElement? = null,
)