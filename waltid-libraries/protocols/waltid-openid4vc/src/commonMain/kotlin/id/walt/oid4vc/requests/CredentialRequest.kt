package id.walt.oid4vc.requests

import id.walt.oid4vc.data.*
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val json = Json {
    ignoreUnknownKeys = true
}

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = CredentialRequestSerializer::class)
data class CredentialRequest(
    val format: CredentialFormat,
    val proof: ProofOfPossession? = null,
    @SerialName("vct") val vct: String? = null,
    @Serializable(ClaimDescriptorMapSerializer::class) val credentialSubject: Map<String, ClaimDescriptor>? = null,
    @SerialName("doctype") val docType: String? = null,
    @Serializable(ClaimDescriptorNamespacedMapSerializer::class) val claims: Map<String, Map<String, ClaimDescriptor>>? = null,
    @SerialName("credential_definition") val credentialDefinition: CredentialDefinition? = null,
    @SerialName("types") val types: List<String>? = null,
    @SerialName("display") val display: List<DisplayProperties>? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = json.encodeToJsonElement(CredentialRequestSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<CredentialRequest>() {
        override fun fromJSON(jsonObject: JsonObject): CredentialRequest =
            json.decodeFromJsonElement(CredentialRequestSerializer, jsonObject)

        fun forAuthorizationDetails(authorizationDetails: AuthorizationDetails, proof: ProofOfPossession?) =
            CredentialRequest(
                authorizationDetails.format!!,
                proof,
                authorizationDetails.vct,
                authorizationDetails.credentialSubject,
                authorizationDetails.docType,
                authorizationDetails.claims,
                authorizationDetails.credentialDefinition,
                authorizationDetails.types,
                null,
                authorizationDetails.customParameters
            )

        fun forOfferedCredential(offeredCredential: OfferedCredential, proof: ProofOfPossession?) = CredentialRequest(
            format = offeredCredential.format,
            proof = proof,
            vct = offeredCredential.vct,
            credentialSubject = null,
            docType = offeredCredential.docType,
            claims = null,
            credentialDefinition = offeredCredential.credentialDefinition,
            types = offeredCredential.types,
            display = offeredCredential.display,
            customParameters = offeredCredential.customParameters
        )
    }
}

internal object CredentialRequestSerializer :
    JsonDataObjectSerializer<CredentialRequest>(CredentialRequest.generatedSerializer())

internal object CredentialRequestListSerializer : KSerializer<List<CredentialRequest>> {
    private val internalSerializer = ListSerializer(CredentialRequestSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder): List<CredentialRequest> = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<CredentialRequest>) =
        internalSerializer.serialize(encoder, value)
}
