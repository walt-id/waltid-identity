@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet

import id.walt.cose.Cose
import id.walt.crypto.keys.KeyType
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.supportsJwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.KeySpec
import id.walt.dcql.models.CredentialFormat
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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
        val supportedMdocCoseAlgorithms = if (signingKeys.any { key ->
                key.spec == KeySpec.Ec(EcCurve.P256) &&
                    key.capabilities.supportsSignatureAlgorithm(JwsAlgorithm.ES256.toSignatureAlgorithm())
            }
        ) listOf(Cose.Algorithm.ESP256) else emptyList()

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

        val supportedMdocCoseAlgorithms = if (KeyType.secp256r1 in keyTypes) {
            listOf(Cose.Algorithm.ESP256)
        } else emptyList()

        return capabilities(supportedJwsAlgorithms, supportedMdocCoseAlgorithms)
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
