package id.walt.openid4vp.conformance.testplans.vci.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * VCI Wallet Test Plan: SD-JWT VC with DPoP
 *
 * Tests wallet's ability to receive SD-JWT VC credentials from an issuer
 * using DPoP sender constraint and authorization_code grant.
 *
 * ## Test Configuration
 *
 * | Property | Value |
 * |----------|-------|
 * | Credential Format | sd_jwt_vc |
 * | Sender Constraint | dpop |
 * | Client Authentication | private_key_jwt |
 * | Grant Type | authorization_code |
 * | Flow Variant | issuer_initiated |
 * | FAPI Profile | vci |
 * | Credential Encryption | plain |
 * | Issuance Mode | immediate |
 *
 * ## Certificate Chain
 *
 * The configuration includes a CA-signed certificate chain for HAIP 4.5.1 compliance.
 * Self-signed certificates are not allowed for credential signing.
 *
 * ## Test Modules
 *
 * - `oid4vci-1_0-wallet-test-credential-issuance` - Basic credential issuance
 * - `oid4vci-1_0-wallet-test-credential-issuance-notification` - With notification
 * - `oid4vci-1_0-wallet-happy-path-with-scopes-...` - Scopes-based authorization
 *
 * @param walletApiUrl Base URL of wallet-api2
 * @param credentialOfferEndpoint Endpoint where conformance suite sends offers
 * @param redirectUri OAuth redirect URI for callbacks
 * @param conformanceHost Conformance suite hostname
 * @param conformancePort Conformance suite port
 * @param adapterHost Host IP for Docker container access (default 127.0.0.1)
 */
class VciWalletSdJwtDpop(
    val walletApiUrl: String,
    val credentialOfferEndpoint: String,
    val redirectUri: String,
    val conformanceHost: String,
    val conformancePort: Int,
    val adapterHost: String = "127.0.0.1"
) : VciWalletTestPlan {

    override val description = "VCI Wallet: SD-JWT VC + DPoP + private_key_jwt + authorization_code"

    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "sender_constrain" to "dpop",
        "client_auth_type" to "private_key_jwt",
        "vci_grant_type" to "authorization_code",
        "vci_authorization_code_flow_variant" to "issuer_initiated",
        "authorization_request_type" to "simple",
        "fapi_request_method" to "unsigned",
        "vci_credential_encryption" to "plain",
        "vci_credential_issuance_mode" to "immediate",
        "vci_credential_offer_variant" to "by_value",
        "fapi_profile" to "vci"
    )

    /**
     * Configuration JSON for the conformance suite.
     *
     * Includes:
     * - Server JWKS (issuer signing key)
     * - Client JWKS (wallet key, no private key)
     * - Credential signing JWK with CA-signed x5c chain
     * - VCI-specific settings (credential offer endpoint, configuration ID)
     */
    override val configuration: JsonObject = Json.decodeFromString(
        """
        {
            "alias": "vci_wallet_sdjwt_dpop",
            "description": "Wallet VCI - SD-JWT VC + DPoP + private_key_jwt + authorization_code",
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
                        "MIIBsDCCAVagAwIBAgIUW1zQSPkvzf4gXBvZXVO31XXQqYowCgYIKoZIzj0EAwIwNDEbMBkGA1UEAwwSVGVzdCBDcmVkZW50aWFsIENBMRUwEwYDVQQKDAxXYWx0LmlkIFRlc3QwHhcNMjYwNjMwMTA1MDQ1WhcNMjcwNjMwMTA1MDQ1WjA4MR8wHQYDVQQDDBZUZXN0IENyZWRlbnRpYWwgSXNzdWVyMRUwEwYDVQQKDAxXYWx0LmlkIFRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQewjMsNoG8SFgXy79Gz5QyTzYhI04y+KkYKnJ8KMDWeXPaGoHyZYQnUteRrTAlvNJYrkeCzgF9tcUsQO2dPnn3o0IwQDAdBgNVHQ4EFgQUmHVulwcARfk/UwZlcZYf62xNJJUwHwYDVR0jBBgwFoAUOE24Bp12XncLtXO7LutemEtlilgwCgYIKoZIzj0EAwIDSAAwRQIgNLI1BNpbilApznhdYLrWeCE0m2/M2w1k0QYRrBjNyBUCIQDRm9ziS59vWRP1glgKkAmavHX+B2cDObrjIYmFR1KuIg==",
                        "MIIBvDCCAWOgAwIBAgIUFqjPwMAClt39/DebJo3PqCtEPv0wCgYIKoZIzj0EAwIwNDEbMBkGA1UEAwwSVGVzdCBDcmVkZW50aWFsIENBMRUwEwYDVQQKDAxXYWx0LmlkIFRlc3QwHhcNMjYwNjMwMTA1MDQ1WhcNMzYwNjI3MTA1MDQ1WjA0MRswGQYDVQQDDBJUZXN0IENyZWRlbnRpYWwgQ0ExFTATBgNVBAoMDFdhbHQuaWQgVGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABBI8zD1vGFC3ySjjiFI4WEgLRgLkwWkiSBMdu6VumEEHUx21wI++nWDXNhAF2JgOd3J0hkSuixrOcNTkhwpuFN6jUzBRMB0GA1UdDgQWBBQ4TbgGnXZedwu1c7su616YS2WKWDAfBgNVHSMEGDAWgBQ4TbgGnXZedwu1c7su616YS2WKWDAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIAg9X48chJZjEAutvzvaYxGHVdNx/PP23tUPEpzrhY7iAiBkyDPmXoRVPSFbfU+t9QDqayd1ZQyKkBQ9giJ+RmJwUQ=="
                    ]
                }
            },
            "vci": {
                "credential_offer_endpoint": "http://$adapterHost:7007/credential-offer",
                "credential_configuration_id": "org.iso.18013.5.1.mDL"
            },
            "waitTimeoutSeconds": 120,
            "publish": "no"
        }
        """.trimIndent()
    )
}
