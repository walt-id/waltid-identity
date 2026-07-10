package id.walt.openid4vp.conformance.testplans.plans.vp.verifier

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
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
 * Tests SD-JWT VC verification with STRICT HAIP (High Assurance Interoperability Profile) compliance:
 * - Signed authorization requests (JAR) — HAIP §5.1 V-01
 * - Encrypted VP responses (direct_post.jwt) — HAIP §5.1 V-02  
 * - **x509_hash client identification** — HAIP §5 P-02 (MANDATORY)
 * - P-256 key curve (ES256) — HAIP §7 CF-02
 * - SHA-256 hash algorithm — HAIP §8 CF-03
 * - KB-JWT holder binding validation — HAIP §6.1.1.1 V-14
 * - Same-device flow — HAIP §5.1 V-03
 *
 * Conformance test plan: oid4vp-1final-verifier-haip-test-plan
 *
 * CRITICAL HAIP REQUIREMENTS:
 * - MUST use x509_hash (NOT x509_san_dns) per HAIP §5 requirement P-02
 * - Certificate chain MUST NOT include trust anchor (root CA)
 * - Leaf certificate MUST NOT be self-signed
 * - MUST support ECDH-ES with P-256 for response encryption
 * - MUST support both A128GCM and A256GCM for JWE enc
 *
 * @see <a href="https://openid.net/specs/openid4vc-high-assurance-interoperability-profile-1_0-final.html">HAIP 1.0 Specification</a>
 */
