package id.walt.oid4vc.data

import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_AUTHORIZATION_TYPE
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
 * The request parameter authorization_details defined in Section 2 of [I-D.ietf-oauth-rar] MUST be used to convey the details about the Credentials the Wallet wants to obtain.
 * (https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-request-issuance-of-a-certa)
 * @param type REQUIRED. JSON string that determines the authorization details type. MUST be set to openid_credential for the purpose of OID4VC.
 * @param format REQUIRED. String representing the format in which the Credential is requested to be issued. This Credential format identifier determines further claims in the authorization details object specifically used to identify the Credential type to be issued.
 * @param types REQUIRED (W3C jwt_vc_json credential format). JSON array as defined in Appendix E.1.1.2. This claim contains the type values the Wallet requests authorization for at the issuer.
 * @param credentialSubject OPTIONAL (W3C jwt_vc_json credential format). A JSON object containing a list of key value pairs, where the key identifies the claim offered in the Credential. The value MAY be a dictionary, which allows to represent the full (potentially deeply nested) structure of the verifiable credential to be issued. This object indicates the claims the Wallet would like to turn up in the credential to be issued.
 * @param docType REQUIRED (ISO mso_mdoc credential format). JSON string identifying the credential type.
 * @param claims OPTIONAL (ISO mso_mdoc credential format). A JSON object containing a list of key value pairs, where the key is a certain namespace as defined in [ISO.18013-5] (or any profile of it), and the value is a JSON object. This object also contains a list of key value pairs, where the key is a claim that is defined in the respective namespace and is offered in the Credential. The value is a JSON object detailing the specifics of the claim.
 * @param credentialDefinition REQUIRED (W3C ldp_vc, jwt_vc_json-ld credential formats). JSON object containing (and isolating) the detailed description of the credential type. This object MUST be processed using full JSON-LD processing.
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = AuthorizationDetailsSerializer::class)
data class AuthorizationDetails(
    @EncodeDefault val type: String = OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
    val format: CredentialFormat? = null,
    val vct: String? = null,
    @SerialName("types") val types: List<String>? = null,
    @Serializable(ClaimDescriptorMapSerializer::class) val credentialSubject: Map<String, ClaimDescriptor>? = null,
    @SerialName("doctype") val docType: String? = null,
    @Serializable(ClaimDescriptorNamespacedMapSerializer::class) val claims: Map<String, Map<String, ClaimDescriptor>>? = null,
    @SerialName("credential_definition") val credentialDefinition: CredentialDefinition? = null,
    val locations: List<String>? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = json.encodeToJsonElement(AuthorizationDetailsSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<AuthorizationDetails>() {
        override fun fromJSON(jsonObject: JsonObject): AuthorizationDetails =
            json.decodeFromJsonElement(AuthorizationDetailsSerializer, jsonObject)

        fun fromOfferedCredential(offeredCredential: OfferedCredential, issuerLocation: String? = null) =
            AuthorizationDetails(
                OPENID_CREDENTIAL_AUTHORIZATION_TYPE,
                offeredCredential.format,
                offeredCredential.vct,
                offeredCredential.types,
                null,
                offeredCredential.docType,
                null,
                offeredCredential.credentialDefinition,
                issuerLocation?.let { listOf(it) },
                offeredCredential.customParameters
            )
    }
}

internal object AuthorizationDetailsSerializer :
    JsonDataObjectSerializer<AuthorizationDetails>(AuthorizationDetails.generatedSerializer())

internal object AuthorizationDetailsListSerializer : KSerializer<List<AuthorizationDetails>> {
    private val internalSerializer = ListSerializer(AuthorizationDetailsSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder): List<AuthorizationDetails> = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<AuthorizationDetails>) =
        internalSerializer.serialize(encoder, value)
}
