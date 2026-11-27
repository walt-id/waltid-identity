package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.config.ConformanceSuiteAlias
import id.walt.openid4vp.conformance.testplans.keys.ClientAttestationTestAuthority
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * VCI Wallet Test Plan: SD-JWT VC or ISO mdoc HAIP target
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
class VciWalletHaip(
    val walletApiUrl: String,
    val credentialOfferEndpoint: String,
    val redirectUri: String,
    val conformanceHost: String,
    val conformancePort: Int,
    val adapterHost: String = "127.0.0.1",
    /**
     * Attester whose certificate chain the suite validates the wallet's client attestation against.
     *
     * Passed in whole rather than as an issuer plus a PEM so the advertised trust anchor is
     * necessarily the one that signed the attestation; two loose strings can drift apart, and the
     * resulting failure surfaces as an opaque signature error.
     */
    val attestationAuthority: ClientAttestationTestAuthority,
    override val credentialFormat: String = "sd_jwt_vc",
    override val credentialOfferVariant: String = "by_value",
    override val authFlowVariant: String = "issuer_initiated",
) : VciWalletTestPlan {

    init {
        require(credentialFormat in setOf("sd_jwt_vc", "mdoc")) { "Unsupported HAIP credential format: $credentialFormat" }
        require(credentialOfferVariant in setOf("by_value", "by_reference")) {
            "Unsupported HAIP credential offer variant: $credentialOfferVariant"
        }
        require(authFlowVariant in setOf("issuer_initiated", "wallet_initiated")) {
            "Unsupported HAIP authorization flow: $authFlowVariant"
        }
        require(authFlowVariant != "wallet_initiated" || credentialOfferVariant == "by_value") {
            "Wallet-initiated issuance has no credential offer delivery variant"
        }
    }

    private val formatLabel = if (credentialFormat == "mdoc") "ISO mdoc" else "SD-JWT VC"
    private val configurationId = if (credentialFormat == "mdoc") "eu.europa.ec.eudi.pid.mdoc.1" else "eu.europa.ec.eudi.pid.1"

    companion object {
        // Key attestation is a separate HAIP feature from client attestation: it attests the
        // *proof* key inside a credential request, and the suite verifies it against this JWKS
        // (VerifyKeyAttestationSignatureUsingConfigJwks) rather than against a certificate chain.
        private val keyAttestationJwks = Json.decodeFromString<JsonObject>(
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

    override val description = "VCI Wallet: $formatLabel + $authFlowVariant + $credentialOfferVariant (HAIP target)"
    override val planName: String = "oid4vci-1_0-wallet-haip-test-plan"
    override val isHaip: Boolean = true
    override val clientAuthType: String = "client_attestation"
    override val senderConstraint: String = "dpop"
    override val grantType: String = "authorization_code"

    override val variant = mapOf(
        "credential_format" to credentialFormat,
        "vci_authorization_code_flow_variant" to authFlowVariant,
        // The HAIP plan fixes authorization_request_type itself and rejects it being restated
        // ("Variant 'authorization_request_type' has been set by user, but test plan already sets
        // this variant"), so unlike the non-HAIP plans it must not appear here.
        "vci_credential_offer_variant" to credentialOfferVariant
    )

    override val configuration: JsonObject = buildJsonObject {
        put("alias", ConformanceSuiteAlias.unique("vci_wallet_${credentialFormat}_haip"))
        put("description", "Wallet VCI - $formatLabel + client_attestation + authorization_code (HAIP full target)")

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
            put("client_id", ConformanceConfig.VCI_WALLET_CLIENT_ID)
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

        if (credentialFormat == "mdoc") {
            val credential = VciWalletMdocDpop(
                walletApiUrl, credentialOfferEndpoint, redirectUri, conformanceHost, conformancePort, adapterHost,
            ).configuration.getValue("credential").jsonObject
            val signer = credential.getValue("signing_jwk").jsonObject
            val root = signer.getValue("x5c").jsonArray.last().jsonPrimitive.content
            val anchor = "-----BEGIN CERTIFICATE-----\n${root.chunked(64).joinToString("\n")}\n-----END CERTIFICATE-----"
            putJsonObject("credential") {
                put("signing_jwk", signer)
                put("trust_anchor_pem", anchor)
                put("status_list_trust_anchor_pem", anchor)
            }
        }

        putJsonObject("client_attestation") {
            // issuer and trust_anchor are both mandatory for HAIP; AbstractVCIWalletTest refuses
            // to configure the test without them.
            put("issuer", attestationAuthority.issuer)
            put("trust_anchor", attestationAuthority.trustAnchorPem)
            // attester_jwks is deliberately absent: only the *issuer* test suite signs attestations
            // from it. For wallet tests the suite verifies our attestation against the x5c chain, so
            // publishing a JWKS here - previously including its private key - attested to nothing.
            put("key_attestation_jwks", keyAttestationJwks)
            put("key_attestation_trust_anchor_pem", trustAnchorPem)
        }

        putJsonObject("vci") {
            put("credential_offer_endpoint", ConformanceConfig.vciSuiteCredentialOfferEndpoint(adapterHost))
            put("credential_configuration_id", configurationId)
        }

        put("waitTimeoutSeconds", 30)
        put("publish", "no")
    }
}