class SdJwtVcX509HashRequestUriSignedDirectPostHaip(
    verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session",

    conformanceHost: String = "localhost.emobix.co.uk",
    conformancePort: Int = 8443
) : TestPlan {

    // Verifier key (P-256 as required by HAIP §7)
    val verifierKey = Json.decodeFromString<DirectSerializedKey>(
        """{"type":"jwk","jwk":{"kty":"EC","crv":"P-256","d":"0piz-la29hD31ITj7uN-U6urITkQR9CKbia4PKOksag","x":"bvayR9-jroMIIAu_i8hTvbZKWLfJlqjs_L04qB29Kq0","y":"D7aI-xpoGqHJLCZshyMBexRB37Aehfb_80ep-REJzJI"}}"""
    )

    // Certificate chain: [leaf, intermediate] - HAIP compliant
    // - Leaf signed by intermediate CA (NOT self-signed) per HAIP §4.1 CF-05
    // - Trust anchor (root CA) NOT included per HAIP §4.1 CF-04
    val verifierCertificateChain = listOf(
        // Leaf certificate (CN=verifier.example.com, signed by intermediate CA)
        "MIIB8zCCAZigAwIBAgIUWKvmcrsfgyVa5yKtMJO3rcimTw0wCgYIKoZIzj0EAwIwPTEpMCcGA1UEAwwgd2FsdC5pZCBWZXJpZmllciBJbnRlcm1lZGlhdGUgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwHhcNMjYwNzAxMTAwNTM2WhcNMjcwNzAxMTAwNTM2WjAxMR0wGwYDVQQDDBR2ZXJpZmllci5leGFtcGxlLmNvbTEQMA4GA1UECgwHd2FsdC5pZDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABG72skffo66DCCALv4vIU722Sli3yZao7Py9OKgdvSqtD7aI+xpoGqHJLCZshyMBexRB37Aehfb/80ep+REJzJKjgYEwfzAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIHgDAfBgNVHREEGDAWghR2ZXJpZmllci5leGFtcGxlLmNvbTAdBgNVHQ4EFgQU11ZoOevLE5Sc374ImQT/XSpucXMwHwYDVR0jBBgwFoAUriQP8EdKQj+FXrLUYFHBTuDwWP8wCgYIKoZIzj0EAwIDSQAwRgIhALXrw/oLNimuZlquX89d5unpzmEwFLHmMrWQntk10E++AiEApTAXvA9QSXgKhLvRjpNtXgsyx7nPELgffXV5XBvQQ4w=",
        // Intermediate CA certificate (signed by root CA, root NOT included)
        "MIIB2TCCAYCgAwIBAgIUFzJslsc5kJg0w5WjHp4CFJrdA4wwCgYIKoZIzj0EAwIwNTEhMB8GA1UEAwwYd2FsdC5pZCBWZXJpZmllciBSb290IENBMRAwDgYDVQQKDAd3YWx0LmlkMB4XDTI2MDcwMTEwMDUzMVoXDTMxMDYzMDEwMDUzMVowPTEpMCcGA1UEAwwgd2FsdC5pZCBWZXJpZmllciBJbnRlcm1lZGlhdGUgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQoGHsUavjtXLXyknuqh8oOa0W+UyBkLg3BOMRdiPwV5/tdZsbBrfpP98vBAxKOdX3pnJ4+kTDOPYx5nILxf2vto2YwZDASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUriQP8EdKQj+FXrLUYFHBTuDwWP8wHwYDVR0jBBgwFoAU0QRC19p+05uIc3RHfLeswJ8A40cwCgYIKoZIzj0EAwIDRwAwRAIgRLZY2AXwFUjCUB2Cm9DL1nNvoFdVKUvrfasAG6CEPnICIEPmlSIuQRsdVGRNCrykZ+mgA6MgaOGsXA7M8RsxclSn"
    )

    // Wallet/Credential signer certificate chain (for conformance suite to issue test credentials)
    val signerCertificateChain = Json.encodeToString(
        listOf("MIIBeTCCAR8CFHrWgrGl5KdefSvRQhR+aoqdf48+MAoGCCqGSM49BAMCMBcxFTATBgNVBAMMDE1ET0MgUk9PVCBDQTAgFw0yNTA1MTQxNDA4MDlaGA8yMDc1MDUwMjE0MDgwOVowZTELMAkGA1UEBhMCQVQxDzANBgNVBAgMBlZpZW5uYTEPMA0GA1UEBwwGVmllbm5hMRAwDgYDVQQKDAd3YWx0LmlkMRAwDgYDVQQLDAd3YWx0LmlkMRAwDgYDVQQDDAd3YWx0LmlzMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG0RINBiF+oQUD3d5DGnegQuXenI29JDaMGoMvioKRBN53d4UazakS2unu8BnsEtxutS2kqRhYBPYk9RAriU3gTAKBggqhkjOPQQDAgNIADBFAiAOMwM7hH7q9Di+mT6qCi4LvB+kH8OxMheIrZ2eRPxtDQIhALHzTxwvN8Udt0Z2Cpo8JBihqacfeXkIxVAO8XkxmXhB")
    )

    // Signer JWK with x5c chain (for conformance suite to issue test credentials with holder binding)
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

    // DCQL Query for SD-JWT VC with selective disclosure
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
            // HAIP-specific test plan for SD-JWT VC
            // The HAIP plan automatically uses x509_hash client_id_prefix
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
                    "x509_certificate_chain": ${Json.encodeToString(verifierCertificateChain)},
                    "request_object_trust_anchor_pem": ${TestKeyMaterial.VERIFIER_ROOT_CA_PEM_JSON}
                },
                "description": "HAIP Verifier - SD-JWT VC + x509_hash + JAR + direct_post.jwt + P-256 + SHA-256",
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

                // HAIP mandatory requirements:
                signedRequest = true,        // JAR - HAIP §5.1 V-01
                encryptedResponse = true,    // direct_post.jwt - HAIP §5.1 V-02

                clientMetadata = ClientMetadata(
                    // HAIP §5.1 V-07: Must support both A128GCM and A256GCM
                    encryptedResponseEncValuesSupported = listOf("A128GCM", "A256GCM")
                ),

                // HAIP §5 P-02: MUST use x509_hash
                // For x509_hash, client_id format is: "x509_hash:" + base64url(SHA-256(DER(leaf_cert)))
                // The conformance suite will validate this matches the certificate in x5c
                clientId = null, // Let verifier compute x509_hash from x5c

                key = verifierKey,
                x5c = verifierCertificateChain
            ),
            urlConfig = UrlConfig(
                urlPrefix = verifier2UrlPrefix,
                //urlHost, // <-- set by TestPlanRunner
            ),
            redirects = VerificationSessionRedirects(
                // HAIP §5.1 V-05: redirect_uri for same-device flow
                successRedirectUri = Url("https://example.org/verification-success")
            )
        )
    )
}
