package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * VP Wallet Test Plan: SD-JWT VC + x509_san_dns + request_uri_signed + direct_post.jwt (HAIP)
 * 
 * Tests wallet's ability to:
 * - Authenticate signed authorization requests (x509_san_dns)
 * - Generate encrypted VP responses (direct_post.jwt)
 * - Include KB-JWT holder binding
 * - Use P-256 keys and SHA-256 hashing
 * 
 * NOTE: This uses x509_san_dns (baseline) instead of x509_hash (HAIP strict).
 * For official HAIP certification, use VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip.
 * 
 * Expected test modules (14):
 * - oid4vp-1final-wallet-happy-flow
 * - oid4vp-1final-wallet-alternate-request-object-claims
 * - oid4vp-1final-wallet-request-uri-method-post
 * - oid4vp-1final-wallet-dcql-sd-jwt-vc-happy-flow
 * - etc.
 */
class VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "VP Wallet: SD-JWT VC + x509_san_dns + request_uri_signed + direct_post.jwt (HAIP)"

    override val planName = "oid4vp-1final-wallet-haip-test-plan"

    // Note: client_id_prefix and vp_profile are NOT specified here because the HAIP test plan
    // (oid4vp-1final-wallet-haip-test-plan) already defines them per-module.
    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "response_mode" to "direct_post.jwt"
    )

    /**
     * Test plan configuration for conformance suite.
     * 
     * Includes `client.jwks` with x5c so conformance suite can:
     * 1. Sign the authorization request (JAR)
     * 2. Extract DNS SAN for x509_san_dns client_id
     */
    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "vp_wallet_sd_jwt_vc_haip",
            "description": "VP Wallet: SD-JWT VC + x509_san_dns + request_uri_signed + direct_post.jwt (HAIP)",
            "server": {
                "authorization_endpoint": "$walletApiUrl"
            },
            "client": {
                "client_id_scheme": "x509_san_dns",
                "authorization_encrypted_response_alg": "ECDH-ES",
                "authorization_encrypted_response_enc": "A256GCM",
                "jwks": {
                    "keys": [
                        {
                            "kty": "EC",
                            "crv": "P-256",
                            "alg": "ES256",
                            "use": "sig",
                            "d": "AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA",
                            "x": "G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0",
                            "y": "VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4",
                            "x5c": ["${TestKeyMaterial.VERIFIER_LEAF_CERT}", "${TestKeyMaterial.VERIFIER_CA_CERT}"]
                        }
                    ]
                }
            },
            "publish": "everything"
        }
        """.trimIndent()
    )
}
