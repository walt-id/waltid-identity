package id.walt.openid4vci.metadata.issuer

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Representation of a credential_configuration_supported entry (OpenID4VCI 1.0).
 */
@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = CredentialConfigurationSerializer::class)
data class CredentialConfiguration(
    val format: CredentialFormat,
    @SerialName("scope")
    val scope: String? = null,
    @SerialName("credential_definition")
    val credentialDefinition: CredentialDefinition? = null,
    @SerialName("doctype")
    val doctype: String? = null,
    @SerialName("vct")
    val vct: String? = null,
    @SerialName("credential_signing_alg_values_supported")
    @Serializable(SigningAlgIdSetSerializer::class)
    val credentialSigningAlgValuesSupported: Set<SigningAlgId>? = null,
    @SerialName("cryptographic_binding_methods_supported")
    val cryptographicBindingMethodsSupported: Set<CryptographicBindingMethod>? = null,
    @SerialName("proof_types_supported")
    val proofTypesSupported: Map<String, ProofType>? = null,
    @SerialName("credential_metadata")
    val credentialMetadata: CredentialMetadata? = null,
    @SerialName("display")
    val display: List<CredentialDisplay>? = null,
    val customParameters: Map<String, JsonElement>? = null,
) {
    init {
        scope?.let { value ->
            require(value.isNotBlank()) { "scope must not be blank" }
        }
        display?.let { entries ->
            val locales = entries.mapNotNull { it.locale }
            require(locales.size == locales.distinct().size) {
                "display entries must not duplicate locales"
            }
        }
        cryptographicBindingMethodsSupported?.let { methods ->
            require(methods.isNotEmpty()) {
                "cryptographic_binding_methods_supported must not be empty"
            }
        }
        proofTypesSupported?.let { proofs ->
            require(proofs.isNotEmpty()) {
                "proof_types_supported must not be empty"
            }
            require(proofs.keys.all { it.isNotBlank() }) {
                "proof_types_supported keys must not be blank"
            }
        }
        if (cryptographicBindingMethodsSupported != null) {
            require(proofTypesSupported != null) {
                "proof_types_supported must be present when cryptographic_binding_methods_supported is set"
            }
        }
        if (proofTypesSupported != null) {
            require(cryptographicBindingMethodsSupported != null) {
                "cryptographic_binding_methods_supported must be present when proof_types_supported is set"
            }
        }
        credentialSigningAlgValuesSupported?.let { algorithms ->
            require(algorithms.isNotEmpty()) {
                "credential_signing_alg_values_supported must not be empty"
            }
            require(
                algorithms.none {
                    (it is SigningAlgId.Jose && it.value.isBlank()) ||
                        (it is SigningAlgId.LdSuite && it.value.isBlank()) ||
                        (it is SigningAlgId.CoseName && it.value.isBlank())
                }
            ) {
                "credential_signing_alg_values_supported must not contain blank entries"
            }
            when (format) {
                CredentialFormat.LDP_VC ->
                    require(algorithms.all { it is SigningAlgId.LdSuite }) {
                        "credential_signing_alg_values_supported must contain LD suite identifiers for ${format.value}"
                    }
                CredentialFormat.MSO_MDOC ->
                    require(algorithms.all { it is SigningAlgId.CoseValue || it is SigningAlgId.CoseName }) {
                        "credential_signing_alg_values_supported must contain COSE identifiers for ${format.value}"
                    }
                else ->
                    require(algorithms.all { it is SigningAlgId.Jose }) {
                        "credential_signing_alg_values_supported must contain JOSE identifiers for ${format.value}"
                    }
            }
        }
        customParameters?.let { params ->
            require(params.keys.none { it in CredentialConfigurationSerializer.knownKeys }) {
                "customParameters must not override standard credential configuration fields"
            }
        }
    }
}

internal object CredentialConfigurationSerializer : KSerializer<CredentialConfiguration> {
    private val lenientJson = Json { ignoreUnknownKeys = true }

    override val descriptor: SerialDescriptor =
        CredentialConfiguration.generatedSerializer().descriptor

    internal val knownKeys =
        descriptor.elementNames
            .filter { it != "customParameters" }
            .toSet() + "id"

