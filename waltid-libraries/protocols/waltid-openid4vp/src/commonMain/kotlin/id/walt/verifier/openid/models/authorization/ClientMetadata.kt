package id.walt.verifier.openid.models.authorization

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*

/**
 * Represents the 'client_metadata' parameter.
 * See: Section 5.1 and RFC 7591 for internationalized metadata support.
 * 
 * Supports RFC 7591 language-tagged metadata fields (e.g., `client_name#fr-FR`, `logo_uri#en`).
 * Language tags follow BCP 47 format and are case-insensitive.
 */
@Serializable(with = ClientMetadataSerializer::class)
data class ClientMetadata(
    /**
     * OPTIONAL. A JSON Web Key Set [RFC7517] that contains one or more public keys,
     * such as those used by the Wallet for response encryption.
     */
    val jwks: Jwks? = null,

    /**
     * REQUIRED when not available to the Wallet via another mechanism.
     * An object defining the formats and proof types of Presentations and Credentials
     * that a Verifier supports. (Corresponds to vp_formats_supported in Verifier Metadata - Section 11.1)
     */
    @SerialName("vp_formats_supported")
    val vpFormatsSupported: Map<String, JsonObject>? = null,

    /**
     * OPTIONAL. Array of strings, where each string is a JWE `enc` algorithm
     * that can be used as the content encryption algorithm for encrypting the Response.
     * See Section 5.1
     */
    @SerialName("encrypted_response_enc_values_supported")
    val encryptedResponseEncValuesSupported: List<String>? = null,

    /**
     * OPTIONAL. Human-readable name of the client (Verifier).
     * Supports internationalization via language tags per RFC 7591.
     * Use [clientNameI18n] for language-specific values.
     */
    @SerialName("client_name")
    val clientName: String? = null,

    /**
     * OPTIONAL. Internationalized client name values.
     * Key is BCP 47 language tag (e.g., "fr-FR", "en", "de").
     * Example: `clientNameI18n = mapOf("fr-FR" to "Nom du Client", "en" to "Client Name")`
     */
    val clientNameI18n: Map<String, String> = emptyMap(),

    /**
     * OPTIONAL. URL string that references a logo for the client.
     * Supports internationalization via language tags per RFC 7591.
     */
    @SerialName("logo_uri")
    val logoUri: String? = null,

    /**
     * OPTIONAL. Internationalized logo URI values.
     * Key is BCP 47 language tag.
     */
    val logoUriI18n: Map<String, String> = emptyMap(),

    /**
     * OPTIONAL. URL string that points to a human-readable Terms of Service.
     * Supports internationalization via language tags per RFC 7591.
     */
    @SerialName("tos_uri")
    val tosUri: String? = null,

    /**
     * OPTIONAL. Internationalized Terms of Service URI values.
     * Key is BCP 47 language tag.
     */
    val tosUriI18n: Map<String, String> = emptyMap(),

    /**
     * OPTIONAL. URL string that points to a human-readable Privacy Policy.
     * Supports internationalization via language tags per RFC 7591.
     */
    @SerialName("policy_uri")
    val policyUri: String? = null,

    /**
     * OPTIONAL. Internationalized Privacy Policy URI values.
     * Key is BCP 47 language tag.
     */
    val policyUriI18n: Map<String, String> = emptyMap(),

    /**
     * OPTIONAL. URL string that points to a web page providing information about the client.
     * Supports internationalization via language tags per RFC 7591.
     */
    @SerialName("client_uri")
    val clientUri: String? = null,

    /**
     * OPTIONAL. Internationalized client URI values.
     * Key is BCP 47 language tag.
     */
    val clientUriI18n: Map<String, String> = emptyMap(),

    /**
     * Additional custom fields that don't match known metadata fields.
     * This includes any language-tagged fields not explicitly handled above.
     */
    val additionalFields: Map<String, JsonElement> = emptyMap(),
) {
    /** keys are JWK */
    @Serializable
    data class Jwks(val keys: List<JsonObject>)

    companion object {
        private val jsonParser = Json { ignoreUnknownKeys = true }
        fun fromJson(jsonString: String): Result<ClientMetadata> {
            return runCatching {
                jsonParser.decodeFromString<ClientMetadata>(jsonString)
            }
        }
    }
}

/**
 * Custom serializer for ClientMetadata that handles RFC 7591 internationalized metadata fields.
 * 
 * Language-tagged fields follow the pattern: `field_name#language-tag` (e.g., `client_name#fr-FR`).
 * The language tag is separated from the field name by a `#` character.
 */
object ClientMetadataSerializer : KSerializer<ClientMetadata> {
    // Fields that support internationalization per RFC 7591
    private val i18nFields = setOf("client_name", "logo_uri", "tos_uri", "policy_uri", "client_uri")
    
