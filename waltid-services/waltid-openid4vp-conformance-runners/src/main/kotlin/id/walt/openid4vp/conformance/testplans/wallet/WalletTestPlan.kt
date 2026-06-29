package id.walt.openid4vp.conformance.testplans.wallet

import kotlinx.serialization.json.JsonObject

/**
 * Base interface for wallet-side conformance test plans
 * 
 * Unlike verifier test plans, wallet test plans define:
 * - How the wallet should be configured/initialized
 * - What test plan to create on the conformance suite
 * - Expected outcomes for wallet behavior
 * 
 * The conformance suite acts as the verifier and sends
 * authorization requests to the wallet endpoint.
 */
interface WalletTestPlan {
    /**
     * Human-readable description of this test plan
     */
    val description: String

    /**
     * OpenID4VP test plan name on conformance suite
     * Example: "oid4vp-1final-wallet-haip-test-plan"
     */
    val planName: String

    /**
     * Test plan variant parameters
     * Example: {"credential_format": "sd_jwt_vc", "client_id_prefix": "x509_san_dns"}
     */
    val variant: Map<String, String>

    /**
     * Test plan configuration JSON for conformance suite
     * Defines wallet endpoint, credentials, etc.
     */
    val configuration: JsonObject

    /**
     * Whether this test plan expects the wallet to reject requests
     * (for negative security tests)
     */
    val expectRejection: Boolean
        get() = false

    /**
     * Whether this is an optional test plan
     */
    val optional: Boolean
        get() = false

    /**
     * Wallet API base URL
     */
    val walletApiUrl: String

    /**
     * Extract credential format from variant
     */
    val credentialFormat: String
        get() = variant["credential_format"] ?: "sd_jwt_vc"

    /**
     * Extract client ID scheme from variant
     */
    val clientIdScheme: String
        get() = variant["client_id_prefix"] ?: "x509_san_dns"

    /**
     * Check if this is a HAIP test plan
     */
    val isHAIP: Boolean
        get() = variant["vp_profile"] == "haip"

    /**
     * Check if encrypted response is required
     */
    val requiresEncryptedResponse: Boolean
        get() = variant["response_mode"] == "direct_post.jwt" || isHAIP

    /**
     * Check if signed request is required
     */
    val requiresSignedRequest: Boolean
        get() = variant["request_method"] == "request_uri_signed" || isHAIP

    /**
     * Check if this is an mdoc credential format
     */
    val isMdoc: Boolean
        get() = credentialFormat.startsWith("iso_")

    /**
     * Check if this is an SD-JWT VC format
     */
    val isSdJwtVc: Boolean
        get() = credentialFormat == "sd_jwt_vc"
}
