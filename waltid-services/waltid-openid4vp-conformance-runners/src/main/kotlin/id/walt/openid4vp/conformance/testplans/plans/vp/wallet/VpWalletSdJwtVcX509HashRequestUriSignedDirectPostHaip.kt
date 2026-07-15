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
 * @see <a href="https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html">HAIP 1.0</a>
 */
class VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    private val uniqueId = System.currentTimeMillis()

    override val description = "VP Wallet: SD-JWT VC + x509_hash + request_uri_signed + direct_post.jwt (HAIP Strict)"

    override val planName = "oid4vp-1final-wallet-haip-test-plan"

    // Note: client_id_prefix is NOT specified here because the HAIP test plan
    // (oid4vp-1final-wallet-haip-test-plan) already defines it per-module.
    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "response_mode" to "direct_post.jwt"
    )

    /**
     * Test plan configuration for conformance suite.
     *
     * Required for HAIP wallet tests:
     * - client.jwks with x5c: For signing authorization requests and computing x509_hash client_id
     * - client.dcql: DCQL query defining what credentials/claims to request
     * - credential.trust_anchor: PEM certificate for validating credential signatures
     * - credential.status_list_trust_anchor: PEM certificate for status list validation
     */
    override val configuration: JsonObject by lazy { Json.decodeFromString(
        """
        {
            "alias": "waltid_sdjwt_x509hash_$uniqueId",
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
                },
                "dcql": {
                    "credentials": [
                        {
                            "id": "pid",
                            "format": "dc+sd-jwt",
                            "meta": {
                                "vct_values": ["http://localhost:7002/openid4vci/identity_credential"]
                            },
                            "claims": [
                                {"path": ["given_name"]},
                                {"path": ["family_name"]},
                                {"path": ["birthdate"]}
                            ]
                        }
                    ]
                }
            },
            "credential": {
                "trust_anchor_pem": ${TestKeyMaterial.ISSUER_CA_PEM_JSON},
                "status_list_trust_anchor_pem": ${TestKeyMaterial.ISSUER_CA_PEM_JSON}
            },
            "publish": "everything"
        }
        """.trimIndent()
    )}
}