    // Regular (non-i18n) fields
    private val regularFields = setOf(
        "jwks",
        "vp_formats_supported",
        "encrypted_response_enc_values_supported"
    )

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ClientMetadata") {
        element<JsonObject?>("jwks", isOptional = true)
        element<Map<String, JsonObject>?>("vp_formats_supported", isOptional = true)
        element<List<String>?>("encrypted_response_enc_values_supported", isOptional = true)
        element<String?>("client_name", isOptional = true)
        element<String?>("logo_uri", isOptional = true)
        element<String?>("tos_uri", isOptional = true)
        element<String?>("policy_uri", isOptional = true)
        element<String?>("client_uri", isOptional = true)
    }

    override fun deserialize(decoder: Decoder): ClientMetadata {
        require(decoder is JsonDecoder) { "ClientMetadata can only be deserialized from JSON" }
        
        val json = decoder.decodeJsonElement().jsonObject
        val jsonParser = Json { ignoreUnknownKeys = true }

        // Extract regular fields
        val jwks = json["jwks"]?.let { jsonParser.decodeFromJsonElement<ClientMetadata.Jwks>(it) }
        val vpFormatsSupported = json["vp_formats_supported"]?.jsonObject?.mapValues { it.value.jsonObject }
        val encryptedResponseEncValuesSupported = json["encrypted_response_enc_values_supported"]
            ?.jsonArray?.map { it.jsonPrimitive.content }

        // Extract base i18n fields (without language tags)
        val clientName = (json["client_name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val logoUri = (json["logo_uri"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val tosUri = (json["tos_uri"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val policyUri = (json["policy_uri"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val clientUri = (json["client_uri"] as? JsonPrimitive)?.takeIf { it.isString }?.content

        // Extract language-tagged fields
        val clientNameI18n = mutableMapOf<String, String>()
        val logoUriI18n = mutableMapOf<String, String>()
        val tosUriI18n = mutableMapOf<String, String>()
        val policyUriI18n = mutableMapOf<String, String>()
        val clientUriI18n = mutableMapOf<String, String>()
        val additionalFields = mutableMapOf<String, JsonElement>()

        // Process all fields to find language-tagged variants
        json.forEach { (key, value) ->
            if (key.contains("#")) {
                val parts = key.split("#", limit = 2)
                if (parts.size == 2) {
                    val fieldName = parts[0]
                    val languageTag = parts[1]
                    
                    when (fieldName) {
                        "client_name" -> {
                            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { clientNameI18n[languageTag] = it }
                        }
                        "logo_uri" -> {
                            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { logoUriI18n[languageTag] = it }
                        }
                        "tos_uri" -> {
                            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { tosUriI18n[languageTag] = it }
                        }
                        "policy_uri" -> {
                            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { policyUriI18n[languageTag] = it }
                        }
                        "client_uri" -> {
                            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { clientUriI18n[languageTag] = it }
                        }
                        else -> {
                            // Unknown i18n field - store in additionalFields
                            additionalFields[key] = value
                        }
                    }
                }
            } else if (!regularFields.contains(key) && !i18nFields.contains(key)) {
                // Unknown non-i18n field - store in additionalFields
                additionalFields[key] = value
            }
        }

        return ClientMetadata(
            jwks = jwks,
            vpFormatsSupported = vpFormatsSupported,
            encryptedResponseEncValuesSupported = encryptedResponseEncValuesSupported,
            clientName = clientName,
            clientNameI18n = clientNameI18n,
            logoUri = logoUri,
            logoUriI18n = logoUriI18n,
            tosUri = tosUri,
            tosUriI18n = tosUriI18n,
            policyUri = policyUri,
            policyUriI18n = policyUriI18n,
            clientUri = clientUri,
            clientUriI18n = clientUriI18n,
            additionalFields = additionalFields,
        )
    }

    override fun serialize(encoder: Encoder, value: ClientMetadata) {
        require(encoder is JsonEncoder) { "ClientMetadata can only be serialized to JSON" }
        
        val jsonObject = buildJsonObject {
            // Regular fields
            value.jwks?.let { put("jwks", Json.encodeToJsonElement(it)) }
            value.vpFormatsSupported?.let { put("vp_formats_supported", Json.encodeToJsonElement(it)) }
            value.encryptedResponseEncValuesSupported?.let { put("encrypted_response_enc_values_supported", Json.encodeToJsonElement(it)) }
            
            // Base i18n fields (without language tags)
            value.clientName?.let { put("client_name", it) }
            value.logoUri?.let { put("logo_uri", it) }
            value.tosUri?.let { put("tos_uri", it) }
            value.policyUri?.let { put("policy_uri", it) }
            value.clientUri?.let { put("client_uri", it) }
            
            // Language-tagged fields
            value.clientNameI18n.forEach { (lang, name) ->
                put("client_name#$lang", name)
            }
            value.logoUriI18n.forEach { (lang, uri) ->
                put("logo_uri#$lang", uri)
            }
            value.tosUriI18n.forEach { (lang, uri) ->
                put("tos_uri#$lang", uri)
            }
            value.policyUriI18n.forEach { (lang, uri) ->
                put("policy_uri#$lang", uri)
            }
            value.clientUriI18n.forEach { (lang, uri) ->
                put("client_uri#$lang", uri)
            }
            
            // Additional fields (including unknown i18n fields)
            value.additionalFields.forEach { (key, element) ->
                put(key, element)
            }
        }
        
        encoder.encodeJsonElement(jsonObject)
    }
}
