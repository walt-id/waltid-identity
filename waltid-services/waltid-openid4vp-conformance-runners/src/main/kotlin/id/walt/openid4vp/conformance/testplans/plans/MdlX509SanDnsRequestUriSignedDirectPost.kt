package id.walt.openid4vp.conformance.testplans.plans

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.openid4vp.conformance.testplans.runner.req.TestPlanConfiguration
import id.walt.openid4vp.verifier.Verification2Session
import id.walt.openid4vp.verifier.Verification2Session.VerificationSessionRedirects
import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionSetup
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

object MdlX509SanDnsRequestUriSignedDirectPost : TestPlan {

    // Verifier
    val verifierKey = Json.decodeFromString<DirectSerializedKey>("""{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}""")
    val verifierCertificateChain = listOf("MIIBVzCB/aADAgECAggNKZAvUrtimzAKBggqhkjOPQQDAjAfMR0wGwYDVQQDDBR2ZXJpZmllci5leGFtcGxlLmNvbTAeFw0yNTEwMTQwNjI0MjBaFw0yNjEwMTQwNjI0MjBaMB8xHTAbBgNVBAMMFHZlcmlmaWVyLmV4YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEG/TgBc0BkmMipiQ/6gkamIn3mmp7hcTrZuyrLTmknP1WRExl1dhdIx9/kAkuuceI3THkxXq7/y+sBzK0ZR7jPqMjMCEwHwYDVR0RBBgwFoIUdmVyaWZpZXIuZXhhbXBsZS5jb20wCgYIKoZIzj0EAwIDSQAwRgIhAOu0RGM6BjVQUepeLBogw+ZD3MQ9vFppbPIGMPjtn/qdAiEAttfdfyXHfzJ2tr+Pczyckzv3NlM43461cvP96sIzOQA=")

    // DCQL

    // language=JSON
    val dcqlQuery = """
        {
            "credentials": [
                {
                    "id": "my_photoid",
                    "format": "mso_mdoc",
                    "meta": {
                        "doctype_value": "org.iso.23220.photoid.1"
                    },
                    "claims": [
                        { "path": [ "org.iso.18013.5.1", "family_name_unicode" ] },
                        { "path": [ "org.iso.18013.5.1", "given_name_unicode" ] },
                        { "path": [ "org.iso.18013.5.1", "issuing_authority_unicode" ] },
                        {
                            "path": [ "org.iso.18013.5.1", "resident_postal_code" ],
                            "values": [ 1180, 1190, 1200, 1210 ]
                        },
                        {
                            "path": [ "org.iso.18013.5.1", "issuing_country" ],
                            "values": [ "AT" ]
                        },
                        { "path": [ "org.iso.23220.photoid.1", "person_id" ] },
                        { "path": [ "org.iso.23220.photoid.1", "resident_street" ] },
                        { "path": [ "org.iso.23220.photoid.1", "administrative_number" ] },
                        { "path": [ "org.iso.23220.photoid.1", "travel_document_number" ] },
                        { "path": [ "org.iso.23220.dtc.1", "dtc_version" ] },
                        { "path": [ "org.iso.23220.dtc.1", "dtc_dg1" ] }
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
                           "response_mode": "direct_post"
                         }""".trimIndent()
            )
        },

        testPlanCreationConfiguration = Json.decodeFromString<JsonObject>(
            """
            {
                
                "client": {
                    "client_id": "test123"
                },
                "description": "Verifier - iso_mdl + x509_san_dns + request_uri_signed + direct_post",
                "server": {
                    "authorization_endpoint": "https://localhost.emobix.co.uk:8443"
                }
            }
        """.trimIndent()
        ),
        verificationSessionSetup = VerificationSessionSetup(
            dcqlQuery = Json.decodeFromString(dcqlQuery),
            policies = Verification2Session.DefinedVerificationPolicies(),

            signedRequest = true,
            encryptedResponse = false,

            urlPrefix = "https://verifier2.localhost/verification-session",
            //urlHost, // <-- set by TestPlanRunner
            redirects = VerificationSessionRedirects(
                successRedirectUri = "https://example.org/veriifcation-success"
            ),
            clientMetadata = ClientMetadata(),
            clientId = "x509_san_dns:test123",

            key = verifierKey,
            x5c = verifierCertificateChain
        )
    )
}
