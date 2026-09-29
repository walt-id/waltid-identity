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
    @SerialName("credential_identifiers")
    val credentialIdentifiers: List<String>? = null,
    val claims: JsonElement? = null,
    /** Credential Issuer Identifiers associated with this authorization detail. */
    val locations: List<String>? = null,
) {
    /** Retains the published constructor for clients that do not specify issuer locations. */
    constructor(
        type: String,
        credentialConfigurationId: String,
        credentialIdentifiers: List<String>? = null,
        claims: JsonElement? = null,
    ) : this(type, credentialConfigurationId, credentialIdentifiers, claims, locations = null)

    /** Retains the published copy signature while carrying the current issuer locations forward. */
    fun copy(
        type: String = this.type,
        credentialConfigurationId: String = this.credentialConfigurationId,
        credentialIdentifiers: List<String>? = this.credentialIdentifiers,
        claims: JsonElement? = this.claims,
    ): AuthorizationDetail = AuthorizationDetail(type, credentialConfigurationId, credentialIdentifiers, claims, locations)
}
