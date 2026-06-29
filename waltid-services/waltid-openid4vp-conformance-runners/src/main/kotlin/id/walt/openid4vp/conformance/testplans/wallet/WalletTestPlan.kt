package id.walt.openid4vp.conformance.testplans.wallet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wallet Test Plan Configuration
 * 
 * Defines a wallet-side conformance test plan for OpenID4VP.
 * 
 * Unlike verifier test plans (where the conformance suite acts as the wallet),
 * wallet test plans reverse the roles:
 * - Conformance suite = Verifier (generates authorization requests)
 * - Local wallet = Presenter (responds to requests)
 * 
 * @param planName OpenID4VP test plan name (e.g., "oid4vp-1final-wallet-haip-test-plan")
 * @param variant Test plan variant parameters
 * @param walletApiUrl Base URL of local wallet API instance
 * @param conformanceHost Conformance suite hostname
 * @param conformancePort Conformance suite port
 * @param haipMode Enable HAIP compliance mode
 * @param expectRejection For negative tests - expect wallet to reject request
 */
@Serializable
data class WalletTestPlan(
    val planName: String,
    val variant: Map<String, String>,
    val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int,
    val haipMode: Boolean = false,
    val expectRejection: Boolean = false
) {
    /**
     * Get credential format from variant
     */
    val credentialFormat: String
        get() = variant["credential_format"] ?: "sd_jwt_vc"

    /**
     * Get client ID prefix (authentication scheme) from variant
     */
    val clientIdPrefix: String
        get() = variant["client_id_prefix"] ?: "x509_san_dns"

    /**
     * Get request method from variant
     */
    val requestMethod: String
        get() = variant["request_method"] ?: "request_uri_signed"

    /**
     * Get VP profile from variant
     */
    val vpProfile: String
        get() = variant["vp_profile"] ?: "plain_vp"

    /**
     * Get response mode from variant
     */
    val responseMode: String
        get() = variant["response_mode"] ?: if (haipMode) "direct_post.jwt" else "direct_post"

    /**
     * Is this an ISO mdoc credential format?
     */
    val isMdoc: Boolean
        get() = credentialFormat in listOf("iso_mdl", "iso_mdoc", "iso_photoid", "mso_mdoc")

    /**
     * Is this an SD-JWT VC credential format?
     */
    val isSdJwtVc: Boolean
        get() = credentialFormat in listOf("sd_jwt_vc", "dc_sd_jwt")

    /**
     * Is encrypted response required?
     */
    val requiresEncryptedResponse: Boolean
        get() = haipMode || responseMode.endsWith(".jwt")

    /**
     * Is signed request required?
     */
    val requiresSignedRequest: Boolean
        get() = haipMode || requestMethod.contains("signed")

    /**
     * Build variant JSON for conformance suite API
     */
    fun toVariantJson(): JsonObject {
        return kotlinx.serialization.json.buildJsonObject {
            variant.forEach { (key, value) ->
                put(key, kotlinx.serialization.json.JsonPrimitive(value))
            }
        }
    }

    /**
     * Build test plan description for logging
     */
    fun describe(): String = buildString {
        append(planName)
        append(" [")
        append(variant.entries.joinToString(", ") { "${it.key}=${it.value}" })
        append("]")
        if (haipMode) append(" (HAIP)")
        if (expectRejection) append(" (Negative)")
    }
}
