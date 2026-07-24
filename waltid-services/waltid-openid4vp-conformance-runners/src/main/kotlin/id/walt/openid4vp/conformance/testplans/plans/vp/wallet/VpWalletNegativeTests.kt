package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * VP Wallet Test Plan: Negative Tests - Security Validation (HAIP)
 *
 * Tests that wallet correctly rejects non-HAIP-compliant requests.
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
            "alias": "vp_wallet_negative_tests_haip",
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
