package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * HAIP-Compliant VP Wallet Test Plan: SD-JWT VC + x509_hash + request_uri_signed + direct_post.jwt
 * 
 * Tests wallet's ability to handle STRICT HAIP compliance:
 * - **x509_hash client identification** — HAIP §5 P-02 (MANDATORY)
 * - Authenticate signed authorization requests (JAR) — HAIP §5.1 W-27
 * - Generate encrypted VP responses (direct_post.jwt) — HAIP §5.1 W-28
 * - Include KB-JWT holder binding — HAIP §6.1.1.1 W-36
 * - Use P-256 keys and SHA-256 hashing — HAIP §7-8 CF-02, CF-03
 * 
 * CRITICAL HAIP REQUIREMENTS:
 * - MUST use x509_hash (NOT x509_san_dns) per HAIP §5 requirement P-02
 * - MUST validate x509_hash matches SHA-256 hash of DER-encoded leaf certificate
 * - MUST validate certificate chain (no trust anchor, not self-signed)
 * - MUST encrypt response using ECDH-ES with P-256
 * - MUST support A256GCM (or A128GCM) for JWE enc
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
 * - oid4vp-1final-wallet-ensure-client-id-x509-hash
 * - oid4vp-1final-wallet-ensure-response-type-always-vp-token
 * - oid4vp-1final-wallet-ensure-response-mode-direct-post-jwt
 * - oid4vp-1final-wallet-ensure-response-encrypted
 * - oid4vp-1final-wallet-ensure-nonce-always-present
 * 
 * @see <a href="https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html">HAIP 1.0</a>
 */
class VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "VP Wallet: SD-JWT VC + x509_hash + request_uri_signed + direct_post.jwt (HAIP Strict)"

    override val planName = "oid4vp-1final-wallet-haip-test-plan"

    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "client_id_prefix" to "x509_hash",  // HAIP §5 P-02 MANDATORY
        "response_mode" to "direct_post.jwt",
        "vp_profile" to "haip"
    )

    /**
     * Test plan configuration for conformance suite.
     * 
     * CRITICAL: Must include `client.jwks` with x5c for x509_hash scheme!
     * The conformance suite (acting as verifier) needs this to:
     * 1. Sign the authorization request (JAR) with the certificate
     * 2. Compute x509_hash client_id from the leaf certificate
     * 
     * Per HAIP spec:
     * - x509_hash = "x509_hash:" + base64url(SHA-256(DER(leaf_cert)))
     * - Certificate chain must NOT include trust anchor (root CA)
     * - Leaf certificate must NOT be self-signed
     */
    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "vp_wallet_sd_jwt_vc_haip_x509_hash",
            "description": "VP Wallet: SD-JWT VC + x509_hash + request_uri_signed + direct_post.jwt (HAIP Strict)",
            "server": {
                "authorization_endpoint": "$walletApiUrl"
            },
            "client": {
                "client_id_scheme": "x509_hash",
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
