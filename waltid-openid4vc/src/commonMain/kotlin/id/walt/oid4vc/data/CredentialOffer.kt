package id.walt.oid4vc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class CredentialOffer private constructor(
    @SerialName("credential_issuer") val credentialIssuer: String,
    val credentials: List<JsonElement>,
    val grants: Map<String, GrantDetails>,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(CredentialOfferSerializer, this).jsonObject

    fun resolveOfferedCredentials(providerMetadata: OpenIDProviderMetadata): List<OfferedCredential> {
        val supportedCredentials =
            providerMetadata.credentialsSupported?.filter { !it.id.isNullOrEmpty() }?.associateBy { it.id!! } ?: mapOf()
        return credentials.mapNotNull { c ->
            if (c is JsonObject) {
                OfferedCredential.fromJSON(c)
            } else if (c is JsonPrimitive && c.isString) {
                supportedCredentials[c.content]?.let {
                    OfferedCredential.fromProviderMetadata(it)
                }
            } else null
        }
    }

    class Builder(private val credentialIssuer: String) {
        private val credentials = mutableListOf<JsonElement>()
        private val grants = mutableMapOf<String, GrantDetails>()
        fun addOfferedCredential(supportedCredentialId: String) = this.also {
            credentials.add(JsonPrimitive(supportedCredentialId))
        }

        fun addOfferedCredential(offeredCredential: OfferedCredential) = this.also {
            credentials.add(offeredCredential.toJSON())
        }

        fun addAuthorizationCodeGrant(issuerState: String) = this.also {
            grants[GrantType.authorization_code.value] = GrantDetails(issuerState)
        }

        fun addPreAuthorizedCodeGrant(preAuthCode: String, userPinRequired: Boolean? = null) = this.also {
            grants[GrantType.pre_authorized_code.value] =
                GrantDetails(preAuthorizedCode = preAuthCode, userPinRequired = userPinRequired)
        }

        fun build() = CredentialOffer(credentialIssuer, credentials, grants)
    }

    companion object : JsonDataObjectFactory<CredentialOffer>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(CredentialOfferSerializer, jsonObject)
    }
}

object CredentialOfferSerializer : JsonDataObjectSerializer<CredentialOffer>(CredentialOffer.serializer())
