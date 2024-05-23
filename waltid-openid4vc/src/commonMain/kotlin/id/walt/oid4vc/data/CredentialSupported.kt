package id.walt.oid4vc.data

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/*
@context: [...],      // json-ld only
doctype: "..."        // mDL only
types: [...],
credentialSubject: {  // vc only
  prop1: {
    mandatory: bool,
    value_type: "...",
    display: {
      name: "...",
      locale: "..."
    }
  },
  prop2: {...}
},
claims: {           // mDL only
  "namespace...": {
    prop1: {
      mandatory: bool,
      value_type: "...",
      display: {
        name: "...",
        locale: "..."
      }
    },
    prop2: {...}
  }
},
order: [...]

 */


/**
 * Objects that appear in the credentials_supported metadata parameter.
 * (https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html#name-objects-comprising-credenti)
 * @param id REQUIRED. A JSON string identifying the respective object. The value MUST be unique across all credentials_supported entries in the Credential Issuer Metadata.
 * @param format REQUIRED. A JSON string identifying the format of this credential, e.g. jwt_vc_json or ldp_vc. Depending on the format value, the object contains further elements defining the type and (optionally) particular claims the credential MAY contain, and information how to display the credential. Appendix E defines Credential Format Profiles introduced by this specification.
 * @param cryptographicBindingMethodsSupported OPTIONAL. Array of case sensitive strings that identify how the Credential is bound to the identifier of the End-User who possesses the Credential as defined in Section 7.1. Support for keys in JWK format [RFC7517] is indicated by the value jwk. Support for keys expressed as a COSE Key object [RFC8152] (for example, used in [ISO.18013-5]) is indicated by the value cose_key. When Cryptographic Binding Method is a DID, valid values MUST be a did: prefix followed by a method-name using a syntax as defined in Section 3.1 of [DID-Core], but without a :and method-specific-id. For example, support for the DID method with a method-name "example" would be represented by did:example. Support for all DID methods listed in Section 13 of [DID_Specification_Registries] is indicated by sending a DID without any method-name.
 * @param cryptographicSuitesSupported OPTIONAL. Array of case sensitive strings that identify the cryptographic suites that are supported for the cryptographic_binding_methods_supported. Cryptosuites for Credentials in jwt_vc format should use algorithm names defined in IANA JOSE Algorithms Registry. Cryptosuites for Credentials in ldp_vc format should use signature suites names defined in Linked Data Cryptographic Suite Registry.
 * @param display OPTIONAL. An array of objects, where each object contains the display properties of the supported credential for a certain language. Below is a non-exhaustive list of parameters that MAY be included. Note that the display name of the supported credential is obtained from display.name and individual claim names from claims.display.name values.
 * @param context REQUIRED (W3C JSON-LD credentials): JSON array as defined in [VC_DATA], Section 4.1.
 * @param types REQUIRED (W3C verifiable credentials):  JSON array designating the types a certain credential type supports according to [VC_DATA], Section 4.3.
 * @param docType REQUIRED (ISO mDL/mdocs): JSON string identifying the credential type.
 * @param credentialSubject OPTIONAL (W3C verifiable credentials): A JSON object containing a list of key value pairs, where the key identifies the claim offered in the Credential. The value MAY be a dictionary, which allows to represent the full (potentially deeply nested) structure of the verifiable credential to be issued. The value is a JSON object detailing the specifics about the support for the claim
 * @param claims OPTIONAL (ISO mDL/mdocs): A JSON object containing a list of key value pairs, where the key is a certain namespace as defined in [ISO.18013-5] (or any profile of it), and the value is a JSON object. This object also contains a list of key value pairs, where the key is a claim that is defined in the respective namespace and is offered in the Credential. The value is a JSON object detailing the specifics of the claim
 * @param order OPTIONAL. An array of claims.display.name values that lists them in the order they should be displayed by the Wallet.
 */
@Serializable
data class CredentialSupported(
    val id: String,
    val format: CredentialFormat,
    @SerialName("cryptographic_binding_methods_supported") val cryptographicBindingMethodsSupported: Set<String>? = null,
    @SerialName("cryptographic_suites_supported") val cryptographicSuitesSupported: Set<String>? = null,
    @Serializable(DisplayPropertiesListSerializer::class) val display: List<DisplayProperties>? = null,
    @SerialName("@context") val context: List<JsonElement>? = null,
    val types: List<String>? = null,
    @SerialName("doctype") val docType: String? = null,
    @Serializable(ClaimDescriptorMapSerializer::class) val credentialSubject: Map<String, ClaimDescriptor>? = null,
    @Serializable(ClaimDescriptorNamespacedMapSerializer::class) val claims: Map<String, Map<String, ClaimDescriptor>>? = null,
    val order: List<String>? = null,
    override val customParameters: Map<String, JsonElement> = mapOf()
) : JsonDataObject() {
    override fun toJSON(): JsonObject = Json.encodeToJsonElement(CredentialSupportedSerializer, this).jsonObject

    companion object : JsonDataObjectFactory<CredentialSupported>() {
        override fun fromJSON(jsonObject: JsonObject): CredentialSupported =
            Json.decodeFromJsonElement(CredentialSupportedSerializer, jsonObject)
    }
}

object CredentialSupportedSerializer : JsonDataObjectSerializer<CredentialSupported>(CredentialSupported.serializer())

object CredentialSupportedListSerializer : KSerializer<List<CredentialSupported>> {
    private val internalSerializer = ListSerializer(CredentialSupportedSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<CredentialSupported>) =
        internalSerializer.serialize(encoder, value)
}

object CredentialSupportedMapSerializer : KSerializer<Map<String, CredentialSupported>> {
    private val internalSerializer = MapSerializer(String.serializer(), CredentialSupportedSerializer)
    override val descriptor: SerialDescriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: Map<String, CredentialSupported>) = internalSerializer.serialize(encoder, value)
}
