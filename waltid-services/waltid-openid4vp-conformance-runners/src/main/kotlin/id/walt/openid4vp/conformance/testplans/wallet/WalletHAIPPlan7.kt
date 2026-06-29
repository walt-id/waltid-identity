package id.walt.openid4vp.conformance.testplans.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * HAIP Plan 7: Negative Tests (Security Validation)
 * 
 * Tests that wallet correctly REJECTS non-HAIP-compliant requests:
 * - Unsigned requests (when HAIP policy enforced)
 * - Requests requiring cleartext responses (when HAIP policy enforced)
 * - Requests with weak cryptographic parameters
 * - Requests with invalid certificates
 * - Requests with replay attacks
 * 
 * Expected test modules (9):
 * - oid4vp-1final-wallet-haip-reject-unsigned-request
 * - oid4vp-1final-wallet-haip-reject-cleartext-response
 * - oid4vp-1final-wallet-haip-reject-weak-curve
 * - oid4vp-1final-wallet-haip-reject-weak-hash
 * - oid4vp-1final-wallet-haip-reject-missing-holder-binding
 * - oid4vp-1final-wallet-haip-reject-expired-certificate
 * - oid4vp-1final-wallet-haip-reject-untrusted-ca
 * - oid4vp-1final-wallet-haip-reject-wallet-nonce-mismatch
 * - oid4vp-1final-wallet-haip-reject-insecure-origin
 */
class WalletHAIPPlan7(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "HAIP Plan 7: Negative Tests (Security Validation)"

    override val planName = "oid4vp-1final-wallet-haip-negative-test-plan"

    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "client_id_prefix" to "x509_san_dns",
        "vp_profile" to "haip"
    )

    override val expectRejection = true

    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "description": "Wallet HAIP - Negative Tests (Security Validation)",
            "wallet": {
                "endpoint": "$walletApiUrl/wallet/present",
                "credential_format": "sd_jwt_vc",
                "security_policy": "haip"
            },
            "server": {
                "authorization_endpoint": "https://$conformanceHost:$conformancePort"
            },
            "publish": "everything"
        }
        """.trimIndent()
    )
}
