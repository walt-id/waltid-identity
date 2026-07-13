package id.walt.openid4vp.conformance.testplans.plans.vp.wallet

import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * VP Wallet Test Plan: mDL (ISO 18013-5) + x509_san_dns + request_uri_signed + direct_post.jwt (HAIP)
 * 
 * Tests wallet's ability to:
 * - Authenticate signed authorization requests (x509_san_dns)
 * - Generate encrypted VP responses with mdoc (direct_post.jwt)
 * - Include DeviceAuth holder binding (MSO + DeviceSignature)
 * - Validate session transcript per ISO 18013-7 Annex C
 * 
 * NOTE: This uses x509_san_dns (baseline) instead of x509_hash (HAIP strict).
 * For official HAIP certification, use VpWalletMdlX509HashRequestUriSignedDirectPostHaip.
 */
class VpWalletMdlX509SanDnsRequestUriSignedDirectPost(
    override val walletApiUrl: String,
    val conformanceHost: String,
    val conformancePort: Int
) : WalletTestPlan {

    override val description = "VP Wallet: mDL + x509_san_dns + request_uri_signed + direct_post.jwt (HAIP)"

    override val planName = "oid4vp-1final-wallet-haip-test-plan"

    // Note: client_id_prefix and vp_profile are NOT specified here because the HAIP test plan
    // (oid4vp-1final-wallet-haip-test-plan) already defines them per-module.
    override val variant = mapOf(
        "credential_format" to "iso_mdl",
        "response_mode" to "direct_post.jwt"
    )

    /**
     * Test plan configuration for conformance suite.
     * 
     * Required for HAIP wallet tests:
     * - client.jwks with x5c: For signing authorization requests and computing client_id
     * - client.dcql: DCQL query defining what credentials/claims to request (mDL format)
     * - credential.trust_anchor: PEM certificate for validating credential signatures
     * - credential.status_list_trust_anchor: PEM certificate for status list validation
     */
    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "waltid_wallet_mdl_x509san",
            "description": "VP Wallet: mDL + x509_san_dns + request_uri_signed + direct_post.jwt (HAIP)",
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
