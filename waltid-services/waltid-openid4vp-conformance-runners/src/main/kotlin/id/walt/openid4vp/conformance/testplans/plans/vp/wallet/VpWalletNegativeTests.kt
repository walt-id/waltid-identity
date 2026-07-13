package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * VP Wallet Test Plan: Negative Tests - Security Validation (HAIP)
 * 
 * Tests that wallet correctly REJECTS non-HAIP-compliant requests:
 * - Unsigned requests (when HAIP policy enforced)
 * - Requests requiring cleartext responses (when HAIP policy enforced)
 * - Requests with weak cryptographic parameters
 * - Requests with invalid certificates
 * - Requests with replay attacks
 * 
 * HAIP Security Requirements:
 * - Must reject unsigned requests (MANDATORY)
 * - Must reject cleartext response requests (MANDATORY)
 * - Must reject weak cryptographic parameters (MANDATORY)
 * - Must reject untrusted certificates (MANDATORY)
 * 
 * Expected test modules (9):
 * - oid4vp-1final-wallet-reject-unsigned-request
 * - oid4vp-1final-wallet-reject-cleartext-response
 * - oid4vp-1final-wallet-reject-weak-curve
 * - oid4vp-1final-wallet-reject-weak-hash
 * - oid4vp-1final-wallet-reject-missing-holder-binding
 * - oid4vp-1final-wallet-reject-expired-certificate
 * - oid4vp-1final-wallet-reject-untrusted-ca
 * - oid4vp-1final-wallet-reject-wallet-nonce-mismatch
 * - oid4vp-1final-wallet-reject-insecure-origin
 */
class VpWalletNegativeTests(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "VP Wallet: Negative Tests - Security Validation (HAIP)"

    override val planName = "oid4vp-1final-wallet-haip-test-plan"

    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "response_mode" to "direct_post.jwt"
    )

    override val expectRejection = true

    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "waltid_wallet_negative_haip",
            "description": "VP Wallet: Negative Tests - Security Validation (HAIP)",
            "server": {
                "authorization_endpoint": "$walletApiUrl"
            },
            "client": {
                "authorization_encrypted_response_alg": "ECDH-ES",
                "authorization_encrypted_response_enc": "A256GCM"
            },
            "publish": "everything"
        }
        """.trimIndent()
    )
}