    override fun serialize(encoder: Encoder, value: CredentialConfiguration) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("CredentialConfigurationSerializer can only be used with JSON")

        val base = buildJsonObject {
            put("format", Json.encodeToJsonElement(CredentialFormat.serializer(), value.format))
            value.scope?.let { put("scope", JsonPrimitive(it)) }
            value.credentialDefinition?.let {
                put("credential_definition", Json.encodeToJsonElement(CredentialDefinition.serializer(), it))
            }
            value.doctype?.let { put("doctype", JsonPrimitive(it)) }
            value.vct?.let { put("vct", JsonPrimitive(it)) }
            value.credentialSigningAlgValuesSupported?.let {
                put("credential_signing_alg_values_supported", Json.encodeToJsonElement(SigningAlgIdSetSerializer, it))
            }
            value.cryptographicBindingMethodsSupported?.let {
                val serializer = SetSerializer(CryptographicBindingMethod.serializer())
                put("cryptographic_binding_methods_supported", Json.encodeToJsonElement(serializer, it))
            }
            value.proofTypesSupported?.let {
                val serializer = MapSerializer(String.serializer(), ProofType.serializer())
                put("proof_types_supported", Json.encodeToJsonElement(serializer, it))
            }
            value.credentialMetadata?.let {
                put("credential_metadata", Json.encodeToJsonElement(CredentialMetadata.serializer(), it))
            }
            value.display?.let {
                val serializer = ListSerializer(CredentialDisplay.serializer())
                put("display", Json.encodeToJsonElement(serializer, it))
            }
        }
        val merged = value.customParameters?.let { extras ->
            buildJsonObject {
                base.forEach { (key, jsonValue) -> put(key, jsonValue) }
                extras.forEach { (key, jsonValue) -> put(key, jsonValue) }
            }
        } ?: base

        jsonEncoder.encodeJsonElement(merged)
    }

    override fun deserialize(decoder: Decoder): CredentialConfiguration {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("CredentialConfigurationSerializer can only be used with JSON")
        val jsonObject = jsonDecoder.decodeJsonElement().jsonObject

        val customParameters = jsonObject.filterKeys { it !in knownKeys }.takeIf { it.isNotEmpty() }
        val formatElement = jsonObject["format"]
            ?: throw SerializationException("credential configuration format is required")
        val format = lenientJson.decodeFromJsonElement(CredentialFormat.serializer(), formatElement)

        return CredentialConfiguration(
            format = format,
            scope = jsonObject.string("scope"),
            credentialDefinition = jsonObject["credential_definition"]?.let {
                lenientJson.decodeFromJsonElement(CredentialDefinition.serializer(), it)
            },
            doctype = jsonObject.string("doctype"),
            vct = jsonObject.string("vct"),
            credentialSigningAlgValuesSupported = jsonObject["credential_signing_alg_values_supported"]?.let {
                lenientJson.decodeFromJsonElement(SigningAlgIdSetSerializer, it)
            },
            cryptographicBindingMethodsSupported = jsonObject["cryptographic_binding_methods_supported"]?.let {
                val serializer = SetSerializer(CryptographicBindingMethod.serializer())
                lenientJson.decodeFromJsonElement(serializer, it)
            },
            proofTypesSupported = jsonObject["proof_types_supported"]?.let {
                val serializer = MapSerializer(String.serializer(), ProofType.serializer())
                lenientJson.decodeFromJsonElement(serializer, it)
            },
            credentialMetadata = jsonObject["credential_metadata"]?.let {
                lenientJson.decodeFromJsonElement(CredentialMetadata.serializer(), it)
            },
            display = jsonObject["display"]?.let {
                val serializer = ListSerializer(CredentialDisplay.serializer())
                lenientJson.decodeFromJsonElement(serializer, it)
            },
            customParameters = customParameters,
        )
    }
}

private fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

/**
 * Format-specific credential definition metadata.
 */
@Serializable
data class CredentialDefinition(
    @SerialName("@context")
    val context: List<String>? = null,
    val type: List<String>? = null,
)

/**
 * Metadata about a supported proof type.
 */
