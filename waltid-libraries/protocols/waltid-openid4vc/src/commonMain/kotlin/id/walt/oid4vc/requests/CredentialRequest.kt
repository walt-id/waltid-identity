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

/**
 * OID4VCI draft 13 batch proofs format: { "jwt": ["<jwt1>", "<jwt2>", ...] }
 */
@Serializable
data class BatchProofs(
    val jwt: List<String>? = null,
    val cwt: List<String>? = null
) {
    /**
     * Converts the first JWT proof to a ProofOfPossession for backwards compatibility
     */
    fun toProofOfPossession(): ProofOfPossession? {
        return jwt?.firstOrNull()?.let { jwtString ->
            ProofOfPossession.fromJSON(
                kotlinx.serialization.json.buildJsonObject {
                    put("proof_type", kotlinx.serialization.json.JsonPrimitive("jwt"))
                    put("jwt", kotlinx.serialization.json.JsonPrimitive(jwtString))
                }
            )
        } ?: cwt?.firstOrNull()?.let { cwtString ->
            ProofOfPossession.fromJSON(
                kotlinx.serialization.json.buildJsonObject {
                    put("proof_type", kotlinx.serialization.json.JsonPrimitive("cwt"))
                    put("cwt", kotlinx.serialization.json.JsonPrimitive(cwtString))
                }
            )
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = CredentialRequestSerializer::class)
data class CredentialRequest(
    val format: CredentialFormat? = null,
    val proof: ProofOfPossession? = null,
    val proofs: BatchProofs? = null,
    @SerialName("credential_configuration_id") val credentialConfigurationId: String? = null,
    @SerialName("credential_identifier") val credentialIdentifier: String? = null,  // EUDI wallet compatibility
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

    /**
     * Returns the proof, checking both singular 'proof' and batch 'proofs' fields.
     * Per OID4VCI draft 13, 'proofs' is the preferred format.
     */
    fun getEffectiveProof(): ProofOfPossession? {
        return proof ?: proofs?.toProofOfPossession()
    }

    companion object : JsonDataObjectFactory<CredentialRequest>() {
        override fun fromJSON(jsonObject: JsonObject): CredentialRequest =
            json.decodeFromJsonElement(CredentialRequestSerializer, jsonObject)

        fun forAuthorizationDetails(authorizationDetails: AuthorizationDetails, proof: ProofOfPossession?) =
            CredentialRequest(
                format = authorizationDetails.format,
                proof = proof,
                proofs = null,
                credentialConfigurationId = null,
                credentialIdentifier = null,
                vct = authorizationDetails.vct,
                credentialSubject = authorizationDetails.credentialSubject,
                docType = authorizationDetails.docType,
                claims = authorizationDetails.claims,
                credentialDefinition = authorizationDetails.credentialDefinition,
                types = authorizationDetails.types,
                display = null,
                customParameters = authorizationDetails.customParameters
            )

        fun forOfferedCredential(offeredCredential: OfferedCredential, proof: ProofOfPossession?) = CredentialRequest(
            format = offeredCredential.format,
            proof = proof,
            proofs = null,
            credentialConfigurationId = null,
            credentialIdentifier = null,
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
