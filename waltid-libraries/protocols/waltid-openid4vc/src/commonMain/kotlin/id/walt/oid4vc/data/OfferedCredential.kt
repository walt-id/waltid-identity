package id.walt.oid4vc.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class OfferedCredential(
    val format: CredentialFormat,
    val types: List<String>? = null,
    @SerialName("doctype") val docType: String? = null,
    @SerialName("credential_definition") val credentialDefinition: JsonLDCredentialDefinition? = null,
    @SerialName("proof_types_supported") val proofTypesSupported: Map<ProofType, ProofTypeMetadata>? = null,
    @SerialName("cryptographic_binding_methods_supported") val cryptographicBindingMethodsSupported: Set<String>? = null,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(OfferedCredentialSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<OfferedCredential>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(OfferedCredentialSerializer, jsonObject)

        fun fromProviderMetadata(credential: CredentialSupported) = OfferedCredential(
            credential.format, credential.types, credential.docType,
            JsonLDCredentialDefinition(credential.context, credential.types),
            credential.proofTypesSupported,
            credential.cryptographicBindingMethodsSupported,
            credential.customParameters
        )
    }
}

object OfferedCredentialSerializer : JsonDataObjectSerializer<OfferedCredential>(OfferedCredential.serializer())
