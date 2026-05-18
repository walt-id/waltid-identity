package id.waltid.openid4vp.wallet

import id.walt.cose.toCoseAlgorithm
import id.walt.crypto.keys.KeyType
import id.walt.dcql.models.CredentialFormat
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
        capabilitiesFromKeyTypes(KeyType.entries.toSet())

    fun capabilitiesFromKeyTypes(keyTypes: Set<KeyType>): RuntimeCapabilities {
        val supportedJwsAlgorithms = keyTypes
            .map(KeyType::jwsAlg)
            .distinct()
            .sorted()

        val supportedMdocCoseAlgorithms = keyTypes
            .mapNotNull(KeyType::toCoseAlgorithm)
            .distinct()
            .sorted()

        return RuntimeCapabilities(
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
    }

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
