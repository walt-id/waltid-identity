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
 * mDL Verifier Test Plan (Plain VP, non-HAIP)
 * 
 * Tests ISO mDL (mobile Driving License) verification with:
 * - Signed authorization requests (JAR)
 * - x509_san_dns client identification
 * - plain_vp profile (non-HAIP)
 * - direct_post response mode (unencrypted)
 * - P-256 key curve
 * 
 * Conformance test plan: oid4vp-1final-verifier-test-plan
 * 
 * IMPORTANT: The certificate chain MUST NOT have a self-signed leaf certificate.
 * The conformance suite validates: "Leaf certificate in x5c chain must not be self-signed"
 */
class MdlX509SanDnsRequestUriSignedDirectPost(
    verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session",

    conformanceHost: String = "localhost.emobix.co.uk",
    conformancePort: Int = 8443
) : TestPlan {

    // Verifier key (P-256) - must match the leaf certificate's public key
    val verifierKey = Json.decodeFromString<DirectSerializedKey>(
        """{"type":"jwk","jwk":{"kty":"EC","crv":"P-256","d":"0piz-la29hD31ITj7uN-U6urITkQRw2Kbia4PKOksag","x":"bvayR9-jroMIILu_i8hTvbZKWLfJlqjs_L04qB29Kq0","y":"D7aI-xpoGqHJLCZshyMBexRB37Aehfb_80ep-REJzJI"}}"""
    )
    
    // Certificate chain: [leaf, intermediate] - leaf signed by intermediate CA, NOT self-signed
    // CN=verifier.example.com signed by CN=walt.id Verifier Intermediate CA
    val verifierCertificateChain = listOf(
        // Leaf certificate (signed by intermediate CA)
        "MIIB8zCCAZigAwIBAgIUWKvmcrsfgyVa5yKtMJO3rcimTw0wCgYIKoZIzj0EAwIwPTEpMCcGA1UEAwwgd2FsdC5pZCBWZXJpZmllciBJbnRlcm1lZGlhdGUgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwHhcNMjYwNzAxMTAwNTM2WhcNMjcwNzAxMTAwNTM2WjAxMR0wGwYDVQQDDBR2ZXJpZmllci5leGFtcGxlLmNvbTEQMA4GA1UECgwHd2FsdC5pZDBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABG72skffo66DCCALv4vIU722Sli3yZao7Py9OKgdvSqtD7aI+xpoGqHJLCZshyMBexRB37Aehfb/80ep+REJzJKjgYEwfzAMBgNVHRMBAf8EAjAAMA4GA1UdDwEB/wQEAwIHgDAfBgNVHREEGDAWghR2ZXJpZmllci5leGFtcGxlLmNvbTAdBgNVHQ4EFgQU11ZoOevLE5Sc374ImQT/XSpucXMwHwYDVR0jBBgwFoAUriQP8EdKQj+FXrLUYFHBTuDwWP8wCgYIKoZIzj0EAwIDSQAwRgIhALXrw/oLNimuZlquX89d5unpzmEwFLHmMrWQntk10E++AiEApTAXvA9QSXgKhLvRjpNtXgsyx7nPELgffXV5XBvQQ4w=",
        // Intermediate CA certificate (signed by root CA)
        "MIIB2TCCAYCgAwIBAgIUFzJslsc5kJg0w5WjHp4CFJrdA4wwCgYIKoZIzj0EAwIwNTEhMB8GA1UEAwwYd2FsdC5pZCBWZXJpZmllciBSb290IENBMRAwDgYDVQQKDAd3YWx0LmlkMB4XDTI2MDcwMTEwMDUzMVoXDTMxMDYzMDEwMDUzMVowPTEpMCcGA1UEAwwgd2FsdC5pZCBWZXJpZmllciBJbnRlcm1lZGlhdGUgQ0ExEDAOBgNVBAoMB3dhbHQuaWQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQoGHsUavjtXLXyknuqh8oOa0W+UyBkLg3BOMRdiPwV5/tdZsbBrfpP98vBAxKOdX3pnJ4+kTDOPYx5nILxf2vto2YwZDASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1UdDwEB/wQEAwIBBjAdBgNVHQ4EFgQUriQP8EdKQj+FXrLUYFHBTuDwWP8wHwYDVR0jBBgwFoAU0QRC19p+05uIc3RHfLeswJ8A40cwCgYIKoZIzj0EAwIDRwAwRAIgRLZY2AXwFUjCUB2Cm9DL1nNvoFdVKUvrfasAG6CEPnICIEPmlSIuQRsdVGRNCrykZ+mgA6MgaOGsXA7M8RsxclSn"
    )

    // DCQL query for ISO mDL verification
    // language=JSON
    val dcqlQuery = """
        {
            "credentials": [
                {
                    "id": "my_mdl",
                    "format": "mso_mdoc",
                    "meta": {
                        "doctype_value": "org.iso.18013.5.1.mDL"
                    },
                    "claims": [
                        { "path": [ "org.iso.18013.5.1", "family_name" ] },
                        { "path": [ "org.iso.18013.5.1", "given_name" ] },
                        { "path": [ "org.iso.18013.5.1", "birth_date" ] },
                        { "path": [ "org.iso.18013.5.1", "issue_date" ] },
                        { "path": [ "org.iso.18013.5.1", "expiry_date" ] },
                        { "path": [ "org.iso.18013.5.1", "issuing_country" ] },
                        { "path": [ "org.iso.18013.5.1", "issuing_authority" ] },
                        { "path": [ "org.iso.18013.5.1", "document_number" ] },
                        { "path": [ "org.iso.18013.5.1", "portrait" ] },
                        { "path": [ "org.iso.18013.5.1", "driving_privileges" ] }
                    ]
                }
            ]
        }
    """.trimIndent()


    override val config = TestPlanConfiguration(
        testPlanCreationUrl = {
            append("planName", "oid4vp-1final-verifier-test-plan")
            append(
                "variant", /* language=json*/
                """{
                    "credential_format": "iso_mdl",
                    "client_id_prefix": "x509_san_dns",
                    "request_method": "request_uri_signed",
                    "vp_profile": "plain_vp",
                    "response_mode": "direct_post"
                }""".trimIndent()
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                "client": {
                    "client_id": "verifier.example.com"
                },
                "description": "Verifier - iso_mdl + x509_san_dns + request_uri_signed + plain_vp + direct_post",
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

                signedRequest = true,        // JAR - signed authorization requests
                encryptedResponse = false,   // plain_vp - unencrypted responses

                clientMetadata = ClientMetadata(),
                // For x509_san_dns, client_id is the DNS name from the cert's SAN
                clientId = "x509_san_dns:verifier.example.com",

                key = verifierKey,
                x5c = verifierCertificateChain
            ),
            urlConfig = UrlConfig(
                urlPrefix = verifier2UrlPrefix,
                //urlHost // <-- set by TestPlanRunner
            ),
            redirects = VerificationSessionRedirects(
                successRedirectUri = Url("https://example.org/verification-success")
            )
        )
    )
}
