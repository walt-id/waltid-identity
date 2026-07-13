package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * HAIP-Compliant VP Wallet Test Plan: mDL (ISO 18013-5) + x509_hash + request_uri_signed + direct_post.jwt
 * 
 * Tests wallet's ability to handle STRICT HAIP compliance with ISO mDL credentials:
 * - **x509_hash client identification** — HAIP §5 P-02 (MANDATORY)
 * - Authenticate signed authorization requests (JAR) — HAIP §5.1 W-27
 * - Generate encrypted VP responses with mdoc (direct_post.jwt) — HAIP §5.1 W-28
 * - Include DeviceAuth holder binding (MSO + DeviceSignature) — HAIP §6.1.2
 * - Validate session transcript per ISO 18013-7 Annex C
 * - Use P-256 keys and SHA-256 hashing — HAIP §7-8 CF-02, CF-03
 * 
 * CRITICAL HAIP REQUIREMENTS:
 * - MUST use x509_hash (NOT x509_san_dns) per HAIP §5 requirement P-02
 * - MUST validate x509_hash matches SHA-256 hash of DER-encoded leaf certificate
 * - MUST validate certificate chain (no trust anchor, not self-signed)
 * - MUST encrypt response using ECDH-ES with P-256
 * - MUST include DeviceAuth with session transcript binding
 * 
 * @see <a href="https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html">HAIP 1.0</a>
 * @see <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5:2021</a>
 */
class VpWalletMdlX509HashRequestUriSignedDirectPostHaip(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "VP Wallet: mDL + x509_hash + request_uri_signed + direct_post.jwt (HAIP Strict)"

    override val planName = "oid4vp-1final-wallet-haip-test-plan"

    // Note: client_id_prefix is NOT specified here because the HAIP test plan
    // (oid4vp-1final-wallet-haip-test-plan) already defines it per-module.
    override val variant = mapOf(
        "credential_format" to "iso_mdl",
        "response_mode" to "direct_post.jwt"
    )

    /**
     * Test plan configuration for conformance suite.
     * 
     * Required for HAIP wallet tests:
     * - client.jwks with x5c: For signing authorization requests and computing x509_hash client_id
     * - client.dcql: DCQL query defining what credentials/claims to request (mDL format)
     * - credential.trust_anchor: PEM certificate for validating credential signatures
     * - credential.status_list_trust_anchor: PEM certificate for status list validation
     */
    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "waltid_vp_wallet_mdl_x509_hash",
            "description": "VP Wallet: mDL + x509_hash + request_uri_signed + direct_post.jwt (HAIP Strict)",
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
                            "id": "mdl",
                            "format": "mso_mdoc",
                            "meta": {
                                "doctype_value": "org.iso.18013.5.1.mDL"
                            },
                            "claims": [
                                {"path": ["org.iso.18013.5.1", "given_name"]},
                                {"path": ["org.iso.18013.5.1", "family_name"]},
                                {"path": ["org.iso.18013.5.1", "birth_date"]}
                            ]
                        }
                    ]
                }
            },
            "credential": {
                "trust_anchor_pem": ${TestKeyMaterial.VERIFIER_CA_PEM_JSON},
                "status_list_trust_anchor_pem": ${TestKeyMaterial.VERIFIER_CA_PEM_JSON}
            },
            "publish": "everything"
        }
        """.trimIndent()
    )
}
