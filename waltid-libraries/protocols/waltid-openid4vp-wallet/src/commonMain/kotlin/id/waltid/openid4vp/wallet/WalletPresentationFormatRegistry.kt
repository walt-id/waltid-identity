@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet

import id.walt.cose.Cose
import id.walt.crypto.keys.KeyType
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.supportsJwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.KeySpec
import id.walt.dcql.models.CredentialFormat
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Single source of truth for VP format support in this wallet implementation.
 * Both presentation dispatch and request_uri POST wallet metadata are derived from this registry.
 */
object WalletPresentationFormatRegistry {

    enum class SupportedFormat(val credentialFormat: CredentialFormat) {
        JWT_VC_JSON(CredentialFormat.JWT_VC_JSON),
        DC_SD_JWT(CredentialFormat.DC_SD_JWT),
        MSO_MDOC(CredentialFormat.MSO_MDOC);

        val primaryId: String get() = credentialFormat.id.first()
        val allIds: Set<String> get() = credentialFormat.id.toSet()
    }

    data class RuntimeCapabilities(
        val supportedFormats: Set<SupportedFormat>,
        val supportedJwsAlgorithms: List<String>,
        val supportedMdocCoseAlgorithms: List<Int>,
    )

    val supportedFormats: List<SupportedFormat> = SupportedFormat.entries

    fun resolve(formatId: String): SupportedFormat? =
        supportedFormats.find { formatId in it.allIds }

    fun defaultCapabilities(): RuntimeCapabilities =
        capabilitiesFromV1KeyTypes(KeyType.entries.toSet())

    fun capabilitiesFromKeys(keys: Collection<Crypto2Key>): RuntimeCapabilities {
        val signingKeys = keys.filter { it.capabilities.signer != null }
        val supportedJwsAlgorithms = signingKeys
            .flatMap { key ->
                JwsAlgorithm.fullySpecified.filter { algorithm ->
                    key.spec.supportsJwsAlgorithm(algorithm) &&
                        key.capabilities.supportsSignatureAlgorithm(algorithm.toSignatureAlgorithm())
                }
            }
            .map(JwsAlgorithm::identifier)
            .distinct()
            .sorted()
        val supportedMdocCoseAlgorithms = signingKeys
            .mapNotNull { it.spec.toMdocDeviceAuthCoseAlgorithm() }
            .distinct()
            .sorted()

        return capabilities(supportedJwsAlgorithms, supportedMdocCoseAlgorithms)
    }

    fun capabilitiesFromKeys(
        keys: Collection<Crypto2Key>,
        fallbackKeyTypes: Set<KeyType>,
    ): RuntimeCapabilities {
        val crypto2Capabilities = capabilitiesFromKeys(keys)
        val fallbackCapabilities = capabilitiesFromV1KeyTypes(fallbackKeyTypes)
        return capabilities(
            supportedJwsAlgorithms = (
                crypto2Capabilities.supportedJwsAlgorithms + fallbackCapabilities.supportedJwsAlgorithms
                ).distinct().sorted(),
            supportedMdocCoseAlgorithms = (
                crypto2Capabilities.supportedMdocCoseAlgorithms + fallbackCapabilities.supportedMdocCoseAlgorithms
                ).distinct().sorted(),
        )
    }

    @Deprecated("Use capabilitiesFromKeys with actual crypto2 keys")
    fun capabilitiesFromKeyTypes(keyTypes: Set<KeyType>): RuntimeCapabilities =
        capabilitiesFromV1KeyTypes(keyTypes)

    private fun capabilitiesFromV1KeyTypes(keyTypes: Set<KeyType>): RuntimeCapabilities {
        val supportedJwsAlgorithms = keyTypes
            .map { if (it == KeyType.Ed25519) "Ed25519" else it.jwsAlg }
            .distinct()
            .sorted()

        val supportedMdocCoseAlgorithms = keyTypes
            .mapNotNull { it.toMdocDeviceAuthCoseAlgorithm() }
            .distinct()
            .sorted()

        return capabilities(supportedJwsAlgorithms, supportedMdocCoseAlgorithms)
    }

    /**
     * COSE algorithms this wallet can use for ISO 18013-5 device authentication.
     *
     * P-256 device authentication is advertised with the fully specified identifier (ESP256);
     * Ed25519 device authentication uses EdDSA.
     */
    private fun KeySpec.toMdocDeviceAuthCoseAlgorithm(): Int? = when (this) {
        KeySpec.Ec(EcCurve.P256) -> Cose.Algorithm.ESP256
        KeySpec.Edwards(EdwardsCurve.ED25519) -> Cose.Algorithm.EdDSA
        else -> null
    }