@Serializable
data class ProofType(
    @SerialName("proof_signing_alg_values_supported")
    val proofSigningAlgValuesSupported: Set<String>,
    @SerialName("key_attestations_required")
    val keyAttestationsRequired: KeyAttestationsRequired? = null,
) {
    init {
        require(proofSigningAlgValuesSupported.isNotEmpty()) {
            "proof_signing_alg_values_supported must not be empty"
        }
        require(proofSigningAlgValuesSupported.none { it.isBlank() }) {
            "proof_signing_alg_values_supported must not contain blank entries"
        }
    }
}

@Serializable
data class KeyAttestationsRequired(
    @SerialName("key_storage")
    val keyStorage: Set<String>? = null,
    @SerialName("user_authentication")
    val userAuthentication: Set<String>? = null,
) {
    init {
        keyStorage?.let { values ->
            require(values.isNotEmpty()) {
                "key_attestations_required.key_storage must not be empty"
            }
            require(values.none { it.isBlank() }) {
                "key_attestations_required.key_storage must not contain blank entries"
            }
        }
        userAuthentication?.let { values ->
            require(values.isNotEmpty()) {
                "key_attestations_required.user_authentication must not be empty"
            }
            require(values.none { it.isBlank() }) {
                "key_attestations_required.user_authentication must not contain blank entries"
            }
        }
    }
}

@Serializable
data class CredentialMetadata(
    @SerialName("display")
    val display: List<CredentialDisplay>? = null,
    @SerialName("claims")
    val claims: List<ClaimDescription>? = null,
) {
    init {
        display?.let { entries ->
            require(entries.isNotEmpty()) {
                "credential_metadata.display must not be empty"
            }
            val locales = entries.mapNotNull { it.locale }
            require(locales.size == locales.distinct().size) {
                "credential_metadata.display entries must not duplicate locales"
            }
        }
        claims?.let { entries ->
            require(entries.isNotEmpty()) {
                "credential_metadata.claims must not be empty"
            }
        }
    }
}

/**
 * Claim description object (Appendix B.2).
 */
@Serializable
data class ClaimDescription(
    val path: List<String>,
    val mandatory: Boolean? = null,
    val display: List<ClaimDisplay>? = null,
) {
    init {
        require(path.isNotEmpty()) { "claim description path must not be empty" }
        require(path.none { it.isBlank() }) { "claim description path must not contain blank segments" }
        display?.let { entries ->
            require(entries.isNotEmpty()) { "claim description display must not be empty" }
            val locales = entries.mapNotNull { it.locale }
            require(locales.size == locales.distinct().size) {
                "claim description display entries must not duplicate locales"
            }
        }
    }
}

@Serializable
data class ClaimDisplay(
    val name: String? = null,
    val locale: String? = null,
) {
    init {
        name?.let { require(it.isNotBlank()) { "claim display name must not be blank" } }
        locale?.let { require(it.isNotBlank()) { "claim display locale must not be blank" } }
    }
}

/**
 * Display metadata for a supported credential.
 */
@Serializable
data class CredentialDisplay(
    val name: String,
    val locale: String? = null,
    val logo: CredentialDisplayLogo? = null,
    val description: String? = null,
    @SerialName("background_color")
    val backgroundColor: String? = null,
    @SerialName("background_image")
    val backgroundImage: CredentialDisplayBackgroundImage? = null,
    @SerialName("text_color")
    val textColor: String? = null,
) {
    init {
        require(name.isNotBlank()) { "display name must not be blank" }
        locale?.let { require(it.isNotBlank()) { "display locale must not be blank" } }
    }
}

@Serializable
data class CredentialDisplayLogo(
    val uri: String,
    @SerialName("alt_text")
    val altText: String? = null,
) {
    init {
        require(uri.isNotBlank()) { "logo uri must not be blank" }
        require(uri.contains(":")) { "logo uri must include a scheme" }
    }
}

@Serializable
data class CredentialDisplayBackgroundImage(
    val uri: String,
) {
    init {
        require(uri.isNotBlank()) { "background_image uri must not be blank" }
        require(uri.contains(":")) { "background_image uri must include a scheme" }
    }
}
