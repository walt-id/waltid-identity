package id.walt.oid4vc.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = OfferedCredentialSerializer::class)
data class OfferedCredential(
    val format: CredentialFormat,
    val types: List<String>? = null, // for draft 11
    val vct: String? = null,
    @SerialName("display") val display: List<DisplayProperties>? = null,
    @SerialName("doctype") val docType: String? = null,
    @SerialName("credential_definition") val credentialDefinition: CredentialDefinition? = null,
    @SerialName("proof_types_supported") val proofTypesSupported: Map<ProofType, ProofTypeMetadata>? = null,
    @SerialName("cryptographic_binding_methods_supported") val cryptographicBindingMethodsSupported: Set<String>? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(OfferedCredentialSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<OfferedCredential>() {
        override fun fromJSON(jsonObject: JsonObject): OfferedCredential =
            Json.decodeFromJsonElement(OfferedCredentialSerializer, jsonObject)

        fun fromProviderMetadata(credential: CredentialSupported) = OfferedCredential(
            credential.format,
            credential.types,
            credential.vct,
            credential.display,
            credential.docType,
            CredentialDefinition(type = credential.credentialDefinition?.type),
            credential.proofTypesSupported,
            credential.cryptographicBindingMethodsSupported,
            credential.customParameters
        )
    }
}

internal object OfferedCredentialSerializer :
    JsonDataObjectSerializer<OfferedCredential>(OfferedCredential.generatedSerializer())
