package id.walt.openid4vp.conformance.testplans.plans.vp.verifier

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.openid4vp.conformance.testplans.plans.TestPlan
import id.walt.openid4vp.conformance.testplans.runner.req.TestPlanConfiguration
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.UrlConfig
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.Verification2Session.VerificationSessionRedirects
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * HAIP-compliant SD-JWT VC Verifier Test Plan
 * 
 * Tests SD-JWT VC verification with HAIP (High Assurance Interoperability Profile) requirements:
 * - Signed authorization requests (JAR)
 * - Encrypted VP responses (direct_post.jwt)
 * - x509_san_dns client identification (HAIP uses x509_san_dns, not x509_hash)
 * - P-256 key curve
 * 
 * Conformance test plan: oid4vp-1final-verifier-haip-test-plan
 * 
 * IMPORTANT: The certificate chain MUST NOT have a self-signed leaf certificate.
 * The conformance suite validates: "Leaf certificate in x5c chain must not be self-signed"
 */
class SdJwtVcX509SanDnsRequestUriSignedDirectPost(
    verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session",

    conformanceHost: String = "localhost.emobix.co.uk",
    conformancePort: Int = 8443
) : TestPlan {

    // Verifier key (P-256 as required by HAIP) - must match the leaf certificate's public key
    val verifierKey = Json.decodeFromString<DirectSerializedKey>(
        """{"type":"jwk","jwk":{"kty":"EC","crv":"P-256","d":"0piz-la29hD31ITj7uN-U6urITkQR9CKbia4PKOksag","x":"bvayR9-jroMIIAu_i8hTvbZKWLfJlqjs_L04qB29Kq0","y":"D7aI-xpoGqHJLCZshyMBexRB37Aehfb_80ep-REJzJI"}}"""
    )
    
    // Certificate chain: [leaf, intermediate] - leaf signed by intermediate CA, NOT self-signed
    // CN=verifier.example.com signed by CN=walt.id Verifier Intermediate CA
    val verifierCertificateChain = listOf(
        // Leaf certificate (signed by intermediate CA)
        "MIIB8zCCAZigAwIBAgIUWKvmcrsfgyVa5yKtMJO3rcimTw0wCgYIKoZIzj0EAwIwPTEpMCcGA1UEAwwgd2FsdC5pZCBWZXJpZmllciBJbnRlcm1lZGlhdGUgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwHhcNMjYwNzAxMTAwNTM2WhcNMjcwNzAxMTAwNTM2WjAxMR0wGwYDVQQDDBR2ZXJpZmllci5leGFtcGxlLmNvbTEQMA4GA1UECgwHd2FsdC5pZDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABG72skffo66DCCALv4vIU722Sli3yZao7Py9OKgdvSqtD7aI+xpoGqHJLCZshyMBexRB37Aehfb/80ep+REJzJKjgYEwfzAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIHgDAfBgNVHREEGDAWghR2ZXJpZmllci5leGFtcGxlLmNvbTAdBgNVHQ4EFgQU11ZoOevLE5Sc374ImQT/XSpucXMwHwYDVR0jBBgwFoAUriQP8EdKQj+FXrLUYFHBTuDwWP8wCgYIKoZIzj0EAwIDSQAwRgIhALXrw/oLNimuZlquX89d5unpzmEwFLHmMrWQntk10E++AiEApTAXvA9QSXgKhLvRjpNtXgsyx7nPELgffXV5XBvQQ4w=",
        // Intermediate CA certificate (signed by root CA)
        "MIIB2TCCAYCgAwIBAgIUFzJslsc5kJg0w5WjHp4CFJrdA4wwCgYIKoZIzj0EAwIwNTEhMB8GA1UEAwwYd2FsdC5pZCBWZXJpZmllciBSb290IENBMRAwDgYDVQQKDAd3YWx0LmlkMB4XDTI2MDcwMTEwMDUzMVoXDTMxMDYzMDEwMDUzMVowPTEpMCcGA1UEAwwgd2FsdC5pZCBWZXJpZmllciBJbnRlcm1lZGlhdGUgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQoGHsUavjtXLXyknuqh8oOa0W+UyBkLg3BOMRdiPwV5/tdZsbBrfpP98vBAxKOdX3pnJ4+kTDOPYx5nILxf2vto2YwZDASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUriQP8EdKQj+FXrLUYFHBTuDwWP8wHwYDVR0jBBgwFoAU0QRC19p+05uIc3RHfLeswJ8A40cwCgYIKoZIzj0EAwIDRwAwRAIgRLZY2AXwFUjCUB2Cm9DL1nNvoFdVKUvrfasAG6CEPnICIEPmlSIuQRsdVGRNCrykZ+mgA6MgaOGsXA7M8RsxclSn"
    )

    // Wallet/Credential signer certificate chain (for conformance suite to issue test credentials)
    val signerCertificateChain = Json.encodeToString(
        listOf("MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB")
    )

    // language=JSON
    private val signerJwk = """
        {
            "kty": "EC",
            "crv": "P-256",
            "alg": "ES256",
            "d": "KJ4k3Vcl5Sj9Mfq4rrNXBm2MoPoY3_Ak_PIR_EgsFhQ",
            "x": "G0RINBiF-oQUD3d5DGnegQuXenI29JDaMGoMvioKRBM",
            "y": "ed3eFGs2pEtrp7vAZ7BLcbrUtpKkYWAT2JPUQK4lN4E",
            "x5c": $signerCertificateChain
        }
    """.trimIndent()

    // DCQL Query for SD-JWT VC
    // language=JSON
    val dcqlQuery = """
        {
            "credentials": [
                {
                    "id": "pid",
                    "format": "dc+sd-jwt",
                    "meta": {
                        "vct_values": ["https://credentials.example.com/identity_credential"]
                    },
                    "claims": [
                        {"path": ["given_name"]},
                        {"path": ["family_name"]},
                        {"path": ["birthdate"]},
                        {"path": ["age_in_years"]}
                    ]
                }
            ]
        }
    """.trimIndent()


    override val config = TestPlanConfiguration(
        testPlanCreationUrl = {
            // Use HAIP test plan for SD-JWT VC
            // Note: The HAIP plan sets request_method internally, so don't include it
            append("planName", "oid4vp-1final-verifier-haip-test-plan")
            append(
                "variant", /* language=json*/
                """{
                    "credential_format": "sd_jwt_vc",
                    "response_mode": "direct_post.jwt"
                }""".trimIndent()
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                "credential": {
                    "signing_jwk": $signerJwk
                },
                "client": {
                    "client_id": "verifier.example.com"
                },
                "description": "HAIP Verifier - sd_jwt_vc + x509_san_dns + request_uri_signed + direct_post.jwt",
                "server": {
                    "authorization_endpoint": "https://$conformanceHost:$conformancePort"
                }
            }
        """.trimIndent()
        ),
        verificationSessionSetup = CrossDeviceFlowSetup(
            core = GeneralFlowConfig(
                dcqlQuery = Json.decodeFromString(dcqlQuery),
                policies = Verification2Session.DefinedVerificationPolicies(),

                // HAIP requirements:
                signedRequest = true,        // JAR - signed authorization requests
                encryptedResponse = true,    // direct_post.jwt - encrypted responses

                clientMetadata = ClientMetadata(),
                // For x509_san_dns, client_id is the DNS name from the cert's SAN
                clientId = "x509_san_dns:verifier.example.com",

                key = verifierKey,
                x5c = verifierCertificateChain
            ),
            urlConfig = UrlConfig(
                urlPrefix = verifier2UrlPrefix,
                //urlHost, // <-- set by TestPlanRunner
            ),
            redirects = VerificationSessionRedirects(
                successRedirectUri = Url("https://example.org/verification-success")
            )
        )
    )
}
