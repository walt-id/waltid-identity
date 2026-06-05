package id.walt.openid4vp.conformance.testplans.plans

import id.walt.openid4vp.conformance.testplans.runner.req.IssuerTestPlanConfiguration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * OpenID4VCI 1.0 Final Issuer Test Plan
 * 
 * Tests the Issuer implementation against the OpenID Conformance Suite.
 * The conformance suite acts as a wallet testing our issuer.
 * 
 * Profile:
 * - FAPI: vci
 * - Sender Constraint: dpop
 * - Client Authentication: client_attestation
 * - Auth code flow variant: Both (wallet_initiated, issuer_initiated)
 * - Credential Format: Both (sd_jwt_vc, iso_mdl)
 * - Auth Request Type: Simple
 * - Request Method: Unsigned
 * - Grant Type: Both (authorization_code, pre_authorization_code)
 * - Credential Response Encryption: Plain
 * - FAPI Response Mode: Plain
 */
class Oid4vciIssuerClientAttestationDpop(
    val issuerUrl: String = "https://issuer.localhost",
    val conformanceHost: String = "localhost.emobix.co.uk",
    val conformancePort: Int = 8443
) : IssuerTestPlan {

    // Client keys for DPoP
    // language=JSON
    private val clientJwks = """
        {
            "keys": [
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "alg": "ES256",
                    "use": "sig",
                    "kid": "conformance-test-key",
                    "d": "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
                    "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
                    "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"
                }
            ]
        }
    """.trimIndent()

    // Client attestation issuer (the entity that issues client attestations)
    private val clientAttestationIssuer = "https://client-attestation.example.com"

    override val config = IssuerTestPlanConfiguration(
        testPlanCreationUrl = {
            append("planName", "oid4vci-1_0-issuer-test-plan")
            append(
                "variant", /* language=json */
                """{
                    "fapi_profile": "plain_fapi",
                    "sender_constrain": "dpop",
                    "client_auth_type": "client_attestation",
                    "vci_authorization_code_flow_variant": "wallet_initiated",
                    "authorization_request_type": "simple",
                    "fapi_request_method": "unsigned",
                    "vci_grant_type": "authorization_code",
                    "vci_credential_issuer_metadata": "discovery",
                    "fapi_response_mode": "plain_response"
                }""".trimIndent()
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                "vci": {
                    "credential_issuer_url": "$issuerUrl",
                    "credential_configuration_id": "VerifiableCredential",
                    "client_attestation_issuer": "$clientAttestationIssuer"
                },
                "client": {
                    "client_id": "conformance-test-client",
                    "jwks": $clientJwks
                },
                "client2": {
                    "client_id": "conformance-test-client-2",
                    "jwks": $clientJwks
                },
                "description": "OID4VCI 1.0 Issuer - DPoP + Client Attestation"
            }
            """.trimIndent()
        ),

        issuerUrl = issuerUrl
    )
}

/**
 * Variant for pre-authorized code flow
 */
class Oid4vciIssuerClientAttestationDpopPreAuth(
    val issuerUrl: String = "https://issuer.localhost",
    val conformanceHost: String = "localhost.emobix.co.uk",
    val conformancePort: Int = 8443
) : IssuerTestPlan {

    // language=JSON
    private val clientJwks = """
        {
            "keys": [
                {
                    "kty": "EC",
                    "crv": "P-256",
                    "alg": "ES256",
                    "use": "sig",
                    "kid": "conformance-test-key",
                    "d": "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
                    "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
                    "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E"
                }
            ]
        }
    """.trimIndent()

    private val clientAttestationIssuer = "https://client-attestation.example.com"

    override val config = IssuerTestPlanConfiguration(
        testPlanCreationUrl = {
            append("planName", "oid4vci-1_0-issuer-test-plan")
            append(
                "variant", /* language=json */
                """{
                    "fapi_profile": "plain_fapi",
                    "sender_constrain": "dpop",
                    "client_auth_type": "client_attestation",
                    "vci_authorization_code_flow_variant": "issuer_initiated",
                    "authorization_request_type": "simple",
                    "fapi_request_method": "unsigned",
                    "vci_grant_type": "pre_authorization_code",
                    "vci_credential_issuer_metadata": "discovery",
                    "fapi_response_mode": "plain_response"
                }""".trimIndent()
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                "vci": {
                    "credential_issuer_url": "$issuerUrl",
                    "credential_configuration_id": "VerifiableCredential",
                    "client_attestation_issuer": "$clientAttestationIssuer"
                },
                "client": {
                    "client_id": "conformance-test-client",
                    "jwks": $clientJwks
                },
                "client2": {
                    "client_id": "conformance-test-client-2",
                    "jwks": $clientJwks
                },
                "description": "OID4VCI 1.0 Issuer - DPoP + Client Attestation (Pre-Auth)"
            }
            """.trimIndent()
        ),

        issuerUrl = issuerUrl
    )
}
