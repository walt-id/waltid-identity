package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * VCI Wallet Test Plan: SD-JWT VC HAIP full target
 *
 * This profile targets the full HAIP wallet conformance suite contract.
 *
 * It must therefore call the HAIP-specific conformance plan with the suite's
 * required HAIP defaults, even if the current wallet implementation still fails
 * the resulting modules at runtime.
 *
 * The suite-side HAIP plan already fixes:
 * - sender_constrain = dpop
 * - client_auth_type = client_attestation
 * - fapi_profile = vci_haip
 * - authorization_request_type = simple
 * - fapi_request_method = unsigned
 * - vci_grant_type = authorization_code
 * - fapi_response_mode = plain_response
 *
 * This runner therefore only sets the remaining wallet-specific selectors here
 * and supplies the HAIP-specific configuration objects required by the suite.
 */
class VciWalletSdJwtHaip(
    val walletApiUrl: String,
    val credentialOfferEndpoint: String,
    val redirectUri: String,
    val conformanceHost: String,
    val conformancePort: Int,
    val adapterHost: String = "127.0.0.1"
) : VciWalletTestPlan {

    companion object {
        private const val clientAttestationIssuer = "https://wallet.test.attester.example"

        // Test attester key used to satisfy HAIP client-attestation configuration requirements.
        private val clientAttesterJwks = Json.decodeFromString<JsonObject>(
            """
            {
              "keys": [
                {
                  "kty": "EC",
                  "d": "LdeE1GY68degnCA4f49mcjIOhWoubfeIs_KekzTeFm0",
                  "crv": "P-256",
                  "kid": "h0DQmZoTW_Qw4pNR5hszRscrSRaxB1anD9XCLyfh0lc",
                  "x": "qcYiEHtnh5ikzzycbk3Lfsi36b98KrSYdDbO9zPysK0",
                  "y": "9tJsFltI2Ddw95EI2mX9J7bu7UveP3vV280Buhiryyg"
                }
              ]
            }
            """.trimIndent()
        )

        private val trustAnchorPem = """
            -----BEGIN CERTIFICATE-----
            MIIBvzCCAWWgAwIBAgIUfwihQAhmEdaEwBYsG+ejcHcFjTwwCgYIKoZIzj0EAwIw
            NTEhMB8GA1UEAwwYd2FsdC5pZCBWZXJpZmllciBSb290IENBMRAwDgYDVQQKDAd3
            YWx0LmlkMB4XDTI2MDcwMTEwMDUyNVoXDTM2MDYyODEwMDUyNVowNTEhMB8GA1UE
            AwwYd2FsdC5pZCBWZXJpZmllciBSb290IENBMRAwDgYDVQQKDAd3YWx0LmlkMFkw
            EwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXBk/JIOavtCGTtnheu6Ow3KEUzrXANwX
            P2XfbZQ+MG8jwJy37glKsQdJqs2t+l4AnlU10881D27TFUm5aq5286NTMFEwHQYD
            VR0OBBYEFNEEQtfaftObiHN0R3y3rMCfAONHMB8GA1UdIwQYMBaAFNEEQtfaftOb
            iHN0R3y3rMCfAONHMA8GA1UdEwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIh
            AMDtiBAc264oLWmkjWbckhhZ/XbC9rCdBV5Lu3M/aWCAAiBKUMq7gaf7i9iKNT30
            gw4g1u9yPw6wqf/QCx3ODl3BJg==
            -----END CERTIFICATE-----
        """.trimIndent()
    }

    override val description = "VCI Wallet: SD-JWT VC + authorization_code (HAIP full target)"
    override val planName: String = "oid4vci-1_0-wallet-haip-test-plan"
    override val isHaip: Boolean = true
    override val clientAuthType: String = "client_attestation"
    override val senderConstraint: String = "dpop"
    override val grantType: String = "authorization_code"

    override val variant = mapOf(
        "credential_format" to "sd_jwt_vc",
        "vci_authorization_code_flow_variant" to "issuer_initiated",
        "vci_credential_offer_variant" to "by_value"
    )

    override val configuration: JsonObject = buildJsonObject {
        put("alias", "vci_wallet_sdjwt_haip_full_target")
        put("description", "Wallet VCI - SD-JWT VC + client_attestation + authorization_code (HAIP full target)")

        putJsonObject("server") {
            putJsonObject("jwks") {
                put("keys", Json.decodeFromString<JsonObject>(
                    """
                    {
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
                    """.trimIndent()
                )["keys"]!!)
            }
        }

        putJsonObject("client") {
            put("client_id", "wallet-conformance-test")
            put("redirect_uri", redirectUri)
            putJsonObject("jwks") {
                put("keys", Json.decodeFromString<JsonObject>(
                    """
                    {
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
                    """.trimIndent()
                )["keys"]!!)
            }
        }

        putJsonObject("credential") {
            put("trust_anchor_pem", trustAnchorPem)
            put("status_list_trust_anchor_pem", trustAnchorPem)
            putJsonObject("signing_jwk") {
                put("kty", "EC")
                put("crv", "P-256")
                put("x", "HsIzLDaBvEhYF8u_Rs-UMk82ISNOMvipGCpyfCjA1nk")
                put("y", "c9oagfJlhCdS15GtMCW80liuR4LOAX21xSxA7Z0-efc")
                put("d", "c6XRnq85BooKJ3D7VAJGJ0NxZy9uROeCn5_a58eC8Bs")
                put("use", "sig")
                put("alg", "ES256")
                put("kid", "credential-key-1")
                put("x5c", Json.decodeFromString<JsonObject>(
                    """
                    {
                      "x5c": [
                        "MIIBsDCCAVagAwIBAgIUW1zQSPkvzf4gXBvZXVO31XXQqYowCgYIKoZIzj0EAwIwNDEbMBkGA1UEAwwSVGVzdCBDcmVkZW50aWFsIENBMRUwEwYDVQQKDAxXYWx0LmlkIFRlc3QwHhcNMjYwNjMwMTA1MDQ1WhcNMjcwNjMwMTA1MDQ1WjA4MR8wHQYDVQQDDBZUZXN0IENyZWRlbnRpYWwgSXNzdWVyMRUwEwYDVQQKDAxXYWx0LmlkIFRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQewjMsNoG8SFgXy79Gz5QyTzYhI04y+KkYKnJ8KMDWeXPaGoHyZYQnUteRrTAlvNJYrkeCzgF9tcUsQO2dPnn3o0IwQDAdBgNVHQ4EFgQUmHVulwcARfk/UwZlcZYf62xNJJUwHwYDVR0jBBgwFoAUOE24Bp12XncLtXO7LutemEtlilgwCgYIKoZIzj0EAwIDSAAwRQIgNLI1BNpbilApznhdYLrWeCE0m2/M2w1k0QYRrBjNyBUCIQDRm9ziS59vWRP1glgKkAmavHX+B2cDObrjIYmFR1KuIg==",
                        "MIIBvDCCAWOgAwIBAgIUFqjPwMAClt39/DebJo3PqCtEPv0wCgYIKoZIzj0EAwIwNDEbMBkGA1UEAwwSVGVzdCBDcmVkZW50aWFsIENBMRUwEwYDVQQKDAxXYWx0LmlkIFRlc3QwHhcNMjYwNjMwMTA1MDQ1WhcNMzYwNjI3MTA1MDQ1WjA0MRswGQYDVQQDDBJUZXN0IENyZWRlbnRpYWwgQ0ExFTATBgNVBAoMDFdhbHQuaWQgVGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABBI8zD1vGFC3ySjjiFI4WEgLRgLkwWkiSBMdu6VumEEHUx21wI++nWDXNhAF2JgOd3J0hkSuixrOcNTkhwpuFN6jUzBRMB0GA1UdDgQWBBQ4TbgGnXZedwu1c7su616YS2WKWDAfBgNVHSMEGDAWgBQ4TbgGnXZedwu1c7su616YS2WKWDAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIAg9X48chJZjEAutvzvaYxGHVdNx/PP23tUPEpzrhY7iAiBkyDPmXoRVPSFbfU+t9QDqayd1ZQyKkBQ9giJ+RmJwUQ=="
                      ]
                    }
                    """.trimIndent()
                )["x5c"]!!)
            }
        }

        putJsonObject("client_attestation") {
            put("issuer", clientAttestationIssuer)
            put("trust_anchor", trustAnchorPem)
            put("attester_jwks", clientAttesterJwks)
            put("key_attestation_jwks", clientAttesterJwks)
            put("key_attestation_trust_anchor_pem", trustAnchorPem)
        }

        putJsonObject("vci") {
            put("credential_offer_endpoint", "http://$adapterHost:7007/credential-offer")
            put("credential_configuration_id", "eu.europa.ec.eudi.pid.1")
        }

        put("waitTimeoutSeconds", 120)
        put("publish", "no")
    }
}
