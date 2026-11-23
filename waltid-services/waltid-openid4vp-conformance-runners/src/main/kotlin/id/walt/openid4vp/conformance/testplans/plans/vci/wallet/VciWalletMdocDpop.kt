package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.config.ConformanceSuiteAlias
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * VCI Wallet Test Plan: ISO mdoc with DPoP
 *
 * Tests wallet's ability to receive ISO 18013-5 mdoc credentials from an issuer
 * using DPoP sender constraint and authorization_code grant.
 *
 * ## Test Configuration
 *
 * | Property | Value |
 * |----------|-------|
 * | Credential Format | mdoc (suite variant for mso_mdoc) |
 * | Sender Constraint | dpop |
 * | Client Authentication | private_key_jwt |
 * | Grant Type | authorization_code |
 * | Flow Variant | issuer_initiated |
 * | FAPI Profile | vci |
 * | Credential Encryption | plain |
 * | Issuance Mode | immediate |
 *
 * @param walletApiUrl Base URL of wallet-api2
 * @param credentialOfferEndpoint Endpoint where conformance suite sends offers
 * @param redirectUri OAuth redirect URI for callbacks
 * @param conformanceHost Conformance suite hostname
 * @param conformancePort Conformance suite port
 * @param adapterHost Host IP for Docker container access (default 127.0.0.1)
 */
