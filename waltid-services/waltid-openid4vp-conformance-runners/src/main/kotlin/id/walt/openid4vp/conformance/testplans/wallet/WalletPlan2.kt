package id.walt.openid4vp.conformance.testplans.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Wallet Plan 2: mDL (Mobile Driving License) Baseline (HAIP)
 * 
 * Tests wallet's ability to:
 * - Authenticate signed authorization requests (x509_san_dns)
 * - Generate encrypted VP responses with mdoc (direct_post.jwt)
 * - Include DeviceAuth holder binding (MSO + DeviceSignature)
 * - Validate session transcript per ISO 18013-7 Annex C
 * 
 * HAIP Requirements:
 * - Signed request authentication (MANDATORY)
 * - Encrypted response generation (MANDATORY)
 * - DeviceAuth holder binding (MANDATORY for mdoc)
 * - Session transcript validation (MANDATORY for mdoc)
 * 
 * Expected test modules (6):
 * - oid4vp-1final-wallet-mdl-happy-flow
 * - oid4vp-1final-wallet-mdl-device-auth
 * - oid4vp-1final-wallet-mdl-session-transcript
 * - oid4vp-1final-wallet-mdl-invalid-mso-signature
 * - oid4vp-1final-wallet-mdl-invalid-device-signature
 * - oid4vp-1final-wallet-mdl-replay-protection
 */
class WalletPlan2(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "Wallet Plan 2: mDL Baseline (HAIP - x509_hash + direct_post.jwt)"

    override val planName = "oid4vp-1final-wallet-haip-test-plan"

    override val variant = mapOf(
        "credential_format" to "iso_mdl",
        "response_mode" to "direct_post.jwt"
    )

    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "Wallet Plan 2 - mDL (HAIP)",
            "description": "Wallet HAIP - mDL + x509_san_dns + request_uri_signed + direct_post.jwt",
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
