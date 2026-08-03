package id.walt.openid4vp.conformance.testplans.plans.vci.wallet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path

/** Creates the suite configuration for one [WalletVariant]. */
class Oid4vciWalletVariantPlan(
    val variantContext: WalletVariant,
    private val adapterPublicUrl: String,
    private val clientId: String,
    private val clientAttestationIssuer: String,
    private val clientAttesterJwks: JsonObject,
    private val clientAttestationTrustAnchorPem: String,
    private val keyAttestationTrustAnchorPem: String,
    private val clientCertificatePem: String,
) : VciWalletTestPlan {
    override val description: String = variantContext.description
    override val planName: String = variantContext.conformanceTestPlanName
    override val variant: Map<String, String> = variantContext.toJsonObject()
        .mapValues { (_, value) -> value.toString().trim('"') }

    override val configuration: JsonObject = buildJsonObject {
        put("alias", "vci_wallet_${variantContext.id}")
        put("description", variantContext.description)
        putJsonObject("server") {
            putJsonObject("jwks") {
                put("keys", serverJwks["keys"]!!)
            }
        }
        putJsonObject("client") {
            put("client_id", clientId)
            put("redirect_uri", "$adapterPublicUrl/callback")
            put("certificate", clientCertificatePem)
            putJsonObject("jwks") {
                put("keys", clientJwks["keys"]!!)
            }
        }
        putJsonObject("credential") {
            put("trust_anchor_pem", credentialTrustAnchorPem)
            put("status_list_trust_anchor_pem", credentialTrustAnchorPem)
            put("signing_jwk", credentialSigningJwk)
        }
        putJsonObject("client_attestation") {
            put("issuer", clientAttestationIssuer)
            put("trust_anchor", clientAttestationTrustAnchorPem)
            put("attester_jwks", clientAttesterJwks)
            put("key_attestation_jwks", clientAttesterJwks)
            put("key_attestation_trust_anchor_pem", keyAttestationTrustAnchorPem)
        }
        putJsonObject("vci") {
            if (variantContext.authorizationCodeFlowVariant != "wallet_initiated") {
                put("credential_offer_endpoint", "$adapterPublicUrl/credential-offer")
            }
            // release-v5.1.x reads these legacy fields. Current suite releases
            // read the top-level client_attestation object above.
            if (variantContext.clientAuthType == "client_attestation") {
                put("client_attestation_issuer", clientAttestationIssuer)
                put("client_attestation_trust_anchor", clientAttestationTrustAnchorPem)
            }
            put(
                "credential_configuration_id",
                if (variantContext.credentialFormat == "mdoc") "eu.europa.ec.eudi.pid.mdoc.1" else "eu.europa.ec.eudi.pid.1"
            )
        }
        put("waitTimeoutSeconds", 120)
        put("maxWaitForNotificationSeconds", 20)
        put("publish", "no")
    }

    override val isHaip: Boolean
        get() = variantContext.isHaip

    override val credentialFormat: String
        get() = variantContext.credentialFormat

    override val grantType: String
        get() = variantContext.grantType

    override val senderConstraint: String
        get() = variantContext.senderConstrain

    override val clientAuthType: String
        get() = variantContext.clientAuthType

    companion object {
        private val serverJwks = jsonObject(
            """{"keys":[{"kty":"EC","crv":"P-256","x":"G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM","y":"ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E","use":"sig","alg":"ES256","kid":"issuer-key-1"}]}"""
        )
        private val clientJwks = jsonObject(
            """{"keys":[{"kty":"EC","crv":"P-256","x":"d5KVpCdze-46QteHfgAswRurlSYUylJ1JntvcbaZ__Y","y":"uqvaPeOm7SGsdXr34frqkJGAz8tHmR0EmpsSbfqgwDA","use":"sig","alg":"ES256","kid":"wallet-static-key"}]}"""
        )
        private val credentialSigningJwk = jsonObject(
            """{"kty":"EC","crv":"P-256","x":"HsIzLDaBvEhYF8u_Rs-UMk82ISNOMvipGCpyfCjA1nk","y":"c9oagfJlhCdS15GtMCW80liuR4LOAX21xSxA7Z0-efc","d":"c6XRnq85BooKJ3D7VAJGJ0NxZy9uROeCn5_a58eC8Bs","use":"sig","alg":"ES256","kid":"credential-key-1","x5c":["MIIBsDCCAVagAwIBAgIUW1zQSPkvzf4gXBvZXVO31XXQqYowCgYIKoZIzj0EAwIwNDEbMBkGA1UEAwwSVGVzdCBDcmVkZW50aWFsIENBMRUwEwYDVQQKDAxXYWx0LmlkIFRlc3QwHhcNMjYwNjMwMTA1MDQ1WhcNMjcwNjMwMTA1MDQ1WjA4MR8wHQYDVQQDDBZUZXN0IENyZWRlbnRpYWwgSXNzdWVyMRUwEwYDVQQKDAxXYWx0LmlkIFRlc3QwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQewjMsNoG8SFgXy79Gz5QyTzYhI04y+KkYKnJ8KMDWeXPaGoHyZYQnUteRrTAlvNJYrkeCzgF9tcUsQO2dPnn3o0IwQDAdBgNVHQ4EFgQUmHVulwcARfk/UwZlcZYf62xNJJUwHwYDVR0jBBgwFoAUOE24Bp12XncLtXO7LutemEtlilgwCgYIKoZIzj0EAwIDSAAwRQIgNLI1BNpbilApznhdYLrWeCE0m2/M2w1k0QYRrBjNyBUCIQDRm9ziS59vWRP1glgKkAmavHX+B2cDObrjIYmFR1KuIg==","MIIBvDCCAWOgAwIBAgIUFqjPwMAClt39/DebJo3PqCtEPv0wCgYIKoZIzj0EAwIwNDEbMBkGA1UEAwwSVGVzdCBDcmVkZW50aWFsIENBMRUwEwYDVQQKDAxXYWx0LmlkIFRlc3QwHhcNMjYwNjMwMTA1MDQ1WhcNMzYwNjI3MTA1MDQ1WjA0MRswGQYDVQQDDBJUZXN0IENyZWRlbnRpYWwgQ0ExFTATBgNVBAoMDFdhbHQuaWQgVGVzdDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABBI8zD1vGFC3ySjjiFI4WEgLRgLkwWkiSBMdu6VumEEHUx21wI++nWDXNhAF2JgOd3J0hkSuixrOcNTkhwpuFN6jUzBRMB0GA1UdDgQWBBQ4TbgGnXZedwu1c7su616YS2WKWDAfBgNVHSMEGDAWgBQ4TbgGnXZedwu1c7su616YS2WKWDAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIAg9X48chJZjEAutvzvaYxGHVdNx/PP23tUPEpzrhY7iAiBkyDPmXoRVPSFbfU+t9QDqayd1ZQyKkBQ9giJ+RmJwUQ=="]}"""
        )

        val credentialTrustAnchorPem: String = """
            -----BEGIN CERTIFICATE-----
            MIIBvDCCAWOgAwIBAgIUFqjPwMAClt39/DebJo3PqCtEPv0wCgYIKoZIzj0EAwIw
            NDEbMBkGA1UEAwwSVGVzdCBDcmVkZW50aWFsIENBMRUwEwYDVQQKDAxXYWx0Lmlk
            IFRlc3QwHhcNMjYwNjMwMTA1MDQ1WhcNMzYwNjI3MTA1MDQ1WjA0MRswGQYDVQQD
            DBJUZXN0IENyZWRlbnRpYWwgQ0ExFTATBgNVBAoMDFdhbHQuaWQgVGVzdDBZMBMG
            ByqGSM49AgEGCCqGSM49AwEHA0IABBI8zD1vGFC3ySjjiFI4WEgLRgLkwWkiSBMd
            u6VumEEHUx21wI++nWDXNhAF2JgOd3J0hkSuixrOcNTkhwpuFN6jUzBRMB0GA1Ud
            DgQWBBQ4TbgGnXZedwu1c7su616YS2WKWDAfBgNVHSMEGDAWgBQ4TbgGnXZedwu1
            c7su616YS2WKWDAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0cAMEQCIAg9
            X48chJZjEAutvzvaYxGHVdNx/PP23tUPEpzrhY7iAiBkyDPmXoRVPSFbfU+t9QDq
            ayd1ZQyKkBQ9giJ+RmJwUQ==
            -----END CERTIFICATE-----
        """.trimIndent()