class VciWalletMdocDpop(
    val walletApiUrl: String,
    val credentialOfferEndpoint: String,
    val redirectUri: String,
    val conformanceHost: String,
    val conformancePort: Int,
    val adapterHost: String = "127.0.0.1"
) : VciWalletTestPlan {

    override val description = "VCI Wallet: ISO mdoc + DPoP + private_key_jwt + authorization_code"

    override val variant = mapOf(
        "credential_format" to "mdoc",
        "sender_constrain" to "dpop",
        "client_auth_type" to "private_key_jwt",
        "vci_grant_type" to "authorization_code",
        "vci_authorization_code_flow_variant" to "issuer_initiated",
        // Wallet2 authorizes with `authorization_details` carrying the credential_configuration_id
        // (OID4VCI 1.0 Section 5.1.2), i.e. it is a Rich Authorization Requests client. With "simple"
        // - which is also the suite's default when the axis is unset - AbstractVCIWalletTest runs
        // ExtractRequestedScopes ("only check scopes if we expect scopes") and fails the module with
        // "Missing scope parameter".
        "authorization_request_type" to "rar",
        "fapi_request_method" to "unsigned",
        "vci_credential_encryption" to "plain",
        "vci_credential_issuance_mode" to "immediate",
        "vci_credential_offer_variant" to "by_value",
        "fapi_profile" to "vci"
    )

    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "${ConformanceSuiteAlias.unique("vci_wallet_mdoc_dpop")}",
            "description": "Wallet VCI - ISO mdoc + DPoP + private_key_jwt + authorization_code",
            "server": {
                "jwks": {
                    "keys": [
                        {
                            "kty": "EC",
                            "crv": "P-256",
                            "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
                            "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E",
                            "use": "sig",
                            "alg": "ES256",
                            "kid": "issuer-key-1"
                        }
                    ]
                }
            },
            "client": {
                "client_id": "wallet-conformance-test",
                "redirect_uri": "$redirectUri",
                "jwks": {
                    "keys": [
                        {
                            "kty": "EC",
                            "crv": "P-256",
                            "x": "d5KVpCdze-46QteHfgAswRurlSYUylJ1JntvcbaZ__Y",
                            "y": "uqvaPeOm7SGsdXr34frqkJGAz8tHmR0EmpsSbfqgwDA",
                            "use": "sig",
                            "alg": "ES256",
                            "kid": "wallet-static-key"
                        }
                    ]
                }
            },
            "credential": {
                "signing_jwk": {
                    "kty": "EC",
                    "crv": "P-256",
                    "x": "HsIzLDaBvEhYF8u_Rs-UMk82ISNOMvipGCpyfCjA1nk",
                    "y": "c9oagfJlhCdS15GtMCW80liuR4LOAX21xSxA7Z0-efc",
                    "d": "c6XRnq85BooKJ3D7VAJGJ0NxZy9uROeCn5_a58eC8Bs",
                    "use": "sig",
                    "alg": "ES256",
                    "kid": "credential-key-1",
                    "x5c": [
                        "MIICHjCCAcSgAwIBAgIUd++v16C/8Sj5jt4mvlG6FDj5iFMwCgYIKoZIzj0EAwIwKjEbMBkGA1UEAwwSVGVzdCBDcmVkZW50aWFsIENBMQswCQYDVQQGEwJBVDAeFw0yNjExMDgwOTMyMTBaFw0yODAyMDgwOTMyMTBaMEUxHzAdBgNVBAMMFlRlc3QgQ3JlZGVudGlhbCBJc3N1ZXIxFTATBgNVBAoMDFdhbHQuaWQgVGVzdDELMAkGA1UEBhMCQVQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQewjMsNoG8SFgXy79Gz5QyTzYhI04y+KkYKnJ8KMDWeXPaGoHyZYQnUteRrTAlvNJYrkeCzgF9tcUsQO2dPnn3o4GsMIGpMB0GA1UdDgQWBBSYdW6XBwBF+T9TBmVxlh/rbE0klTAOBgNVHQ8BAf8EBAMCB4AwFQYDVR0lAQH/BAswCQYHKIGMXQUBAjAaBgNVHRIEEzARhg9odHRwczovL3dhbHQuaWQwJAYDVR0fBB0wGzAZoBegFYYTaHR0cHM6Ly93YWx0LmlkL2NybDAfBgNVHSMEGDAWgBRrhlniwmD5fIMR/cNeNY94otbF2zAKBggqhkjOPQQDAgNIADBFAiAEEE/qbaeE2ZWyxpK+++RQ+1RiksEZCk7ug0g/CsdkiAIhAJ+UqPitNTgQSBGX9r0OjPr487w8bNUu2/YTq6VduSE6",
                        "MIIB2TCCAYCgAwIBAgIUSjW1Pd3Y7jR3XPFTp5rGqnQeuS8wCgYIKoZIzj0EAwIwKjEbMBkGA1UEAwwSVGVzdCBDcmVkZW50aWFsIENBMQswCQYDVQQGEwJBVDAeFw0yNjExMDgwOTMyMTBaFw0zNTExMDcwOTMyMTBaMCoxGzAZBgNVBAMMElRlc3QgQ3JlZGVudGlhbCBDQTELMAkGA1UEBhMCQVQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAARCmqVhfaXBdoklHTnmdcgg+TUYZAiwFxTAZgTKlPu2A0u/VtEEVfBIt3SJcxKReJoND46m6MzGDPdc7c6SLy+Qo4GDMIGAMBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYDVR0PAQH/BAQDAgEGMB0GA1UdDgQWBBRrhlniwmD5fIMR/cNeNY94otbF2zAaBgNVHRIEEzARhg9odHRwczovL3dhbHQuaWQwHwYDVR0jBBgwFoAUa4ZZ4sJg+XyDEf3DXjWPeKLWxdswCgYIKoZIzj0EAwIDRwAwRAIgKBXCZQUP/TGzXRi9443bW1Cjv/KinU2lXviiwMpo0zMCIC2BTXpI+gfFKYGm1li0/LTOn/ZqxF/cu4rujOQZdYE3"
                    ]
                }
            },
            "vci": {
                "credential_offer_endpoint": "${ConformanceConfig.vciSuiteCredentialOfferEndpoint(adapterHost)}",
                "credential_configuration_id": "eu.europa.ec.eudi.pid.mdoc.1"
            },
            "waitTimeoutSeconds": 30,
            "publish": "no"
        }
        """.trimIndent()
    )
}
