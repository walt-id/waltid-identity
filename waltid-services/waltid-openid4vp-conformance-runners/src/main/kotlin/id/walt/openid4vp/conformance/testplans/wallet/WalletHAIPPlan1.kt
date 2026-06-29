package id.walt.openid4vp.conformance.testplans.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * HAIP Plan 1: SD-JWT VC Baseline
 * 
 * Tests wallet's ability to:
 * - Authenticate signed authorization requests (x509_san_dns)
 * - Generate encrypted VP responses (direct_post.jwt)
 * - Include KB-JWT holder binding
 * - Use P-256 keys and SHA-256 hashing
 * 
 * Expected test modules (11):
 * - oid4vp-1final-wallet-haip-happy-flow
 * - oid4vp-1final-wallet-haip-minimal-cnf-jwk
 * - oid4vp-1final-wallet-haip-request-uri-method-post
 * - oid4vp-1final-wallet-haip-invalid-kb-jwt-signature
 * - oid4vp-1final-wallet-haip-invalid-credential-signature
 * - oid4vp-1final-wallet-haip-invalid-sd-hash
 * - oid4vp-1final-wallet-haip-invalid-kb-jwt-nonce
 * - oid4vp-1final-wallet-haip-invalid-kb-jwt-aud
 * - oid4vp-1final-wallet-haip-kb-jwt-iat-in-past
 * - oid4vp-1final-wallet-haip-kb-jwt-iat-in-future
 * - oid4vp-1final-wallet-haip-transaction-data-validation
 */
class WalletHAIPPlan1(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "HAIP Plan 1: SD-JWT VC Baseline (x509_san_dns + direct_post.jwt)"

    override val planName = "oid4vp-1final-wallet-haip-test-plan"

    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "client_id_prefix" to "x509_san_dns",
        "request_method" to "request_uri_signed",
        "vp_profile" to "haip",
        "response_mode" to "direct_post.jwt"
    )

    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "description": "Wallet HAIP - SD-JWT VC + x509_san_dns + request_uri_signed + direct_post.jwt",
            "wallet": {
                "endpoint": "$walletApiUrl/wallet/present",
                "credential_format": "sd_jwt_vc"
            },
            "server": {
                "authorization_endpoint": "https://$conformanceHost:$conformancePort"
            },
            "publish": "everything"
        }
        """.trimIndent()
    )
}
