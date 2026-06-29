package id.walt.openid4vp.conformance.testplans.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Wallet Plan 1: SD-JWT VC Baseline (HAIP)
 * 
 * Tests wallet's ability to:
 * - Authenticate signed authorization requests (x509_san_dns)
 * - Generate encrypted VP responses (direct_post.jwt)
 * - Include KB-JWT holder binding
 * - Use P-256 keys and SHA-256 hashing
 * 
 * HAIP Requirements:
 * - Signed request authentication (MANDATORY)
 * - Encrypted response generation (MANDATORY)
 * - P-256 key curve (MANDATORY)
 * - SHA-256 hash algorithm (MANDATORY)
 * 
 * Expected test modules (14):
 * - oid4vp-1final-wallet-happy-flow
 * - oid4vp-1final-wallet-alternate-request-object-claims
 * - oid4vp-1final-wallet-request-uri-method-post
 * - oid4vp-1final-wallet-dcql-sd-jwt-vc-happy-flow
 * - oid4vp-1final-wallet-dcql-sd-jwt-vc-credential-query
 * - oid4vp-1final-wallet-dcql-sd-jwt-vc-single-credential-multiple-queries
 * - oid4vp-1final-wallet-ensure-request-object-always-signed
 * - oid4vp-1final-wallet-ensure-request-uri-always-present
 * - oid4vp-1final-wallet-ensure-client-id-equals-client-id-scheme
 * - oid4vp-1final-wallet-ensure-client-id-x509-san-dns
 * - oid4vp-1final-wallet-ensure-response-type-always-vp-token
 * - oid4vp-1final-wallet-ensure-response-mode-direct-post-jwt
 * - oid4vp-1final-wallet-ensure-response-encrypted
 * - oid4vp-1final-wallet-ensure-nonce-always-present
 */
class WalletPlan1(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "Wallet Plan 1: SD-JWT VC Baseline (HAIP - x509_hash + direct_post.jwt)"

    override val planName = "oid4vp-1final-wallet-haip-test-plan"

    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "response_mode" to "direct_post.jwt"
    )

    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "Wallet Plan 1 - SD-JWT VC (HAIP)",
            "description": "Wallet HAIP - SD-JWT VC + x509_san_dns + request_uri_signed + direct_post.jwt",
            "server": {
                "authorization_endpoint": "$walletApiUrl"
            },
            "client": {
                "authorization_encrypted_response_alg": "ECDH-ES",
                "authorization_encrypted_response_enc": "A256GCM"
            },
            "publish": "no"
        }
        """.trimIndent()
    )
}
