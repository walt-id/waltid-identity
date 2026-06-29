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
 * Profile: VCI (plain - not HAIP)
 * - FAPI Profile: vci
 * - Sender Constraint: dpop
 * - Client Authentication: mtls
 * - Authorization Code Flow: wallet_initiated
 * - Auth Request Type: simple
 * - Request Method: unsigned
 * - Grant Type: authorization_code
 * - Credential Format: sd_jwt_vc
 * - Credential Response Encryption: plain
 * - Response Mode: plain_response
 */
class Oid4vciIssuerClientAttestationDpop(
    val issuerUrl: String = "https://issuer.localhost",
    val conformanceHost: String = "localhost.emobix.co.uk",
    val conformancePort: Int = 8443
) : IssuerTestPlan {

    // Client keys for DPoP and MTLS
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

    override val config = IssuerTestPlanConfiguration(
        testPlanCreationUrl = {
            // Non-HAIP plan - requires all variants to be specified
            append("planName", "oid4vci-1_0-issuer-test-plan")
            // Full variant specification for VCI profile
            append(
                "variant", /* language=json */
                """{
                    "fapi_profile": "vci",
                    "sender_constrain": "dpop",
                    "client_auth_type": "mtls",
                    "credential_format": "sd_jwt_vc",
                    "vci_authorization_code_flow_variant": "wallet_initiated",
                    "authorization_request_type": "simple",
                    "fapi_request_method": "unsigned",
                    "vci_grant_type": "authorization_code",
                    "vci_credential_encryption": "plain",
                    "fapi_response_mode": "plain_response"
                }""".trimIndent()
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                "alias": "oid4vci_issuer_sd_jwt_vc_wallet_initiated",
                "description": "OID4VCI 1.0 Issuer - DPoP + MTLS",
                "vci": {
                    "credential_issuer_url": "$issuerUrl"
                },
                "client": {
                    "client_id": "conformance-test-client",
                    "jwks": $clientJwks,
                    "dpop_signing_alg": "ES256"
                },
                "client2": {
                    "client_id": "conformance-test-client-2",
                    "jwks": $clientJwks,
                    "dpop_signing_alg": "ES256"
                },
                "mtls": {
                    "cert": "-----BEGIN CERTIFICATE-----\nMIIBfTCCASOgAwIBAgIUdIGqCM6FoR7iZlGvFoFJ6CvT5/4wCgYIKoZIzj0EAwIw\nGTEXMBUGA1UEAwwOQ29uZm9ybWFuY2UgQ0EwHhcNMjQwMTAxMDAwMDAwWhcNMjUw\nMTAxMDAwMDAwWjAaMRgwFgYDVQQDDA9jb25mb3JtYW5jZS10bHMwWTATBgcqhkjO\nPQIBBggqhkjOPQMBBwNCAAQbREg0GIX6hBQPd3kMad6BC5d6cjb0kNowagy+KgpE\nE3nd3hRrNqRLa6e7wGewS3G61LaSpGFgE9iT1ECuJTeBozUwMzAJBgNVHRMEAjAA\nMAsGA1UdDwQEAwIF4DAZBgNVHREEEjAQgg5sb2NhbGhvc3Q6NzAwMjAKBggqhkjO\nPQQDAgNIADBFAiEA7E9z2RPlHPHcBxkGJvPDXFl0RJnqwE8bUHJNK0D4fAQCICXn\nKjGE0D8wq3O8SJl7xJ0Y+1n3qP7LKC0g4i8aWL+V\n-----END CERTIFICATE-----",
                    "key": "-----BEGIN EC PRIVATE KEY-----\nMHQCAQEEICieJN1XJeUo/TH6uK6zVwZtjKD6GN/wJPzyEfxILBYUoAcGBSuBBAAK\noUQDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2un\nu8BnsEtxutS2kqRhYBPYk9RAriU3gQ==\n-----END EC PRIVATE KEY-----"
                },
                "mtls2": {
                    "cert": "-----BEGIN CERTIFICATE-----\nMIIBfTCCASOgAwIBAgIUdIGqCM6FoR7iZlGvFoFJ6CvT5/4wCgYIKoZIzj0EAwIw\nGTEXMBUGA1UEAwwOQ29uZm9ybWFuY2UgQ0EwHhcNMjQwMTAxMDAwMDAwWhcNMjUw\nMTAxMDAwMDAwWjAaMRgwFgYDVQQDDA9jb25mb3JtYW5jZS10bHMwWTATBgcqhkjO\nPQIBBggqhkjOPQMBBwNCAAQbREg0GIX6hBQPd3kMad6BC5d6cjb0kNowagy+KgpE\nE3nd3hRrNqRLa6e7wGewS3G61LaSpGFgE9iT1ECuJTeBozUwMzAJBgNVHRMEAjAA\nMAsGA1UdDwQEAwIF4DAZBgNVHREEEjAQgg5sb2NhbGhvc3Q6NzAwMjAKBggqhkjO\nPQQDAgNIADBFAiEA7E9z2RPlHPHcBxkGJvPDXFl0RJnqwE8bUHJNK0D4fAQCICXn\nKjGE0D8wq3O8SJl7xJ0Y+1n3qP7LKC0g4i8aWL+V\n-----END CERTIFICATE-----",
                    "key": "-----BEGIN EC PRIVATE KEY-----\nMHQCAQEEICieJN1XJeUo/TH6uK6zVwZtjKD6GN/wJPzyEfxILBYUoAcGBSuBBAAK\noUQDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2un\nu8BnsEtxutS2kqRhYBPYk9RAriU3gQ==\n-----END EC PRIVATE KEY-----"
                }
            }
            """.trimIndent()
        ),

        issuerUrl = issuerUrl
    )
}

/**
 * Variant for pre-authorized code flow (issuer-initiated)
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

    override val config = IssuerTestPlanConfiguration(
        testPlanCreationUrl = {
            // Non-HAIP plan - requires all variants to be specified
            append("planName", "oid4vci-1_0-issuer-test-plan")
            // Full variant specification for pre-auth flow
            // Note: pre_authorization_code isn't a valid variant - use issuer_initiated instead
            append(
                "variant", /* language=json */
                """{
                    "fapi_profile": "vci",
                    "sender_constrain": "dpop",
                    "client_auth_type": "mtls",
                    "credential_format": "sd_jwt_vc",
                    "vci_authorization_code_flow_variant": "issuer_initiated",
                    "authorization_request_type": "simple",
                    "fapi_request_method": "unsigned",
                    "vci_grant_type": "authorization_code",
                    "vci_credential_encryption": "plain",
                    "fapi_response_mode": "plain_response"
                }""".trimIndent()
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                "alias": "oid4vci_issuer_sd_jwt_vc_issuer_initiated",
                "description": "OID4VCI 1.0 Issuer - DPoP + MTLS (Issuer Initiated)",
                "vci": {
                    "credential_issuer_url": "$issuerUrl",
                    "credential_offer_endpoint": "$issuerUrl/credential-offer"
                },
                "client": {
                    "client_id": "conformance-test-client",
                    "jwks": $clientJwks,
                    "dpop_signing_alg": "ES256"
                },
                "client2": {
                    "client_id": "conformance-test-client-2",
                    "jwks": $clientJwks,
                    "dpop_signing_alg": "ES256"
                },
                "mtls": {
                    "cert": "-----BEGIN CERTIFICATE-----\nMIIBfTCCASOgAwIBAgIUdIGqCM6FoR7iZlGvFoFJ6CvT5/4wCgYIKoZIzj0EAwIw\nGTEXMBUGA1UEAwwOQ29uZm9ybWFuY2UgQ0EwHhcNMjQwMTAxMDAwMDAwWhcNMjUw\nMTAxMDAwMDAwWjAaMRgwFgYDVQQDDA9jb25mb3JtYW5jZS10bHMwWTATBgcqhkjO\nPQIBBggqhkjOPQMBBwNCAAQbREg0GIX6hBQPd3kMad6BC5d6cjb0kNowagy+KgpE\nE3nd3hRrNqRLa6e7wGewS3G61LaSpGFgE9iT1ECuJTeBozUwMzAJBgNVHRMEAjAA\nMAsGA1UdDwQEAwIF4DAZBgNVHREEEjAQgg5sb2NhbGhvc3Q6NzAwMjAKBggqhkjO\nPQQDAgNIADBFAiEA7E9z2RPlHPHcBxkGJvPDXFl0RJnqwE8bUHJNK0D4fAQCICXn\nKjGE0D8wq3O8SJl7xJ0Y+1n3qP7LKC0g4i8aWL+V\n-----END CERTIFICATE-----",
                    "key": "-----BEGIN EC PRIVATE KEY-----\nMHQCAQEEICieJN1XJeUo/TH6uK6zVwZtjKD6GN/wJPzyEfxILBYUoAcGBSuBBAAK\noUQDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2un\nu8BnsEtxutS2kqRhYBPYk9RAriU3gQ==\n-----END EC PRIVATE KEY-----"
                },
                "mtls2": {
                    "cert": "-----BEGIN CERTIFICATE-----\nMIIBfTCCASOgAwIBAgIUdIGqCM6FoR7iZlGvFoFJ6CvT5/4wCgYIKoZIzj0EAwIw\nGTEXMBUGA1UEAwwOQ29uZm9ybWFuY2UgQ0EwHhcNMjQwMTAxMDAwMDAwWhcNMjUw\nMTAxMDAwMDAwWjAaMRgwFgYDVQQDDA9jb25mb3JtYW5jZS10bHMwWTATBgcqhkjO\nPQIBBggqhkjOPQMBBwNCAAQbREg0GIX6hBQPd3kMad6BC5d6cjb0kNowagy+KgpE\nE3nd3hRrNqRLa6e7wGewS3G61LaSpGFgE9iT1ECuJTeBozUwMzAJBgNVHRMEAjAA\nMAsGA1UdDwQEAwIF4DAZBgNVHREEEjAQgg5sb2NhbGhvc3Q6NzAwMjAKBggqhkjO\nPQQDAgNIADBFAiEA7E9z2RPlHPHcBxkGJvPDXFl0RJnqwE8bUHJNK0D4fAQCICXn\nKjGE0D8wq3O8SJl7xJ0Y+1n3qP7LKC0g4i8aWL+V\n-----END CERTIFICATE-----",
                    "key": "-----BEGIN EC PRIVATE KEY-----\nMHQCAQEEICieJN1XJeUo/TH6uK6zVwZtjKD6GN/wJPzyEfxILBYUoAcGBSuBBAAK\noUQDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2un\nu8BnsEtxutS2kqRhYBPYk9RAriU3gQ==\n-----END EC PRIVATE KEY-----"
                }
            }
            """.trimIndent()
        ),

        issuerUrl = issuerUrl
    )
}