    private fun KeyType.toMdocDeviceAuthCoseAlgorithm(): Int? = when (this) {
        KeyType.secp256r1 -> Cose.Algorithm.ESP256
        KeyType.Ed25519 -> Cose.Algorithm.EdDSA
        else -> null
    }

    private fun capabilities(
        supportedJwsAlgorithms: List<String>,
        supportedMdocCoseAlgorithms: List<Int>,
    ): RuntimeCapabilities = RuntimeCapabilities(
        supportedFormats = buildSet {
            if (supportedJwsAlgorithms.isNotEmpty()) {
                add(SupportedFormat.JWT_VC_JSON)
                add(SupportedFormat.DC_SD_JWT)
            }
            if (supportedMdocCoseAlgorithms.isNotEmpty()) {
                add(SupportedFormat.MSO_MDOC)
            }
        },
        supportedJwsAlgorithms = supportedJwsAlgorithms,
        supportedMdocCoseAlgorithms = supportedMdocCoseAlgorithms,
    )

    fun buildVpFormatsSupported(
        capabilities: RuntimeCapabilities = defaultCapabilities(),
    ): JsonObject =
        buildJsonObject {
            capabilities.supportedFormats.forEach {
                put(it.primaryId, buildVpFormatMetadata(it, capabilities))
            }
        }

    /** Returns whether the wallet and verifier share at least one compatible presentation format. */
    fun supportsAny(
        verifierFormats: Map<String, JsonObject>,
        capabilities: RuntimeCapabilities = defaultCapabilities(),
        requestedFormats: Set<SupportedFormat> = capabilities.supportedFormats,
    ): Boolean = verifierFormats.any { (formatId, verifierMetadata) ->
        val format = resolve(formatId) ?: return@any false
        format in requestedFormats && format in capabilities.supportedFormats && verifierMetadata.algorithmsMatch(
            walletMetadata = buildVpFormatMetadata(format, capabilities),
            fields = format.holderAlgorithmFields,
        )
    }

    private fun buildVpFormatMetadata(
        format: SupportedFormat,
        capabilities: RuntimeCapabilities,
    ): JsonObject = buildJsonObject {
        when (format) {
            SupportedFormat.JWT_VC_JSON -> {
                put("alg_values", capabilities.supportedJwsAlgorithms.toJsonArray(::JsonPrimitive))
            }

            SupportedFormat.DC_SD_JWT -> {
                val algorithms = capabilities.supportedJwsAlgorithms.toJsonArray(::JsonPrimitive)
                put("sd-jwt_alg_values", algorithms)
                put("kb-jwt_alg_values", algorithms)
            }

            SupportedFormat.MSO_MDOC -> {
                val algorithms = capabilities.supportedMdocCoseAlgorithms.toJsonArray(::JsonPrimitive)
                put("issuerauth_alg_values", algorithms)
                put("deviceauth_alg_values", algorithms)
            }
        }
    }

    /**
     * Algorithm constraints that apply to keys controlled by this wallet.
     * Issuer-signature constraints are credential properties and are handled by credential matching.
     */
    private val SupportedFormat.holderAlgorithmFields: Set<String>
        get() = when (this) {
            SupportedFormat.JWT_VC_JSON -> setOf("alg_values")
            SupportedFormat.DC_SD_JWT -> setOf("kb-jwt_alg_values")
            SupportedFormat.MSO_MDOC -> setOf("deviceauth_alg_values")
        }

    private fun JsonObject.algorithmsMatch(
        walletMetadata: JsonObject,
        fields: Set<String>,
    ): Boolean = fields.all { field ->
        val requested = get(field)?.let { value ->
            runCatching { value.jsonArray.map { it.jsonPrimitive.content }.toSet() }.getOrNull()
                ?: return false
        } ?: return@all true
        val supported = walletMetadata[field]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
        requested.isNotEmpty() && supported.any(requested::contains)
    }

    private fun <T> Iterable<T>.toJsonArray(toPrimitive: (T) -> JsonPrimitive): JsonArray =
        JsonArray(map(toPrimitive))
}

internal fun AuthorizationRequest.supportedPresentationAlgorithms(
    format: WalletPresentationFormatRegistry.SupportedFormat,
    field: String,
): Set<String>? {
    val formatMetadata = clientMetadata?.vpFormatsSupported
        ?.entries
        ?.firstOrNull { it.key in format.allIds }
        ?.value
        ?: return null
    val value = formatMetadata[field] ?: return null
    val values = value as? JsonArray
        ?: throw IllegalArgumentException("Verifier metadata $field must be an array")
    require(values.isNotEmpty()) { "Verifier metadata $field must not be empty" }
    return values.mapTo(mutableSetOf()) {
        (it as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("Verifier metadata $field values must be primitives")
    }
}
