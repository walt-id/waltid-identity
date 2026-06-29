package id.walt.openid4vp.conformance.testplans.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * HAIP Plan 2: mDL (Mobile Driving License) Baseline
 * 
 * Tests wallet's ability to:
 * - Authenticate signed authorization requests (x509_san_dns)
 * - Generate encrypted VP responses with mdoc (direct_post.jwt)
 * - Include DeviceAuth holder binding (MSO + DeviceSignature)
 * - Validate session transcript per ISO 18013-7 Annex C
 * 
 * Expected test modules (6):
 * - oid4vp-1final-wallet-haip-mdl-happy-flow
 * - oid4vp-1final-wallet-haip-mdl-device-auth
 * - oid4vp-1final-wallet-haip-mdl-session-transcript
 * - oid4vp-1final-wallet-haip-mdl-invalid-mso-signature
 * - oid4vp-1final-wallet-haip-mdl-invalid-device-signature
 * - oid4vp-1final-wallet-haip-mdl-replay-protection
 */
class WalletHAIPPlan2(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "HAIP Plan 2: mDL Baseline (x509_san_dns + direct_post.jwt)"

    override val planName = "oid4vp-1final-wallet-test-plan"

    override val variant = mapOf(
        "credential_format" to "iso_mdl",
        "client_id_prefix" to "x509_san_dns",
        "request_method" to "request_uri_signed",
        "vp_profile" to "haip",
        "response_mode" to "direct_post.jwt"
    )

    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "HAIP Plan 2 - mDL",
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
