package id.walt.oid4vc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class CredentialOffer private constructor(
    @SerialName("credential_issuer") val credentialIssuer: String,
    @SerialName("credential_configuration_ids") val credentialConfigurationIds: Set<String>,
    val grants: Map<String, GrantDetails>,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(CredentialOfferSerializer, this).jsonObject

    class Builder(private val credentialIssuer: String) {
        private val supportedCredentialIds = mutableSetOf<String>()
        private val grants = mutableMapOf<String, GrantDetails>()
        fun addOfferedCredential(supportedCredentialId: String) = this.also {
            supportedCredentialIds.add(supportedCredentialId)
        }

        fun addAuthorizationCodeGrant(issuerState: String? = null, authorizationServer: String? = null) = this.also {
            grants[GrantType.authorization_code.value] = GrantDetails(issuerState = issuerState, authorizationServer = authorizationServer)
        }

        fun addPreAuthorizedCodeGrant(preAuthCode: String, txCode: TxCode? = null, interval: Int? = null, authorizationServer: String? = null) = this.also {
            grants[GrantType.pre_authorized_code.value] =
                GrantDetails(preAuthorizedCode = preAuthCode, txCode = txCode, interval = interval, authorizationServer = authorizationServer)
        }

        fun build() = CredentialOffer(credentialIssuer, supportedCredentialIds, grants)
    }

    companion object : JsonDataObjectFactory<CredentialOffer>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(CredentialOfferSerializer, jsonObject)
    }
}

object CredentialOfferSerializer : JsonDataObjectSerializer<CredentialOffer>(CredentialOffer.serializer())
