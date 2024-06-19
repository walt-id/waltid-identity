package id.walt.oid4vc.requests

import id.walt.oid4vc.data.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Serializable
data class CredentialRequest(
    val format: CredentialFormat,
    val proof: ProofOfPossession? = null,
    val types: List<String>? = null,
    @Serializable(ClaimDescriptorMapSerializer::class) val credentialSubject: Map<String, ClaimDescriptor>? = null,
    @SerialName("doctype") val docType: String? = null,
    @Serializable(ClaimDescriptorNamespacedMapSerializer::class) val claims: Map<String, Map<String, ClaimDescriptor>>? = null,
    @SerialName("credential_definition") val credentialDefinition: JsonLDCredentialDefinition? = null,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(CredentialRequestSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<CredentialRequest>() {
        override fun fromJSON(jsonObject: JsonObject) =
            Json.decodeFromJsonElement(CredentialRequestSerializer, jsonObject)

        fun forAuthorizationDetails(authorizationDetails: AuthorizationDetails, proof: ProofOfPossession?) =
            CredentialRequest(
                authorizationDetails.format!!,
                proof,
                authorizationDetails.types,
                authorizationDetails.credentialSubject,
                authorizationDetails.docType,
                authorizationDetails.claims,
                authorizationDetails.credentialDefinition,
                authorizationDetails.customParameters
            )

        fun forOfferedCredential(offeredCredential: OfferedCredential, proof: ProofOfPossession?) = CredentialRequest(
            offeredCredential.format, proof, offeredCredential.types, null,
            offeredCredential.docType, null,
            offeredCredential.credentialDefinition, offeredCredential.customParameters
        )
    }
}

object CredentialRequestSerializer : JsonDataObjectSerializer<CredentialRequest>(CredentialRequest.serializer())

object CredentialRequestListSerializer : KSerializer<List<CredentialRequest>> {
    private val internalSerializer = ListSerializer(CredentialRequestSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder): List<CredentialRequest> = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<CredentialRequest>) =
        internalSerializer.serialize(encoder, value)
}
